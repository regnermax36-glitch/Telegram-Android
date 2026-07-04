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
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch, roll;
    private String iso = "ISO --";
    private String shutter = "1/--";

    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.CENTER);

        bgPaint.setColor(0x15000000);
        borderPaint.setColor(0x25FFFFFF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
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

    public void updateMetadata(CameraSessionWrapper session) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (session == null || !session.isInitiated()) {
            return;
        }

        if (session.isCamera2()) {
            Camera2Session s2 = session.getCamera2Session();
            CaptureResult result = s2.getLastCaptureResult();
            if (result != null) {
                Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
                if (isoVal != null) {
                    iso = "ISO " + isoVal;
                }
                Long expTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                if (expTime != null) {
                    float seconds = expTime / 1_000_000_000f;
                    if (seconds < 1) {
                        shutter = "1/" + Math.round(1f / seconds);
                    } else {
                        shutter = String.format(Locale.US, "%.1fs", seconds);
                    }
                }
            }
        } else {
            if (session != null && session.getCamera() != null) {
                org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                    try {
                        Camera.Parameters params = session.getCamera().getParameters();
                        String isoVal = params.get("iso");
                        if (isoVal == null) isoVal = params.get("iso-speed");
                        if (isoVal == null) isoVal = params.get("nv-iso-speed");
                        final String finalIsoVal = isoVal;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (finalIsoVal != null) {
                                iso = "ISO " + finalIsoVal;
                            }
                            shutter = "1/--";
                            invalidate();
                        });
                    } catch (Exception ignore) {}
                });
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(160), AndroidUtilities.dp(32));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(16);

        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f, false);
        canvas.drawRect(rect, bgPaint);
        canvas.restore();

        canvas.drawRoundRect(rect, r, r, borderPaint);

        float sectionW = getWidth() / 4f;
        float cy = getHeight() / 2f + AndroidUtilities.dp(3.5f);

        canvas.drawText(iso, sectionW * 0.5f, cy, textPaint);
        canvas.drawText(shutter, sectionW * 1.5f, cy, textPaint);
        canvas.drawText(String.format(Locale.US, "P %+d°", Math.round(pitch)), sectionW * 2.5f, cy, textPaint);
        canvas.drawText(String.format(Locale.US, "R %+d°", Math.round(roll)), sectionW * 3.5f, cy, textPaint);
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
