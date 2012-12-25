package me.onemobile.android.download;

import java.io.File;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Receives system broadcasts (boot, network connectivity)
 */
public class DownloadReceiver extends BroadcastReceiver {

	public static String DEFAULT_PAGE = "DEFAULT_PAGE";

	SystemFacade mSystemFacade = null;

	public void onReceive(Context context, Intent intent) {
		if (mSystemFacade == null) {
			mSystemFacade = new RealSystemFacade(context);
		}
		String action = intent.getAction();

		if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
			startService(context, action);
		} else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (info != null && info.isConnected()) {
				startService(context, action);
			}
		} else if (action.equals(Constants.ACTION_RETRY)) {
			startService(context, action);
		} else if (action.equals(Constants.ACTION_OPEN) || action.equals(Constants.ACTION_LIST) || action.equals(Constants.ACTION_HIDE)) {
			handleNotificationBroadcast(context, intent);
		}
	}

	/**
	 * Handle any broadcast related to a system notification.
	 */
	private void handleNotificationBroadcast(Context context, Intent intent) {
		Uri uri = intent.getData();
		if (uri == null) {
			return;
		}
		String action = intent.getAction();
		if (Constants.LOGVV) {
			if (action.equals(Constants.ACTION_OPEN)) {
				Log.v(Constants.TAG, "Receiver open for " + uri);
			} else if (action.equals(Constants.ACTION_LIST)) {
				Log.v(Constants.TAG, "Receiver list for " + uri);
			} else { // ACTION_HIDE
				Log.v(Constants.TAG, "Receiver hide for " + uri);
			}
		}

		Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
		if (cursor == null) {
			return;
		}
		try {
			if (!cursor.moveToFirst()) {
				mSystemFacade.cancelAllNotifications();
				return;
			}

			if (action.equals(Constants.ACTION_OPEN)) {
				openDownload(context, cursor);
				hideNotification(context, uri, cursor);
			} else if (action.equals(Constants.ACTION_LIST)) {
				sendNotificationClickedIntent(intent, cursor, context, uri);
			} else { // ACTION_HIDE
				hideNotification(context, uri, cursor);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			cursor.close();
		}
	}

	/**
	 * Hide a system notification for a download.
	 * 
	 * @param uri
	 *            URI to update the download
	 * @param cursor
	 *            Cursor for reading the download's fields
	 */
	private void hideNotification(Context context, Uri uri, Cursor cursor) {
		mSystemFacade.cancelNotification(ContentUris.parseId(uri));

		int statusColumn = cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_STATUS);
		int status = cursor.getInt(statusColumn);
		int visibilityColumn = cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_VISIBILITY);
		int visibility = cursor.getInt(visibilityColumn);
		if (Downloads.Impl.isStatusCompleted(status) && visibility == Downloads.Impl.VISIBILITY_VISIBLE_NOTIFY_COMPLETED) {
			ContentValues values = new ContentValues();
			values.put(Downloads.Impl.COLUMN_VISIBILITY, Downloads.Impl.VISIBILITY_VISIBLE);
			context.getContentResolver().update(uri, values, null, null);
		}
	}

	/**
	 * Open the download that cursor is currently pointing to, since it's
	 * completed notification has been clicked.
	 */
	private void openDownload(final Context context, Cursor cursor) {
		final String filename = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl._DATA));
		if (filename == null || filename.length() == 0) {
			return;
		}
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		if (sharedPref.getBoolean(DownloadInfo.AUTO_INSTALL, false)) {
			new Thread() {
				public void run() {
					// DownloadUtil.instance.updateAppStatus(context, pkg,
					// DownloadUtil.status_installing);
					if (!Util.autoInstall(filename)) {
						// DownloadUtil.instance.updateAppStatus(context, pkg,
						// DownloadUtil.status_downloaded);
						installDirectly(context, filename);
					}
				}
			}.start();
			return;
		}
		String mimetype = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_MIME_TYPE));
		Uri path = Uri.parse(filename);
		// If there is no scheme, then it must be a file
		if (path.getScheme() == null) {
			path = Uri.fromFile(new File(filename));
		}

		Intent activityIntent = new Intent(Intent.ACTION_VIEW);
		activityIntent.setDataAndType(path, mimetype);
		activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			context.startActivity(activityIntent);
		} catch (ActivityNotFoundException ex) {
			Log.d(Constants.TAG, "no activity for " + mimetype, ex);
		}
	}

	private void installDirectly(Context context, String apkpath) {
		if (apkpath == null || apkpath.length() == 0) {
			return;
		}
		Uri path = Uri.parse(apkpath);
		// If there is no scheme, then it must be a file
		if (path.getScheme() == null) {
			path = Uri.fromFile(new File(apkpath));
		}
		Intent activityIntent = new Intent(Intent.ACTION_VIEW);
		activityIntent.setDataAndType(path, "application/vnd.android.package-archive");
		activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			context.startActivity(activityIntent);
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(context, ResUtil.getString(context, "installation_package_not_exist"), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Notify the owner of a running download that its notification was clicked.
	 * 
	 * @param intent
	 *            the broadcast intent sent by the notification manager
	 * @param cursor
	 *            Cursor for reading the download's fields
	 */
	private void sendNotificationClickedIntent(Context context, Intent intent, Cursor cursor) {
		String pckg = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_PACKAGE));
		if (pckg == null) {
			return;
		}

		String clazz = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_CLASS));
		boolean isPublicApi = cursor.getInt(cursor.getColumnIndex(Downloads.Impl.COLUMN_IS_PUBLIC_API)) != 0;

		Intent appIntent = null;
		if (isPublicApi) {
			appIntent = new Intent(DownloadManager.ACTION_NOTIFICATION_CLICKED);
			appIntent.setPackage(pckg);
		} else { // legacy behavior
			if (clazz == null) {
				return;
			}
			appIntent = new Intent(Downloads.Impl.ACTION_NOTIFICATION_CLICKED);
			appIntent.setClassName(pckg, clazz);
			if (intent.getBooleanExtra("multiple", true)) {
				appIntent.setData(Downloads.get_CONTENT_URI(context));
			} else {
				long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(Downloads.Impl._ID));
				appIntent.setData(ContentUris.withAppendedId(Downloads.get_CONTENT_URI(context), downloadId));
				// appIntent.putExtra(Downloads.Impl._ID, downloadId);
			}
		}

		mSystemFacade.sendBroadcast(appIntent);
	}

	private void sendNotificationClickedIntent(Intent intent, Cursor cursor, Context context, Uri uri) {
		String pckg = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_PACKAGE));
		if (pckg == null) {
			mSystemFacade.cancelAllNotifications();
			return;
		}

		String clazz = cursor.getString(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_NOTIFICATION_CLASS));
		boolean isPublicApi = cursor.getInt(cursor.getColumnIndex(Downloads.Impl.COLUMN_IS_PUBLIC_API)) != 0;

		Intent appIntent = null;
		if (isPublicApi) {
			appIntent = new Intent(DownloadManager.ACTION_NOTIFICATION_CLICKED);
			appIntent.setPackage(pckg);
			mSystemFacade.sendBroadcast(appIntent);
		} else { // legacy behavior
			if (clazz == null) {
				return;
			}
			// appIntent = new
			// Intent(Downloads.Impl.ACTION_NOTIFICATION_CLICKED);
			// appIntent.setClassName(pckg, clazz);
			// if (intent.getBooleanExtra("multiple", true)) {
			if (DownloadNotification.downloadingCount > 1) {
				try {
					Intent intent2 = new Intent(context, Class.forName(context.getPackageName() + ".MyAppsActivity"));
					intent2.putExtra(DEFAULT_PAGE, 3);
					intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent2.setData(Uri.parse("downloads://myapps?type=3"));
					context.startActivity(intent2);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			} else {
				long _id = cursor.getLong(cursor.getColumnIndex(Downloads.Impl._ID));
				Cursor cc = context.getContentResolver().query(AppsStatusProvider.getURI(context),
						new String[] { AppsStatusProvider.PACKAGE, AppsStatusProvider.APPID }, AppsStatusProvider.APPDOWNLOADINGID + " ='" + _id + "'", null,
						null);
				String pkg = "";
				int appid = -1;
				if (cc != null) {
					if (cc.moveToFirst()) {
						pkg = cc.getString(0);
						appid = cc.getInt(1);
					}
					cc.close();
				}

				if ((pkg == null || pkg.length() == 0) && appid <= 0) {
					try {
						context.getContentResolver().delete(uri, Downloads.Impl._ID + "='" + _id + "'", null);
						NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
						mNotificationManager.cancel(Long.valueOf(_id).intValue());
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					try {
						Intent intent2 = new Intent(context, Class.forName(context.getPackageName() + ".AppDetailsFragmentActivity"));
						intent2.putExtra("APPID", appid);
						intent2.putExtra("APPPKG", pkg);
						intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(intent2);
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// mSystemFacade.sendBroadcast(appIntent);

	}

	private void startService(Context context, String action) {
		Intent it = new Intent(context, DownloadService.class);
		it.putExtra("AC", action);
		context.startService(it);
	}
}