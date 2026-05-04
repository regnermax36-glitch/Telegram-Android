package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch;
    private float roll;
    private double altitude;
    private double latitude;
    private double longitude;

    private int currentAccount;
    private CameraView cameraView;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);

        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_CAPTION_XFER);
        backgroundPaint.setColor(0x20ffffff);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x40000000);

        levelPaint.setColor(Color.WHITE);
        levelPaint.setStrokeWidth(AndroidUtilities.dp(1));
        levelPaint.setStyle(Paint.Style.STROKE);

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
        RectF rect = AndroidUtilities.rectTmp;
        rect.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(12);

        // Draw glassmorphic background
        canvas.save();
        blurDrawer.drawRect(canvas, 0, 0, 1f, true);
        canvas.drawRoundRect(rect, r, r, backgroundPaint);
        canvas.restore();

        float x = AndroidUtilities.dp(8);
        float y = AndroidUtilities.dp(14);
        float lineSpacing = AndroidUtilities.dp(14);

        // Technical Data
        String isoStr = "ISO --";
        String expStr = "EXP --";
        if (cameraView != null) {
            CameraSessionWrapper session = cameraView.getCameraSession();
            if (session != null) {
                // Values would normally be fetched from session parameters
                // ISO and Exposure are usually hidden in flatten parameters for Camera1
                // and available via CaptureResult for Camera2.
                // For this UI demo, we use placeholders or static values if unavailable.
            }
        }

        canvas.drawText(String.format(Locale.US, "PITCH: %.1f°", pitch), x, y, textPaint);
        y += lineSpacing;
        canvas.drawText(String.format(Locale.US, "ROLL: %.1f°", roll), x, y, textPaint);
        y += lineSpacing;
        canvas.drawText(String.format(Locale.US, "ALT: %.1fm", altitude), x, y, textPaint);
        y += lineSpacing;
        canvas.drawText(String.format(Locale.US, "LAT: %.4f", latitude), x, y, textPaint);
        y += lineSpacing;
        canvas.drawText(String.format(Locale.US, "LON: %.4f", longitude), x, y, textPaint);

        // Level indicator
        float cx = getWidth() / 2f;
        float cy = getHeight() - AndroidUtilities.dp(20);
        float levelWidth = AndroidUtilities.dp(40);
        canvas.drawLine(cx - levelWidth / 2f, cy, cx + levelWidth / 2f, cy, levelPaint);

        canvas.save();
        canvas.rotate(-roll, cx, cy);
        canvas.drawLine(cx - levelWidth / 3f, cy, cx + levelWidth / 3f, cy, levelPaint);
        canvas.restore();
    }

    private void updateLocation() {
        Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (location != null) {
            altitude = location.getAltitude();
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(80), AndroidUtilities.dp(100));
        updateLocation();
    }
}
