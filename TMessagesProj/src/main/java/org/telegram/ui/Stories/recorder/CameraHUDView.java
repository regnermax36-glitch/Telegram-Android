package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private int currentAccount;
    private SensorManager sensorManager;
    private Sensor rotationSensor;

    private float pitch;
    private float roll;
    private int iso;
    private String shutterSpeed = "--";
    private float exposure;
    private double altitude;

    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        backgroundPaint.setColor(0x20ffffff);

        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    public void updateMetadata(Camera camera) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                String isoValue = params.get("iso");
                if (isoValue == null) isoValue = params.get("iso-speed");
                if (isoValue == null) isoValue = params.get("nv-iso-speed");

                try {
                    iso = isoValue != null ? Integer.parseInt(isoValue) : 0;
                } catch (Exception ignore) {
                    iso = 0;
                }

                float exposureStep = params.getExposureCompensationStep();
                exposure = params.getExposureCompensation() * exposureStep;
            } catch (Exception e) {
                // Ignore
            }
        }

        updateLocation();
        invalidate();
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (characteristics != null && result != null) {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (isoVal != null) iso = isoVal;

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds < 1.0) {
                    shutterSpeed = String.format(Locale.US, "1/%d", (int) (1.0 / seconds));
                } else {
                    shutterSpeed = String.format(Locale.US, "%.1fs", seconds);
                }
            }

            Integer exposureCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            if (exposureCompensation != null && step != null) {
                exposure = exposureCompensation * step.floatValue();
            }
        }

        updateLocation();
        invalidate();
    }

    private void updateLocation() {
        Location lastLocation = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (lastLocation != null) {
            altitude = lastLocation.getAltitude();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientationValues = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationValues);

            pitch = (float) Math.toDegrees(orientationValues[1]);
            roll = (float) Math.toDegrees(orientationValues[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = AndroidUtilities.dp(44);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(32);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        if (blurDrawer != null) {
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        }
        canvas.drawRect(rectF, backgroundPaint);
        canvas.restore();

        float padding = AndroidUtilities.dp(16);
        float x = padding;
        float cy = getHeight() / 2f + textPaint.getTextSize() / 2f - AndroidUtilities.dp(2);

        String isoText = String.format(Locale.US, "ISO %d", iso);
        canvas.drawText(isoText, x, cy, textPaint);
        x += textPaint.measureText(isoText) + padding;

        String shutterText = String.format(Locale.US, "SHTR %s", shutterSpeed);
        canvas.drawText(shutterText, x, cy, textPaint);
        x += textPaint.measureText(shutterText) + padding;

        String expText = String.format(Locale.US, "EV %.1f", exposure);
        canvas.drawText(expText, x, cy, textPaint);
        x += textPaint.measureText(expText) + padding;

        String altText = String.format(Locale.US, "ALT %.0fm", altitude);
        canvas.drawText(altText, x, cy, textPaint);
        x += textPaint.measureText(altText) + padding;

        String levelText = String.format(Locale.US, "LVL %.1f° / %.1f°", roll, pitch);
        canvas.drawText(levelText, x, cy, textPaint);
    }
}
