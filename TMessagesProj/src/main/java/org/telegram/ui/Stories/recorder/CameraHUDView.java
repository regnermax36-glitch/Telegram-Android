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
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private String iso = "ISO --";
    private String shutter = "1/--";
    private String ev = "EV 0.0";
    private String ptch = "PTCH 0°";
    private String roll = "ROLL 0°";
    private String alti = "ALT 0m";
    private String pos = "0.0000° N 0.0000° E";

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        bgPaint.setColor(0x15ffffff);
        strokePaint.setColor(0x25ffffff);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    private long lastMetadataUpdate;

    public void updateMetadata(CameraSessionWrapper session) {
        if (session == null) return;

        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (session.isCamera2()) {
            Camera2Session s = session.getCamera2Session();
            if (s != null) {
                CaptureResult result = s.getLastCaptureResult();
                if (result != null) {
                    Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
                    Long exposureVal = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    if (isoVal != null) iso = "ISO " + isoVal;
                    if (exposureVal != null) {
                        double seconds = exposureVal / 1_000_000_000.0;
                        if (seconds > 0) {
                            if (seconds >= 1) {
                                shutter = String.format(Locale.US, "%.1fs", seconds);
                            } else {
                                shutter = "1/" + (int) (1.0 / seconds);
                            }
                        }
                    }
                }
            }
        } else {
            CameraSession s = session.getCamera1Session();
            if (s != null) {
                Camera camera = s.getCamera();
                if (camera != null) {
                    try {
                        Camera.Parameters params = camera.getParameters();
                        String isoVal = params.get("iso");
                        if (isoVal == null) isoVal = params.get("iso-speed");
                        if (isoVal == null) isoVal = params.get("nv-iso-speed");
                        if (isoVal != null) iso = "ISO " + isoVal;

                        int evIndex = params.getExposureCompensation();
                        float evStep = params.getExposureCompensationStep();
                        ev = String.format(Locale.US, "EV %.1f", evIndex * evStep);
                    } catch (Exception ignore) {}
                }
            }
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            alti = String.format(Locale.US, "ALT %.0fm", loc.getAltitude());
            pos = String.format(Locale.US, "%.4f° %s %.4f° %s",
                Math.abs(loc.getLatitude()), loc.getLatitude() >= 0 ? "N" : "S",
                Math.abs(loc.getLongitude()), loc.getLongitude() >= 0 ? "E" : "W");
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float padding = AndroidUtilities.dp(12);
        float w = AndroidUtilities.dp(130);
        float h = AndroidUtilities.dp(84);
        float r = AndroidUtilities.dp(32);

        float x = padding;
        float y = padding;

        path.rewind();
        RectF rect = AndroidUtilities.rectTmp;
        rect.set(x, y, x + w, y + h);
        path.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rect, bgPaint);
        canvas.restore();
        canvas.drawPath(path, strokePaint);

        float tx = x + AndroidUtilities.dp(10);
        float ty = y + AndroidUtilities.dp(16);
        canvas.drawText(iso, tx, ty, textPaint); ty += AndroidUtilities.dp(14);
        canvas.drawText(shutter, tx, ty, textPaint); ty += AndroidUtilities.dp(14);
        canvas.drawText(ev, tx, ty, textPaint);

        tx = x + w / 2 + AndroidUtilities.dp(4);
        ty = y + AndroidUtilities.dp(16);
        canvas.drawText(ptch, tx, ty, textPaint); ty += AndroidUtilities.dp(14);
        canvas.drawText(roll, tx, ty, textPaint); ty += AndroidUtilities.dp(14);
        canvas.drawText(alti, tx, ty, textPaint);

        canvas.drawText(pos, x + AndroidUtilities.dp(10), y + h - AndroidUtilities.dp(10), textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            ptch = String.format(Locale.US, "PTCH %.0f°", Math.toDegrees(orientationAngles[1]));
            roll = String.format(Locale.US, "ROLL %.0f°", Math.toDegrees(orientationAngles[2]));
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
}
