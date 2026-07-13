package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch;
    private float roll;
    private String iso = "100";
    private String ev = "0.0";
    private String sec = "1/125";

    public CameraHUDView(Context context) {
        super(context);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setLetterSpacing(0.05f);

        linePaint.setColor(0x80FFFFFF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
    }

    public void updateMetadata(String iso, String ev, String sec) {
        this.iso = iso;
        this.ev = ev;
        this.sec = sec;
        invalidate();
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
    protected void onDraw(Canvas canvas) {
        float x = AndroidUtilities.dp(16);
        float y = AndroidUtilities.dp(24);
        float step = AndroidUtilities.dp(14);

        canvas.drawText(String.format(Locale.US, "PTCH: %+05.1f°", pitch), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "ROLL: %+05.1f°", roll), x, y + step, textPaint);

        float rightX = getWidth() - AndroidUtilities.dp(16);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("ISO: " + iso, rightX, y, textPaint);
        canvas.drawText("EV: " + ev, rightX, y + step, textPaint);
        canvas.drawText("SEC: " + sec, rightX, y + step * 2, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Draw minimalist corner brackets
        float bracketSize = AndroidUtilities.dp(12);
        float m = AndroidUtilities.dp(8);

        // Top Left
        canvas.drawLine(m, m, m + bracketSize, m, linePaint);
        canvas.drawLine(m, m, m, m + bracketSize, linePaint);

        // Top Right
        canvas.drawLine(getWidth() - m, m, getWidth() - m - bracketSize, m, linePaint);
        canvas.drawLine(getWidth() - m, m, getWidth() - m, m + bracketSize, linePaint);

        // Bottom Left
        canvas.drawLine(m, getHeight() - m, m + bracketSize, getHeight() - m, linePaint);
        canvas.drawLine(m, getHeight() - m, m, getHeight() - m - bracketSize, linePaint);

        // Bottom Right
        canvas.drawLine(getWidth() - m, getHeight() - m, getWidth() - m - bracketSize, getHeight() - m, linePaint);
        canvas.drawLine(getWidth() - m, getHeight() - m, getWidth() - m, getHeight() - m - bracketSize, linePaint);
    }
}
