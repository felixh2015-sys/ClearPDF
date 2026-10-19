package com.chethan616.clearpdf.export;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipOutputStream;

import static com.chethan616.clearpdf.export.OoxmlZip.esc;

/**
 * Writes an editable Word (.docx) document from PDF pages: flowing paragraphs, headings,
 * real Word tables and one page break per PDF page. Opens in Word, LibreOffice, Google Docs
 * and WPS.
 */
public final class DocxExporter {

    private DocxExporter() {}

    public static void write(List<ExportPage> pages, String title, OutputStream out) throws IOException {
        ZipOutputStream zip = OoxmlZip.open(out);
        OoxmlZip.put(zip, "[Content_Types].xml", CONTENT_TYPES);
        OoxmlZip.put(zip, "_rels/.rels", ROOT_RELS);
        OoxmlZip.put(zip, "docProps/core.xml", core(title));
        OoxmlZip.put(zip, "word/_rels/document.xml.rels", DOC_RELS);
        OoxmlZip.put(zip, "word/styles.xml", STYLES);
        OoxmlZip.put(zip, "word/document.xml", document(pages));
        zip.finish();
        zip.close();
    }

    static String document(List<ExportPage> pages) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");

        float pageW = 595f, pageH = 842f;
        boolean wrote = false;
        for (int p = 0; p < pages.size(); p++) {
            ExportPage page = pages.get(p);
            if (p == 0) {
                pageW = page.widthPt;
                pageH = page.heightPt;
            }
            if (p > 0) sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
            List<LayoutAnalyzer.Block> blocks = LayoutAnalyzer.blocks(page);
            for (LayoutAnalyzer.Block b : blocks) {
                if (b instanceof LayoutAnalyzer.Paragraph) {
                    paragraph(sb, (LayoutAnalyzer.Paragraph) b);
                } else if (b instanceof LayoutAnalyzer.Table) {
                    table(sb, (LayoutAnalyzer.Table) b, page.widthPt);
                    sb.append("<w:p/>"); // Word needs a paragraph between adjacent tables
                }
                wrote = true;
            }
        }
        if (!wrote) sb.append("<w:p/>");

        boolean landscape = pageW > pageH;
        sb.append("<w:sectPr><w:pgSz w:w=\"").append(twips(pageW)).append("\" w:h=\"").append(twips(pageH)).append('"');
        if (landscape) sb.append(" w:orient=\"landscape\"");
        sb.append("/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr>");
        sb.append("</w:body></w:document>");
        return sb.toString();
    }

    private static void paragraph(StringBuilder sb, LayoutAnalyzer.Paragraph p) {
        sb.append("<w:p>");
        if (p.headingLevel == 1) sb.append("<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>");
        else if (p.headingLevel == 2) sb.append("<w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>");
        runs(sb, p.text, p.bold, p.headingLevel == 0 ? p.fontSizePt : 0f);
        sb.append("</w:p>");
    }

    /** Text as runs; tabs become real Word tabs. A font size of 0 keeps the style's size. */
    private static void runs(StringBuilder sb, String text, boolean bold, float fontSizePt) {
        String rPr = "";
        if (bold || fontSizePt > 0) {
            StringBuilder r = new StringBuilder("<w:rPr>");
            if (bold) r.append("<w:b/>");
            if (fontSizePt > 0) {
                int half = Math.round(fontSizePt * 2f);
                r.append("<w:sz w:val=\"").append(half).append("\"/><w:szCs w:val=\"").append(half).append("\"/>");
            }
            rPr = r.append("</w:rPr>").toString();
        }
        String[] parts = text.split("\t", -1);
        for (int i = 0; i < parts.length; i++) {
            sb.append("<w:r>").append(rPr);
            if (i > 0) sb.append("<w:tab/>");
            if (parts[i].length() > 0) sb.append("<w:t xml:space=\"preserve\">").append(esc(parts[i])).append("</w:t>");
            sb.append("</w:r>");
        }
    }

    private static void table(StringBuilder sb, LayoutAnalyzer.Table t, float pageWidthPt) {
        int cols = Math.max(t.columnCount, 1);
        // Spread the columns over the text width (page minus 2 cm margins on each side).
        int total = Math.max(twips(pageWidthPt) - 2 * 1134, 2000);
        int colW = total / cols;
        sb.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"").append(colW * cols)
          .append("\" w:type=\"dxa\"/><w:tblLook w:val=\"04A0\" w:firstRow=\"1\" w:lastRow=\"0\" w:firstColumn=\"0\" w:lastColumn=\"0\" w:noHBand=\"0\" w:noVBand=\"1\"/></w:tblPr><w:tblGrid>");
        for (int c = 0; c < cols; c++) sb.append("<w:gridCol w:w=\"").append(colW).append("\"/>");
        sb.append("</w:tblGrid>");
        for (String[] row : t.rows) {
            sb.append("<w:tr>");
            for (int c = 0; c < cols; c++) {
                String v = c < row.length && row[c] != null ? row[c] : "";
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(colW).append("\" w:type=\"dxa\"/></w:tcPr><w:p>");
                if (v.length() > 0) sb.append("<w:r><w:t xml:space=\"preserve\">").append(esc(v)).append("</w:t></w:r>");
                sb.append("</w:p></w:tc>");
            }
            sb.append("</w:tr>");
        }
        sb.append("</w:tbl>");
    }

    private static int twips(float pt) {
        return Math.round(pt * 20f);
    }

    private static String core(String title) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String now = f.format(new Date());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<dc:title>" + esc(title) + "</dc:title><dc:creator>PaperPDF</dc:creator>"
            + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>"
            + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
            + "</cp:coreProperties>";
    }

    private static final String CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
        + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
        + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
        + "</Types>";

    private static final String ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
        + "</Relationships>";

    private static final String DOC_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        + "</Relationships>";

    private static final String STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
        + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/>"
        + "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"es-ES\"/></w:rPr></w:rPrDefault>"
        + "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
        + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>"
        + "<w:pPr><w:keepNext/><w:spacing w:before=\"240\" w:after=\"120\"/><w:outlineLvl w:val=\"0\"/></w:pPr>"
        + "<w:rPr><w:b/><w:color w:val=\"0F4C5C\"/><w:sz w:val=\"36\"/><w:szCs w:val=\"36\"/></w:rPr></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>"
        + "<w:pPr><w:keepNext/><w:spacing w:before=\"200\" w:after=\"80\"/><w:outlineLvl w:val=\"1\"/></w:pPr>"
        + "<w:rPr><w:b/><w:color w:val=\"0F4C5C\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/></w:rPr></w:style>"
        + "<w:style w:type=\"table\" w:default=\"1\" w:styleId=\"TableNormal\"><w:name w:val=\"Normal Table\"/><w:tblPr><w:tblInd w:w=\"0\" w:type=\"dxa\"/>"
        + "<w:tblCellMar><w:top w:w=\"0\" w:type=\"dxa\"/><w:left w:w=\"108\" w:type=\"dxa\"/><w:bottom w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"108\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr></w:style>"
        + "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/><w:basedOn w:val=\"TableNormal\"/>"
        + "<w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:tblPr><w:tblBorders>"
        + "<w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/><w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/>"
        + "<w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/><w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/>"
        + "<w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/><w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A0A7AD\"/>"
        + "</w:tblBorders></w:tblPr></w:style>"
        + "</w:styles>";
}
