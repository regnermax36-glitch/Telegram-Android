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
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.ui.Components.BlurringShader;

import java.util.Locale;

public class CameraHUDView extends View implements SensorEventListener {

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurringShader.StoryBlurDrawer blurDrawer;
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private final StoryRecorder storyRecorder;
    private float roll;
    private float pitch;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private boolean wasLevel = false;

    private String isoStr = "ISO --";
    private String shutterStr = "1/--";
    private String evStr = "EV --";

    // Gesture manual EV Controls
    private float startY;
    private int startEV;
    private boolean isDraggingEV = false;
    private float evRulerAlpha = 0f;
    private final Runnable fadeOutRulerRunnable = this::fadeOutRuler;

    public CameraHUDView(Context context, BlurringShader.BlurManager blurManager, StoryRecorder storyRecorder) {
        super(context);
        this.storyRecorder = storyRecorder;
        this.blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_IOS28);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setShadowLayer(AndroidUtilities.dp(1), 0, AndroidUtilities.dp(0.5f), 0x80000000);

        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

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
        AndroidUtilities.cancelRunOnUIThread(fadeOutRulerRunnable);
    }

    public void updateMetadata(Camera.Parameters params) {
        if (params == null) return;
        try {
            int iso = 0;
            String isoVal = params.get("iso");
            if (isoVal == null) isoVal = params.get("iso-speed");
            if (isoVal == null) isoVal = params.get("nv-iso-speed");
            if (isoVal != null) {
                isoStr = "ISO " + isoVal;
            }

            float ev = params.getExposureCompensation() * params.getExposureCompensationStep();
            evStr = String.format(Locale.US, "EV %+.1f", ev);

            invalidate();
        } catch (Exception ignore) {}
    }

    public void updateMetadata(CameraCharacteristics characteristics, CaptureResult result) {
        if (result == null) return;
        try {
            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (iso != null) {
                isoStr = "ISO " + iso;
            }

            Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime != null) {
                double seconds = exposureTime / 1_000_000_000.0;
                if (seconds >= 1) {
                    shutterStr = String.format(Locale.US, "%.1fs", seconds);
                } else {
                    shutterStr = "1/" + Math.round(1.0 / seconds);
                }
            }

            Integer evIdx = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            if (evIdx != null && characteristics != null) {
                android.util.Rational step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (step != null) {
                    float ev = evIdx * step.floatValue();
                    evStr = String.format(Locale.US, "EV %+.1f", ev);
                }
            }
            invalidate();
        } catch (Exception ignore) {}
    }

    private void fadeOutRuler() {
        if (evRulerAlpha > 0f) {
            evRulerAlpha -= 0.05f;
            if (evRulerAlpha < 0f) {
                evRulerAlpha = 0f;
            }
            invalidate();
            if (evRulerAlpha > 0f) {
                AndroidUtilities.runOnUIThread(fadeOutRulerRunnable, 16);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (storyRecorder.getCameraView() == null) {
            return super.onTouchEvent(event);
        }
        DualCameraView cameraView = storyRecorder.getCameraView();
        CameraSessionWrapper session = cameraView.getCameraSession();
        if (session == null || !session.isInitiated()) {
            return super.onTouchEvent(event);
        }

        int action = event.getAction();
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            // Check if touch is on the right side of the screen
            if (x > getWidth() - AndroidUtilities.dp(80)) {
                startY = y;
                startEV = session.getCurrentEV();
                isDraggingEV = true;
                evRulerAlpha = 1.0f;
                AndroidUtilities.cancelRunOnUIThread(fadeOutRulerRunnable);
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (isDraggingEV) {
                float dy = startY - y;
                int evDelta = (int) (dy / AndroidUtilities.dp(20));
                int targetEV = startEV + evDelta;
                int minEV = session.getMinEV();
                int maxEV = session.getMaxEV();
                targetEV = Math.max(minEV, Math.min(maxEV, targetEV));

                if (targetEV != session.getCurrentEV()) {
                    session.setEV(targetEV);
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                evRulerAlpha = 1.0f;
                AndroidUtilities.cancelRunOnUIThread(fadeOutRulerRunnable);
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (isDraggingEV) {
                isDraggingEV = false;
                AndroidUtilities.cancelRunOnUIThread(fadeOutRulerRunnable);
                AndroidUtilities.runOnUIThread(fadeOutRulerRunnable, 1500);
                invalidate();
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();

        // Draw Composition Grid Lines (Rule of Thirds + Golden Ratio + Diagonal guides)
        float thirdW = w / 3f;
        float thirdH = h / 3f;
        gridPaint.setColor(0x13FFFFFF); // Ultra-subtle composition guides
        gridPaint.setStrokeWidth(AndroidUtilities.dp(0.5f));
        gridPaint.setStyle(Paint.Style.STROKE);

        // Rule of Thirds
        canvas.drawLine(thirdW, 0, thirdW, h, gridPaint);
        canvas.drawLine(thirdW * 2, 0, thirdW * 2, h, gridPaint);
        canvas.drawLine(0, thirdH, w, thirdH, gridPaint);
        canvas.drawLine(0, thirdH * 2, w, thirdH * 2, gridPaint);

        // Golden Ratio lines
        gridPaint.setColor(0x06FFFFFF);
        canvas.drawLine(w * 0.382f, 0, w * 0.382f, h, gridPaint);
        canvas.drawLine(w * 0.618f, 0, w * 0.618f, h, gridPaint);
        canvas.drawLine(0, h * 0.382f, w, h * 0.382f, gridPaint);
        canvas.drawLine(0, h * 0.618f, w, h * 0.618f, gridPaint);

        // Diagonals
        canvas.drawLine(0, 0, w, h, gridPaint);
        canvas.drawLine(w, 0, 0, h, gridPaint);

        // Center crosshair
        float crossSize = AndroidUtilities.dp(6);
        gridPaint.setColor(0x20FFFFFF);
        gridPaint.setStrokeWidth(AndroidUtilities.dp(1f));
        canvas.drawLine(w / 2 - crossSize, h / 2, w / 2 + crossSize, h / 2, gridPaint);
        canvas.drawLine(w / 2, h / 2 - crossSize, w / 2, h / 2 + crossSize, gridPaint);


        // Draw Telemetry Glass Card (Slightly larger to fit the live histogram)
        float cardW = AndroidUtilities.dp(110);
        float cardH = AndroidUtilities.dp(50);
        float cardX = AndroidUtilities.dp(16);
        float cardY = h - cardH - AndroidUtilities.dp(100);

        rect.set(cardX, cardY, cardX + cardW, cardY + cardH);
        path.rewind();
        path.addRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        blurDrawer.drawRect(canvas, 0, 0, 1.0f);

        // Render Live Reacting Luminance Histogram inside the glass telemetry card
        Path histPath = new Path();
        float histX = cardX + AndroidUtilities.dp(72);
        float histW = AndroidUtilities.dp(32);
        float histY = cardY + cardH - AndroidUtilities.dp(4);
        float histH = AndroidUtilities.dp(24);

        histPath.moveTo(histX, histY);

        float evPercent = 0.5f; // Neutral default
        try {
            DualCameraView cv = storyRecorder.getCameraView();
            if (cv != null && cv.getCameraSession() != null) {
                CameraSessionWrapper s = cv.getCameraSession();
                int minEV = s.getMinEV();
                int maxEV = s.getMaxEV();
                int curEV = s.getCurrentEV();
                if (maxEV > minEV) {
                    evPercent = (float) (curEV - minEV) / (maxEV - minEV);
                }
            }
        } catch (Exception ignore) {}

        float peak = histX + histW * evPercent;
        double time = System.currentTimeMillis() / 200.0;
        float noise1 = (float) Math.sin(time) * AndroidUtilities.dp(2);
        float noise2 = (float) Math.cos(time * 1.5) * AndroidUtilities.dp(1.5f);

        histPath.lineTo(histX, histY);
        histPath.cubicTo(
            histX + histW * 0.25f, histY,
            peak - histW * 0.15f, histY - histH * 0.6f + noise1,
            peak, histY - histH * 0.9f + noise2
        );
        histPath.cubicTo(
            peak + histW * 0.15f, histY - histH * 0.9f + noise2,
            histX + histW * 0.75f, histY,
            histX + histW, histY
        );
        histPath.lineTo(histX + histW, histY);
        histPath.close();

        Paint histFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        histFillPaint.setStyle(Paint.Style.FILL);
        histFillPaint.setColor(0x4000FFCC); // Soft Cyan fill
        canvas.drawPath(histPath, histFillPaint);

        Paint histStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        histStrokePaint.setStyle(Paint.Style.STROKE);
        histStrokePaint.setStrokeWidth(AndroidUtilities.dp(1f));
        histStrokePaint.setColor(0xFF00FFCC); // Cyan outline
        canvas.drawPath(histPath, histStrokePaint);

        canvas.restore();

        // Telemetry Card Border Outline
        Paint cardOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardOutline.setStyle(Paint.Style.STROKE);
        cardOutline.setStrokeWidth(AndroidUtilities.dp(1f));
        cardOutline.setColor(0x25FFFFFF);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), cardOutline);

        // Text labels
        canvas.drawText(isoStr, cardX + AndroidUtilities.dp(8), cardY + AndroidUtilities.dp(14), textPaint);
        canvas.drawText(shutterStr, cardX + AndroidUtilities.dp(8), cardY + AndroidUtilities.dp(28), textPaint);
        canvas.drawText(evStr, cardX + AndroidUtilities.dp(8), cardY + AndroidUtilities.dp(42), textPaint);


        // Draw Manual Gesture EV Slide Ruler Scale
        if (evRulerAlpha > 0f) {
            float rulerH = AndroidUtilities.dp(160);
            float rulerY = h / 2;
            float rulerX = w - AndroidUtilities.dp(28);

            rect.set(rulerX - AndroidUtilities.dp(16), rulerY - rulerH / 2, rulerX + AndroidUtilities.dp(16), rulerY + rulerH / 2);
            path.rewind();
            path.addRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            blurDrawer.drawRect(canvas, 0, 0, evRulerAlpha * 0.7f);
            canvas.restore();

            Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(1));
            outlinePaint.setColor(Color.argb((int) (evRulerAlpha * 37), 255, 255, 255));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

            try {
                DualCameraView cameraView = storyRecorder.getCameraView();
                if (cameraView != null && cameraView.getCameraSession() != null) {
                    CameraSessionWrapper session = cameraView.getCameraSession();
                    int curEV = session.getCurrentEV();
                    int minEV = session.getMinEV();
                    int maxEV = session.getMaxEV();
                    float step = session.getEVStep();
                    int range = maxEV - minEV;

                    if (range > 0) {
                        for (int i = minEV; i <= maxEV; i++) {
                            float pct = (float) (i - minEV) / range;
                            float ty = rulerY + rulerH / 2 - AndroidUtilities.dp(12) - (rulerH - AndroidUtilities.dp(24)) * pct;

                            float lineLen;
                            int color;
                            if (i == curEV) {
                                lineLen = AndroidUtilities.dp(14);
                                color = Color.argb((int) (evRulerAlpha * 255), 0, 255, 204); // Cyber Cyan
                            } else if (i % 3 == 0 || i == 0) {
                                lineLen = AndroidUtilities.dp(8);
                                color = Color.argb((int) (evRulerAlpha * 170), 255, 255, 255);
                            } else {
                                lineLen = AndroidUtilities.dp(5);
                                color = Color.argb((int) (evRulerAlpha * 85), 255, 255, 255);
                            }

                            Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            tickPaint.setColor(color);
                            tickPaint.setStrokeWidth(AndroidUtilities.dp(1));
                            canvas.drawLine(rulerX - lineLen, ty, rulerX, ty, tickPaint);

                            if (i == curEV || i == 0 || i == minEV || i == maxEV) {
                                Paint evTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                evTextPaint.setColor(i == curEV ? Color.argb((int) (evRulerAlpha * 255), 0, 255, 204) : Color.argb((int) (evRulerAlpha * 170), 255, 255, 255));
                                evTextPaint.setTextSize(AndroidUtilities.dp(8));
                                evTextPaint.setTypeface(AndroidUtilities.bold());
                                String label = String.format(Locale.US, "%+.1f", i * step);
                                if (i == 0) label = "0.0";
                                canvas.drawText(label, rulerX - lineLen - AndroidUtilities.dp(14), ty + AndroidUtilities.dp(3), evTextPaint);
                            }
                        }
                    }
                }
            } catch (Exception ignore) {}
        }


        // Draw Horizon Leveler
        canvas.save();
        canvas.translate(w / 2, h / 2);
        canvas.rotate(-roll);

        float lineW = AndroidUtilities.dp(40);
        linePaint.setAlpha(128);
        canvas.drawLine(-lineW, 0, -AndroidUtilities.dp(10), 0, linePaint);
        canvas.drawLine(AndroidUtilities.dp(10), 0, lineW, 0, linePaint);

        if (Math.abs(roll) < 1.0f) {
            linePaint.setColor(0xFF00FF00);
            linePaint.setAlpha(255);
        } else {
            linePaint.setColor(Color.WHITE);
            linePaint.setAlpha(255);
        }
        canvas.drawCircle(0, 0, AndroidUtilities.dp(2), linePaint);

        canvas.restore();

        // Pitch/Roll technical data
        String telemetry = String.format(Locale.US, "ROLL %.1f°  PTCH %.1f°", roll, pitch);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(telemetry, w / 2, h / 2 + AndroidUtilities.dp(60), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
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

            // Cinematic Horizon Leveler vibration feedback
            if (Math.abs(roll) < 1.0f) {
                if (!wasLevel) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    wasLevel = true;
                }
            } else if (Math.abs(roll) > 1.5f) {
                wasLevel = false;
            }

            invalidate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
