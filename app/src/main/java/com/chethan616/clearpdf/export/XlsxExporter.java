package com.chethan616.clearpdf.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import static com.chethan616.clearpdf.export.OoxmlZip.esc;

/**
 * Writes an editable Excel (.xlsx) workbook from PDF pages. Text is laid out in rows and
 * columns by position, and numbers ("1.234,56", "1,234.56", "-45", "12%") become real numeric
 * cells so they can be summed straight away.
 */
public final class XlsxExporter {

    private XlsxExporter() {}

    /**
     * @param onePerPage one sheet per PDF page; otherwise every page is stacked on one sheet.
     * @param sheetPrefix sheet-name prefix, e.g. "Página".
     */
    public static void write(List<ExportPage> pages, boolean onePerPage, String sheetPrefix, OutputStream out) throws IOException {
        List<String> names = new ArrayList<>();
        List<String> sheets = new ArrayList<>();
        if (onePerPage && pages.size() > 1) {
            for (int p = 0; p < pages.size(); p++) {
                List<LayoutAnalyzer.Grid> one = new ArrayList<>();
                one.add(LayoutAnalyzer.grid(pages.get(p)));
                sheets.add(sheet(one));
                names.add(sheetName(sheetPrefix + " " + (p + 1), names));
            }
        } else {
            List<LayoutAnalyzer.Grid> all = new ArrayList<>();
            for (ExportPage page : pages) all.add(LayoutAnalyzer.grid(page));
            sheets.add(sheet(all));
            names.add(sheetName(pages.size() == 1 ? sheetPrefix + " 1" : "Datos", names));
        }

        ZipOutputStream zip = OoxmlZip.open(out);
        OoxmlZip.put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
        OoxmlZip.put(zip, "_rels/.rels", ROOT_RELS);
        OoxmlZip.put(zip, "xl/workbook.xml", workbook(names));
        OoxmlZip.put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
        OoxmlZip.put(zip, "xl/styles.xml", STYLES);
        for (int i = 0; i < sheets.size(); i++) {
            OoxmlZip.put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i));
        }
        zip.finish();
        zip.close();
    }

    static String sheet(List<LayoutAnalyzer.Grid> grids) {
        int maxCols = 1;
        for (LayoutAnalyzer.Grid g : grids) maxCols = Math.max(maxCols, g.columnCount);
        int[] widths = new int[maxCols];

        StringBuilder rows = new StringBuilder(32 * 1024);
        int r = 0;
        for (int gi = 0; gi < grids.size(); gi++) {
            LayoutAnalyzer.Grid g = grids.get(gi);
            if (gi > 0) r++; // one empty row between pages
            for (String[] row : g.rows) {
                r++;
                StringBuilder cells = new StringBuilder();
                for (int c = 0; c < row.length; c++) {
                    String v = row[c] == null ? "" : row[c].trim();
                    if (v.isEmpty()) continue;
                    widths[c] = Math.max(widths[c], Math.min(v.length(), 60));
                    String ref = colName(c) + r;
                    Double num = parseNumber(v);
                    if (num != null) {
                        boolean pct = v.endsWith("%");
                        cells.append("<c r=\"").append(ref).append("\"").append(pct ? " s=\"1\"" : "").append("><v>")
                             .append(formatDouble(num)).append("</v></c>");
                    } else {
                        cells.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                             .append(esc(v)).append("</t></is></c>");
                    }
                }
                if (cells.length() > 0) rows.append("<row r=\"").append(r).append("\">").append(cells).append("</row>");
            }
        }

        StringBuilder sb = new StringBuilder(rows.length() + 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
          .append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>")
          .append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int c = 0; c < maxCols; c++) {
            double w = Math.max(8, widths[c] + 2);
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
              .append(formatDouble(w)).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>").append(rows).append("</sheetData></worksheet>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ numbers

    private static final Pattern NUMERIC = Pattern.compile("^[-+(]?[$€£¥]?\\s?[-+]?\\d[\\d.,\\s]*\\)?%?$");

    /**
     * Parses numbers written with either decimal convention. Returns null for anything that
     * is not clearly a number (dates, phone numbers, IDs with leading zeros stay as text).
     */
    static Double parseNumber(String raw) {
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 24 || !NUMERIC.matcher(s).matches()) return null;
        boolean negative = s.startsWith("-") || (s.startsWith("(") && s.endsWith(")"));
        boolean percent = s.endsWith("%");
        String digits = s.replaceAll("[^0-9.,]", "");
        if (digits.isEmpty()) return null;
        // Leading zeros mean a code ("00123"), not a quantity.
        if (digits.length() > 1 && digits.charAt(0) == '0' && Character.isDigit(digits.charAt(1))) return null;

        int lastDot = digits.lastIndexOf('.');
        int lastComma = digits.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            // Both present: the one that comes last is the decimal separator.
            char dec = lastDot > lastComma ? '.' : ',';
            char thou = dec == '.' ? ',' : '.';
            if (!validGroups(digits.substring(0, Math.max(lastDot, lastComma)), thou)) return null;
            normalized = digits.replace(String.valueOf(thou), "").replace(dec, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sep = lastDot >= 0 ? '.' : ',';
            int count = digits.length() - digits.replace(String.valueOf(sep), "").length();
            int after = digits.length() - digits.lastIndexOf(sep) - 1;
            if (count > 1) {
                // "1.234.567" — thousands only.
                if (!validGroups(digits, sep) || after != 3) return null;
                normalized = digits.replace(String.valueOf(sep), "");
            } else if (after == 3 && sep == '.' && digits.indexOf(sep) <= 3) {
                // "1.234" is ambiguous; in Spanish-language documents it means one thousand two hundred…
                normalized = digits.replace(".", "");
            } else if (after == 3 && sep == ',' && digits.indexOf(sep) <= 3) {
                normalized = digits.replace(",", "");
            } else {
                normalized = digits.replace(sep, '.');
            }
        } else {
            normalized = digits;
        }
        if (normalized.isEmpty() || normalized.equals(".")) return null;
        try {
            double v = Double.parseDouble(normalized);
            if (negative) v = -v;
            if (percent) v = v / 100.0;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True when [s] is digit groups of three after the first, split by [sep] ("1.234.567"). */
    private static boolean validGroups(String s, char sep) {
        String[] parts = s.split(Pattern.quote(String.valueOf(sep)), -1);
        if (parts[0].isEmpty() || parts[0].length() > 3) return parts.length == 1 && !parts[0].isEmpty();
        for (int i = 1; i < parts.length; i++) if (parts[i].length() != 3) return false;
        return true;
    }

    static String formatDouble(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String s = String.valueOf(v);
        return s.contains("E") ? String.format(java.util.Locale.US, "%.10f", v).replaceAll("0+$", "") : s;
    }

    // ------------------------------------------------------------------ package parts

    static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String sheetName(String wanted, List<String> taken) {
        String base = wanted.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (base.length() > 31) base = base.substring(0, 31);
        if (base.isEmpty()) base = "Hoja";
        String name = base;
        int i = 2;
        while (containsIgnoreCase(taken, name)) {
            String suffix = " (" + i++ + ")";
            name = (base.length() + suffix.length() > 31 ? base.substring(0, 31 - suffix.length()) : base) + suffix;
        }
        return name;
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
              .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String workbook(List<String> names) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<bookViews><workbookView/></bookViews><sheets>");
        for (int i = 0; i < names.size(); i++) {
            sb.append("<sheet name=\"").append(esc(names.get(i))).append("\" sheetId=\"").append(i + 1)
              .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
              .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(sheetCount + 1)
          .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private static final String ROOT_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";

    /** Style 0 = default, style 1 = percentage. */
    private static final String STYLES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
        + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>"
        + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"10\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>";
}
