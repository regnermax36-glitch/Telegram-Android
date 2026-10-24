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
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private float roll;
    private float pitch;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private String isoStr = "ISO --";
    private String shutterStr = "1/--";
    private String evStr = "EV --";

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(0.5f), 0x80000000);

        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

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
            int iso = 0;
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) {
                isoStr = "ISO " + isoVal;
            }

            float ev = params.getExposureCompensation() * params.getExposureCompensationStep();
            evStr = String.format(Locale.US, "EV %+.1f", ev);

            invalidate();
        } catch (Exception ignore) {}
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        try {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (iso != null) {
                isoStr = "ISO " + iso;
            }

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds >= 1) {
                    shutterStr = String.format(Locale.US, "%.1fs", seconds);
                } else {
                    shutterStr = "1/" + Math.round(1.0 / seconds);
                }
            }

            Integer evIdx = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (evIdx != null && characteristics != null) {
                android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (step != null) {
                    float ev = evIdx * step.floatValue();
                    evStr = String.format(Locale.US, "EV %+.1f", ev);
                }
            }
            invalidate();
        } catch (Exception ignore) {}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        // Draw Telemetry Glass Card
        float cardW = AndroidUtilities.dp(70);
        float cardH = AndroidUtilities.dp(40);
        float cardX = AndroidUtilities.dp(16);
        float cardY = h - cardH - AndroidUtilities.dp(100);

        rect.set(cardX, cardY, cardX + cardW, cardY + cardH);
        path.rewind();
        path.addRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();

        canvas.drawText(isoStr, cardX + AndroidUtilities.dp(6), cardY + AndroidUtilities.dp(12), textPaint);
        canvas.drawText(shutterStr, cardX + AndroidUtilities.dp(6), cardY + AndroidUtilities.dp(24), textPaint);
        canvas.drawText(evStr, cardX + AndroidUtilities.dp(6), cardY + AndroidUtilities.dp(36), textPaint);

        // Draw Horizon Leveler
        canvas.save();
        canvas.translate(w / 2, h / 2);
        canvas.rotate(-roll);

        float lineW = AndroidUtilities.dp(40);
        linePaint.setAlpha(128);
        canvas.drawLine(-lineW, 0, -AndroidUtilities.dp(10), 0, linePaint);
        canvas.drawLine(AndroidUtilities.dp(10), 0, lineW, 0, linePaint);

        if (Math.abs(roll) < 1.0f) {
            linePaint.setColor(0xFF00FF00);
            linePaint.setAlpha(255);
        } else {
            linePaint.setColor(Color.WHITE);
            linePaint.setAlpha(255);
        }
        canvas.drawCircle(0, 0, AndroidUtilities.dp(2), linePaint);

        canvas.restore();

        // Pitch/Roll technical data
        String telemetry = String.format(Locale.US, "ROLL %.1f°  PTCH %.1f°", roll, pitch);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(telemetry, w / 2, h / 2 + AndroidUtilities.dp(60), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);

            roll = (float) Math.toDegrees(orientation[2]);
            pitch = (float) Math.toDegrees(orientation[1]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
