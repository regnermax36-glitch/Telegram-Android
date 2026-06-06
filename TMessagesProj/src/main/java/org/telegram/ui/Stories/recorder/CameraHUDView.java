package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.util.Rational;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;

    private String iso = "---";
    private String shutter = "---";
    private String ev = "+0.0";
    private float roll;
    private float pitch;

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private final Path path = new Path();
    private final RectF rectF = new RectF();

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        if (Build.VERSION.SDK_INT >= 21) {
            textPaint.setLetterSpacing(0.05f);
        }

        linePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 0x40));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
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

    public void updateMetadata(Camera.Parameters params) {
        if (params == null) return;

        String isoVal = params.get("iso");
        if (isoVal == null) isoVal = params.get("iso-speed");
        if (isoVal == null) isoVal = params.get("nv-iso-speed");
        this.iso = isoVal != null ? isoVal : "---";

        try {
            float exposureTime = Float.parseFloat(params.get("exposure-time"));
            if (exposureTime > 0) {
                if (exposureTime < 1.0f) {
                    this.shutter = "1/" + Math.round(1.0f / exposureTime);
                } else {
                    this.shutter = String.format("%.1fs", exposureTime);
                }
            }
        } catch (Exception ignore) {}

        float evStep = params.getExposureCompensationStep();
        int evIndex = params.getExposureCompensation();
        this.ev = (evIndex >= 0 ? "+" : "") + String.format("%.1f", evIndex * evStep);

        invalidate();
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (characteristics == null || result == null) return;

        Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
        this.iso = isoVal != null ? String.valueOf(isoVal) : "---";

        Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposureTimeNs != null && exposureTimeNs > 0) {
            double exposureTime = exposureTimeNs / 1_000_000_000.0;
            if (exposureTime < 1.0) {
                this.shutter = "1/" + Math.round(1.0 / exposureTime);
            } else {
                this.shutter = String.format("%.1fs", exposureTime);
            }
        }

        Rational evStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        Integer evIndex = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
        if (evStep != null && evIndex != null) {
            float evVal = evIndex * evStep.floatValue();
            this.ev = (evVal >= 0 ? "+" : "") + String.format("%.1f", evVal);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = AndroidUtilities.dp(32);
        rectF.set(0, 0, getWidth(), getHeight());
        path.rewind();
        path.addRoundRect(rectF, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();

        // Draw ISO/Shutter/EV
        float margin = AndroidUtilities.dp(16);
        canvas.drawText("ISO " + iso.toUpperCase(), margin, margin + AndroidUtilities.dp(10), textPaint);
        canvas.drawText("SHT " + shutter, margin, margin + AndroidUtilities.dp(24), textPaint);
        canvas.drawText("EV " + ev, margin, margin + AndroidUtilities.dp(38), textPaint);

        // Draw Level
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float len = AndroidUtilities.dp(40);

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(-roll);
        canvas.drawLine(-len, 0, len, 0, linePaint);
        canvas.drawLine(0, -AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), linePaint);
        canvas.restore();

        canvas.drawText("ROLL " + Math.round(roll) + "°", getWidth() - margin - textPaint.measureText("ROLL -00°"), margin + AndroidUtilities.dp(10), textPaint);
        canvas.drawText("PTCH " + Math.round(pitch) + "°", getWidth() - margin - textPaint.measureText("ROLL -00°"), margin + AndroidUtilities.dp(24), textPaint);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            roll = (float) Math.toDegrees(Math.atan2(x, Math.sqrt(y * y + z * z)));
            pitch = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }
}
