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
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float[] gravity;
    private float[] geomagnetic;
    private float pitch, roll;

    private int currentAccount = UserConfig.selectedAccount;
    private String iso = "---";
    private String shutter = "---";
    private String coords = "---";
    private String altitude = "---";

    private long lastMetadataUpdate;
    private Camera lastCamera;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        glassPaint.setColor(0x15ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magSensor != null) {
            sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(Camera camera) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500 && lastCamera == camera) {
            return;
        }
        lastMetadataUpdate = now;
        lastCamera = camera;

        if (camera != null) {
            try {
                Camera.Parameters params = camera.getParameters();
                String isoVal = params.get("iso");
                if (isoVal == null) isoVal = params.get("iso-speed");
                if (isoVal == null) isoVal = params.get("nv-iso-speed");
                iso = isoVal != null ? isoVal : "AUTO";

                float exposureTime = 0;
                try {
                    String exp = params.get("exposure-time");
                    if (exp != null) exposureTime = Float.parseFloat(exp);
                } catch (Exception ignore) {}

                if (exposureTime > 0) {
                    shutter = String.format(Locale.US, "1/%d", Math.round(1.0f / exposureTime));
                } else {
                    shutter = "AUTO";
                }
            } catch (Exception ignore) {}
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            coords = String.format(Locale.US, "%.4f, %.4f", loc.getLatitude(), loc.getLongitude());
            altitude = String.format(Locale.US, "%.0fm", loc.getAltitude());
        } else {
            coords = "NO GPS";
            altitude = "---";
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blurManager == null) return;

        float padding = AndroidUtilities.dp(18);
        float itemW = AndroidUtilities.dp(70);
        float itemH = AndroidUtilities.dp(34);
        float r = AndroidUtilities.dp(8);

        // Draw ISO/Shutter card
        drawCard(canvas, padding, padding, itemW, itemH, r, "ISO " + iso, "SHUT " + shutter);

        // Draw Orientation/GPS card
        float rightX = getMeasuredWidth() - itemW - padding;
        drawCard(canvas, rightX, padding, itemW, itemH, r,
                String.format(Locale.US, "P %.0f° R %.0f°", pitch, roll),
                altitude + " " + coords);
    }

    private void drawCard(Canvas canvas, float x, float y, float w, float h, float r, String line1, String text2) {
        rect.set(x, y, x + w, y + h);
        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);

        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rect, glassPaint);

        canvas.drawText(line1, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(14), textPaint);
        canvas.drawText(text2, x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(28), textPaint);
        canvas.restore();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
        }

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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}
