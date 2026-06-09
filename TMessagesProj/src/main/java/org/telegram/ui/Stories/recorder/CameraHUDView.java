package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private final SensorManager sensorManager;
    private final Sensor gravitySensor;
    private float roll, pitch;

    private int currentAccount;
    private int iso;
    private String shutterSpeed = "";
    private float exposure;
    private String altitude = "--- m";
    private String lat = "0.000", lon = "0.000";

    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        backgroundPaint.setColor(0x20ffffff);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(dp(9));
        textPaint.setTypeface(AndroidUtilities.bold());

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI);
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

        if (session.isCamera2()) {
            Camera2Session s2 = session.getCamera2Session();
            CaptureResult result = s2.getLastCaptureResult();
            if (result != null) {
                Long sensorExposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                Integer sensorIso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                if (sensorIso != null) iso = sensorIso;
                if (sensorExposure != null) {
                    double seconds = sensorExposure / 1_000_000_000.0;
                    if (seconds > 0) {
                        shutterSpeed = String.format(Locale.US, "1/%d", (int) (1.0 / seconds));
                    }
                }
            }
        } else {
            Camera c = session.getCamera();
            if (c != null) {
                try {
                    Camera.Parameters params = c.getParameters();
                    String isoStr = params.get("iso");
                    if (isoStr == null) isoStr = params.get("iso-speed");
                    if (isoStr == null) isoStr = params.get("nv-iso-speed");
                    if (isoStr != null) {
                        try {
                            iso = Integer.parseInt(isoStr);
                        } catch (Exception ignore) {}
                    }
                    int exposureIndex = params.getExposureCompensation();
                    float step = params.getExposureCompensationStep();
                    exposure = exposureIndex * step;
                } catch (Exception ignore) {}
            }
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            altitude = String.format(Locale.US, "%d m", (int) loc.getAltitude());
            lat = String.format(Locale.US, "%.3f", loc.getLatitude());
            lon = String.format(Locale.US, "%.3f", loc.getLongitude());
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(110), dp(56));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = dp(12);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRoundRect(rect, r, r, backgroundPaint);

        float x = dp(8);
        float y = dp(14);
        float lineH = dp(12);

        canvas.drawText(String.format(Locale.US, "ISO %d", iso), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "EV %.1f", exposure), x + dp(50), y, textPaint);

        y += lineH;
        canvas.drawText(String.format(Locale.US, "SHTR %s", shutterSpeed), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "PTCH %.1f°", pitch), x + dp(50), y, textPaint);

        y += lineH;
        canvas.drawText(String.format(Locale.US, "ALTI %s", altitude), x, y, textPaint);
        canvas.drawText(String.format(Locale.US, "ROLL %.1f°", roll), x + dp(50), y, textPaint);

        y += lineH;
        canvas.drawText(String.format(Locale.US, "GPS %s, %s", lat, lon), x, y, textPaint);

        canvas.restore();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            roll = (float) Math.toDegrees(Math.atan2(x, Math.sqrt(y*y + z*z)));
            pitch = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x*x + z*z)));
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
