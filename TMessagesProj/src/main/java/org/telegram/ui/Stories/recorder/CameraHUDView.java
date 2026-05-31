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
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    private String iso = "---";
    private String shut = "---";
    private String ev = "0.0";
    private String ptch = "0°";

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        pillPaint.setColor(0x20ffffff);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        labelPaint.setColor(0x80ffffff);
        labelPaint.setTextSize(AndroidUtilities.dp(9));
        labelPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    public void updateMetadata(Camera.Parameters params) {
        if (params == null) return;
        try {
            iso = params.get("iso");
            if (iso == null) iso = params.get("iso-speed");
            if (iso == null) iso = params.get("nv-iso-speed");
            if (iso == null) iso = "---";

            float exposure = params.getExposureCompensation() * params.getExposureCompensationStep();
            ev = String.format(Locale.US, "%.1f", exposure);
            if (exposure > 0) ev = "+" + ev;

            // Simplified shutter speed for Camera1
            shut = "AUTO";
        } catch (Exception ignore) {}
        invalidate();
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        try {
            Integer sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (sensorSensitivity != null) {
                iso = String.valueOf(sensorSensitivity);
            }

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds >= 1) {
                    shut = String.format(Locale.US, "%.1fs", seconds);
                } else {
                    shut = "1/" + Math.round(1.0 / seconds);
                }
            }

            Integer exposureCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (exposureCompensation != null && characteristics != null) {
                android.util.Range<Integer> range = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (step != null) {
                    float exposure = exposureCompensation * step.floatValue();
                    ev = String.format(Locale.US, "%.1f", exposure);
                    if (exposure > 0) ev = "+" + ev;
                }
            }
        } catch (Exception ignore) {}
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blurManager == null) return;

        // Draw ISO Pill
        drawPill(canvas, AndroidUtilities.dp(16), AndroidUtilities.dp(80), "ISO", iso);

        // Draw SHUT Pill
        drawPill(canvas, AndroidUtilities.dp(16), AndroidUtilities.dp(120), "SHUT", shut);

        // Draw EV Pill
        drawPill(canvas, getMeasuredWidth() - AndroidUtilities.dp(16 + 50), AndroidUtilities.dp(80), "EV", ev);

        // Draw PTCH Pill
        drawPill(canvas, getMeasuredWidth() - AndroidUtilities.dp(16 + 50), AndroidUtilities.dp(120), "PTCH", ptch);
    }

    private void drawPill(Canvas canvas, float x, float y, String label, String value) {
        float w = AndroidUtilities.dp(50);
        float h = AndroidUtilities.dp(32);
        float r = AndroidUtilities.dp(16);

        rectF.set(x, y, x + w, y + h);

        canvas.save();
        path.rewind();
        path.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rectF, pillPaint);
        canvas.restore();

        canvas.drawText(label, x + (w - labelPaint.measureText(label)) / 2f, y + AndroidUtilities.dp(12), labelPaint);
        canvas.drawText(value, x + (w - textPaint.measureText(value)) / 2f, y + h - AndroidUtilities.dp(6), textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float pitch = (float) Math.toDegrees(orientationAngles[1]);
            ptch = Math.round(pitch) + "°";
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
