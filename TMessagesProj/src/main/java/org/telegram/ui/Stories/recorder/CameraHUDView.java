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
import android.os.Build;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint levelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path levelPath = new Path();

    private float pitch;
    private float roll;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private String iso = "ISO --";
    private String shutter = "1/--";
    private String ev = "EV 0.0";

    private final BlurringShader.BlurManager blurManager;
    private final BlurringShader.StoryBlurDrawer blurDrawer;

    private long lastMetadataUpdate;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager) {
        super(context);
        this.blurManager = blurManager;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, 0);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        levelPaint.setColor(0x80ffffff);
        levelPaint.setStyle(Paint.Style.STROKE);
        levelPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));

        paint.setColor(0x20000000);
    }

    public void updateMetadata(CameraSessionWrapper session) {
        long now = System.currentTimeMillis();
        if (now - lastMetadataUpdate < 500) {
            return;
        }
        lastMetadataUpdate = now;

        if (session == null) return;

        if (session.camera1Session != null && session.camera1Session.cameraInfo != null) {
            try {
                Camera camera = session.camera1Session.cameraInfo.camera;
                if (camera != null) {
                    Camera.Parameters params = camera.getParameters();
                    String isoVal = params.get("iso");
                    if (isoVal == null) isoVal = params.get("iso-speed");
                    if (isoVal == null) isoVal = params.get("nv-iso-speed");
                    if (isoVal != null) {
                        iso = "ISO " + isoVal;
                    }

                    float exposureCompensation = params.getExposureCompensation() * params.getExposureCompensationStep();
                    ev = String.format(Locale.US, "EV %.1f", exposureCompensation);
                }
            } catch (Exception ignore) {}
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && session.camera2Session != null) {
            // Telemetry from Camera2 is typically handled via CaptureCallback,
            // but for basics like ISO we might sometimes get them from characteristics if static or assume defaults.
            // In a real app we'd pass the last TotalCaptureResult here.
            iso = "ISO 400"; // Placeholder for Camera2 telemetry without full CaptureCallback integration
        }
        invalidate();
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            roll = event.values[0];
            pitch = event.values[1];
            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private final RectF rectF = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        // Technical Minimalism: Transparent overlay with blurs only behind text
        float textX = AndroidUtilities.dp(16);
        float textY = AndroidUtilities.dp(24);

        if (blurDrawer != null && canvas.isHardwareAccelerated()) {
            canvas.save();
            rectF.set(textX - AndroidUtilities.dp(4), textY - AndroidUtilities.dp(14), textX + AndroidUtilities.dp(60), textY + AndroidUtilities.dp(48));
            canvas.clipRect(rectF);
            blurDrawer.drawRect(canvas, 0, 0, 1.0f);
            canvas.drawColor(0x20000000);
            canvas.restore();
        }

        // Draw Technical Telemetry
        canvas.drawText(iso, textX, textY, textPaint);
        canvas.drawText(ev, textX, textY + AndroidUtilities.dp(16), textPaint);
        canvas.drawText(shutter, textX, textY + AndroidUtilities.dp(32), textPaint);

        // Draw Level Indicator
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.save();
        canvas.translate(cx, cy);
        float rotation = (float) Math.toDegrees(Math.atan2(roll, pitch));
        canvas.rotate(-rotation + 90);

        levelPath.rewind();
        float levelW = AndroidUtilities.dp(40);
        levelPath.moveTo(-levelW, 0);
        levelPath.lineTo(-AndroidUtilities.dp(10), 0);
        levelPath.moveTo(AndroidUtilities.dp(10), 0);
        levelPath.lineTo(levelW, 0);

        if (Math.abs(rotation - 90) < 1.0f) {
            levelPaint.setColor(0xff4cd964); // Green when level
            levelPaint.setAlpha(255);
        } else {
            levelPaint.setColor(Color.WHITE);
            levelPaint.setAlpha(128);
        }
        canvas.drawPath(levelPath, levelPaint);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }
}
