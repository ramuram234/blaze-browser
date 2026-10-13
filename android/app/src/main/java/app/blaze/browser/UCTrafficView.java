package app.blaze.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * UC Browser TrafficRoundProgressBar reimplementation.
 *
 * Rewritten from UC Browser source:
 *   com.uc.browser.business.traffic.TrafficRoundProgressBar
 *
 * Draws two concentric arcs:
 *   - Outer arc: virtual/background (uc_orange tint, dim)
 *   - Inner arc: real progress (uc_orange, full opacity)
 * Plus center text showing speed.
 *
 * UC Browser b(float) = setDownloadPercent, c(float) = setUploadPercent
 */
public class UCTrafficView extends View {

    private final Paint bgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval     = new RectF();

    // Download arc angle (0-360 degrees), UC Browser: (downloadPct * 360) / 100
    private int downloadAngle = 0;
    // Upload arc angle
    private int uploadAngle = 0;
    // Center text (speed)
    private String speedText = "0\nKB/s";

    public UCTrafficView(Context context) {
        super(context); init(context);
    }
    public UCTrafficView(Context context, AttributeSet attrs) {
        super(context, attrs); init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float strokeW = 5f * density;

        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(strokeW);
        bgPaint.setColor(0x44FF9C38); // UC Browser traffic_panel_round_virtual_color

        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeWidth(strokeW);
        fgPaint.setColor(0xFFFF9C38); // UC Browser traffic_panel_round_progress_color
        fgPaint.setStrokeCap(Paint.Cap.ROUND);

        txtPaint.setStyle(Paint.Style.FILL);
        txtPaint.setColor(0xFFFF9C38);
        txtPaint.setTextSize(9f * density);
        txtPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** UC Browser b(float f): setDownloadPercent(pct) */
    public void setDownloadPercent(float pct) {
        this.downloadAngle = (int)((pct * 360f) / 100f);
        postInvalidate();
    }

    /** UC Browser c(float f): setUploadPercent(pct) */
    public void setUploadPercent(float pct) {
        this.uploadAngle = (int)((pct * 360f) / 100f);
        postInvalidate();
    }

    public void setSpeedText(String text) {
        this.speedText = text;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = fgPaint.getStrokeWidth();
        oval.set(pad, pad, getWidth() - pad, getHeight() - pad);

        // UC Browser: draw background arc (virtual color, full 360)
        canvas.drawArc(oval, -90, 360, false, bgPaint);

        // UC Browser: draw download progress arc
        if (downloadAngle > 0) {
            canvas.drawArc(oval, -90, Math.min(downloadAngle, 360), false, fgPaint);
        }

        // Draw speed text in center
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f + txtPaint.getTextSize() / 3f;
        String[] lines = speedText.split("\\n");
        if (lines.length == 2) {
            canvas.drawText(lines[0], cx, cy - txtPaint.getTextSize() * 0.6f, txtPaint);
            txtPaint.setTextSize(txtPaint.getTextSize() * 0.85f);
            canvas.drawText(lines[1], cx, cy + txtPaint.getTextSize() * 0.4f, txtPaint);
            txtPaint.setTextSize(txtPaint.getTextSize() / 0.85f);
        } else {
            canvas.drawText(speedText, cx, cy, txtPaint);
        }
    }
}
