package me.onemobile.android.download;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppsStatusDBHelper extends SQLiteOpenHelper {
	
	public AppsStatusDBHelper(final Context context) {
		super(context, AppsStatusProvider.DB_NAME, null, AppsStatusProvider.DB_VERSION);
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {
		createTable(db);
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, int oldV, final int newV) {
		dropTable(db);
		createTable(db);
	}
	
	private void createTable(SQLiteDatabase db) {
		try {
			db.execSQL("CREATE TABLE " + AppsStatusProvider.DB_TABLE + "(" 
					+ AppsStatusProvider.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," 
					+ AppsStatusProvider.VERSION + " TEXT, " 
					+ AppsStatusProvider.ICON_URL + " TEXT, " 
					+ AppsStatusProvider.PATH + " TEXT, " 
					+ AppsStatusProvider.STATUS + " INTEGER, " 
					+ AppsStatusProvider.APPID + " INTEGER, " 
					+ AppsStatusProvider.TITLE + " TEXT, " 
					+ AppsStatusProvider.PACKAGE + " TEXT, " 
					+ AppsStatusProvider.ISINSTALLED + " TEXT, "
					+ AppsStatusProvider.TOTALBYTES + " INTEGER, "
					+ AppsStatusProvider.CURRENTBYTES + " INTEGER, " 
					+ AppsStatusProvider.APPSRC + " INTEGER, "
					+ AppsStatusProvider.APPNAME + " TEXT, " 
					+ AppsStatusProvider.APPDOWNLOADINGID + " INTEGER, " 
					+ AppsStatusProvider.VERSIONCODE + " INTEGER, " 
					+ AppsStatusProvider.BEFOREDOWNLOADINGSTATUS + " INTEGER, " 
					+ AppsStatusProvider.SIGNATURE + " TEXT, " 
					+ AppsStatusProvider.APPDOWNLOADINGURL + " TEXT, " 
					+ AppsStatusProvider.UPDATEVERSIONCODE + " INTEGER, "
					+ AppsStatusProvider.UPDATEVERSIONNAME + " TEXT, " 
					+ AppsStatusProvider.LASTMODIFYTIME + " LONG, " 
					+ AppsStatusProvider.APPTYPE + " INTEGER, " 
					+ AppsStatusProvider.LOCATION + " INTEGER, "
					+ AppsStatusProvider.PERCENT_UPDATE + " INTEGER " 
					+");");
		} catch (SQLException ex) {
			throw ex;
		}
	}

	private void dropTable(SQLiteDatabase db) {
		try {
			db.execSQL("DROP TABLE IF EXISTS " + AppsStatusProvider.DB_TABLE);
		} catch (SQLException ex) {
			throw ex;
		}
	}
}