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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private float pitch, roll;

    private int currentAccount;
    private long lastMetadataUpdate;
    private String iso = "ISO --";
    private String shutter = "1/--";
    private String locationStr = "LAT -- LON --";
    private String altitudeStr = "ALT --";

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.LEFT);

        bgPaint.setColor(0x20ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    public void updateMetadata(Camera camera) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                String isoVal = params.get("iso");
                if (isoVal == null) isoVal = params.get("iso-speed");
                if (isoVal == null) isoVal = params.get("nv-iso-speed");
                if (isoVal != null) {
                    iso = "ISO " + isoVal;
                }

                float exposureTime = 0;
                String exposureStr = params.get("exposure-time");
                if (exposureStr != null) {
                    exposureTime = Float.parseFloat(exposureStr);
                }
                if (exposureTime > 0) {
                    shutter = String.format(Locale.US, "1/%d", Math.round(1.0f / exposureTime));
                } else {
                    shutter = "1/--";
                }
            } catch (Exception ignore) {}
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            locationStr = String.format(Locale.US, "LAT %.4f LON %.4f", loc.getLatitude(), loc.getLongitude());
            altitudeStr = String.format(Locale.US, "ALT %.1fm", loc.getAltitude());
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blurManager == null) return;

        float padding = AndroidUtilities.dp(16);
        float w = AndroidUtilities.dp(120);
        float h = AndroidUtilities.dp(60);

        rect.set(padding, padding, padding + w, padding + h);
        float r = AndroidUtilities.dp(12);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rect, bgPaint);
        canvas.restore();

        float tx = rect.left + AndroidUtilities.dp(8);
        float ty = rect.top + AndroidUtilities.dp(14);
        canvas.drawText(iso, tx, ty, textPaint);
        ty += AndroidUtilities.dp(12);
        canvas.drawText(shutter, tx, ty, textPaint);
        ty += AndroidUtilities.dp(12);
        canvas.drawText(String.format(Locale.US, "PITCH %.1f° ROLL %.1f°", pitch, roll), tx, ty, textPaint);
        ty += AndroidUtilities.dp(12);
        canvas.drawText(locationStr, tx, ty, textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
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
                invalidate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
