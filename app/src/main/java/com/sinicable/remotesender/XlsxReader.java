package com.sinicable.remotesender;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class XlsxReader {
    private XlsxReader() {}
    public static List<String> read(Context c, Uri uri, int limit) throws Exception {
        File f=File.createTempFile("rs_",".xlsx",c.getCacheDir());
        try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(f)){
            if(in==null) throw new IOException("Cannot open XLSX");
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) out.write(buf,0,n);
        }
        LinkedHashSet<String> phones=new LinkedHashSet<>();
        try(ZipFile z=new ZipFile(f)){
            List<String> shared=shared(z);
            ZipEntry e=z.getEntry("xl/worksheets/sheet1.xml");
            if(e==null) throw new IOException("First worksheet not found");
            XmlPullParser p=Xml.newPullParser(); p.setInput(z.getInputStream(e),"UTF-8");
            String type=null,value=null;
            for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
                if(ev==XmlPullParser.START_TAG){
                    if("c".equals(p.getName())){type=p.getAttributeValue(null,"t");value=null;}
                    else if("v".equals(p.getName())||"t".equals(p.getName())) value=p.nextText();
                } else if(ev==XmlPullParser.END_TAG&&"c".equals(p.getName())){
                    String v=value==null?"":value;
                    if("s".equals(type)) try{int i=Integer.parseInt(v); if(i>=0&&i<shared.size())v=shared.get(i);}catch(Exception ignored){}
                    String ph=PhoneNormalizer.normalize(v);
                    if(ph!=null) phones.add(ph);
                    if(phones.size()>=limit) break;
                }
            }
        } finally { f.delete(); }
        return new ArrayList<>(phones);
    }
    private static List<String> shared(ZipFile z)throws Exception{
        ArrayList<String> out=new ArrayList<>(); ZipEntry e=z.getEntry("xl/sharedStrings.xml"); if(e==null)return out;
        XmlPullParser p=Xml.newPullParser(); p.setInput(z.getInputStream(e),"UTF-8"); StringBuilder b=null;
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
            if(ev==XmlPullParser.START_TAG&&"si".equals(p.getName()))b=new StringBuilder();
            else if(ev==XmlPullParser.START_TAG&&"t".equals(p.getName())&&b!=null)b.append(p.nextText());
            else if(ev==XmlPullParser.END_TAG&&"si".equals(p.getName())&&b!=null){out.add(b.toString());b=null;}
        }
        return out;
    }
}
