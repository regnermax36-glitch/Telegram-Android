package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.location.Location;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private String iso = "---";
    private String shutter = "---";
    private String ev = "0.0";
    private String geo = "---";
    private float roll, pitch;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private int currentAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        textPaint.setTextSize(dp(10));
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setLetterSpacing(0.05f);

        linePaint.setColor(0x40FFFFFF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1));

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

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result != null) {
            Integer isoVal = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (isoVal != null) iso = String.valueOf(isoVal);

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds >= 1) {
                    shutter = String.format(Locale.US, "%.1fs", seconds);
                } else {
                    shutter = "1/" + Math.round(1.0 / seconds);
                }
            }

            Integer exposureComp = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (exposureComp != null && characteristics != null) {
                android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (step != null) {
                    ev = String.format(Locale.US, "%s%.1f", exposureComp >= 0 ? "+" : "", exposureComp * step.floatValue());
                }
            }
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null) {
            geo = String.format(Locale.US, "%.3f, %.3f", loc.getLatitude(), loc.getLongitude());
        }

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(dp(140), dp(64));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = dp(12);
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawColor(0x20FFFFFF);
        canvas.restore();

        canvas.drawRoundRect(rect, r, r, linePaint);

        float x = dp(8);
        float y = dp(16);
        float dy = dp(14);

        canvas.drawText("ISO " + iso, x, y, textPaint);
        canvas.drawText("SHT " + shutter, x + dp(60), y, textPaint);
        canvas.drawText("EV " + ev, x, y + dy, textPaint);
        canvas.drawText("GEO " + geo, x, y + dy * 2, textPaint);

        canvas.drawText(String.format(Locale.US, "ROLL %.1f°", roll), x + dp(60), y + dy, textPaint);
        canvas.drawText(String.format(Locale.US, "PTCH %.1f°", pitch), x + dp(60), y + dy * 2, textPaint);
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
}
