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
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraInfo;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final RectF rect = new RectF();

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private float[] gravity = new float[3];
    private float[] geomagnetic = new float[3];
    private float pitch, roll;

    private int currentAccount = UserConfig.selectedAccount;
    private CameraView cameraView;
    private String iso = "ISO: --";
    private String shutter = "S: --";
    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);

        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        bgPaint.setColor(0x30ffffff);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        linePaint.setColor(0x40ffffff);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void setCameraView(CameraView cameraView) {
        this.cameraView = cameraView;
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata() {
        if (cameraView == null || System.currentTimeMillis() - lastMetadataUpdate < 500) return;
        lastMetadataUpdate = System.currentTimeMillis();

        CameraSessionWrapper session = cameraView.getCameraSession();
        if (session != null) {
            CameraInfo info = session.camera1Session != null ? session.camera1Session.cameraInfo : null;
            if (info != null && info.camera != null) {
                try {
                    Camera.Parameters params = info.camera.getParameters();
                    String isoVal = params.get("iso");
                    if (isoVal == null) isoVal = params.get("iso-speed");
                    if (isoVal == null) isoVal = params.get("nv-iso-speed");
                    iso = "ISO: " + (isoVal != null ? isoVal : "AUTO");

                    String exposureTime = params.get("exposure-time");
                    if (exposureTime == null) exposureTime = params.get("shutter-speed");
                    if (exposureTime != null) {
                        try {
                            float exp = Float.parseFloat(exposureTime);
                            if (exp > 0) {
                                shutter = exp < 1.0f ? String.format("1/%.0f", 1.0f / exp) : String.format("%.1fs", exp);
                            } else {
                                shutter = "S: AUTO";
                            }
                        } catch (NumberFormatException e) {
                            shutter = "S: " + exposureTime;
                        }
                    } else {
                        shutter = "S: AUTO";
                    }
                } catch (Exception ignore) {
                    iso = "ISO: AUTO";
                    shutter = "S: AUTO";
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (blurDrawer == null) return;

        updateMetadata();

        // Glass Cards
        float pad = AndroidUtilities.dp(16);
        float w = getMeasuredWidth();
        float h = getMeasuredHeight();

        // Top Telemetry
        rect.set(pad, pad + AndroidUtilities.statusBarHeight, w - pad, pad + AndroidUtilities.statusBarHeight + AndroidUtilities.dp(40));
        canvas.save();
        Path path = new Path();
        path.addRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), Path.Direction.CW);
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();
        canvas.drawRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), bgPaint);

        canvas.drawText(iso + "   " + shutter, rect.left + AndroidUtilities.dp(20), rect.centerY() + AndroidUtilities.dp(5), textPaint);

        // Orientation & GPS Glass Card
        String orient = String.format("PITCH: %.1f°  ROLL: %.1f°", pitch, roll);
        Location location = LocationController.getInstance(currentAccount).getLastKnownLocation();
        String gps = location != null ? String.format("LAT: %.4f  LON: %.4f  ALT: %.0fm", location.getLatitude(), location.getLongitude(), location.getAltitude()) : "GPS: SEARCHING...";

        float bottomCardH = AndroidUtilities.dp(50);
        rect.set(pad, h - pad - AndroidUtilities.dp(120) - bottomCardH, w - pad, h - pad - AndroidUtilities.dp(120));
        canvas.save();
        path.rewind();
        path.addRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), Path.Direction.CW);
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();
        canvas.drawRoundRect(rect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), bgPaint);

        canvas.drawText(orient, rect.left + AndroidUtilities.dp(20), rect.top + AndroidUtilities.dp(20), textPaint);
        canvas.drawText(gps, rect.left + AndroidUtilities.dp(20), rect.top + AndroidUtilities.dp(40), textPaint);
    }
}
