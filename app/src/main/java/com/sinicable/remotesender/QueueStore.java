package com.sinicable.remotesender;

import android.content.*;
import org.json.*;
import java.util.*;

public final class QueueStore {
    public static final String BALE="BALE",EITAA="EITAA",RUBIKA="RUBIKA";
    private final SharedPreferences p;
    public QueueStore(Context c){p=c.getSharedPreferences("remote_sender",Context.MODE_PRIVATE);}
    public void setNumbers(List<String> n){JSONArray a=new JSONArray();for(String s:n)a.put(s);p.edit().putString("numbers",a.toString()).apply();}
    public List<String> numbers(){ArrayList<String>o=new ArrayList<>();try{JSONArray a=new JSONArray(p.getString("numbers","[]"));for(int i=0;i<a.length();i++)o.add(a.getString(i));}catch(Exception ignored){}return o;}
    public void add(String n){List<String>a=numbers();if(!a.contains(n))a.add(n);setNumbers(a);}
    public void config(String m,int mins,Set<String>apps){p.edit().putString("message",m).putInt("mins",mins).putStringSet("apps",new HashSet<>(apps)).apply();}
    public String message(){return p.getString("message","");} public int mins(){return p.getInt("mins",5);}
    public void rebuild(){JSONArray a=new JSONArray();for(String ph:numbers())for(String app:p.getStringSet("apps",Collections.emptySet()))try{JSONObject o=new JSONObject();o.put("id",UUID.randomUUID());o.put("phone",ph);o.put("app",app);o.put("status","PENDING");a.put(o);}catch(Exception ignored){}p.edit().putString("tasks",a.toString()).putInt("index",0).putLong("ready",0).apply();}
    public Task current(){try{JSONArray a=new JSONArray(p.getString("tasks","[]"));int i=p.getInt("index",0);if(i>=a.length())return null;JSONObject o=a.getJSONObject(i);return new Task(o.optString("id"),o.optString("phone"),o.optString("app"),o.optString("status"));}catch(Exception e){return null;}}
    public void markAndAdvance(String status){try{JSONArray a=new JSONArray(p.getString("tasks","[]"));int i=p.getInt("index",0);if(i<a.length())a.getJSONObject(i).put("status",status);p.edit().putString("tasks",a.toString()).putInt("index",i+1).putLong("ready",System.currentTimeMillis()+mins()*60000L).apply();}catch(Exception ignored){}}
    public long ready(){return p.getLong("ready",0);}
    public void clear(){p.edit().putString("tasks","[]").putInt("index",0).putLong("ready",0).apply();}
    public String stats(){try{JSONArray a=new JSONArray(p.getString("tasks","[]"));return "Queue "+p.getInt("index",0)+"/"+a.length();}catch(Exception e){return "Queue 0/0";}}
    public static String pkg(String app){return BALE.equals(app)?"ir.nasim":EITAA.equals(app)?"ir.eitaa.messenger":"ir.resaneh1.iptv";}
    public static final class Task{public final String id,phone,app,status;Task(String i,String p,String a,String s){id=i;phone=p;app=a;status=s;}}
}
