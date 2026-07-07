package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rectF = new RectF();

    private String iso = "--";
    private String shutter = "--";
    private float pitch, roll;

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        bgPaint.setColor(0x40000000);

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

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result != null) {
            boolean changed = false;
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (isoVal != null) {
                String newIso = String.valueOf(isoVal);
                if (!newIso.equals(iso)) {
                    iso = newIso;
                    changed = true;
                }
            }
            if (exposureTime != null) {
                String newShutter;
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds < 1.0) {
                    newShutter = "1/" + Math.round(1.0 / seconds);
                } else {
                    newShutter = String.format("%.1fs", seconds);
                }
                if (!newShutter.equals(shutter)) {
                    shutter = newShutter;
                    changed = true;
                }
            }
            if (changed) {
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(AndroidUtilities.dp(120), AndroidUtilities.dp(64));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rectF.set(0, 0, getWidth(), getHeight());
        path.rewind();
        path.addRoundRect(rectF, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);

        canvas.save();
        canvas.clipPath(path);
        if (blurDrawer != null) {
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        } else {
            canvas.drawRect(rectF, bgPaint);
        }
        canvas.restore();

        float x = AndroidUtilities.dp(12);
        float y = AndroidUtilities.dp(18);
        canvas.drawText("ISO: " + iso, x, y, textPaint);
        canvas.drawText("SHTR: " + shutter, x, y + AndroidUtilities.dp(14), textPaint);
        canvas.drawText("PTCH: " + Math.round(pitch) + "°", x, y + AndroidUtilities.dp(28), textPaint);
        canvas.drawText("ROLL: " + Math.round(roll) + "°", x, y + AndroidUtilities.dp(42), textPaint);
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
}
