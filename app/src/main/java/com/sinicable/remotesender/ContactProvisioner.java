package com.sinicable.remotesender;

import android.content.*;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.ArrayList;

public final class ContactProvisioner {
    private ContactProvisioner() {}
    public static String ensure(Context c,String phone)throws Exception{
        String d=phone.replaceAll("[^0-9]",""); String tail=d.length()>8?d.substring(d.length()-8):d; String name="RS_"+tail;
        try(Cursor cur=c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID},ContactsContract.Contacts.DISPLAY_NAME_PRIMARY+"=?",
                new String[]{name},null)){ if(cur!=null&&cur.moveToFirst())return name; }
        ArrayList<ContentProviderOperation> ops=new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE,null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME,null).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name).build());
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID,0)
                .withValue(ContactsContract.Data.MIMETYPE,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER,phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
        c.getContentResolver().applyBatch(ContactsContract.AUTHORITY,ops); return name;
    }
}
