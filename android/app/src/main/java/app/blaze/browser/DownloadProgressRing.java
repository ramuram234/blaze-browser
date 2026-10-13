package app.blaze.browser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Locale;

/**
 * Circular progress arc overlaid on the download button in VideoPlayerActivity.
 * Inspired by UC Browser's TrafficRoundProgressBar pattern (circular arc via Canvas.drawArc).
 */
public class DownloadProgressRing extends View {

    private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval       = new RectF();

    private int     progress      = 0;      // 0-100; -1 = indeterminate
    private boolean indeterminate = false;
    private boolean complete      = false;
    private float   indeterminateAngle = 0f;
    private ValueAnimator spinAnimator;

    public DownloadProgressRing(Context ctx) { super(ctx); init(ctx); }
    public DownloadProgressRing(Context ctx, AttributeSet a) { super(ctx, a); init(ctx); }
    public DownloadProgressRing(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(ctx); }

    private void init(Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        float strokeW = 5f * density;

        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeW);
        bgPaint.setColor(0x44FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeW);
        arcPaint.setColor(0xFF1A73E8);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(11f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** Returns sweep angle in degrees for a given percentage. */
    public static float sweepAngle(int percent) {
        return percent * 3.6f;
    }

    public void setProgress(int pct) {
        if (pct == -1) {
            indeterminate = true;
            startSpin();
        } else {
            indeterminate = false;
            stopSpin();
            progress = Math.max(0, Math.min(100, pct));
        }
        invalidate();
    }

    public void setComplete(boolean c) {
        complete = c;
        indeterminate = false;
        stopSpin();
        invalidate();
    }

    private void startSpin() {
        if (spinAnimator != null && spinAnimator.isRunning()) return;
        spinAnimator = ValueAnimator.ofFloat(0f, 360f);
        spinAnimator.setDuration(1000);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setInterpolator(new LinearInterpolator());
        spinAnimator.addUpdateListener(a -> {
            indeterminateAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        spinAnimator.start();
    }

    private void stopSpin() {
        if (spinAnimator != null) { spinAnimator.cancel(); spinAnimator = null; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth()  / 2f;
        float cy = getHeight() / 2f;
        float pad = arcPaint.getStrokeWidth();
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);

        // Background circle
        canvas.drawArc(oval, 0, 360, false, bgPaint);

        if (complete) {
            // Checkmark
            canvas.drawArc(oval, -90, 360, false, arcPaint);
            canvas.drawText("\u2713", cx, cy + textPaint.getTextSize() / 3f, textPaint);
        } else if (indeterminate) {
            canvas.drawArc(oval, indeterminateAngle - 90f, 90f, false, arcPaint);
        } else {
            canvas.drawArc(oval, -90, sweepAngle(progress), false, arcPaint);
            if (progress > 0) {
                String label = progress + "%";
                canvas.drawText(label, cx, cy + textPaint.getTextSize() / 3f, textPaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopSpin();
    }
}