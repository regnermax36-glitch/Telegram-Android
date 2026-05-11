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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private final float[] R = new float[9];
    private final float[] orientation = new float[3];

    private float pitch, roll;
    private String iso = "ISO --";
    private String shutter = "1/--";
    private String locationStr = "LAT: -- LON: --";
    private String altitudeStr = "ALT: --m";

    private long lastMetadataUpdate;
    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND);

        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(Camera camera) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                String isoValue = params.get("iso");
                if (isoValue == null) isoValue = params.get("iso-speed");
                if (isoValue == null) isoValue = params.get("nv-iso-speed");
                if (isoValue != null) {
                    iso = "ISO " + isoValue;
                }

                String exposureTime = params.get("exposure-time");
                if (exposureTime != null) {
                    try {
                        float exp = Float.parseFloat(exposureTime);
                        if (exp > 0) {
                            shutter = String.format(Locale.US, "1/%d", Math.round(1.0f / exp));
                        }
                    } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {}
        }

        Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (location != null) {
            locationStr = String.format(Locale.US, "LAT: %.4f LON: %.4f", location.getLatitude(), location.getLongitude());
            altitudeStr = String.format(Locale.US, "ALT: %.1fm", location.getAltitude());
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float padding = AndroidUtilities.dp(16);
        float w = AndroidUtilities.dp(160);
        float h = AndroidUtilities.dp(80);

        rectF.set(padding, padding, padding + w, padding + h);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, AndroidUtilities.dp(24), AndroidUtilities.dp(24), Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        paint.setColor(0x20ffffff);
        canvas.drawRect(rectF, paint);
        canvas.restore();

        float textX = rectF.left + AndroidUtilities.dp(12);
        float textY = rectF.top + AndroidUtilities.dp(20);

        canvas.drawText(iso, textX, textY, textPaint);
        canvas.drawText(shutter, textX + AndroidUtilities.dp(80), textY, textPaint);

        canvas.drawText(locationStr, textX, textY + AndroidUtilities.dp(20), textPaint);
        canvas.drawText(altitudeStr, textX, textY + AndroidUtilities.dp(40), textPaint);

        // Horizon Level
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(roll);
        paint.setColor(Math.abs(roll) < 1.0f ? 0xFF00FF00 : Color.WHITE);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        canvas.drawLine(-AndroidUtilities.dp(40), 0, -AndroidUtilities.dp(10), 0, paint);
        canvas.drawLine(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(40), 0, paint);
        canvas.restore();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, 3);
        }

        if (SensorManager.getRotationMatrix(R, null, gravity, geomagnetic)) {
            SensorManager.getOrientation(R, orientation);
            pitch = (float) Math.toDegrees(orientation[1]);
            roll = (float) Math.toDegrees(orientation[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}
