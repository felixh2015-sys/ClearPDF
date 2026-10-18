package com.chethan616.clearpdf.export;

/**
 * One positioned piece of text on a page (usually a single word).
 *
 * Coordinates are normalized to the page: 0..1 on both axes, with the origin at the
 * top-left corner, so digital PDF text (PdfBox) and OCR word boxes share one model.
 */
public final class TextChunk {
    public final String text;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    /** Font size in points when known (digital PDFs), or 0 when unknown (OCR). */
    public final float fontSizePt;
    public final boolean bold;

    public TextChunk(String text, float left, float top, float right, float bottom, float fontSizePt, boolean bold) {
        this.text = text == null ? "" : text;
        this.left = Math.min(left, right);
        this.right = Math.max(left, right);
        this.top = Math.min(top, bottom);
        this.bottom = Math.max(top, bottom);
        this.fontSizePt = fontSizePt;
        this.bold = bold;
    }

    public TextChunk(String text, float left, float top, float right, float bottom) {
        this(text, left, top, right, bottom, 0f, false);
    }

    public float height() { return bottom - top; }

    public float centerY() { return (top + bottom) / 2f; }
}
