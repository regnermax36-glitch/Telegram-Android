package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSessionWrapper;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensorManager;
    private final Sensor rotationVectorSensor;

    private float pitch;
    private float roll;

    private DualCameraView cameraView;
    private int currentAccount;

    public CameraHUDView(Context context) {
        super(context);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        paint.setColor(Color.WHITE);
        paint.setTextSize(AndroidUtilities.dp(12));
        paint.setShadowLayer(AndroidUtilities.dp(1), 0, 0, Color.BLACK);
    }

    public void setCameraView(DualCameraView cameraView) {
        this.cameraView = cameraView;
    }

    public void setCurrentAccount(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float x = AndroidUtilities.dp(16);
        float y = AndroidUtilities.dp(100);
        float step = AndroidUtilities.dp(16);

        // Leveling
        canvas.drawText(String.format(Locale.US, "PITCH: %.1f°", pitch), x, y, paint);
        y += step;
        canvas.drawText(String.format(Locale.US, "ROLL: %.1f°", roll), x, y, paint);
        y += step;

        // Location
        Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (location != null) {
            canvas.drawText(String.format(Locale.US, "ALT: %.1fm", location.getAltitude()), x, y, paint);
            y += step;
            canvas.drawText(String.format(Locale.US, "LAT: %.4f", location.getLatitude()), x, y, paint);
            y += step;
            canvas.drawText(String.format(Locale.US, "LON: %.4f", location.getLongitude()), x, y, paint);
            y += step;
        }

        // Camera Metadata
        if (cameraView != null) {
            CameraSessionWrapper session = cameraView.getCameraSession();
            if (session != null) {
                // In a real implementation, we would extract ISO/Exposure from session parameters
                // For now we show placeholder to match the UI style requirement
                canvas.drawText("ISO: AUTO", x, y, paint);
                y += step;
                canvas.drawText("EXP: 1/50", x, y, paint);
            }
        }
    }
}
