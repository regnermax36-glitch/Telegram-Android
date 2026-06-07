package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private float pitch;
    private float roll;
    private int iso;
    private String shutterSpeed = "1/100";
    private double altitude;
    private float exposure;

    private final SensorManager sensorManager;
    private final Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.15f)));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        labelPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.6f)));
        labelPaint.setTextSize(AndroidUtilities.dp(9));
        labelPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    private long lastMetadataUpdate;
    public void updateMetadata(Camera camera) {
        if (camera == null || System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();
        try {
            Camera.Parameters params = camera.getParameters();
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) {
                try {
                    iso = Integer.parseInt(isoVal);
                } catch (Exception ignore) {}
            }

            float exposureTime = 0;
            String exposureTimeVal = params.get("exposure-time");
            if (exposureTimeVal != null) {
                try {
                    exposureTime = Float.parseFloat(exposureTimeVal);
                } catch (Exception ignore) {}
            }
            if (exposureTime > 0) {
                if (exposureTime < 1.0f) {
                    shutterSpeed = String.format(Locale.US, "1/%d", Math.round(1.0f / exposureTime));
                } else {
                    shutterSpeed = String.format(Locale.US, "%.1fs", exposureTime);
                }
            }

            exposure = params.getExposureCompensation() * params.getExposureCompensationStep();

            Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
            if (location != null) {
                altitude = location.getAltitude();
            }
            invalidate();
        } catch (Exception ignore) {}
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (characteristics == null || result == null || System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();
        try {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (isoVal != null) {
                iso = isoVal;
            }

            Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTimeNs != null) {
                float exposureTime = exposureTimeNs / 1_000_000_000.0f;
                if (exposureTime < 1.0f) {
                    shutterSpeed = String.format(Locale.US, "1/%d", Math.round(1.0f / exposureTime));
                } else {
                    shutterSpeed = String.format(Locale.US, "%.1fs", exposureTime);
                }
            }

            Integer exposureCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            if (exposureCompensation != null && step != null) {
                exposure = exposureCompensation * step.floatValue();
            }

            Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
            if (location != null) {
                altitude = location.getAltitude();
            }
            invalidate();
        } catch (Exception ignore) {}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!canvas.isHardwareAccelerated()) return;

        float x = AndroidUtilities.dp(16);
        float y = AndroidUtilities.dp(16);
        float w = AndroidUtilities.dp(100);
        float h = AndroidUtilities.dp(140);
        float r = AndroidUtilities.dp(20);

        rectF.set(x, y, x + w, y + h);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rectF, backgroundPaint);
        canvas.restore();

        float textX = x + AndroidUtilities.dp(12);
        float textY = y + AndroidUtilities.dp(20);
        float lineH = AndroidUtilities.dp(24);

        drawTelemetry(canvas, "ISO", String.valueOf(iso), textX, textY);
        drawTelemetry(canvas, "SHTR", shutterSpeed, textX, textY + lineH);
        drawTelemetry(canvas, "PITCH", String.format(Locale.US, "%.1f°", pitch), textX, textY + lineH * 2);
        drawTelemetry(canvas, "ROLL", String.format(Locale.US, "%.1f°", roll), textX, textY + lineH * 3);
        drawTelemetry(canvas, "ALTI", String.format(Locale.US, "%.0fm", altitude), textX, textY + lineH * 4);
        drawTelemetry(canvas, "EV", String.format(Locale.US, "%+.1f", exposure), textX, textY + lineH * 5);
    }

    private void drawTelemetry(Canvas canvas, String label, String value, float x, float y) {
        canvas.drawText(label, x, y, labelPaint);
        canvas.drawText(value, x, y + AndroidUtilities.dp(12), textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            pitch = (float) Math.toDegrees(orientationAngles[1]);
            roll = (float) Math.toDegrees(orientationAngles[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }
}
