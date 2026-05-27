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
import android.location.Location;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();

    private float iso;
    private String exposure;
    private String shutter;
    private float pitch;
    private float roll;
    private Location location;

    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private int currentAccount = UserConfig.selectedAccount;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textPaint.setLetterSpacing(0.05f);
        }

        bgPaint.setColor(0x15ffffff);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setCurrentAccount(int account) {
        this.currentAccount = account;
    }

    public void updateMetadata(float iso, String exposure, String shutter) {
        this.iso = iso;
        this.exposure = exposure;
        this.shutter = shutter;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        updateLocation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sensorManager.unregisterListener(this);
    }

    private void updateLocation() {
        location = LocationController.getInstance(currentAccount).getLastKnownLocation();
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
        rectF.set(0, 0, getWidth(), getHeight());
        float r = AndroidUtilities.dp(32);
        clipPath.rewind();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);
        canvas.drawRect(rectF, bgPaint);

        float x = AndroidUtilities.dp(24);
        float y = AndroidUtilities.dp(28);
        float lineStep = AndroidUtilities.dp(18);

        drawText(canvas, String.format(Locale.US, "ISO %d", (int)iso), x, y);
        y += lineStep;
        drawText(canvas, String.format(Locale.US, "EV %s", exposure != null ? exposure : "0.0"), x, y);
        y += lineStep;
        drawText(canvas, String.format(Locale.US, "S %s", shutter != null ? shutter : "1/100"), x, y);

        y = AndroidUtilities.dp(28);
        x = getWidth() / 2f;
        drawText(canvas, String.format(Locale.US, "PTCH %.1f°", pitch), x, y);
        y += lineStep;
        drawText(canvas, String.format(Locale.US, "ROLL %.1f°", roll), x, y);

        if (location != null) {
            y = AndroidUtilities.dp(28);
            x = getWidth() - AndroidUtilities.dp(24);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            drawText(canvas, String.format(Locale.US, "LAT %.4f", location.getLatitude()), x, y);
            y += lineStep;
            drawText(canvas, String.format(Locale.US, "LON %.4f", location.getLongitude()), x, y);
            y += lineStep;
            drawText(canvas, String.format(Locale.US, "ALT %.1fm", location.getAltitude()), x, y);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        canvas.restore();
    }

    private void drawText(Canvas canvas, String text, float x, float y) {
        canvas.drawText(text, x, y, textPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(84));
    }
}
