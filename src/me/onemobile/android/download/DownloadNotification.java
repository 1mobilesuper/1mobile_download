package me.onemobile.android.download;

import java.util.LinkedList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

/**
 * This class handles the updating of the Notification Manager for the cases
 * where there is an ongoing download. Once the download is complete (be it
 * successful or unsuccessful) it is no longer the responsibility of this
 * component to show the download in the notification manager.
 * 
 */
class DownloadNotification {

	Context mContext;
	public NotificationManager mNotificationMgr;
	NotificationItem mNotification;
	public static int downloadingCount = 1;

	static final String LOGTAG = "DownloadNotification";
	static final String WHERE_RUNNING = "(" + Downloads.COLUMN_STATUS + " >= '100') AND (" + Downloads.COLUMN_STATUS + " <= '199') AND ("
			+ Downloads.COLUMN_VISIBILITY + " IS NULL OR " + Downloads.COLUMN_VISIBILITY + " == '" + Downloads.VISIBILITY_VISIBLE + "' OR "
			+ Downloads.COLUMN_VISIBILITY + " == '" + Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED + "')";
	static final String WHERE_COMPLETED = Downloads.COLUMN_STATUS + " >= '200' AND " + Downloads.COLUMN_VISIBILITY + " == '"
			+ Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED + "'";

	/**
	 * This inner class is used to collate downloads that are owned by the same
	 * application. This is so that only one notification line item is used for
	 * all downloads of a given application.
	 * 
	 */
	static class NotificationItem {
		int mTotalCurrent = 0;
		int mTotalTotal = 0;
		public LinkedList<Long> mIds = new LinkedList<Long>();
		public LinkedList<String> mNotifyPackageNames = new LinkedList<String>();
		public LinkedList<String> mTitles = new LinkedList<String>();
		public LinkedList<String> mDescriptions = new LinkedList<String>();

		public DownloadNotificationBuilder notificationBuilder = new DownloadNotificationBuilder();

		void addItem(Long id, String pkg, String title, String desc, int currentBytes, int totalBytes) {
			mTotalCurrent += currentBytes;
			mTotalTotal += totalBytes;

			mIds.add(id);
			mNotifyPackageNames.add(pkg);
			mTitles.add(title);
			mDescriptions.add(desc);
		}

		public int size() {
			return mIds.size();
		}

		void clear() {
			mTotalCurrent = 0;
			mTotalTotal = 0;
			mIds.clear();
			mNotifyPackageNames.clear();
			mDescriptions.clear();
			mTitles.clear();
		}
	}

	DownloadNotification(Context ctx) {
		mContext = ctx;
		mNotificationMgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotification = new NotificationItem();
	}

	public void updateNotification() {
		updateActiveNotification();
		updateCompletedNotification();
	}

	private void updateActiveNotification() {
		// Active downloads
		Cursor c = null;
		try {
			c = mContext.getContentResolver().query(
					Downloads.get_CONTENT_URI(mContext),
					new String[] { Downloads.Impl._ID, Downloads.COLUMN_TITLE, Downloads.COLUMN_DESCRIPTION, Downloads.COLUMN_NOTIFICATION_PACKAGE,
							Downloads.COLUMN_NOTIFICATION_CLASS, Downloads.COLUMN_CURRENT_BYTES, Downloads.COLUMN_TOTAL_BYTES, Downloads.COLUMN_STATUS },
					WHERE_RUNNING, null, Downloads.Impl._ID);

		} catch (Exception e) {
			// This is very cazy..
		}
		if (c == null) {
			return;
		}

		// Columns match projection in query above
		final int idColumn = 0;
		final int titleColumn = 1;
		final int descColumn = 2;
		final int ownerColumn = 3;
		final int classOwnerColumn = 4;
		final int currentBytesColumn = 5;
		final int totalBytesColumn = 6;
		// final int statusColumn = 7;

		mNotification.clear();

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			String packageName = c.getString(ownerColumn);
			int max = c.getInt(totalBytesColumn);
			int progress = c.getInt(currentBytesColumn);
			long id = c.getLong(idColumn);
			String title = c.getString(titleColumn);
			if (title == null || title.length() == 0) {
				title = mContext.getResources().getString(ResUtil.getStringId(mContext.getPackageName(), "download_unknown_title"));
			}
			String desc = c.getString(descColumn);
			mNotification.addItem(id, packageName, title, desc, progress, max);
		}
		c.close();

		downloadingCount = mNotification.size();
		if (mNotification.size() > 0) {
			try {
				Notification notif = mNotification.notificationBuilder.build(mNotification, mContext);
				mNotificationMgr.notify(mNotification.mIds.get(0).intValue(), notif);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void updateCompletedNotification() {
		// Completed downloads
		Cursor c = mContext.getContentResolver().query(
				Downloads.get_CONTENT_URI(mContext),
				new String[] { Downloads.Impl._ID, Downloads.COLUMN_TITLE, Downloads.COLUMN_DESCRIPTION, Downloads.COLUMN_NOTIFICATION_PACKAGE,
						Downloads.COLUMN_NOTIFICATION_CLASS, Downloads.COLUMN_CURRENT_BYTES, Downloads.COLUMN_TOTAL_BYTES, Downloads.COLUMN_STATUS,
						Downloads.COLUMN_LAST_MODIFICATION, Downloads.COLUMN_DESTINATION }, WHERE_COMPLETED, null, Downloads.Impl._ID);

		if (c == null) {
			return;
		}

		// Columns match projection in query above
		final int idColumn = 0;
		final int titleColumn = 1;
		@SuppressWarnings("unused")
		final int descColumn = 2;
		@SuppressWarnings("unused")
		final int ownerColumn = 3;
		@SuppressWarnings("unused")
		final int classOwnerColumn = 4;
		@SuppressWarnings("unused")
		final int currentBytesColumn = 5;
		@SuppressWarnings("unused")
		final int totalBytesColumn = 6;
		final int statusColumn = 7;
		final int lastModColumnId = 8;
		final int destinationColumnId = 9;

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			// Add the notifications
			Notification n = new Notification();
			n.icon = android.R.drawable.stat_sys_download_done;

			long id = c.getLong(idColumn);
			String title = c.getString(titleColumn);
			if (title == null || title.length() == 0) {
				title = mContext.getResources().getString(ResUtil.getStringId(mContext.getPackageName(), "download_unknown_title"));
			}
			Uri contentUri = Uri.parse(Downloads.get_CONTENT_URI(mContext) + "/" + id);
			String caption;
			Intent intent;
			if (Downloads.isStatusError(c.getInt(statusColumn))) {
				caption = mContext.getResources().getString(ResUtil.getStringId(mContext.getPackageName(), "notification_download_failed"));
				intent = new Intent(Constants.ACTION_LIST);
			} else {
				caption = mContext.getResources().getString(ResUtil.getStringId(mContext.getPackageName(), "notification_download_complete"));
				if (c.getInt(destinationColumnId) == Downloads.DESTINATION_EXTERNAL) {
					intent = new Intent(Constants.ACTION_OPEN);
				} else {
					intent = new Intent(Constants.ACTION_LIST);
				}
			}
			intent.setClassName(mContext.getPackageName(), DownloadReceiver.class.getName());
			intent.setData(contentUri);

			n.when = c.getLong(lastModColumnId);
			n.setLatestEventInfo(mContext, title, caption, PendingIntent.getBroadcast(mContext, 0, intent, 0));

			intent = new Intent(Constants.ACTION_HIDE);
			intent.setClassName(mContext.getPackageName(), DownloadReceiver.class.getName());
			intent.setData(contentUri);
			n.deleteIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
			try {
				mNotificationMgr.notify(c.getInt(idColumn), n);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();
	}

}
