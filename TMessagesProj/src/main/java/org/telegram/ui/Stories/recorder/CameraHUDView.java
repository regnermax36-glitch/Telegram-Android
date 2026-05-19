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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    private float pitch;
    private float roll;
    private String iso = "ISO --";
    private String exposure = "EV 0.0";
    private String shutter = "1/--";
    private String locationStr = "0.0000° N, 0.0000° E";
    private String altitude = "0 m";

    private long lastMetadataUpdate;
    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_BACKGROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        linePaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));

        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(Camera camera) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;

        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                String isoVal = params.get("iso");
                if (isoVal == null) isoVal = params.get("iso-speed");
                if (isoVal == null) isoVal = params.get("nv-iso-speed");
                iso = "ISO " + (isoVal != null ? isoVal : "--");

                float step = params.getExposureCompensationStep();
                int compensation = params.getExposureCompensation();
                exposure = String.format(Locale.US, "EV %.1f", compensation * step);

                String shutterVal = params.get("exposure-time");
                if (shutterVal != null) {
                    try {
                        float exposureTime = Float.parseFloat(shutterVal);
                        if (exposureTime < 1.0f) {
                            shutter = String.format(Locale.US, "1/%d", (int) (1f / exposureTime));
                        } else {
                            shutter = String.format(Locale.US, "%.1fs", exposureTime);
                        }
                    } catch (Exception ignore) {}
                }
            } catch (Exception ignore) {}
        }

        Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (location != null) {
            locationStr = String.format(Locale.US, "%.4f° %s, %.4f° %s",
                    Math.abs(location.getLatitude()), location.getLatitude() >= 0 ? "N" : "S",
                    Math.abs(location.getLongitude()), location.getLongitude() >= 0 ? "E" : "W");
            altitude = String.format(Locale.US, "%.0f m", location.getAltitude());
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        clipPath.reset();
        clipPath.addRoundRect(rect, AndroidUtilities.dp(32), AndroidUtilities.dp(32), Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blurDrawer != null) {
            canvas.save();
            canvas.clipPath(clipPath);
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
            canvas.drawColor(0x15FFFFFF);
            canvas.restore();
        }

        // Draw HUD Elements
        float padding = AndroidUtilities.dp(24);

        // Top Left: Metadata
        canvas.drawText(iso, padding, padding + AndroidUtilities.dp(12), textPaint);
        canvas.drawText(exposure, padding, padding + AndroidUtilities.dp(26), textPaint);
        canvas.drawText(shutter, padding, padding + AndroidUtilities.dp(40), textPaint);

        // Top Right: Location
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(locationStr, getWidth() - padding, padding + AndroidUtilities.dp(12), textPaint);
        canvas.drawText(altitude, getWidth() - padding, padding + AndroidUtilities.dp(26), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Center: Leveling tool
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.save();
        canvas.rotate(-roll, cx, cy);
        float lineW = AndroidUtilities.dp(40);
        canvas.drawLine(cx - lineW, cy, cx + lineW, cy, linePaint);
        canvas.restore();

        // Target indicators
        linePaint.setAlpha(128);
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(4), linePaint);
        linePaint.setAlpha(255);
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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        SensorManager sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(this);
    }
}
