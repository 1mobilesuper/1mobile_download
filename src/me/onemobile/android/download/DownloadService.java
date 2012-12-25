package me.onemobile.android.download;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

/**
 * Performs the background downloads requested by applications that use the
 * Downloads provider.
 */
public class DownloadService extends Service {
	/**
	 * amount of time to wait to connect to MediaScannerService before timing
	 * out
	 */
	private static final long WAIT_TIMEOUT = 10 * 1000;

	/** Observer to get notified when the content observer's data changes */
	private DownloadManagerContentObserver mObserver;

	/** Class to handle Notification Manager updates */
	private DownloadNotification mNotifier;

	/**
	 * The Service's view of the list of downloads, mapping download IDs to the
	 * corresponding info object. This is kept independently from the content
	 * provider, and the Service only initiates downloads based on this data, so
	 * that it can deal with situation where the data in the content provider
	 * changes or disappears.
	 */
	// private Map<Long, DownloadInfo> mDownloads = Maps.newHashMap();
	private Map<Long, DownloadInfo> mDownloads = new HashMap<Long, DownloadInfo>();
	/**
	 * The thread that updates the internal download list from the content
	 * provider.
	 */
	UpdateThread mUpdateThread;

	/**
	 * Whether the internal download list should be updated from the content
	 * provider.
	 */
	private boolean mPendingUpdate;

	/**
	 * The IPC interface to the Media Scanner
	 */
	// private mMediaScannerService;

	SystemFacade mSystemFacade;

	/**
	 * Receives notifications when the data in the content provider changes
	 */
	private class DownloadManagerContentObserver extends ContentObserver {

		public DownloadManagerContentObserver() {
			super(new Handler());
		}

		/**
		 * Receives notification when the data in the observed content provider
		 * changes.
		 */
		public void onChange(final boolean selfChange) {
			if (Constants.LOGVV) {
				Log.v(Constants.TAG, "Service ContentObserver received notification");
			}
			updateFromProvider();
		}

	}

	/**
	 * Returns an IBinder instance when someone wants to connect to this
	 * service. Binding to this service is not allowed.
	 * 
	 * @throws UnsupportedOperationException
	 */
	public IBinder onBind(Intent i) {
		throw new UnsupportedOperationException("Cannot bind to Download Manager Service");
	}

	/**
	 * Initializes the service when it is first created
	 */
	public void onCreate() {
		super.onCreate();
		if (Constants.LOGVV) {
			Log.v(Constants.TAG, "Service onCreate");
		}

		if (mSystemFacade == null) {
			mSystemFacade = new RealSystemFacade(this);
		}

		mObserver = new DownloadManagerContentObserver();
		getContentResolver().registerContentObserver(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(this), true, mObserver);

		/*
		 * mMediaScannerService = null; mMediaScannerConnecting = false;
		 * mMediaScannerConnection = new MediaScannerConnection();
		 */

		mNotifier = new DownloadNotification(this);

		updateFromProvider();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		if (intent != null) {
			String action = intent.getStringExtra("AC");
			if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				notifyNetworkChanged();
			}
		}
		updateFromProvider();
	}

	/*
	 * @Override public int onStartCommand(Intent intent, int flags, int
	 * startId) { int returnValue = super.onStartCommand(intent, flags,
	 * startId); if (Constants.LOGVV) { Log.v(Constants.TAG, "Service onStart");
	 * } updateFromProvider(); return returnValue; }
	 */

	/**
	 * Cleans up when the service is destroyed
	 */
	public void onDestroy() {
		getContentResolver().unregisterContentObserver(mObserver);
		if (Constants.LOGVV) {
			Log.v(Constants.TAG, "Service onDestroy");
		}
		super.onDestroy();
	}

	/**
	 * Parses data from the content provider into private array
	 */
	private void updateFromProvider() {
		synchronized (this) {
			mPendingUpdate = true;
			if (mUpdateThread == null) {
				mUpdateThread = new UpdateThread();
				mSystemFacade.startThread(mUpdateThread);
			}
		}
	}

	private class UpdateThread extends Thread {
		public UpdateThread() {
			super("Download Service");
		}

		public void run() {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			trimDatabase();
			removeSpuriousFiles();

			boolean keepService = false;
			// for each update from the database, remember which download is
			// supposed to get restarted soonest in the future
			long wakeUp = Long.MAX_VALUE;
			for (;;) {
				synchronized (DownloadService.this) {
					if (mUpdateThread != this) {
						throw new IllegalStateException("multiple UpdateThreads in DownloadService");
					}
					if (!mPendingUpdate) {
						mUpdateThread = null;
						if (!keepService) {
							stopSelf();
						}
						if (wakeUp != Long.MAX_VALUE) {
							scheduleAlarm(wakeUp);
						}
						return;
					}
					mPendingUpdate = false;
				}

				long now = mSystemFacade.currentTimeMillis();
				// boolean mustScan = false;
				keepService = false;
				wakeUp = Long.MAX_VALUE;
				Set<Long> idsNoLongerInDatabase = new HashSet<Long>(mDownloads.keySet());

				Cursor cursor = getContentResolver().query(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(DownloadService.this), null, null, null, null);
				if (cursor == null) {
					continue;
				}

				try {
					DownloadInfo.Reader reader = new DownloadInfo.Reader(getContentResolver(), cursor);
					int idColumn = cursor.getColumnIndexOrThrow(Downloads.Impl._ID);
					int statusColumn = cursor.getColumnIndex(Downloads.Impl.COLUMN_STATUS);

					int count = 0;
					for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
						if (!Downloads.Impl.isStatusCompleted(cursor.getInt(statusColumn))) {
							count++;
						}
						if (count > Constants.MAX_NUM_DOWNLOAD_ENTRIES) {
							break;
						}
						long id = cursor.getLong(idColumn);
						idsNoLongerInDatabase.remove(id);
						DownloadInfo info = mDownloads.get(id);
						if (info != null) {
							updateDownload(reader, info, now);
						} else {
							info = insertDownload(reader, now);
						}

						/*
						 * if (info.shouldScanFile() && !scanFile(info, true,
						 * false)) { mustScan = true; keepService = true; }
						 */
						if (info.hasCompletionNotification()) {
							keepService = true;
						}
						long next = info.nextAction(now);
						if (next == 0) {
							keepService = true;
						} else if (next > 0 && next < wakeUp) {
							wakeUp = next;
						}
					}
				} finally {
					cursor.close();
				}

				for (Long id : idsNoLongerInDatabase) {
					deleteDownload(id);
				}

				// is there a need to start the DownloadService? yes, if there
				// are rows to be
				// deleted.
				/*
				 * if (!mustScan) { for (DownloadInfo info :
				 * mDownloads.values()) { if (info.mDeleted &&
				 * TextUtils.isEmpty(info.mMediaProviderUri)) { mustScan = true;
				 * keepService = true; break; } } }
				 */
				mNotifier.updateNotification();
				/*
				 * if (mustScan) { bindMediaScanner(); } else {
				 * mMediaScannerConnection.disconnectMediaScanner(); }
				 */

				// look for all rows with deleted flag set and delete the rows
				// from the database
				// permanently
				for (DownloadInfo info : mDownloads.values()) {
					if (info.mDeleted) {
						// this row is to be deleted from the database. but does
						// it have
						// mediaProviderUri?
						if (TextUtils.isEmpty(info.mMediaProviderUri)) {
							if (info.shouldScanFile()) {
								// initiate rescan of the file to - which will
								// populate
								// mediaProviderUri column in this row
								if (!scanFile(info, false, true)) {
									throw new IllegalStateException("scanFile failed!");
								}
							} else {
								// this file should NOT be scanned. delete the
								// file.
								Helpers.deleteFile(DownloadService.this, getContentResolver(), info.mId, info.mFileName, info.mMimeType);
							}
						} else {
							// yes it has mediaProviderUri column already filled
							// in.
							// delete it from MediaProvider database and then
							// from downloads table
							// in DownProvider database (the order of deletion
							// is important).
							getContentResolver().delete(Uri.parse(info.mMediaProviderUri), null, null);
							// the following deletes the file and then deletes
							// it from downloads db
							Helpers.deleteFile(DownloadService.this, getContentResolver(), info.mId, info.mFileName, info.mMimeType);
						}
					}
				}
			}
		}

		private void scheduleAlarm(long wakeUp) {
			AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			if (alarms == null) {
				Log.e(Constants.TAG, "couldn't get alarm manager");
				return;
			}

			if (Constants.LOGV) {
				Log.v(Constants.TAG, "scheduling retry in " + wakeUp + "ms");
			}

			Intent intent = new Intent(Constants.ACTION_RETRY);
			// intent.setClassName("me.test.dp",
			// DownloadReceiver.class.getName()); // what!!!!!
			intent.setClass(getApplicationContext(), DownloadReceiver.class);
			alarms.set(AlarmManager.RTC_WAKEUP, mSystemFacade.currentTimeMillis() + wakeUp,
					PendingIntent.getBroadcast(DownloadService.this, 0, intent, PendingIntent.FLAG_ONE_SHOT));

		}
	}

	/**
	 * Removes files that may have been left behind in the cache directory
	 */
	private void removeSpuriousFiles() {
		File[] files = Environment.getDownloadCacheDirectory().listFiles();
		if (files == null) {
			// The cache folder doesn't appear to exist (this is likely the case
			// when running the simulator).
			return;
		}
		HashSet<String> fileSet = new HashSet<String>();
		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().equals(Constants.KNOWN_SPURIOUS_FILENAME)) {
				continue;
			}
			if (files[i].getName().equalsIgnoreCase(Constants.RECOVERY_DIRECTORY)) {
				continue;
			}
			fileSet.add(files[i].getPath());
		}

		Cursor cursor = getContentResolver().query(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(this), new String[] { Downloads.Impl._DATA }, null, null, null);
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				do {
					fileSet.remove(cursor.getString(0));
				} while (cursor.moveToNext());
			}
			cursor.close();
		}
		Iterator<String> iterator = fileSet.iterator();
		while (iterator.hasNext()) {
			String filename = iterator.next();
			if (Constants.LOGV) {
				Log.v(Constants.TAG, "deleting spurious file " + filename);
			}
			new File(filename).delete();
		}
	}

	/**
	 * Drops old rows from the database to prevent it from growing too large
	 */
	private void trimDatabase() {
		Cursor cursor = getContentResolver().query(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(this), new String[] { Downloads.Impl._ID },
				Downloads.Impl.COLUMN_STATUS + " >= '200'", null, Downloads.Impl.COLUMN_LAST_MODIFICATION);
		if (cursor == null) {
			// This isn't good - if we can't do basic queries in our database,
			// nothing's gonna work
			Log.e(Constants.TAG, "null cursor in trimDatabase");
			return;
		}
		if (cursor.moveToFirst()) {
			int numDelete = cursor.getCount() - Constants.MAX_DOWNLOADS;
			int columnId = cursor.getColumnIndexOrThrow(Downloads.Impl._ID);
			while (numDelete > 0) {
				Uri downloadUri = ContentUris.withAppendedId(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(this), cursor.getLong(columnId));
				getContentResolver().delete(downloadUri, null, null);
				if (!cursor.moveToNext()) {
					break;
				}
				numDelete--;
			}
		}
		cursor.close();
	}

	/**
	 * Keeps a local copy of the info about a download, and initiates the
	 * download if appropriate.
	 */
	private DownloadInfo insertDownload(DownloadInfo.Reader reader, long now) {
		DownloadInfo info = reader.newDownloadInfo(this, mSystemFacade);
		mDownloads.put(info.mId, info);

		if (Constants.LOGVV) {
			info.logVerboseInfo();
		}

		info.startIfReady(now);
		return info;
	}

	/**
	 * Updates the local copy of the info about a download.
	 */
	private void updateDownload(DownloadInfo.Reader reader, DownloadInfo info, long now) {
		int oldVisibility = info.mVisibility;
		int oldStatus = info.mStatus;

		reader.updateFromDatabase(info);

		boolean lostVisibility = oldVisibility == Downloads.Impl.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
				&& info.mVisibility != Downloads.Impl.VISIBILITY_VISIBLE_NOTIFY_COMPLETED && Downloads.Impl.isStatusCompleted(info.mStatus);
		boolean justCompleted = !Downloads.Impl.isStatusCompleted(oldStatus) && Downloads.Impl.isStatusCompleted(info.mStatus);
		if (lostVisibility || justCompleted) {
			mSystemFacade.cancelNotification(info.mId);
		}

		info.startIfReady(now);
	}

	/**
	 * Removes the local copy of the info about a download.
	 */
	private void deleteDownload(long id) {
		DownloadInfo info = mDownloads.get(id);
		if (info.shouldScanFile()) {
			scanFile(info, false, false);
		}
		if (info.mStatus == Downloads.Impl.STATUS_RUNNING) {
			info.mStatus = Downloads.Impl.STATUS_CANCELED;
		}
		if (info.mDestination != Downloads.Impl.DESTINATION_EXTERNAL && info.mFileName != null) {
			new File(info.mFileName).delete();
		}
		mSystemFacade.cancelNotification(info.mId);
		mDownloads.remove(info.mId);
	}

	/**
	 * Attempts to scan the file if necessary.
	 * 
	 * @return true if the file has been properly scanned.
	 */
	private boolean scanFile(DownloadInfo info, final boolean updateDatabase, final boolean deleteFile) {
		return false;
	}

	private void notifyNetworkChanged() {
		if (mDownloads != null) {
			Iterator<Entry<Long, DownloadInfo>> iter = mDownloads.entrySet().iterator();
			synchronized (mDownloads) {
				while (iter.hasNext()) {
					DownloadInfo info = iter.next().getValue();
					info.network_con_changed = true;
				}
			}
		}
	}
}