package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.Camera;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.annotation.TargetApi;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private float pitch, roll;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private int currentAccount;
    private String iso = "--", shutter = "--", aperture = "--", focal = "--";
    private String lat = "0.0", lon = "0.0", alt = "0m";
    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            textPaint.setLetterSpacing(0.05f);
        }

        borderPaint.setColor(0x25FFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(Camera.Parameters params) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;

        if (params != null) {
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) iso = isoVal;

            String expVal = params.get("exposure-time");
            if (expVal != null) {
                try {
                    double s = Double.parseDouble(expVal);
                    if (s >= 1.0) {
                        shutter = String.format(Locale.US, "%.1fs", s);
                    } else {
                        shutter = "1/" + Math.round(1.0 / s);
                    }
                } catch (Exception ignore) {}
            }

            float step = params.getExposureCompensationStep();
            int index = params.getExposureCompensation();
            if (step != 0) {
                aperture = String.format(Locale.US, "EV %.1f", index * step);
            }

            float focalVal = params.getFocalLength();
            focal = String.format(Locale.US, "%.1fmm", focalVal);
        }

        updateLocation();
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = now;

        if (result != null) {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (isoVal != null) iso = String.valueOf(isoVal);

            Long expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (expTime != null) {
                double s = expTime / 1_000_000_000.0;
                if (s >= 1.0) {
                    shutter = String.format(Locale.US, "%.1fs", s);
                } else {
                    shutter = "1/" + Math.round(1.0 / s);
                }
            }

            Float apVal = result.get(CaptureResult.LENS_APERTURE);
            if (apVal != null) aperture = String.format(Locale.US, "f/%.1f", apVal);

            Float focalVal = result.get(CaptureResult.LENS_FOCAL_LENGTH);
            if (focalVal != null) focal = String.format(Locale.US, "%.1fmm", focalVal);
        }

        updateLocation();
    }

    private void updateLocation() {
        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            lat = String.format(Locale.US, "%.4f", loc.getLatitude());
            lon = String.format(Locale.US, "%.4f", loc.getLongitude());
            alt = Math.round(loc.getAltitude()) + "m";
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
        float itemW = AndroidUtilities.dp(80);
        float itemH = AndroidUtilities.dp(36);
        float r = AndroidUtilities.dp(12);

        // Top Left: Hardware Metadata
        drawModule(canvas, padding, padding, itemW * 1.8f, itemH,
            String.format("ISO %s  SHUT %s", iso, shutter),
            String.format("%s  %s", aperture, focal));

        // Top Right: Pitch/Roll
        drawModule(canvas, getWidth() - padding - itemW, padding, itemW, itemH,
            String.format("PTCH %.1f°", pitch),
            String.format("ROLL %.1f°", roll));

        // Bottom Left: Location
        drawModule(canvas, padding, getHeight() - padding - itemH, itemW * 1.5f, itemH,
            String.format("LAT %s  LON %s", lat, lon),
            String.format("ALT %s", alt));
    }

    private void drawModule(Canvas canvas, float x, float y, float w, float h, String line1, String line2) {
        rectF.set(x, y, x + w, y + h);
        float r = AndroidUtilities.dp(12);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        canvas.translate(x, y);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.restore();

        canvas.drawRoundRect(rectF, r, r, borderPaint);

        canvas.drawText(line1, x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(14), textPaint);
        canvas.drawText(line2, x + AndroidUtilities.dp(8), y + AndroidUtilities.dp(28), textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            roll = (float) Math.toDegrees(Math.atan2(event.values[0], event.values[1]));
            pitch = (float) Math.toDegrees(Math.atan2(-event.values[1], event.values[2]));
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }
}
