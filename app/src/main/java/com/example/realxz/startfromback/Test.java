package com.example.realxz.startfromback;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author real xz
 * @date 2019-11-29
 */
public class Test {
    public static int getVivoApplistPermissionStatus(Context context) {
        Uri uri2 = Uri.parse("content://com.vivo.permissionmanager.provider.permission/start_bg_activity");

        ContentValues contentValues = new ContentValues();
        contentValues.put("currentstate", 0);
        context.getContentResolver().update(uri2, contentValues, "pkgname=?", new String[]{"com.example.realxz.startfromback"});

        try {
            Cursor cursor = context.getContentResolver().query(uri2, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                while (cursor.moveToNext()) {
                    String pkgName = cursor.getString(cursor.getColumnIndex("pkgname"));
                    String currentState = cursor.getString(cursor.getColumnIndex("currentstate"));
                    Log.e("realxz", "----------------" + "\n");
                    Log.e("realxz", "pkg name is  " + pkgName);
                    Log.e("realxz", "current state is " + currentState);
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return -1;
    }

    public static String akt(String str, String str2) {

        try {
            byte[] akx = akx(Base64.decode(str, 0), str2);
            if (akx != null) {
                return new String(akx);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static byte[] akx(byte[] bArr, String str) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(aky(str), "AES");
            Cipher instance = Cipher.getInstance("AES");
            instance.init(2, secretKeySpec);
            return instance.doFinal(bArr);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] aky(String str) {
        byte[] bArr = new byte[24];
        byte[] bytes = str.getBytes();
        if (bArr.length <= bytes.length) {
            System.arraycopy(bytes, 0, bArr, 0, bArr.length);
        } else {
            System.arraycopy(bytes, 0, bArr, 0, bytes.length);
        }
        return bArr;
    }
}
