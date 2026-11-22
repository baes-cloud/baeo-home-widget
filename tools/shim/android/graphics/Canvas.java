package android.graphics;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
public class Canvas {
    private final Graphics2D g;
    private final ArrayDeque<Shape> clips = new ArrayDeque<>();
    public Canvas(Bitmap b) {
        g = b.img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
    private void shape(Shape s, Paint p) {
        g.setColor(new Color(p.color, true));
        if (p.style != Paint.Style.STROKE) g.fill(s);
        if (p.style != Paint.Style.FILL) {
            int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT;
            int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER;
            g.setStroke(new BasicStroke(p.strokeWidth, cap, join));
            g.draw(s);
        }
    }
    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        shape(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), p);
    }
    public void drawRect(RectF r, Paint p) { shape(new Rectangle2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawOval(RectF r, Paint p) { shape(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), p); }
    public void drawCircle(float cx, float cy, float r, Paint p) { shape(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r), p); }
    public void drawArc(RectF r, float start, float sweep, boolean useCenter, Paint p) {
        shape(new Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, useCenter ? Arc2D.PIE : Arc2D.OPEN), p);
    }
    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        Paint.Style keep = p.style; p.style = Paint.Style.STROKE;
        shape(new Line2D.Float(x0, y0, x1, y1), p);
        p.style = keep;
    }
    public void drawPath(Path path, Paint p) { shape(path.p, p); }
    public void drawText(String s, float x, float y, Paint p) {
        g.setFont(p.awtFont());
        float w = p.measureText(s);
        float dx = p.align == Paint.Align.CENTER ? -w / 2f : p.align == Paint.Align.RIGHT ? -w : 0f;
        if (p.shadowR > 0f && (p.shadowColor >>> 24) > 0) {
            // Cheap blur: a ring of faint copies.
            int a = (p.shadowColor >>> 24) & 255; int n = 12;
            Color sc = new Color((p.shadowColor & 0xFFFFFF) | (Math.max(1, a / 5) << 24), true);
            g.setColor(sc);
            for (int k = 0; k < n; k++) {
                double t = 2 * Math.PI * k / n; float rr = p.shadowR * 0.6f;
                g.drawString(s, (float) (x + dx + p.shadowDx + rr * Math.cos(t)), (float) (y + p.shadowDy + rr * Math.sin(t)));
            }
            g.drawString(s, x + dx + p.shadowDx, y + p.shadowDy);
        }
        g.setColor(new Color(p.color, true));
        g.drawString(s, x + dx, y);
    }
    public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p) {
        java.awt.Composite old = g.getComposite();
        if (p != null && ((p.color >>> 24) & 255) < 255)
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ((p.color >>> 24) & 255) / 255f));
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(b.img, Math.round(dst.left), Math.round(dst.top), Math.round(dst.width()), Math.round(dst.height()), null);
        g.setComposite(old);
    }
    public int save() { clips.push(g.getClip() == null ? new Rectangle2D.Float(-1e6f, -1e6f, 2e6f, 2e6f) : g.getClip()); return clips.size(); }
    public void restore() { g.setClip(clips.pop()); }
    public boolean clipPath(Path path) { g.clip(path.p); return true; }
}
