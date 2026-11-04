package com.sinicable.remotesender;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PICK=11,CONTACTS=12;
    private QueueStore store; private TextView status,current; private EditText message,mins,test;
    private CheckBox bale,eitaa,rubika,consent;
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new QueueStore(this);setContentView(ui());refresh();}
    private View ui(){
        ScrollView s=new ScrollView(this); LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(24,24,24,40);r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);s.addView(r);
        TextView title=t("Remote Sender",28);title.setGravity(Gravity.CENTER);r.addView(title);
        r.addView(t("XLSX queue for Bale • Eitaa • Rubika",15)); status=t("",15);r.addView(status);
        Button imp=b("1. Import XLSX");imp.setOnClickListener(v->pick());r.addView(imp);
        test=e("+989106119487","Test phone");r.addView(test);Button add=b("Add test phone");add.setOnClickListener(v->{String p=PhoneNormalizer.normalize(test.getText().toString());if(p==null)toast("Invalid phone");else{store.add(p);refresh();}});r.addView(add);
        bale=c("Bale / بله",true);eitaa=c("Eitaa / ایتا",true);rubika=c("Rubika / روبیکا",true);r.addView(bale);r.addView(eitaa);r.addView(rubika);
        message=e(store.message(),"Message text");message.setMinLines(3);r.addView(message);mins=e(String.valueOf(store.mins()),"Minutes between sends 1–1440");mins.setInputType(2);r.addView(mins);
        consent=c("I confirm these recipients expect / permitted these messages.",false);r.addView(consent);
        Button start=b("START QUEUE");start.setOnClickListener(v->start());r.addView(start);
        current=t("",17);r.addView(current);
        Button prep=b("PREPARE NEXT MESSAGE");prep.setOnClickListener(v->prepare());r.addView(prep);
        Button sent=b("MARK SENT");sent.setOnClickListener(v->{store.markAndAdvance("SENT");refresh();});r.addView(sent);
        Button skip=b("SKIP");skip.setOnClickListener(v->{store.markAndAdvance("SKIPPED");refresh();});r.addView(skip);
        Button clear=b("CLEAR QUEUE");clear.setOnClickListener(v->{store.clear();refresh();});r.addView(clear);return s;
    }
    private void start(){
        if(!consent.isChecked()){toast("Recipient permission confirmation required.");return;}
        int m;try{m=Integer.parseInt(mins.getText().toString());}catch(Exception e){m=0;}if(m<1||m>1440){toast("Interval must be 1–1440.");return;}
        Set<String>a=new LinkedHashSet<>();if(bale.isChecked())a.add(QueueStore.BALE);if(eitaa.isChecked())a.add(QueueStore.EITAA);if(rubika.isChecked())a.add(QueueStore.RUBIKA);
        if(a.isEmpty()||store.numbers().isEmpty()||message.getText().toString().trim().isEmpty()){toast("Numbers, target and message are required.");return;}
        store.config(message.getText().toString(),m,a);store.rebuild();refresh();
    }
    private void prepare(){
        QueueStore.Task q=store.current();if(q==null){toast("Queue finished.");return;}long wait=store.ready()-System.currentTimeMillis();if(wait>0){toast("Next message is ready in "+((wait+59999)/60000)+" min.");return;}
        if(checkSelfPermission(Manifest.permission.WRITE_CONTACTS)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS},CONTACTS);return;}
        try{
            String name=ContactProvisioner.ensure(this,q.phone);
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("RemoteSender",store.message()));
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,store.message());send.setPackage(QueueStore.pkg(q.app));
            if(send.resolveActivity(getPackageManager())!=null)startActivity(send);else{Intent launch=getPackageManager().getLaunchIntentForPackage(QueueStore.pkg(q.app));if(launch!=null)startActivity(launch);else throw new Exception("Target app missing");}
            toast("Recipient: "+name+" ("+q.phone+"). Message copied. Send manually, then return and tap MARK SENT.");
        }catch(Exception e){toast(e.getMessage());}
    }
    private void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");startActivityForResult(i,PICK);}
    @Override protected void onActivityResult(int rq,int rc,Intent d){super.onActivityResult(rq,rc,d);if(rq==PICK&&rc==RESULT_OK&&d!=null&&d.getData()!=null){Uri u=d.getData();new Thread(()->{try{List<String>p=XlsxReader.read(this,u,1000);store.setNumbers(p);runOnUiThread(()->{toast("Imported "+p.size()+" unique numbers.");refresh();});}catch(Exception e){runOnUiThread(()->toast("Excel error: "+e.getMessage()));}}).start();}}
    private void refresh(){status.setText("Numbers: "+store.numbers().size()+" | "+store.stats());QueueStore.Task q=store.current();current.setText(q==null?"No current task":"Current: "+q.app+"  "+q.phone+"  ["+q.status+"]");}
    private TextView t(String x,int z){TextView v=new TextView(this);v.setText(x);v.setTextSize(z);v.setPadding(4,8,4,8);return v;}private EditText e(String x,String h){EditText v=new EditText(this);v.setText(x);v.setHint(h);return v;}private Button b(String x){Button v=new Button(this);v.setText(x);v.setAllCaps(false);return v;}private CheckBox c(String x,boolean y){CheckBox v=new CheckBox(this);v.setText(x);v.setChecked(y);return v;}private void toast(String x){Toast.makeText(this,x,Toast.LENGTH_LONG).show();}
}
