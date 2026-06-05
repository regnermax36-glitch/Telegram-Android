package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.location.Location;
import android.hardware.Camera;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private int currentAccount;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF hudRect = new RectF();

    private String iso = "ISO --";
    private String shutter = "1/--";
    private float pitch, roll;
    private String location = "LAT -- LON --";
    private String altitude = "ALT --";

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(11));
        textPaint.setTypeface(AndroidUtilities.bold());

        linePaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
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
    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();

        if (result != null) {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (isoVal != null) iso = String.format(Locale.US, "ISO %d", isoVal);
            if (expTime != null) {
                float seconds = expTime / 1_000_000_000.0f;
                if (seconds < 1.0f) shutter = String.format(Locale.US, "1/%d", Math.round(1.0f / seconds));
                else shutter = String.format(Locale.US, "%.1fs", seconds);
            }
        }
        updateLocation();
    }

    public void updateMetadata(Camera camera) {
        if (camera == null || System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();
        try {
            Camera.Parameters params = camera.getParameters();
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) iso = "ISO " + isoVal;

            float exposure = params.getExposureCompensation() * params.getExposureCompensationStep();
            shutter = String.format(Locale.US, "EV %.1f", exposure);
        } catch (Exception ignore) {}
        updateLocation();
    }

    private void updateLocation() {
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            location = String.format(Locale.US, "LAT %.4f LON %.4f", loc.getLatitude(), loc.getLongitude());
            altitude = String.format(Locale.US, "ALT %.0fm", loc.getAltitude());
        }
        invalidate();
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
        float w = getWidth();
        float h = getHeight();

        // Draw glassmorphic background for HUD area
        // Top-left HUD box
        hudRect.set(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(166), AndroidUtilities.dp(86));
        drawGlassRect(canvas, hudRect);

        canvas.drawText(iso, hudRect.left + AndroidUtilities.dp(10), hudRect.top + AndroidUtilities.dp(18), textPaint);
        canvas.drawText(shutter, hudRect.left + AndroidUtilities.dp(10), hudRect.top + AndroidUtilities.dp(34), textPaint);
        canvas.drawText(location, hudRect.left + AndroidUtilities.dp(10), hudRect.top + AndroidUtilities.dp(50), textPaint);
        canvas.drawText(altitude, hudRect.left + AndroidUtilities.dp(10), hudRect.top + AndroidUtilities.dp(66), textPaint);

        // Center Horizon / Pitch-Roll indicator
        canvas.save();
        canvas.translate(w / 2f, h / 2f);
        canvas.rotate(-roll);

        linePaint.setAlpha(160);
        canvas.drawLine(-AndroidUtilities.dp(45), 0, -AndroidUtilities.dp(12), 0, linePaint);
        canvas.drawLine(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(45), 0, linePaint);

        canvas.rotate(roll); // back to straight for pitch text
        String prText = String.format(Locale.US, "PTCH %.1f° ROLL %.1f°", pitch, roll);
        float prWidth = textPaint.measureText(prText);
        canvas.drawText(prText, -prWidth / 2f, AndroidUtilities.dp(65), textPaint);

        canvas.restore();
    }

    private void drawGlassRect(Canvas canvas, RectF rect) {
        path.rewind();
        path.addRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.restore();

        // Add a very thin white border
        linePaint.setAlpha(60);
        canvas.drawPath(path, linePaint);
    }
}
