package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.view.View;

import android.hardware.Camera;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private SensorManager sensorManager;
    private Sensor rotationSensor;

    private float[] gravity;
    private float[] geomagnetic;
    private float pitch;
    private float roll;

    private int currentAccount = UserConfig.selectedAccount;
    private CameraView cameraView;

    public CameraHUDView(Context context) {
        super(context);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(1), 0x80000000);

        linePaint.setColor(0x80ffffff);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void setCameraView(CameraView cameraView) {
        this.cameraView = cameraView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI);
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
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
        }

        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                pitch = (float) Math.toDegrees(orientation[1]);
                roll = (float) Math.toDegrees(orientation[2]);
            }
        }
        invalidate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw Telemetry HUD
        int w = getWidth();
        int h = getHeight();

        // 1. Level Indicator (Horizon)
        canvas.save();
        canvas.translate(w / 2f, h / 2f);
        canvas.rotate(-roll);
        float lineLen = AndroidUtilities.dp(40);
        float gap = AndroidUtilities.dp(10);
        canvas.drawLine(-lineLen - gap, 0, -gap, 0, linePaint);
        canvas.drawLine(gap, 0, lineLen + gap, 0, linePaint);
        canvas.restore();

        // 2. Data Blocks
        float margin = AndroidUtilities.dp(16);
        float y = margin + AndroidUtilities.dp(60); // Below action bar

        // Top Left: Pitch/Roll
        drawText(canvas, String.format(Locale.US, "PITCH: %+05.1f°", pitch), margin, y);
        y += AndroidUtilities.dp(14);
        drawText(canvas, String.format(Locale.US, "ROLL:  %+05.1f°", roll), margin, y);

        // Top Right: Location (if available)
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            y = margin + AndroidUtilities.dp(60);
            String latStr = String.format(Locale.US, "LAT: %08.5f", loc.getLatitude());
            String lonStr = String.format(Locale.US, "LON: %08.5f", loc.getLongitude());
            float latW = textPaint.measureText(latStr);
            drawText(canvas, latStr, w - margin - latW, y);
            y += AndroidUtilities.dp(14);
            drawText(canvas, lonStr, w - margin - latW, y);

            if (loc.hasAltitude()) {
                y += AndroidUtilities.dp(14);
                String altStr = String.format(Locale.US, "ALT: %.1fm", loc.getAltitude());
                drawText(canvas, altStr, w - margin - latW, y);
            }
        }

        // Bottom Left: Camera Metadata
        y = h - margin - AndroidUtilities.dp(100);
        String iso = "---";
        String exp = "---";
        String f = "---";

        if (cameraView != null) {
            CameraSessionWrapper wrapper = cameraView.getCameraSession();
            if (wrapper != null && wrapper.camera1Session != null) {
                CameraSession session = wrapper.camera1Session;
                if (session.cameraInfo != null && session.cameraInfo.getCamera() != null) {
                    try {
                        Camera.Parameters params = session.cameraInfo.getCamera().getParameters();
                        iso = params.get("iso");
                        if (iso == null) iso = params.get("iso-speed");

                        String expTime = params.get("exposure-time");
                        if (expTime != null) {
                            try {
                                float et = Float.parseFloat(expTime);
                                if (et < 1.0f) {
                                    exp = "1/" + Math.round(1.0f / et);
                                } else {
                                    exp = String.format(Locale.US, "%.1fs", et);
                                }
                            } catch (Exception ignore) {
                                exp = expTime;
                            }
                        }

                        String ap = params.get("f-number");
                        if (ap != null) f = ap;
                    } catch (Exception ignore) {}
                }
            }
        }

        drawText(canvas, "ISO: " + (iso != null ? iso : "AUTO"), margin, y);
        y += AndroidUtilities.dp(14);
        drawText(canvas, "EXP: " + exp, margin, y);
        y += AndroidUtilities.dp(14);
        drawText(canvas, "F: " + f, margin, y);
    }

    private void drawText(Canvas canvas, String text, float x, float y) {
        canvas.drawText(text, x, y, textPaint);
    }
}
