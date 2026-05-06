package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraInfo;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch;
    private float roll;
    private int currentAccount;

    private CameraSessionWrapper cameraSession;
    private String iso = "ISO: N/A";
    private String shutter = "1/--";
    private long lastMetadataUpdate;

    public CameraHUDView(Context context) {
        super(context);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setShadowLayer(AndroidUtilities.dp(1), 0, 0, Color.BLACK);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void setCameraSession(CameraSessionWrapper session) {
        this.cameraSession = session;
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
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    public void updateMetadata() {
        if (cameraSession == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;

        Object sessionObj = cameraSession.getObject();
        if (sessionObj instanceof CameraSession) {
            CameraSession s = (CameraSession) sessionObj;
            CameraInfo info = s.cameraInfo;
            if (info != null && info.camera != null) {
                try {
                    Camera.Parameters params = info.camera.getParameters();
                    String isoVal = params.get("iso");
                    if (isoVal == null) isoVal = params.get("iso-speed");
                    if (isoVal != null) iso = "ISO: " + isoVal;

                    String exposureTime = params.get("exposure-time");
                    if (exposureTime != null) {
                        try {
                            float et = Float.parseFloat(exposureTime);
                            if (et > 0) {
                                shutter = "1/" + Math.round(1f / et);
                            }
                        } catch (Exception ignore) {}
                    }
                } catch (Exception ignore) {}
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cameraSession == null) return;

        float x = AndroidUtilities.dp(16);
        float y = AndroidUtilities.dp(80);
        float lineSpacing = AndroidUtilities.dp(16);

        canvas.drawText(iso + "  " + shutter, x, y, textPaint);
        y += lineSpacing;

        // Orientation
        canvas.drawText(String.format(Locale.US, "PITCH: %.1f°  ROLL: %.1f°", pitch, roll), x, y, textPaint);
        y += lineSpacing;

        // Location
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            canvas.drawText(String.format(Locale.US, "LAT: %.4f  LON: %.4f", loc.getLatitude(), loc.getLongitude()), x, y, textPaint);
            y += lineSpacing;
            canvas.drawText(String.format(Locale.US, "ALT: %.1fm", loc.getAltitude()), x, y, textPaint);
        } else {
            canvas.drawText("GPS: SEARCHING...", x, y, textPaint);
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
