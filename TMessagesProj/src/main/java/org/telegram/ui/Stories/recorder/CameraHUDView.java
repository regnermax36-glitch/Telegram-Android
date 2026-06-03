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
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bgRect = new RectF();
    private final Path clipPath = new Path();

    private String iso = "---";
    private String exposure = "---";
    private float pitch;
    private float roll;

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(11));
        textPaint.setTypeface(AndroidUtilities.bold());

        labelPaint.setColor(0x80FFFFFF);
        labelPaint.setTextSize(AndroidUtilities.dp(8));
        labelPaint.setTypeface(AndroidUtilities.bold());

        linePaint.setColor(0x33FFFFFF);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(Camera camera) {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal == null) isoVal = "---";

            float exp = params.getExposureCompensation() * params.getExposureCompensationStep();
            String expVal = String.format(Locale.US, "%.1f", exp);

            if (!isoVal.equals(iso) || !expVal.equals(exposure)) {
                iso = isoVal;
                exposure = expVal;
                invalidate();
            }
        } catch (Exception e) {
            // Camera might be released
        }
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
        Long expTimeVal = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

        String isoStr = isoVal != null ? String.valueOf(isoVal) : "---";
        String expStr = "---";
        if (expTimeVal != null) {
            double seconds = expTimeVal / 1_000_000_000.0;
            if (seconds >= 1.0) {
                expStr = String.format(Locale.US, "%.1fs", seconds);
            } else {
                expStr = String.format(Locale.US, "1/%d", (int) (1.0 / seconds));
            }
        }

        if (!isoStr.equals(iso) || !expStr.equals(exposure)) {
            iso = isoStr;
            exposure = expStr;
            invalidate();
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientationValues = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationValues);

            pitch = (float) Math.toDegrees(orientationValues[1]);
            roll = (float) Math.toDegrees(orientationValues[2]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        float pad = AndroidUtilities.dp(16);
        float hudW = w - pad * 2;
        float hudH = AndroidUtilities.dp(54);
        float top = h - hudH - pad - AndroidUtilities.dp(100); // Positioned above record control

        bgRect.set(pad, top, w - pad, top + hudH);
        float r = AndroidUtilities.dp(18);

        // Draw glass background
        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(bgRect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.drawColor(0x15FFFFFF);
        canvas.restore();

        float x = bgRect.left + AndroidUtilities.dp(16);
        float y = bgRect.top + AndroidUtilities.dp(22);

        // ISO
        drawValue(canvas, x, y, "ISO", iso);
        x += hudW * 0.2f;

        // Exposure
        drawValue(canvas, x, y, "EXP", exposure);
        x += hudW * 0.2f;

        // Pitch/Roll
        drawValue(canvas, x, y, "PTCH", String.format(Locale.US, "%.1f°", pitch));
        x += hudW * 0.2f;
        drawValue(canvas, x, y, "ROLL", String.format(Locale.US, "%.1f°", roll));
        x += hudW * 0.2f;

        // Location (simplified)
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        String locStr = loc != null ? String.format(Locale.US, "%.2f, %.2f", loc.getLatitude(), loc.getLongitude()) : "---";
        drawValue(canvas, x, y, "LOC", locStr);
    }

    private void drawValue(Canvas canvas, float x, float y, String label, String value) {
        canvas.drawText(label, x, y, labelPaint);
        canvas.drawText(value, x, y + AndroidUtilities.dp(14), textPaint);
    }
}
