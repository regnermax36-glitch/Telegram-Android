package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor magnetometer;

    private final float[] gravity = new float[3];
    private final float[] geomagnetic = new float[3];
    private float pitch;
    private float roll;

    private String iso = "ISO --";
    private String exposure = "1/--";
    private String coords = "0.00° N 0.00° E";
    private String alt = "0m ALT";
    private int currentAccount;

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(Typeface.MONOSPACE);

        linePaint.setColor(0x80ffffff);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));

        glassPaint.setColor(0x15ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
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

    private long lastMetadataUpdate;
    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(CameraView cameraView) {
        if (System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();

        android.location.Location loc = org.telegram.messenger.LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            coords = String.format(java.util.Locale.US, "%.2f° %s %.2f° %s",
                Math.abs(loc.getLatitude()), loc.getLatitude() >= 0 ? "N" : "S",
                Math.abs(loc.getLongitude()), loc.getLongitude() >= 0 ? "E" : "W");
            alt = String.format(java.util.Locale.US, "%.0fm ALT", loc.getAltitude());
        }

        if (cameraView == null || cameraView.getCameraSession() == null) return;
        try {
            android.hardware.Camera.Parameters params = cameraView.getCameraSession().getCamera().getParameters();
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) iso = "ISO " + isoVal;

            String shutter = params.get("exposure-time");
            if (shutter != null) {
                try {
                    float s = Float.parseFloat(shutter);
                    if (s < 1.0f) {
                        exposure = "1/" + Math.round(1.0f / s);
                    } else {
                        exposure = Math.round(s) + "\"";
                    }
                } catch (Exception e) {
                    exposure = shutter;
                }
            } else {
                float exp = params.getExposureCompensation() * params.getExposureCompensationStep();
                exposure = String.format("%+.1f EV", exp);
            }
            invalidate();
        } catch (Exception ignore) {}
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, event.values.length);
        }

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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        if (blurManager != null) {
            // Top Left HUD
            rectF.set(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16 + 80), AndroidUtilities.dp(16 + 40));
            drawGlass(canvas, rectF, AndroidUtilities.dp(12));

            // Top Right HUD
            rectF.set(getWidth() - AndroidUtilities.dp(16 + 100), AndroidUtilities.dp(16), getWidth() - AndroidUtilities.dp(16), AndroidUtilities.dp(16 + 40));
            drawGlass(canvas, rectF, AndroidUtilities.dp(12));

            // Leveler HUD
            rectF.set(cx - AndroidUtilities.dp(60), cy + AndroidUtilities.dp(80), cx + AndroidUtilities.dp(60), cy + AndroidUtilities.dp(110));
            drawGlass(canvas, rectF, AndroidUtilities.dp(15));
        }

        canvas.drawText(iso, AndroidUtilities.dp(24), AndroidUtilities.dp(32), textPaint);
        canvas.drawText(exposure, AndroidUtilities.dp(24), AndroidUtilities.dp(46), textPaint);

        canvas.drawText(coords, getWidth() - AndroidUtilities.dp(108), AndroidUtilities.dp(32), textPaint);
        canvas.drawText(alt, getWidth() - AndroidUtilities.dp(108), AndroidUtilities.dp(46), textPaint);

        // Leveler Graphic
        canvas.save();
        canvas.rotate(-roll, cx, cy);
        linePaint.setAlpha(Math.abs(roll) < 1.0f ? 255 : 128);
        canvas.drawLine(cx - AndroidUtilities.dp(50), cy, cx - AndroidUtilities.dp(10), cy, linePaint);
        canvas.drawLine(cx + AndroidUtilities.dp(10), cy, cx + AndroidUtilities.dp(50), cy, linePaint);
        canvas.restore();

        canvas.drawLine(cx, cy - AndroidUtilities.dp(15), cx, cy + AndroidUtilities.dp(15), linePaint);

        // Pitch/Roll numbers
        String telemetry = String.format(java.util.Locale.US, "P: %.1f°  R: %.1f°", pitch, roll);
        float tw = textPaint.measureText(telemetry);
        canvas.drawText(telemetry, cx - tw / 2f, cy + AndroidUtilities.dp(100), textPaint);
    }

    private void drawGlass(Canvas canvas, RectF rect, float r) {
        canvas.save();
        path.rewind();
        path.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rect, glassPaint);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }
}
