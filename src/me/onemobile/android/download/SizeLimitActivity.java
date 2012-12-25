package me.onemobile.android.download;
import java.util.LinkedList;
import java.util.Queue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.format.Formatter;
import android.util.Log;

/**
 * Activity to show dialogs to the user when a download exceeds a limit on download sizes for
 * mobile networks.  This activity gets started by the background download service when a download's
 * size is discovered to be exceeded one of these thresholds.
 */
public class SizeLimitActivity extends Activity
        implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    private Dialog mDialog;
    private Queue<Intent> mDownloadsToShow = new LinkedList<Intent>();
    private Uri mCurrentUri;
    private Intent mCurrentIntent;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null) {
            mDownloadsToShow.add(intent);
            setIntent(null);
            showNextDialog();
        }
        if (mDialog != null && !mDialog.isShowing()) {
            mDialog.show();
        }
    }

    private void showNextDialog() {
        if (mDialog != null) {
            return;
        }

        if (mDownloadsToShow.isEmpty()) {
            finish();
            return;
        }

        mCurrentIntent = mDownloadsToShow.poll();
        mCurrentUri = mCurrentIntent.getData();
        Cursor cursor = getContentResolver().query(mCurrentUri, null, null, null, null);
        if (cursor == null) {
        	return;
        }
        try {
            if (!cursor.moveToFirst()) {
                Log.e(Constants.TAG, "Empty cursor for URI " + mCurrentUri);
                dialogClosed();
                return;
            }
            showDialog(cursor);
        } finally {
            cursor.close();
        }
    }

    private void showDialog(Cursor cursor) {
        int size = cursor.getInt(cursor.getColumnIndexOrThrow(Downloads.Impl.COLUMN_TOTAL_BYTES));
        String sizeString = Formatter.formatFileSize(this, size);
        String queueText = ResUtil.getString(getApplicationContext(), "button_queue_for_wifi");
        boolean isWifiRequired =
            mCurrentIntent.getExtras().getBoolean(DownloadInfo.EXTRA_IS_WIFI_REQUIRED);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (isWifiRequired) {
            builder.setTitle(ResUtil.getStringId(getPackageName(), "wifi_required_title"))
                    .setMessage(getString(ResUtil.getStringId(getPackageName(), "wifi_required_body"), sizeString, queueText))
                    .setPositiveButton(ResUtil.getStringId(getPackageName(), "button_queue_for_wifi"), this)
                    .setNegativeButton(ResUtil.getStringId(getPackageName(), "button_cancel_download"), this);
        } else {
            builder.setTitle(ResUtil.getStringId(getPackageName(), "wifi_recommended_title"))
                    .setMessage(getString(ResUtil.getStringId(getPackageName(), "wifi_recommended_body"), sizeString, queueText))
                    .setPositiveButton(ResUtil.getStringId(getPackageName(), "button_start_now"), this)
                    .setNegativeButton(ResUtil.getStringId(getPackageName(), "button_queue_for_wifi"), this);
        }
        mDialog = builder.setOnCancelListener(this).show();
    }

    public void onCancel(DialogInterface dialog) {
        dialogClosed();
    }

    private void dialogClosed() {
        mDialog = null;
        mCurrentUri = null;
        showNextDialog();
    }

    public void onClick(DialogInterface dialog, int which) {
        boolean isRequired =
                mCurrentIntent.getExtras().getBoolean(DownloadInfo.EXTRA_IS_WIFI_REQUIRED);
        if (isRequired && which == AlertDialog.BUTTON_NEGATIVE) {
            getContentResolver().delete(mCurrentUri, null, null);
        } else if (!isRequired && which == AlertDialog.BUTTON_POSITIVE) {
            ContentValues values = new ContentValues();
            values.put(Downloads.Impl.COLUMN_BYPASS_RECOMMENDED_SIZE_LIMIT, true);
            getContentResolver().update(mCurrentUri, values , null, null);
        }
        dialogClosed();
    }
}
