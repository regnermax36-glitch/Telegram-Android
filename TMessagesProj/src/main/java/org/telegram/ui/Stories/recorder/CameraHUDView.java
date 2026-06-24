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
import android.location.Location;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private float roll, pitch;

    private int currentAccount;
    private String iso = "---";
    private String shutter = "---";
    private String altitude = "---m";

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dp(1));
        borderPaint.setColor(0x25FFFFFF);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());

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

            Long exposureVal = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureVal != null) {
                double seconds = exposureVal / 1_000_000_000.0;
                if (seconds < 1.0) {
                    shutter = "1/" + Math.round(1.0 / seconds);
                } else {
                    shutter = String.format("%.1fs", seconds);
                }
            }
        }

        Location loc = LocationController.getInstance(currentAccount).getLastKnownLocation();
        if (loc != null && loc.hasAltitude()) {
            altitude = Math.round(loc.getAltitude()) + "m";
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(32);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.restore();

        canvas.drawRoundRect(rect, r, r, borderPaint);

        float padding = AndroidUtilities.dp(16);
        float x = padding;
        float y = getHeight() / 2f + textPaint.getTextSize() / 2f - AndroidUtilities.dp(1);

        canvas.drawText("ISO " + iso, x, y, textPaint);
        x += AndroidUtilities.dp(50);
        canvas.drawText("SHT " + shutter, x, y, textPaint);
        x += AndroidUtilities.dp(60);
        canvas.drawText(String.format("ROLL %.1f°", roll), x, y, textPaint);
        x += AndroidUtilities.dp(70);
        canvas.drawText(String.format("PTCH %.1f°", pitch), x, y, textPaint);
        x += AndroidUtilities.dp(70);
        canvas.drawText("ALT " + altitude, x, y, textPaint);
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
