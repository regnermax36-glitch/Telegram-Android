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
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Path clipPath = new Path();

    private float pitch;
    private float roll;

    private String iso = "--";
    private String shutter = "--";

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

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
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal != null) iso = isoVal;

            String exposure = params.get("exposure-time");
            if (exposure != null) {
                float e = Float.parseFloat(exposure);
                if (e < 1.0f) {
                    shutter = "1/" + Math.round(1.0f / e);
                } else {
                    shutter = String.format("%.1fs", e);
                }
            }
        } catch (Exception ignore) {}
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        // Level indicator
        float centerX = w / 2f;
        float centerY = h / 2f;

        canvas.save();
        canvas.rotate(-roll, centerX, centerY);
        paint.setColor(0x80FFFFFF);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        canvas.drawLine(centerX - AndroidUtilities.dp(40), centerY, centerX - AndroidUtilities.dp(10), centerY, paint);
        canvas.drawLine(centerX + AndroidUtilities.dp(10), centerY, centerX + AndroidUtilities.dp(40), centerY, paint);

        if (Math.abs(roll) < 1.0f) {
            paint.setColor(0xFFF73131);
            canvas.drawCircle(centerX, centerY, AndroidUtilities.dp(2), paint);
        }
        canvas.restore();

        // Telemetry Data
        float padding = AndroidUtilities.dp(16);
        drawInfoBox(canvas, padding, padding, "ISO " + iso);
        drawInfoBox(canvas, w - padding - AndroidUtilities.dp(60), padding, shutter);
    }

    private void drawInfoBox(Canvas canvas, float x, float y, String text) {
        float boxW = AndroidUtilities.dp(50);
        float boxH = AndroidUtilities.dp(18);
        RectF rect = AndroidUtilities.rectTmp;
        rect.set(x, y, x + boxW, y + boxH);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, boxH / 2f, boxH / 2f, Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.restore();

        canvas.drawText(text, x + (boxW - textPaint.measureText(text)) / 2f, y + boxH / 2f + AndroidUtilities.dp(4), textPaint);
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
}
