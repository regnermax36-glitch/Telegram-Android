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
import android.location.Location;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;

import java.util.Locale;

public class HUDView extends View implements SensorEventListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private float pitch;
    private float roll;

    public HUDView(Context context) {
        super(context);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) gravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = event.values;
        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                pitch = (float) Math.toDegrees(orientation[1]);
                roll = (float) Math.toDegrees(orientation[2]);
                invalidate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // Draw Electronic Horizon
        canvas.save();
        canvas.rotate(-roll, cx, cy);
        float horizonWidth = w * 0.4f;
        paint.setAlpha(0x80);
        canvas.drawLine(cx - horizonWidth, cy + (pitch * 2), cx + horizonWidth, cy + (pitch * 2), paint);
        paint.setAlpha(0xFF);
        canvas.restore();

        // Draw Reticle
        float size = AndroidUtilities.dp(40);
        canvas.drawLine(cx - size / 4, cy, cx + size / 4, cy, paint);
        canvas.drawLine(cx, cy - size / 4, cx, cy + size / 4, paint);

        // Brackets
        float bSize = AndroidUtilities.dp(60);
        float bLen = AndroidUtilities.dp(15);
        drawBrackets(canvas, cx, cy, bSize, bLen);

        // Telemetry Panels
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x20ffffff);
        rect.set(AndroidUtilities.dp(16), cy - AndroidUtilities.dp(100), AndroidUtilities.dp(16 + 100), cy + AndroidUtilities.dp(100));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);
        rect.set(w - AndroidUtilities.dp(16 + 100), cy - AndroidUtilities.dp(100), w - AndroidUtilities.dp(16), cy + AndroidUtilities.dp(100));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        // Fetch Location
        Location loc = LocationController.getInstance(0).getLastKnownLocation();
        String alt = loc != null ? String.format(Locale.US, "ALT: %.0fm", loc.getAltitude()) : "ALT: ---";
        String gps = loc != null ? String.format(Locale.US, "LAT: %.4f", loc.getLatitude()) : "LAT: ---";

        // Fetch Camera Info
        CameraSession session = CameraController.getInstance().getCameraSession();
        String iso = "ISO: AUTO";
        if (session != null) {
            // In a real implementation, we would extract ISO from session.getParameters() or Camera2 characteristics
            iso = "ISO: 400";
        }

        canvas.drawText(alt, AndroidUtilities.dp(24), cy - AndroidUtilities.dp(40), textPaint);
        canvas.drawText(gps, AndroidUtilities.dp(24), cy - AndroidUtilities.dp(20), textPaint);
        canvas.drawText(String.format(Locale.US, "PITCH: %.1f°", pitch), AndroidUtilities.dp(24), cy, textPaint);

        canvas.drawText(iso, w - AndroidUtilities.dp(108), cy - AndroidUtilities.dp(40), textPaint);
        canvas.drawText("F: 1.8", w - AndroidUtilities.dp(108), cy - AndroidUtilities.dp(20), textPaint);
        canvas.drawText(String.format(Locale.US, "ROLL: %.1f°", roll), w - AndroidUtilities.dp(108), cy, textPaint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
    }

    private void drawBrackets(Canvas canvas, float cx, float cy, float bSize, float bLen) {
        // Top Left
        canvas.drawLine(cx - bSize, cy - bSize, cx - bSize + bLen, cy - bSize, paint);
        canvas.drawLine(cx - bSize, cy - bSize, cx - bSize, cy - bSize + bLen, paint);
        // Top Right
        canvas.drawLine(cx + bSize, cy - bSize, cx + bSize - bLen, cy - bSize, paint);
        canvas.drawLine(cx + bSize, cy - bSize, cx + bSize, cy - bSize + bLen, paint);
        // Bottom Left
        canvas.drawLine(cx - bSize, cy + bSize, cx - bSize + bLen, cy + bSize, paint);
        canvas.drawLine(cx - bSize, cy + bSize, cx - bSize, cy + bSize - bLen, paint);
        // Bottom Right
        canvas.drawLine(cx + bSize, cy + bSize, cx + bSize - bLen, cy + bSize, paint);
        canvas.drawLine(cx + bSize, cy + bSize, cx + bSize, cy + bSize - bLen, paint);
    }
}
