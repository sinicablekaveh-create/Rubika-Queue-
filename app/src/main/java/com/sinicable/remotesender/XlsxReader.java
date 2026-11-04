package com.sinicable.remotesender;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class XlsxReader {
    private XlsxReader() {}

    public static final class Result {
        public final List<String> phones;
        public final int skippedNoConsent;
        public final int skippedInvalid;

        Result(List<String> phones, int skippedNoConsent, int skippedInvalid) {
            this.phones = phones;
            this.skippedNoConsent = skippedNoConsent;
            this.skippedInvalid = skippedInvalid;
        }
    }

    public static Result read(Context context, Uri uri, int limit) throws Exception {
        File tmp = File.createTempFile("rs_", ".xlsx", context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalArgumentException("Cannot open XLSX.");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
        }

        try (ZipFile zip = new ZipFile(tmp)) {
            List<String> shared = readSharedStrings(zip);
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheet == null) throw new IllegalArgumentException("The first worksheet was not found.");

            TreeMap<Integer, TreeMap<Integer, String>> rows =
                    readSheet(zip.getInputStream(sheet), shared);
            if (rows.isEmpty()) throw new IllegalArgumentException("The worksheet is empty.");

            Map.Entry<Integer, TreeMap<Integer, String>> headerRow = rows.firstEntry();
            int phoneCol = -1;
            int optCol = -1;

            for (Map.Entry<Integer, String> cell : headerRow.getValue().entrySet()) {
                String h = clean(cell.getValue());
                if (isPhoneHeader(h)) phoneCol = cell.getKey();
                if (isOptInHeader(h)) optCol = cell.getKey();
            }

            if (phoneCol < 0) {
                throw new IllegalArgumentException("Missing phone column. Use header: phone.");
            }
            if (optCol < 0) {
                throw new IllegalArgumentException("Missing opt_in column. Use yes/true/1 for approved recipients.");
            }

            LinkedHashSet<String> unique = new LinkedHashSet<>();
            int skippedNoConsent = 0;
            int skippedInvalid = 0;

            for (Map.Entry<Integer, TreeMap<Integer, String>> row : rows.entrySet()) {
                if (row.getKey().equals(headerRow.getKey())) continue;
                String consent = row.getValue().get(optCol);
                if (!truthy(consent)) {
                    skippedNoConsent++;
                    continue;
                }

                String normalized = PhoneNormalizer.normalize(row.getValue().get(phoneCol));
                if (normalized == null) {
                    skippedInvalid++;
                    continue;
                }

                unique.add(normalized);
                if (unique.size() >= limit) break;
            }

            return new Result(new ArrayList<>(unique), skippedNoConsent, skippedInvalid);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        ArrayList<String> out = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return out;

        try (InputStream in = zip.getInputStream(entry)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            StringBuilder current = null;

            for (int event = parser.getEventType();
                 event != XmlPullParser.END_DOCUMENT;
                 event = parser.next()) {
                if (event == XmlPullParser.START_TAG && "si".equals(parser.getName())) {
                    current = new StringBuilder();
                } else if (event == XmlPullParser.START_TAG && "t".equals(parser.getName())
                        && current != null) {
                    current.append(parser.nextText());
                } else if (event == XmlPullParser.END_TAG && "si".equals(parser.getName())
                        && current != null) {
                    out.add(current.toString());
                    current = null;
                }
            }
        }
        return out;
    }

    private static TreeMap<Integer, TreeMap<Integer, String>> readSheet(
            InputStream in, List<String> shared) throws Exception {
        TreeMap<Integer, TreeMap<Integer, String>> rows = new TreeMap<>();

        try (InputStream input = in) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");

            int rowNumber = 0;
            int column = 0;
            String type = null;
            String value = null;

            for (int event = parser.getEventType();
                 event != XmlPullParser.END_DOCUMENT;
                 event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("row".equals(name)) {
                        String r = parser.getAttributeValue(null, "r");
                        rowNumber = r == null ? rowNumber + 1 : Integer.parseInt(r);
                        rows.putIfAbsent(rowNumber, new TreeMap<>());
                    } else if ("c".equals(name)) {
                        type = parser.getAttributeValue(null, "t");
                        column = columnIndex(parser.getAttributeValue(null, "r"));
                        value = null;
                    } else if ("v".equals(name) || "t".equals(name)) {
                        value = parser.nextText();
                    }
                } else if (event == XmlPullParser.END_TAG && "c".equals(parser.getName())) {
                    String resolved = value == null ? "" : value;
                    if ("s".equals(type)) {
                        try {
                            int index = Integer.parseInt(resolved);
                            resolved = index >= 0 && index < shared.size()
                                    ? shared.get(index) : "";
                        } catch (NumberFormatException ignored) {}
                    }
                    rows.putIfAbsent(rowNumber, new TreeMap<>());
                    rows.get(rowNumber).put(column, resolved);
                }
            }
        }

        return rows;
    }

    private static int columnIndex(String cellRef) {
        if (cellRef == null) return 0;
        int value = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (c < 'A' || c > 'Z') break;
            value = value * 26 + (c - 'A' + 1);
        }
        return Math.max(0, value - 1);
    }

    private static String clean(String input) {
        if (input == null) return "";
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
    }

    private static boolean isPhoneHeader(String header) {
        return header.equals("phone")
                || header.equals("mobile")
                || header.equals("number")
                || header.equals("شماره")
                || header.contains("شمارهتلفن")
                || header.contains("موبایل");
    }

    private static boolean isOptInHeader(String header) {
        return header.equals("optin")
                || header.equals("consent")
                || header.contains("رضایت")
                || header.contains("اجازه");
    }

    private static boolean truthy(String value) {
        String v = clean(value);
        return v.equals("yes")
                || v.equals("y")
                || v.equals("true")
                || v.equals("1")
                || v.equals("بله")
                || v.equals("بلی")
                || v.equals("آری");
    }
}
