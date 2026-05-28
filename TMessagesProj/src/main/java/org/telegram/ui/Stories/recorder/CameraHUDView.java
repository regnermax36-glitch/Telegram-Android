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
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch, roll;
    private double altitude;
    private String iso = "---", exposure = "---";

    private long lastMetadataUpdate;
    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        bgPaint.setColor(Color.argb(38, 255, 255, 255)); // Light glass effect

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
        updateLocation();
    }

    public void updateLocation() {
        Location lastKnown = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (lastKnown != null) {
            altitude = lastKnown.getAltitude();
        }
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

    public void updateMetadata(CameraSessionWrapper session) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;

        if (session == null) return;

        if (session.camera2Session != null) {
            updateMetadata(session.camera2Session.getCameraCharacteristics(), session.camera2Session.getLastCaptureResult());
        } else if (session.camera1Session != null) {
            try {
                Camera.Parameters params = session.camera1Session.cameraInfo.getCamera().getParameters();
                String isoVal = params.get("iso");
                if (isoVal == null) isoVal = params.get("iso-speed");
                if (isoVal == null) isoVal = params.get("nv-iso-speed");
                if (isoVal != null) iso = isoVal;

                int exposureIndex = params.getExposureCompensation();
                float step = params.getExposureCompensationStep();
                exposure = String.format(Locale.US, "%.1f EV", exposureIndex * step);
            } catch (Exception ignored) {}
        }
        invalidate();
    }

    // Overloaded for Camera2 specifically
    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
        Long expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

        if (isoVal != null) iso = String.valueOf(isoVal);
        if (expTime != null) {
            double seconds = expTime / 1_000_000_000.0;
            if (seconds < 1.0) {
                exposure = String.format(Locale.US, "1/%d", Math.round(1.0 / seconds));
            } else {
                exposure = String.format(Locale.US, "%.1fs", seconds);
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(160), AndroidUtilities.dp(44));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(12);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        if (blurDrawer != null) {
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        }
        canvas.drawRect(rect, bgPaint);

        float padding = AndroidUtilities.dp(8);
        float x = padding;
        float y = padding + AndroidUtilities.dp(10);

        canvas.drawText(String.format(Locale.US, "PTCH: %.1f°", pitch), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "ROLL: %.1f°", roll), x + AndroidUtilities.dp(80), y, textPaint);

        y += AndroidUtilities.dp(16);
        canvas.drawText(String.format(Locale.US, "ALTI: %dm", (int) altitude), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "ISO: %s  EXPO: %s", iso, exposure), x + AndroidUtilities.dp(60), y, textPaint);

        canvas.restore();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);

            pitch = (float) Math.toDegrees(orientation[1]);
            roll = (float) Math.toDegrees(orientation[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
