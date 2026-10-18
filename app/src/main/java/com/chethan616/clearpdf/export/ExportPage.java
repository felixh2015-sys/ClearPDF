package com.chethan616.clearpdf.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The text of one PDF page, ready to be laid out into Word or Excel. */
public final class ExportPage {
    /** Page size in PDF points (1/72 inch). */
    public final float widthPt;
    public final float heightPt;
    public final List<TextChunk> chunks;
    /** True when the text came from OCR rather than the PDF's own text layer. */
    public final boolean fromOcr;

    public ExportPage(float widthPt, float heightPt, List<TextChunk> chunks, boolean fromOcr) {
        this.widthPt = widthPt > 0 ? widthPt : 595f;
        this.heightPt = heightPt > 0 ? heightPt : 842f;
        List<TextChunk> clean = new ArrayList<>();
        if (chunks != null) {
            for (TextChunk c : chunks) {
                if (c != null && c.text.trim().length() > 0) clean.add(c);
            }
        }
        this.chunks = Collections.unmodifiableList(clean);
        this.fromOcr = fromOcr;
    }

    public boolean isEmpty() { return chunks.isEmpty(); }
}
