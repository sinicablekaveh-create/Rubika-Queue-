package com.sinicable.remotesender;

public final class PhoneNormalizer {
    private PhoneNormalizer() {}
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = digits(raw).trim();
        boolean plus = s.startsWith("+");
        s = s.replaceAll("[^0-9]", "");
        if (s.startsWith("0098")) s = "98" + s.substring(4);
        if (s.startsWith("09") && s.length() == 11) s = "98" + s.substring(1);
        else if (s.startsWith("9") && s.length() == 10) s = "98" + s;
        if (s.startsWith("98") && s.length() == 12) return "+" + s;
        if (plus && s.length() >= 8 && s.length() <= 15) return "+" + s;
        return null;
    }
    private static String digits(String in) {
        String fa="۰۱۲۳۴۵۶۷۸۹", ar="٠١٢٣٤٥٦٧٨٩";
        StringBuilder b=new StringBuilder();
        for(char c:in.toCharArray()){
            int i=fa.indexOf(c);
            if(i>=0)b.append((char)('0'+i));
            else { i=ar.indexOf(c); b.append(i>=0?(char)('0'+i):c); }
        }
        return b.toString();
    }
}
