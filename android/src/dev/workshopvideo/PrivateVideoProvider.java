package dev.workshopvideo;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;

/** Read-only access to one saved video when the user grants a playback URI. */
public final class PrivateVideoProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private VideoApp app(){return (VideoApp)getContext().getApplicationContext();}
    private JSONObject record(Uri uri) throws FileNotFoundException {
        if(!"content".equals(uri.getScheme())||!uri.getAuthority().equals(getContext().getPackageName()+".videos")
            ||uri.getPathSegments().size()!=2||!uri.getPathSegments().get(0).equals("videos")
            ||!uri.getPathSegments().get(1).matches("[0-9a-f]{32}")||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new FileNotFoundException("视频地址无效。");
        try {
            JSONObject record=app().job(uri.getLastPathSegment());
            if(!record.optString("state").equals("saved")||!record.optString("storage").equals("private")||!app().video(record.getString("id")).isFile())
                throw new FileNotFoundException("视频不存在。");
            return record;
        }catch(Exception e){throw new FileNotFoundException("视频不存在。");}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new SecurityException("仅允许读取视频。");
        JSONObject record=record(uri);return ParcelFileDescriptor.open(app().video(record.optString("id")),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){try{return VideoApp.mime(record(uri).optString("name"));}catch(FileNotFoundException e){return null;}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try {
            JSONObject record=record(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++) {
                if(columns[i].equals(OpenableColumns.DISPLAY_NAME))values[i]=record.optString("name","video.mp4");
                else if(columns[i].equals(OpenableColumns.SIZE))values[i]=app().video(record.optString("id")).length();
            }
            cursor.addRow(values);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
