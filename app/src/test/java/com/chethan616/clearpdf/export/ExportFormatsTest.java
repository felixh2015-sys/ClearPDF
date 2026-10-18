package com.chethan616.clearpdf.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ExportFormatsTest {

    @Test
    public void parsesNumbersInBothConventions() {
        assertEquals(1234.56, XlsxExporter.parseNumber("1.234,56"), 1e-9);
        assertEquals(1234.56, XlsxExporter.parseNumber("1,234.56"), 1e-9);
        assertEquals(0.12, XlsxExporter.parseNumber("12%"), 1e-9);
        assertEquals(-300, XlsxExporter.parseNumber("(300)"), 1e-9);
        assertNull(XlsxExporter.parseNumber("00123"));
        assertNull(XlsxExporter.parseNumber("12/10/2026"));
        assertNull(XlsxExporter.parseNumber("04141234567"));
    }

    @Test
    public void detectsTableRows() {
        List<TextChunk> c = new ArrayList<>();
        float[] xs = {0.1f, 0.5f};
        String[][] rows = {{"Producto", "Total"}, {"Cable", "12,50"}, {"Cargador", "30"}};
        for (int r = 0; r < rows.length; r++) {
            float top = 0.2f + r * 0.03f;
            for (int i = 0; i < 2; i++) {
                c.add(new TextChunk(rows[r][i], xs[i], top, xs[i] + 0.1f, top + 0.015f, 11f, false));
            }
        }
        List<LayoutAnalyzer.Block> blocks = LayoutAnalyzer.blocks(new ExportPage(595, 842, c, false));
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0) instanceof LayoutAnalyzer.Table);
        assertEquals(2, ((LayoutAnalyzer.Table) blocks.get(0)).columnCount);
    }

    @Test
    public void writesZipPackages() throws Exception {
        List<ExportPage> pages = Collections.singletonList(new ExportPage(595, 842,
            Collections.singletonList(new TextChunk("Hola ñandú & <amigos>", 0.1f, 0.1f, 0.5f, 0.12f)), true));
        ByteArrayOutputStream docx = new ByteArrayOutputStream();
        DocxExporter.write(pages, "Prueba", docx);
        ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
        XlsxExporter.write(pages, true, "Página", xlsx);
        assertTrue(docx.size() > 500 && docx.toByteArray()[0] == 'P' && docx.toByteArray()[1] == 'K');
        assertTrue(xlsx.size() > 500 && xlsx.toByteArray()[0] == 'P' && xlsx.toByteArray()[1] == 'K');
    }
}
