package me.onemobile.android.download;

import me.onemobile.android.download.DownloadNotification.NotificationItem;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

public class DownloadNotificationBuilder {

	private NotificationCompat.Builder builder;

	public Notification build(NotificationItem item, Context mContext) {
		if (Build.VERSION.SDK_INT >= 11) {
			if (builder != null) {
				refresh(item, mContext);
			} else {
				builder = new NotificationCompat.Builder(mContext);
				builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
				builder.setOngoing(true);
				refresh(item, mContext);
			}
			return builder.build();
		} else {
			return buildBefore11(item, mContext);
		}
	}

	private void refresh(NotificationItem item, Context mContext) {
		StringBuilder title = new StringBuilder(item.mTitles.get(0));
		int size = item.mTitles.size();
		if (size > 1) {
			title.append(mContext.getString(ResUtil.getStringId(mContext.getPackageName(), "notification_filename_separator")));
			title.append(item.mTitles.get(1));
			if (size > 2) {
				title.append(mContext.getString(ResUtil.getStringId(mContext.getPackageName(), "notification_filename_extras"),
						new Object[] { Integer.valueOf(size - 2) }));
			}
		} else if (size == 1) {
			String desc = item.mDescriptions.get(0);
			if (desc != null && desc.length() > 0) {
				builder.setContentText(desc);
			}
		}
		builder.setContentTitle(title);
		builder.setProgress((int) item.mTotalTotal, (int) item.mTotalCurrent, item.mTotalTotal <= 0);

		Intent intent = new Intent(Constants.ACTION_LIST);
		intent.setClassName(mContext.getPackageName(), DownloadReceiver.class.getName());
		intent.setData(ContentUris.withAppendedId(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(mContext), item.mIds.get(0)));
		intent.putExtra("multiple", size > 1);
		builder.setContentIntent(PendingIntent.getBroadcast(mContext, 0, intent, 0));
	}

	// ////////////////////////////////////////
	// SDK < 11
	// ////////////////////////////////////////

	private Notification buildBefore11(NotificationItem item, Context mContext) {
		StringBuilder title = new StringBuilder(item.mTitles.get(0));
		int size = item.mTitles.size();
		String desc = null;
		if (size > 1) {
			title.append(mContext.getString(ResUtil.getStringId(mContext.getPackageName(), "notification_filename_separator")));
			title.append(item.mTitles.get(1));
			if (size > 2) {
				title.append(mContext.getString(ResUtil.getStringId(mContext.getPackageName(), "notification_filename_extras"),
						new Object[] { Integer.valueOf(size - 2) }));
			}
		} else if (size == 1) {
			desc = item.mDescriptions.get(0);
		}

		Intent intent = new Intent(Constants.ACTION_LIST);
		intent.setClassName(mContext.getPackageName(), DownloadReceiver.class.getName());
		intent.setData(ContentUris.withAppendedId(Downloads.get_ALL_DOWNLOADS_CONTENT_URI(mContext), item.mIds.get(0)));
		intent.putExtra("multiple", size > 1);
		PendingIntent contentIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);

		return setupNotification(mContext, title.toString(), desc, item.mTotalTotal, item.mTotalCurrent, (item.mTotalTotal <= 0), contentIntent);
	}

	public Notification setupNotification(Context mContext, String title, String descp, int progressMax, int progressCurrent, boolean indeterminate,
			PendingIntent contentIntent) {

		Notification notification = new Notification();
		notification.icon = android.R.drawable.stat_sys_download;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.contentIntent = contentIntent;

		// Build the RemoteView object
		RemoteViews expandedView = null;
		if (Build.VERSION.SDK_INT <= 8) {
			expandedView = new RemoteViews(mContext.getPackageName(), ResUtil.getLayoutId(mContext.getPackageName(), "status_bar_ongoing_event_progress_bar"));
		} else {
			expandedView = new RemoteViews(mContext.getPackageName(), ResUtil.getLayoutId(mContext.getPackageName(),
					"status_bar_ongoing_event_progress_bar_nine"));
		}

		if (descp != null && descp.length() > 0) {
			expandedView.setTextViewText(ResUtil.getId(mContext.getPackageName(), "description"), descp);
		}
		expandedView.setTextViewText(ResUtil.getId(mContext.getPackageName(), "title"), title);
		expandedView.setProgressBar(ResUtil.getId(mContext.getPackageName(), "progress_bar"), progressMax, progressCurrent, indeterminate);
		expandedView.setTextViewText(ResUtil.getId(mContext.getPackageName(), "progress_text"), getDownloadingText(progressMax, progressCurrent));
		expandedView.setImageViewResource(ResUtil.getId(mContext.getPackageName(), "appIcon"), android.R.drawable.stat_sys_download);
		notification.contentView = expandedView;

		return notification;
	}

	private String getDownloadingText(long totalBytes, long currentBytes) {
		if (totalBytes <= 0) {
			return "";
		}
		long progress = currentBytes * 100 / totalBytes;
		StringBuilder sb = new StringBuilder();
		sb.append(progress);
		sb.append('%');
		return sb.toString();
	}

}
