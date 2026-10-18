package com.chethan616.clearpdf.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small helpers shared by the Word and Excel writers. */
final class OoxmlZip {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private OoxmlZip() {}

    static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(UTF8));
        zip.closeEntry();
    }

    /** Wraps [out] without closing it when the zip is finished. */
    static ZipOutputStream open(OutputStream out) {
        return new ZipOutputStream(new OutputStream() {
            @Override public void write(int b) throws IOException { out.write(b); }
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
            @Override public void flush() throws IOException { out.flush(); }
            @Override public void close() throws IOException { out.flush(); }
        });
    }

    /** XML-escapes text and drops characters XML 1.0 can't carry. */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF)) {
                        if (Character.isHighSurrogate(c)) {
                            if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                                sb.append(c).append(s.charAt(++i));
                            }
                        } else if (!Character.isLowSurrogate(c)) {
                            sb.append(c);
                        }
                    }
            }
        }
        return sb.toString();
    }
}
