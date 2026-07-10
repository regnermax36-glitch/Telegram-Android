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
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Path clipPath = new Path();

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private float pitch, roll;
    private String isoStr = "ISO ---", exposureStr = "EV ---", shutterStr = "1/---";

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

        bgPaint.setColor(0x20000000);

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
        if (characteristics != null && result != null) {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);

            String newIso = iso != null ? "ISO " + iso : "ISO ---";
            String newShutter = "1/---";
            if (exposureTime != null && exposureTime > 0) {
                newShutter = "1/" + Math.round(1_000_000_000.0 / exposureTime);
            }

            Integer aeComp = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            String newEv = "EV ---";
            if (aeComp != null && step != null) {
                float ev = aeComp * step.floatValue();
                newEv = String.format(Locale.US, "EV %+.1f", ev);
            }

            if (!newIso.equals(isoStr) || !newEv.equals(exposureStr) || !newShutter.equals(shutterStr)) {
                isoStr = newIso;
                exposureStr = newEv;
                shutterStr = newShutter;
                invalidate();
            }
        }
    }

    public void updateMetadata(android.hardware.Camera.Parameters params) {
        if (params != null) {
            String iso = params.get("iso");
            if (iso == null) iso = params.get("iso-speed");
            if (iso == null) iso = params.get("nv-iso-speed");

            String newIso = iso != null ? "ISO " + iso : "ISO ---";

            float evStep = params.getExposureCompensationStep();
            int evIndex = params.getExposureCompensation();
            String newEv = String.format(Locale.US, "EV %+.1f", evIndex * evStep);

            if (!newIso.equals(isoStr) || !newEv.equals(exposureStr)) {
                isoStr = newIso;
                exposureStr = newEv;
                shutterStr = "1/---"; // Camera1 doesn't easily expose shutter speed
                invalidate();
            }
        }
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
        int w = getWidth();
        int h = getHeight();

        // Draw background glass
        AndroidUtilities.rectTmp.set(AndroidUtilities.dp(12), AndroidUtilities.dp(12), w - AndroidUtilities.dp(12), AndroidUtilities.dp(36));
        clipPath.rewind();
        clipPath.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(AndroidUtilities.rectTmp, bgPaint);
        canvas.restore();

        // Draw Metadata
        float x = AndroidUtilities.dp(24);
        float y = AndroidUtilities.dp(27);
        canvas.drawText(isoStr, x, y, textPaint);
        x += AndroidUtilities.dp(60);
        canvas.drawText(shutterStr, x, y, textPaint);
        x += AndroidUtilities.dp(60);
        canvas.drawText(exposureStr, x, y, textPaint);

        // Draw Telemetry
        x = w - AndroidUtilities.dp(100);
        canvas.drawText(String.format(Locale.US, "PTCH %+.1f°", pitch), x, y, textPaint);
        x += AndroidUtilities.dp(55);
        canvas.drawText(String.format(Locale.US, "ROLL %+.1f°", roll), x, y, textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(48));
    }
}
