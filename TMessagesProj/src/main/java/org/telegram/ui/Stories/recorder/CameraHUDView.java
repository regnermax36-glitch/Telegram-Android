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
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

/**
 * CameraHUDView implements a futuristic "iOS 28" technical HUD overlay.
 * It displays camera telemetry (ISO, exposure), orientation levels,
 * GPS coordinates, and technical framing brackets.
 */
public class CameraHUDView extends View implements SensorEventListener {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;

    private float[] gravity;
    private float[] geomagnetic;
    private float pitch, roll;

    private String iso = "ISO 100";
    private String shutter = "1/125";
    private String ev = "EV +0.0";
    private String res = "4K 60FPS";
    private String coords = "0°0'0\"N 0°0'0\"E";
    private String alt = "ALT 0M";

    private final long startTime = System.currentTimeMillis();
    private int currentAccount = UserConfig.selectedAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);

        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        linePaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dpf2(0.8f));

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmono.ttf")); // Use monospace if available

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
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateLocation();
    }

    private void updateLocation() {
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            coords = String.format(Locale.US, "%.4f°N %.4f°E", loc.getLatitude(), loc.getLongitude());
            alt = String.format(Locale.US, "ALT %.1fM", loc.getAltitude());
        }
    }

    public void updateMetadata(CameraView cameraView) {
        if (cameraView == null) return;
        CameraSessionWrapper session = cameraView.getCameraSession();
        if (session == null || session.camera1Session == null) return;

        try {
            Camera.Parameters params = session.camera1Session.cameraInfo.camera.getParameters();
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) iso = "ISO " + isoVal;

            float exposureTime = 0.01f; // Default placeholder
            if (params.get("exposure-time") != null) {
                exposureTime = Float.parseFloat(params.get("exposure-time"));
            }
            if (exposureTime > 0) {
                if (exposureTime < 1.0f) {
                    shutter = "1/" + Math.round(1.0f / exposureTime);
                } else {
                    shutter = String.format(Locale.US, "%.1fs", exposureTime);
                }
            }

            int evVal = params.getExposureCompensation();
            float step = params.getExposureCompensationStep();
            ev = String.format(Locale.US, "EV %s%.1f", (evVal >= 0 ? "+" : ""), evVal * step);

            Camera.Size previewSize = params.getPreviewSize();
            if (previewSize != null) {
                res = String.format(Locale.US, "%dp 60FPS", previewSize.height);
            }

            invalidate();
        } catch (Exception ignore) {}
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w == 0 || h == 0) return;

        // 1. Technical Brackets
        drawBrackets(canvas, w, h);

        // 2. Central Reticle
        drawReticle(canvas, w, h);

        // 3. Orientation Level (Pitch/Roll)
        drawLevel(canvas, w, h);

        // 4. Telemetry Panels
        drawTelemetry(canvas, w, h);
    }

    private void drawBrackets(Canvas canvas, int w, int h) {
        float padding = AndroidUtilities.dp(32);
        float len = AndroidUtilities.dp(20);

        linePaint.setAlpha(180);

        // Top Left
        path.reset();
        path.moveTo(padding, padding + len);
        path.lineTo(padding, padding);
        path.lineTo(padding + len, padding);
        canvas.drawPath(path, linePaint);

        // Top Right
        path.reset();
        path.moveTo(w - padding - len, padding);
        path.lineTo(w - padding, padding);
        path.lineTo(w - padding, padding + len);
        canvas.drawPath(path, linePaint);

        // Bottom Left
        path.reset();
        path.moveTo(padding, h - padding - len);
        path.lineTo(padding, h - padding);
        path.lineTo(padding + len, h - padding);
        canvas.drawPath(path, linePaint);

        // Bottom Right
        path.reset();
        path.moveTo(w - padding - len, h - padding);
        path.lineTo(w - padding, h - padding);
        path.lineTo(w - padding, h - padding - len);
        canvas.drawPath(path, linePaint);
    }

    private void drawReticle(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float size = AndroidUtilities.dp(60);

        linePaint.setAlpha(100);
        canvas.drawRect(cx - size/2, cy - size/2, cx + size/2, cy + size/2, linePaint);

        linePaint.setAlpha(200);
        canvas.drawLine(cx - AndroidUtilities.dp(5), cy, cx + AndroidUtilities.dp(5), cy, linePaint);
        canvas.drawLine(cx, cy - AndroidUtilities.dp(5), cx, cy + AndroidUtilities.dp(5), linePaint);
    }

    private void drawLevel(Canvas canvas, int w, int h) {
        float cx = w / 2f;
        float cy = h / 2f;
        float gap = AndroidUtilities.dp(80);
        float len = AndroidUtilities.dp(30);

        linePaint.setAlpha(255);
        if (Math.abs(roll) < 1.0f) {
            linePaint.setColor(0xFF00FF00); // Level green
        } else {
            linePaint.setColor(Color.WHITE);
        }

        canvas.save();
        canvas.rotate(roll, cx, cy);
        canvas.drawLine(cx - gap - len, cy, cx - gap, cy, linePaint);
        canvas.drawLine(cx + gap, cy, cx + gap + len, cy, linePaint);
        canvas.restore();

        linePaint.setColor(Color.WHITE);
    }

    private void drawTelemetry(Canvas canvas, int w, int h) {
        float padding = AndroidUtilities.dp(40);

        // Data card background (Glassmorphism)
        rect.set(padding, h - padding - AndroidUtilities.dp(40), padding + AndroidUtilities.dp(160), h - padding);
        drawGlassPanel(canvas, rect);

        textPaint.setAlpha(255);
        canvas.drawText(iso, padding + AndroidUtilities.dp(8), h - padding - AndroidUtilities.dp(25), textPaint);
        canvas.drawText(shutter, padding + AndroidUtilities.dp(8), h - padding - AndroidUtilities.dp(10), textPaint);
        canvas.drawText(ev, padding + AndroidUtilities.dp(80), h - padding - AndroidUtilities.dp(25), textPaint);
        canvas.drawText(res, padding + AndroidUtilities.dp(80), h - padding - AndroidUtilities.dp(10), textPaint);

        // Right side data: GPS/ALT
        rect.set(w - padding - AndroidUtilities.dp(120), padding, w - padding, padding + AndroidUtilities.dp(30));
        drawGlassPanel(canvas, rect);
        canvas.drawText(coords, w - padding - AndroidUtilities.dp(112), padding + AndroidUtilities.dp(12), textPaint);
        canvas.drawText(alt, w - padding - AndroidUtilities.dp(112), padding + AndroidUtilities.dp(24), textPaint);
    }

    private void drawGlassPanel(Canvas canvas, RectF r) {
        if (blurDrawer != null) {
            canvas.save();
            path.reset();
            path.addRoundRect(r, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Path.Direction.CW);
            canvas.clipPath(path);
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
            canvas.restore();
        }

        linePaint.setAlpha(40);
        canvas.drawRoundRect(r, AndroidUtilities.dp(4), AndroidUtilities.dp(4), linePaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) gravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) geomagnetic = event.values;

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
