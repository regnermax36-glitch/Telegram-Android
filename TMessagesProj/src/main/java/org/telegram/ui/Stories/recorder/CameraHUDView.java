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

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private float roll;
    private float pitch;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private int iso = -1;
    private float exposure = 0;
    private String shutterSpeed = "";

    private int currentAccount;
    private Location lastLocation;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        panelPaint.setColor(0x15ffffff);
        panelPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(0x25ffffff);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(1), 0x40000000);

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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);

            roll = (float) Math.toDegrees(orientation[2]);
            pitch = (float) Math.toDegrees(orientation[1]);
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && result != null) {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exposureTimeVal = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (isoVal != null) iso = isoVal;
            if (exposureTimeVal != null) {
                float seconds = exposureTimeVal / 1_000_000_000f;
                if (seconds > 0) {
                    if (seconds < 1) {
                        shutterSpeed = String.format(Locale.US, "1/%d", Math.round(1f / seconds));
                    } else {
                        shutterSpeed = String.format(Locale.US, "%.1fs", seconds);
                    }
                }
            }
        }
        invalidate();
    }

    public void updateMetadata(Camera.Parameters params) {
        if (params != null) {
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            try {
                if (isoVal != null) iso = Integer.parseInt(isoVal);
            } catch (Exception ignore) {}

            int exposureIndex = params.getExposureCompensation();
            float step = params.getExposureCompensationStep();
            exposure = exposureIndex * step;
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        lastLocation = LocationController.getInstance(currentAccount).getLastKnownLocation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();

        // Leveler
        drawLeveler(canvas, w / 2f, h / 2f);

        // Metadata Panel (Left)
        drawMetadata(canvas, AndroidUtilities.dp(16), h - AndroidUtilities.dp(140));

        // Location Panel (Right)
        drawLocation(canvas, w - AndroidUtilities.dp(16), h - AndroidUtilities.dp(140));
    }

    private void drawLeveler(Canvas canvas, float cx, float cy) {
        canvas.save();
        canvas.rotate(-roll, cx, cy);

        float lineW = AndroidUtilities.dp(40);
        linePaint.setAlpha(0x80);
        canvas.drawLine(cx - lineW, cy, cx + lineW, cy, linePaint);

        float markH = AndroidUtilities.dp(4);
        canvas.drawLine(cx - lineW, cy - markH, cx - lineW, cy + markH, linePaint);
        canvas.drawLine(cx + lineW, cy - markH, cx + lineW, cy + markH, linePaint);

        canvas.restore();

        // Fixed horizon indicators
        linePaint.setAlpha(0xFF);
        canvas.drawLine(cx - lineW - AndroidUtilities.dp(10), cy, cx - lineW - AndroidUtilities.dp(2), cy, linePaint);
        canvas.drawLine(cx + lineW + AndroidUtilities.dp(2), cy, cx + lineW + AndroidUtilities.dp(10), cy, linePaint);
    }

    private void drawMetadata(Canvas canvas, float x, float y) {
        rect.set(x, y, x + AndroidUtilities.dp(80), y + AndroidUtilities.dp(40));
        drawGlassPanel(canvas, rect);

        float tx = x + AndroidUtilities.dp(8);
        float ty = y + AndroidUtilities.dp(16);

        if (iso > 0) {
            canvas.drawText("ISO " + iso, tx, ty, textPaint);
        }
        ty += AndroidUtilities.dp(14);
        if (!shutterSpeed.isEmpty()) {
            canvas.drawText("SHT " + shutterSpeed, tx, ty, textPaint);
        } else if (exposure != 0) {
            canvas.drawText(String.format(Locale.US, "EV %.1f", exposure), tx, ty, textPaint);
        }
    }

    private void drawLocation(Canvas canvas, float x, float y) {
        float panelW = AndroidUtilities.dp(100);
        rect.set(x - panelW, y, x, y + AndroidUtilities.dp(40));
        drawGlassPanel(canvas, rect);

        float tx = x - panelW + AndroidUtilities.dp(8);
        float ty = y + AndroidUtilities.dp(16);

        if (lastLocation != null) {
            canvas.drawText(String.format(Locale.US, "%.4f, %.4f", lastLocation.getLatitude(), lastLocation.getLongitude()), tx, ty, textPaint);
            ty += AndroidUtilities.dp(14);
            canvas.drawText(String.format(Locale.US, "ALT %.1fm", lastLocation.getAltitude()), tx, ty, textPaint);
        } else {
            canvas.drawText("GPS NO SIGNAL", tx, ty, textPaint);
            ty += AndroidUtilities.dp(14);
            canvas.drawText("PTCH " + Math.round(pitch) + "°", tx, ty, textPaint);
        }
    }

    private void drawGlassPanel(Canvas canvas, RectF rect) {
        canvas.save();
        path.rewind();
        path.addRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
        canvas.clipPath(path);

        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();

        canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), panelPaint);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), linePaint);
    }
}
