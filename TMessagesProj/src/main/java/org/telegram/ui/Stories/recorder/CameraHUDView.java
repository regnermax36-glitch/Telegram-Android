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
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private BlurringShader.StoryBlurDrawer blurDrawer;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private int currentAccount;

    private float pitch, roll;
    private String iso = "N/A";
    private String shutterSpeed = "N/A";
    private double altitude = 0;
    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        if (blurManager != null) {
            this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);
        }

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        bgPaint.setColor(0x15ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
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

    public void updateMetadata(CameraView cameraView) {
        if (SystemClock.elapsedRealtime() - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = SystemClock.elapsedRealtime();

        if (cameraView != null) {
            CameraSessionWrapper session = cameraView.getCameraSession();
            if (session != null && session.getCamera() != null) {
                Camera camera = session.getCamera();
                try {
                    Camera.Parameters params = camera.getParameters();
                    String isoVal = params.get("iso");
                    if (isoVal == null) isoVal = params.get("iso-speed");
                    if (isoVal == null) isoVal = params.get("nv-iso-speed");
                    iso = isoVal != null ? isoVal : "AUTO";

                    String exposureTime = params.get("exposure-time");
                    if (exposureTime != null) {
                        try {
                            double et = Double.parseDouble(exposureTime);
                            if (et < 1.0) {
                                shutterSpeed = String.format(Locale.US, "1/%d", (int) (1.0 / et));
                            } else {
                                shutterSpeed = String.format(Locale.US, "%.1fs", et);
                            }
                        } catch (Exception ignore) {}
                    } else {
                        shutterSpeed = "AUTO";
                    }
                } catch (Exception ignore) {}
            }
        }

        if (currentAccount != 0) {
            Location lastLocation = LocationController.getInstance(currentAccount).getLastKnownLocation();
            if (lastLocation != null) {
                altitude = lastLocation.getAltitude();
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(140), AndroidUtilities.dp(80));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(32);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        if (blurDrawer != null) {
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        }
        canvas.drawRoundRect(rectF, r, r, bgPaint);

        float x = AndroidUtilities.dp(16);
        float y = AndroidUtilities.dp(24);
        float lineH = AndroidUtilities.dp(16);

        canvas.drawText("ISO: " + iso, x, y, textPaint);
        canvas.drawText("SHUTTER: " + shutterSpeed, x, y + lineH, textPaint);
        canvas.drawText(String.format(Locale.US, "ALT: %.1f m", altitude), x, y + lineH * 2, textPaint);
        canvas.drawText(String.format(Locale.US, "PITCH: %.1f° ROLL: %.1f°", pitch, roll), x, y + lineH * 3, textPaint);

        canvas.restore();
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
