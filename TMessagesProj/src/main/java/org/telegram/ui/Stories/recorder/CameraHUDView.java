package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera;
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
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private String isoText = "";
    private String shutterText = "";
    private String evText = "";
    private String ptchText = "PTCH: 0°";
    private String rollText = "ROLL: 0°";

    private float pitch;
    private float roll;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        paint.setColor(0x25FFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

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

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && result != null) {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long shutter = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

            String newIso = iso != null ? "ISO: " + iso : "";
            String newShutter = "";
            if (shutter != null) {
                double s = shutter / 1_000_000_000.0;
                if (s < 1.0) {
                    newShutter = String.format(Locale.US, "1/%d", Math.round(1.0 / s));
                } else {
                    newShutter = String.format(Locale.US, "%.1fs", s);
                }
                newShutter = "SHUTTER: " + newShutter;
            }

            if (!newIso.equals(isoText) || !newShutter.equals(shutterText)) {
                isoText = newIso;
                shutterText = newShutter;
                invalidate();
            }
        }
    }

    public void updateMetadata(Camera.Parameters params) {
        if (params != null) {
            String iso = params.get("iso");
            if (iso == null) iso = params.get("iso-speed");
            if (iso == null) iso = params.get("nv-iso-speed");

            String newIso = iso != null ? "ISO: " + iso : "";

            int evIndex = params.getExposureCompensation();
            float step = params.getExposureCompensationStep();
            String newEv = String.format(Locale.US, "EV: %.1f", evIndex * step);

            if (!newIso.equals(isoText) || !newEv.equals(evText)) {
                isoText = newIso;
                evText = newEv;
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(16);

        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.restore();

        canvas.drawRoundRect(rectF, r, r, paint);

        float x = AndroidUtilities.dp(12);
        float y = AndroidUtilities.dp(16);

        if (!isoText.isEmpty()) {
            canvas.drawText(isoText, x, y, textPaint);
            y += AndroidUtilities.dp(14);
        }
        if (!shutterText.isEmpty()) {
            canvas.drawText(shutterText, x, y, textPaint);
            y += AndroidUtilities.dp(14);
        }
        if (!evText.isEmpty()) {
            canvas.drawText(evText, x, y, textPaint);
            y += AndroidUtilities.dp(14);
        }

        canvas.drawText(ptchText, x, y, textPaint);
        y += AndroidUtilities.dp(14);
        canvas.drawText(rollText, x, y, textPaint);
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

            ptchText = String.format(Locale.US, "PTCH: %d°", Math.round(pitch));
            rollText = String.format(Locale.US, "ROLL: %d°", Math.round(roll));
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
