package me.onemobile.android.download;

import java.util.concurrent.ConcurrentHashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

/**
 * Allows application to interact with the download manager.
 */
public final class AppsStatusProvider extends ContentProvider {
	
	public final static int status_default = 0;
	public final static int status_downloading = 100;
	public final static int status_downloaded = 200;
	public final static int status_installing = 300;
	public final static int status_uninstalling = 400;
	public final static int status_update = 500;
	public final static int status_installed = 600;
	public final static int status_uninstalled = 700;

	public static final String DB_NAME = "appsstatus.db";
	
	/*
	 * appsStatus HashMap<appID,sort> sort: 0 downloading 1 install hint 2
	 * downloaded 3 update 4 installed 5 installing 6 uninstalling
	 */
	public static ConcurrentHashMap<String, Integer> appsStatus = new ConcurrentHashMap<String, Integer>();

	
	public static Uri URI;
	
	public static Uri getURI(Context ctx) {
		if (URI == null) {
			URI = Uri.parse("content://" + ResUtil.getString(ctx, "provider_auth_AppsStatusProvider") + "/appsstatus");
			ctx = null;
		}
		return URI;
	}

	/*
	 * 2012-02-17 v2.2 192
	 * 2012-03-21 v2.3 193 
	 * 2012-04-22 v2.4 194
	 * 2012-07-23 v2.7 195
	 * 2012-09-26 v2.9 196
	 * 2012-09-26 v2.9+ 197
	 */
	public static final int DB_VERSION = 197; 
	public static final String DB_TABLE = "appsstatus";

	/** The database that lies underneath this content provider */
	private SQLiteOpenHelper mOpenHelper = null;

	public static final String ID = "_id";
	public static final String VERSION = "version";
	public static final String ICON_URL = "icon";
	public static final String PATH = "path";
	public static final String STATUS = "status";
	public static final String TITLE = "title";
	public static final String APPID = "apkid";
	public static final String PACKAGE = "package";

	public static final String ISINSTALLED = "isinstalled";
	public static final String TOTALBYTES = "total_bytes";
	public static final String CURRENTBYTES = "current_bytes";
	public static final String APPSRC = "appsrc";

	public static final String APPNAME = "appname";
	public static final String APPDOWNLOADINGID = "appdownloadingid";
	public static final String APPDOWNLOADINGURL = "appdownloadingurl";
	public static final String VERSIONCODE = "versioncode";
	public static final String BEFOREDOWNLOADINGSTATUS = "bds";
	public static final String SIGNATURE = "signature";
	public static final String APPTYPE = "apptype";
	public static final String LOCATION = "location"; // 0:phone, 2:sd, 4:unmovable 
	public static final String PERCENT_UPDATE = "percent_update";
	
	//when a app need to be updated,blow value should be filled
	public static final String UPDATEVERSIONCODE="updateversioncode";
	public static final String UPDATEVERSIONNAME="updateversionname";
	
	public static final String LASTMODIFYTIME="lastmodifytime";

	public boolean onCreate() {
		mOpenHelper = new AppsStatusDBHelper(getContext());
		return true;
	}
	
	public Uri insert(final Uri uri, final ContentValues values) {
		try {
			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			long rowID = db.insert(DB_TABLE, null, values);
			
			if (rowID != -1) {
				getContext().getContentResolver().notifyChange(uri, null);
			}
			return Uri.parse(getURI(getContext()) + "/" + rowID);
		} catch (Exception e) {
			return Uri.EMPTY;
		}
	}

	@Override
	public Cursor query(final Uri uri, String[] projection, final String selection, final String[] selectionArgs, final String sort) {
		try {
			SQLiteDatabase db = mOpenHelper.getReadableDatabase();
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(DB_TABLE);
			Cursor ret = qb.query(db, projection, selection, selectionArgs, null, null, sort);
			ret.setNotificationUri(getContext().getContentResolver(), uri);
			return ret;
		} catch (Exception e) {
			return null;
		}
	}
	
	@Override
	public int update(final Uri uri, final ContentValues values, final String where, final String[] whereArgs) {
		try {
			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			int count=db.update(DB_TABLE, values, where, whereArgs);
			getContext().getContentResolver().notifyChange(uri, null);
			return count;
		} catch (Exception e) {
			return 0;
		}
	}

	public int delete(final Uri uri, final String where, final String[] whereArgs) {
		try {
			SQLiteDatabase db = mOpenHelper.getWritableDatabase();
			int count= db.delete(DB_TABLE, where , whereArgs);
			getContext().getContentResolver().notifyChange(uri, null);
			return count;
		} catch (Exception e) {
			return 0;
		}
	}

	public String getType(Uri uri) {
		return null;
	}
}

