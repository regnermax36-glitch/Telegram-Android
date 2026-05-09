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
import android.location.Location;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float[] rotationMatrix = new float[9];
    private float[] orientationValues = new float[3];
    private float pitch, roll;

    private CameraView cameraView;
    private BlurringShader.BlurManager blurManager;
    private BlurringShader.StoryBlurDrawer blurDrawer;

    private int currentAccount;
    private String iso = "---";
    private String shutter = "---";
    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        if (blurManager != null) {
            this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);
        }

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTextAlign(Paint.Align.LEFT);

        bgPaint.setColor(0x20ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
    }

    public void setCameraView(CameraView cameraView) {
        this.cameraView = cameraView;
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationValues);
            pitch = (float) Math.toDegrees(orientationValues[1]);
            roll = (float) Math.toDegrees(orientationValues[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateMetadata() {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;
        boolean changed = false;

        if (cameraView != null) {
            CameraSessionWrapper wrapper = cameraView.getCameraSession();
            if (wrapper != null && wrapper.camera1Session != null && wrapper.camera1Session.cameraInfo != null && wrapper.camera1Session.cameraInfo.camera != null) {
                try {
                    Camera.Parameters params = wrapper.camera1Session.cameraInfo.camera.getParameters();
                    String isoVal = params.get("iso");
                    if (isoVal == null) isoVal = params.get("iso-speed");
                    if (isoVal == null) isoVal = params.get("nv-iso-speed");
                    if (isoVal == null) isoVal = "---";
                    if (!iso.equals(isoVal)) {
                        iso = isoVal;
                        changed = true;
                    }

                    String exposureTime = params.get("exposure-time");
                    if (exposureTime != null) {
                        try {
                            float et = Float.parseFloat(exposureTime);
                            if (et > 0) {
                                String newShutter = String.format(Locale.US, "1/%d", (int) (1.0f / et));
                                if (!shutter.equals(newShutter)) {
                                    shutter = newShutter;
                                    changed = true;
                                }
                            }
                        } catch (Exception ignore) {}
                    }
                } catch (Exception ignore) {}
            }
        }
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateMetadata();

        rectF.set(AndroidUtilities.dp(16), AndroidUtilities.dp(16), getWidth() - AndroidUtilities.dp(16), AndroidUtilities.dp(100));
        float r = AndroidUtilities.dp(32);

        if (blurDrawer != null && canvas.isHardwareAccelerated()) {
            canvas.save();
            clipPath.rewind();
            clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
            canvas.restore();
        }

        canvas.drawRoundRect(rectF, r, r, bgPaint);

        float x = rectF.left + AndroidUtilities.dp(20);
        float y = rectF.top + AndroidUtilities.dp(30);
        float lineStep = AndroidUtilities.dp(20);

        canvas.drawText("ISO: " + iso, x, y, textPaint);
        canvas.drawText("SHUTTER: " + shutter, x + AndroidUtilities.dp(100), y, textPaint);

        y += lineStep;
        canvas.drawText(String.format(Locale.US, "PITCH: %.1f°  ROLL: %.1f°", pitch, roll), x, y, textPaint);

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            y += lineStep;
            canvas.drawText(String.format(Locale.US, "ALT: %.1fm  GPS: %.4f, %.4f", loc.getAltitude(), loc.getLatitude(), loc.getLongitude()), x, y, textPaint);
        }

    }
}
