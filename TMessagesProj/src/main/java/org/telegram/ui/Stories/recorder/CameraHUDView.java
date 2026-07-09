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
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private float pitch, roll;
    private String iso = "N/A", shutter = "N/A", ev = "0.0";
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1));
        borderPaint.setColor(0x25FFFFFF);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setLetterSpacing(0.05f);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
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
            String newIso = params.get("iso");
            if (newIso == null) newIso = params.get("iso-speed");
            if (newIso == null) newIso = params.get("nv-iso-speed");
            if (newIso == null) newIso = "AUTO";

            float exposure = params.getExposureCompensation() * params.getExposureCompensationStep();
            String newEv = String.format(Locale.US, "%.1f", exposure);

            if (!newIso.equals(iso) || !newEv.equals(ev)) {
                iso = newIso;
                ev = newEv;
                shutter = "AUTO";
                invalidate();
            }
        } catch (Exception ignore) {}
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean changed = false;
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (isoVal != null) {
                String newIso = String.valueOf(isoVal);
                if (!newIso.equals(iso)) {
                    iso = newIso;
                    changed = true;
                }
            }

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                String newShutter;
                if (seconds >= 1) newShutter = String.format(Locale.US, "%.1fs", seconds);
                else newShutter = "1/" + Math.round(1.0 / seconds);
                if (!newShutter.equals(shutter)) {
                    shutter = newShutter;
                    changed = true;
                }
            }

            Integer ae = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (ae != null && characteristics != null) {
                android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (step != null) {
                    String newEv = String.format(Locale.US, "%.1f", ae * step.floatValue());
                    if (!newEv.equals(ev)) {
                        ev = newEv;
                        changed = true;
                    }
                }
            }
            if (changed) {
                invalidate();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);
            pitch = (float) Math.toDegrees(orientation[1]);
            roll = (float) Math.toDegrees(orientation[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(160), AndroidUtilities.dp(44));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(12);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();

        canvas.drawRoundRect(rectF, r, r, borderPaint);

        float x = AndroidUtilities.dp(12);
        float y = AndroidUtilities.dp(18);
        float line2 = AndroidUtilities.dp(34);

        canvas.drawText("PTCH: " + Math.round(pitch) + "°", x, y, textPaint);
        canvas.drawText("ROLL: " + Math.round(roll) + "°", x + AndroidUtilities.dp(80), y, textPaint);

        canvas.drawText("ISO " + iso, x, line2, textPaint);
        canvas.drawText("S " + shutter, x + AndroidUtilities.dp(55), line2, textPaint);
        canvas.drawText("EV " + ev, x + AndroidUtilities.dp(110), line2, textPaint);
    }
}
