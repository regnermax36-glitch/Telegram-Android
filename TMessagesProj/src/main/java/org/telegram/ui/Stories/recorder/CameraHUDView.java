package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;

    private int iso;
    private String exposure;
    private String aperture;
    private float pitch;
    private float roll;
    private double latitude, longitude, altitude;

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());

        bgPaint.setColor(0x15ffffff);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setColor(0x25ffffff);

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

    private long lastMetadataUpdate;

    public void updateMetadata(Camera.Parameters params) {
        if (params == null || System.currentTimeMillis() - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = System.currentTimeMillis();

        String isoValue = params.get("iso");
        if (isoValue == null) isoValue = params.get("iso-speed");
        if (isoValue == null) isoValue = params.get("nv-iso-speed");
        try {
            iso = isoValue != null ? Integer.parseInt(isoValue) : 0;
        } catch (Exception ignore) {}

        float exposureTime = 0;
        try {
            String exp = params.get("exposure-time");
            if (exp != null) exposureTime = Float.parseFloat(exp);
        } catch (Exception ignore) {}

        if (exposureTime > 0) {
            if (exposureTime < 1.0f) {
                exposure = String.format(Locale.US, "1/%d", Math.round(1.0f / exposureTime));
            } else {
                exposure = String.format(Locale.US, "%.1fs", exposureTime);
            }
        } else {
            exposure = "--";
        }

        aperture = "f/1.8"; // Default if not found

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            latitude = loc.getLatitude();
            longitude = loc.getLongitude();
            altitude = loc.getAltitude();
        }
        invalidate();
    }

    private final Path path = new Path();

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getMeasuredWidth();
        float h = getMeasuredHeight();
        float r = AndroidUtilities.dp(32);

        path.rewind();
        AndroidUtilities.rectTmp.set(0, 0, w, h);
        path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.drawRect(0, 0, w, h, bgPaint);
        canvas.restore();
        canvas.drawPath(path, strokePaint);

        float padding = AndroidUtilities.dp(16);
        float lineH = AndroidUtilities.dp(14);
        float y = padding + lineH;

        canvas.drawText(String.format(Locale.US, "ISO %d  SHUT %s  APT %s", iso, exposure, aperture), padding, y, textPaint);
        y += lineH;
        canvas.drawText(String.format(Locale.US, "PTCH %d°  ROLL %d°", Math.round(pitch), Math.round(roll)), padding, y, textPaint);
        y += lineH;
        if (latitude != 0) {
            canvas.drawText(String.format(Locale.US, "LAT %.4f  LON %.4f  ALT %dm", latitude, longitude, (int) altitude), padding, y, textPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(220), AndroidUtilities.dp(64));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            pitch = (float) Math.toDegrees(orientationAngles[1]);
            roll = (float) Math.toDegrees(orientationAngles[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
