package android.graphics;
import java.awt.Font;
import java.awt.font.FontRenderContext;
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1, FILTER_BITMAP_FLAG = 2, SUBPIXEL_TEXT_FLAG = 128;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }
    public int color = 0xFF000000;
    public Style style = Style.FILL;
    public Cap cap = Cap.BUTT;
    public Join join = Join.MITER;
    public Align align = Align.LEFT;
    public float strokeWidth = 1f, textSize = 12f;
    public Typeface tf;
    public PathEffect effect;
    public float letterSpacing = 0f, shadowR = 0f, shadowDx = 0f, shadowDy = 0f;
    public int shadowColor = 0;
    public void setLetterSpacing(float em) { letterSpacing = em; }
    public void setShadowLayer(float r, float dx, float dy, int c) { shadowR = r; shadowDx = dx; shadowDy = dy; shadowColor = c; }
    public Paint(int flags) {}
    public Paint() {}
    public void setColor(int c) { color = c; }
    public void setAlpha(int a) { color = (color & 0x00FFFFFF) | ((a & 255) << 24); }
    public void setStyle(Style s) { style = s; }
    public void setStrokeWidth(float w) { strokeWidth = w; }
    public void setStrokeCap(Cap c) { cap = c; }
    public void setStrokeJoin(Join j) { join = j; }
    public void setTextAlign(Align a) { align = a; }
    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }
    public void setTypeface(Typeface t) { tf = t; }
    public void setPathEffect(PathEffect e) { effect = e; }
    public Font awtFont() {
        Font f = tf.font.deriveFont(textSize);
        if (letterSpacing != 0f) f = f.deriveFont(java.util.Collections.singletonMap(java.awt.font.TextAttribute.TRACKING, letterSpacing));
        return f;
    }
    public float measureText(String s) {
        return (float) awtFont().getStringBounds(s, new FontRenderContext(null, true, true)).getWidth();
    }
}
