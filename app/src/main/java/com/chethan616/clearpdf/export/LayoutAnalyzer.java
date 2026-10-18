package com.chethan616.clearpdf.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the loose words of a page into structure: lines, cells (runs of words separated by a
 * wide gap), paragraphs, headings and tables. Pure Java with no Android dependencies, so it
 * can be unit-tested on any JVM.
 *
 * Everything is measured in PDF points, because normalized x and y use different units
 * (page width vs page height) and gaps must be compared against text height.
 */
public final class LayoutAnalyzer {

    private LayoutAnalyzer() {}

    // ------------------------------------------------------------------ model

    /** A run of words on one line, separated from its neighbours by a wide gap. */
    public static final class Cell {
        public final String text;
        public final float left, right, top, bottom; // points
        public final float fontSizePt;
        public final boolean bold;

        Cell(String text, float left, float right, float top, float bottom, float fontSizePt, boolean bold) {
            this.text = text;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.fontSizePt = fontSizePt;
            this.bold = bold;
        }
    }

    /** One visual line of text, in points, left to right. */
    public static final class Line {
        public final List<Cell> cells;
        public final float left, right, top, bottom;
        /** Typical text height on this line, in points. */
        public final float heightPt;
        public final float fontSizePt;
        public final boolean bold;

        Line(List<Cell> cells, float heightPt, float fontSizePt, boolean bold) {
            this.cells = cells;
            float l = Float.MAX_VALUE, r = -Float.MAX_VALUE, t = Float.MAX_VALUE, b = -Float.MAX_VALUE;
            for (Cell c : cells) {
                l = Math.min(l, c.left);
                r = Math.max(r, c.right);
                t = Math.min(t, c.top);
                b = Math.max(b, c.bottom);
            }
            this.left = l;
            this.right = r;
            this.top = t;
            this.bottom = b;
            this.heightPt = heightPt;
            this.fontSizePt = fontSizePt;
            this.bold = bold;
        }

        public String text(String cellSeparator) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) sb.append(cellSeparator);
                sb.append(cells.get(i).text);
            }
            return sb.toString();
        }
    }

    public interface Block {}

    public static final class Paragraph implements Block {
        public final String text;
        /** 0 = body text, 1 = title, 2 = subtitle/section. */
        public final int headingLevel;
        public final boolean bold;
        public final float fontSizePt;

        Paragraph(String text, int headingLevel, boolean bold, float fontSizePt) {
            this.text = text;
            this.headingLevel = headingLevel;
            this.bold = bold;
            this.fontSizePt = fontSizePt;
        }
    }

    public static final class Table implements Block {
        /** rows x columns; empty string for an empty cell. */
        public final List<String[]> rows;
        public final int columnCount;

        Table(List<String[]> rows, int columnCount) {
            this.rows = rows;
            this.columnCount = columnCount;
        }
    }

    /** A page broken into rows and columns, for spreadsheets. */
    public static final class Grid {
        public final List<String[]> rows;
        public final int columnCount;

        Grid(List<String[]> rows, int columnCount) {
            this.rows = rows;
            this.columnCount = columnCount;
        }
    }

    // ------------------------------------------------------------------ lines

    public static List<Line> lines(ExportPage page) {
        final float w = page.widthPt;
        final float h = page.heightPt;
        List<TextChunk> sorted = new ArrayList<>(page.chunks);
        Collections.sort(sorted, new Comparator<TextChunk>() {
            @Override
            public int compare(TextChunk a, TextChunk b) {
                return Float.compare(a.centerY(), b.centerY());
            }
        });

        // Group words into lines by vertical position.
        List<List<TextChunk>> groups = new ArrayList<>();
        List<Float> centers = new ArrayList<>();
        List<Float> heights = new ArrayList<>();
        for (TextChunk c : sorted) {
            float cy = c.centerY();
            int last = groups.size() - 1;
            boolean join = false;
            if (last >= 0) {
                float tol = 0.5f * Math.max(heights.get(last), c.height());
                join = Math.abs(cy - centers.get(last)) <= tol;
            }
            if (join) {
                List<TextChunk> g = groups.get(last);
                g.add(c);
                float n = g.size();
                centers.set(last, centers.get(last) + (cy - centers.get(last)) / n);
                heights.set(last, Math.max(heights.get(last), c.height()));
            } else {
                List<TextChunk> g = new ArrayList<>();
                g.add(c);
                groups.add(g);
                centers.add(cy);
                heights.add(c.height());
            }
        }

        List<Line> out = new ArrayList<>();
        for (List<TextChunk> g : groups) {
            Collections.sort(g, new Comparator<TextChunk>() {
                @Override
                public int compare(TextChunk a, TextChunk b) {
                    return Float.compare(a.left, b.left);
                }
            });
            float[] hs = new float[g.size()];
            float[] fs = new float[g.size()];
            int boldCount = 0;
            int fsCount = 0;
            for (int i = 0; i < g.size(); i++) {
                TextChunk c = g.get(i);
                hs[i] = c.height() * h;
                if (c.fontSizePt > 0) fs[fsCount++] = c.fontSizePt;
                if (c.bold) boldCount++;
            }
            float lineH = Math.max(median(hs, hs.length), 1f);
            float fontPt = fsCount > 0 ? median(fs, fsCount) : 0f;
            boolean lineBold = boldCount * 2 > g.size();
            // Words further apart than about one text height belong to different cells.
            float gapLimit = Math.max(lineH * 1.0f, 4f);

            List<Cell> cells = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            float cl = 0, cr = 0, ct = 0, cb = 0, cfSum = 0;
            int cfN = 0, cBold = 0, cWords = 0;
            for (int i = 0; i < g.size(); i++) {
                TextChunk c = g.get(i);
                float l = c.left * w, r = c.right * w, t = c.top * h, b = c.bottom * h;
                if (sb.length() > 0 && l - cr > gapLimit) {
                    cells.add(new Cell(sb.toString().trim(), cl, cr, ct, cb, cfN > 0 ? cfSum / cfN : 0f, cBold * 2 > cWords));
                    sb.setLength(0);
                }
                if (sb.length() == 0) {
                    cl = l; cr = r; ct = t; cb = b; cfSum = 0; cfN = 0; cBold = 0; cWords = 0;
                } else {
                    sb.append(' ');
                }
                sb.append(c.text.trim());
                cr = Math.max(cr, r);
                ct = Math.min(ct, t);
                cb = Math.max(cb, b);
                if (c.fontSizePt > 0) { cfSum += c.fontSizePt; cfN++; }
                if (c.bold) cBold++;
                cWords++;
            }
            if (sb.length() > 0) {
                cells.add(new Cell(sb.toString().trim(), cl, cr, ct, cb, cfN > 0 ? cfSum / cfN : 0f, cBold * 2 > cWords));
            }
            if (!cells.isEmpty()) out.add(new Line(cells, lineH, fontPt, lineBold));
        }
        return out;
    }

    // ------------------------------------------------------------------ blocks (Word)

    public static List<Block> blocks(ExportPage page) {
        List<Line> lines = lines(page);
        List<Block> out = new ArrayList<>();
        if (lines.isEmpty()) return out;

        float bodyH = bodyHeight(lines);
        float bodyFont = bodyFontSize(lines, bodyH);
        float textLeft = Float.MAX_VALUE, textRight = -Float.MAX_VALUE;
        for (Line l : lines) {
            textLeft = Math.min(textLeft, l.left);
            textRight = Math.max(textRight, l.right);
        }
        float textWidth = Math.max(textRight - textLeft, 1f);

        int i = 0;
        while (i < lines.size()) {
            // A run of 2+ consecutive multi-cell lines reads as a table.
            int j = i;
            while (j < lines.size() && lines.get(j).cells.size() >= 2
                    && (j == i || lines.get(j).top - lines.get(j - 1).bottom < 2.5f * bodyH)) {
                j++;
            }
            if (j - i >= 2) {
                out.add(toTable(lines.subList(i, j)));
                i = j;
                continue;
            }

            Line first = lines.get(i);
            int level = headingLevel(first, bodyH);
            StringBuilder text = new StringBuilder(first.text("\t"));
            Line prev = first;
            int k = i + 1;
            while (k < lines.size()) {
                Line cur = lines.get(k);
                if (cur.cells.size() >= 2 && level == 0) break;
                if (headingLevel(cur, bodyH) != level) break;
                float gap = cur.top - prev.bottom;
                if (gap > 0.8f * bodyH) break;
                if (gap < -0.5f * bodyH) break; // went back up the page (columns)
                boolean prevShort = prev.right < textRight - 0.18f * textWidth;
                if (prevShort && level == 0) break;
                if (cur.left > prev.left + 1.2f * bodyH && level == 0) break; // indented first line
                if (cur.bold != prev.bold && level == 0) break;
                appendLine(text, cur.text("\t"));
                prev = cur;
                k++;
            }
            float font = paragraphFont(first, bodyFont, bodyH, level);
            out.add(new Paragraph(text.toString(), level, first.bold, font));
            i = k;
        }
        return out;
    }

    private static void appendLine(StringBuilder sb, String next) {
        int n = sb.length();
        if (n > 1 && sb.charAt(n - 1) == '-' && Character.isLetter(sb.charAt(n - 2))
                && next.length() > 0 && Character.isLowerCase(next.charAt(0))) {
            sb.setLength(n - 1); // re-join a word hyphenated across lines
            sb.append(next);
        } else {
            sb.append(' ').append(next);
        }
    }

    private static int headingLevel(Line line, float bodyH) {
        if (line.cells.size() > 1) return 0;
        float ratio = line.heightPt / bodyH;
        if (ratio >= 1.75f) return 1;
        if (ratio >= 1.3f) return 2;
        return 0;
    }

    private static float paragraphFont(Line first, float bodyFont, float bodyH, int level) {
        float f = first.fontSizePt > 0 ? first.fontSizePt : bodyFont * (first.heightPt / bodyH);
        if (level == 0 && Math.abs(f - bodyFont) < 1.6f) f = bodyFont;
        return clamp(Math.round(f * 2f) / 2f, 7f, 40f);
    }

    private static float bodyHeight(List<Line> lines) {
        float[] hs = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) hs[i] = lines.get(i).heightPt;
        return Math.max(median(hs, hs.length), 1f);
    }

    private static float bodyFontSize(List<Line> lines, float bodyH) {
        float[] fs = new float[lines.size()];
        int n = 0;
        for (Line l : lines) if (l.fontSizePt > 0) fs[n++] = l.fontSizePt;
        if (n > 0) return clamp(Math.round(median(fs, n) * 2f) / 2f, 7f, 28f);
        // OCR: no font information. A word box is roughly 1.2-1.4x the font size.
        return clamp(Math.round(bodyH / 1.3f * 2f) / 2f, 8f, 14f);
    }

    // ------------------------------------------------------------------ tables & grids

    private static Table toTable(List<Line> rows) {
        float[][] cols = columns(rows);
        List<String[]> data = new ArrayList<>();
        for (Line l : rows) data.add(place(l, cols));
        return new Table(data, cols.length);
    }

    /** Lays a whole page out as rows and columns, for Excel. */
    public static Grid grid(ExportPage page) {
        List<Line> lines = lines(page);
        List<Line> multi = new ArrayList<>();
        for (Line l : lines) if (l.cells.size() >= 2) multi.add(l);
        float[][] cols = multi.isEmpty() ? new float[][]{{0f, page.widthPt}} : columns(multi);
        List<String[]> rows = new ArrayList<>();
        float bodyH = lines.isEmpty() ? 10f : bodyHeight(lines);
        Line prev = null;
        for (Line l : lines) {
            // Keep visible blank space between blocks as one empty row.
            if (prev != null && l.top - prev.bottom > 1.5f * bodyH) rows.add(new String[cols.length]);
            rows.add(place(l, cols));
            prev = l;
        }
        return new Grid(rows, cols.length);
    }

    /**
     * Column intervals found by projecting every cell onto the x axis and merging overlaps,
     * which also handles right-aligned numbers whose left edges don't line up.
     */
    static float[][] columns(List<Line> lines) {
        List<float[]> spans = new ArrayList<>();
        for (Line l : lines) for (Cell c : l.cells) spans.add(new float[]{c.left, c.right});
        Collections.sort(spans, new Comparator<float[]>() {
            @Override
            public int compare(float[] a, float[] b) {
                return Float.compare(a[0], b[0]);
            }
        });
        List<float[]> merged = new ArrayList<>();
        for (float[] s : spans) {
            float[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && s[0] <= last[1] + 1f) {
                last[1] = Math.max(last[1], s[1]);
            } else {
                merged.add(new float[]{s[0], s[1]});
            }
        }
        return merged.toArray(new float[0][]);
    }

    /** Puts each cell of [line] into the column it overlaps most. */
    static String[] place(Line line, float[][] cols) {
        String[] row = new String[cols.length];
        for (Cell c : line.cells) {
            int best = 0;
            float bestScore = -Float.MAX_VALUE;
            for (int i = 0; i < cols.length; i++) {
                float overlap = Math.min(c.right, cols[i][1]) - Math.max(c.left, cols[i][0]);
                float score = overlap >= 0 ? overlap : -Math.min(Math.abs(c.left - cols[i][0]), Math.abs(c.left - cols[i][1]));
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            row[best] = row[best] == null ? c.text : row[best] + " " + c.text;
        }
        for (int i = 0; i < row.length; i++) if (row[i] == null) row[i] = "";
        return row;
    }

    // ------------------------------------------------------------------ utils

    static float median(float[] values, int n) {
        if (n <= 0) return 0f;
        float[] copy = Arrays.copyOf(values, n);
        Arrays.sort(copy);
        return n % 2 == 1 ? copy[n / 2] : (copy[n / 2 - 1] + copy[n / 2]) / 2f;
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
