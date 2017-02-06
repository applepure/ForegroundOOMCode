package com.example.user.myapplication;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;


import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;


/**
 * A woefully undocumented class to self-update an application, uses resources in
 * res/values/updater.xml
 */
public class Updater {

    // Logging tag
    private static final String TAG = "Updater";

    /**
     * Downloads and installs an apk
     * @param context the current context
     * @param apkUrl the url of the apk to download and install
     */
    public static void update(Context context, String apkUrl) {

        update(context, apkUrl, 0, false);

    }

    /**
     * Downloads and installs the specified version of the current application
     * @param context the current context
     * @param versionCode the versionCode of the application to install
     */
    private static void update(Context context, int versionCode){

        update(context, String.format(context.getString(R.string.updater_apkurl), Integer.toString(versionCode)), versionCode, true);

    }

    /**
     * Downloads and installs an apk with specified versionCode and cleanup options
     * @param context the current Context
     * @param apkUrl the url of the apk to download and install
     * @param versionCode the versionCode of the apk to download and install, use 0 for none
     * @param doCleanup whether to clean up previous downloaded versions
     */
    private static void update(Context context, String apkUrl, int versionCode, Boolean doCleanup){

        try {

            //Create directory if not exists
            File file = new File(context.getExternalFilesDir(null) + context.getString(R.string.updater_localdir));

            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();

            //Get apk name
            String[] urlParts = apkUrl.split("/");

            //Async task to download file and install
            DownloadFileAsync downloadFile = new DownloadFileAsync(context);

            //Params for asynctask
            DownloadRequest params = new DownloadRequest(apkUrl, file.getAbsolutePath() + "/" + urlParts[urlParts.length-1], versionCode);
            params.doCleanup = doCleanup;

            // Do work in background
            downloadFile.execute(params);

        } catch (Exception e) {

            e.printStackTrace();
            Toast.makeText(context, R.string.updater_error, Toast.LENGTH_LONG).show();

        }

    }

    /**
     * Checks for updates and launches a dialog if a new update is available
     * @param context the current context
     * @param isSilent show a dialog during the update check
     */
    @SuppressWarnings("unused")
    public static void checkForUpdate(Context context, boolean isSilent) {

        new Updater.CheckForUpdateAsync(context, isSilent, null).execute();

    }

    /**
     * Checks for updates and launches a dialog if a new update is available, a callback is invoked
     * when the update check is complete
     * @param context the current context
     * @param isSilent show a dialog during the update check
     * @param callbacks the callback to invoke when the update check is completed
     */
    public static void checkForUpdate(Context context, boolean isSilent, CheckForUpdateAsync.UpdateCallbacks callbacks) {

        new Updater.CheckForUpdateAsync(context, isSilent, callbacks).execute();

    }

    /**
     * Cleans up previous downloads.  The current version and most recent previous version are retained
     * assuming versionCode has been incremented with no gaps
     * @param context the current context
     * @param versionCode the current versionCode
     */
    private static void cleanUp(Context context, int versionCode){

        //Get apk name
        String[] urlParts = context.getString(R.string.updater_apkurl).split("/");
        String apkName = urlParts[urlParts.length-1];

        //File handle
        File file = new File(context.getExternalFilesDir(null) + context.getString(R.string.updater_localdir));

        for (File f : file.listFiles()){

            // Delete all but the current and previous versions
            if (!(f.getName().equals(String.format(apkName, versionCode)) || f.getName().equals(String.format(apkName, versionCode-1)))){
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }

        }

    }

    private static class DownloadFileAsync extends AsyncTask<DownloadRequest, Integer, String> {

        ProgressDialog mProgressDialog;
        Context mContext;
        File mOutputFile;

        public DownloadFileAsync(Context context) {

            mContext = context;
            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setMessage(mContext.getString(R.string.updater_downloading));
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);

        }

        @Override
        protected String doInBackground(DownloadRequest... request) {

            // For some reason this method must use varargs, just use the first argument
            DownloadRequest params = request[0];

            try {

                // Connect via url
                URL url = new URL(params.apkUrl);
                URLConnection connection = url.openConnection();
                connection.connect();
                Log.d(TAG, "Downloading new version from: " + url.toExternalForm());

                // this will be useful so that you can show a typical 0-100% progress bar
                int fileLength = connection.getContentLength();
                mProgressDialog.setMax(fileLength);

                // downloaded file path
                mOutputFile = new File(params.localApk);

                // download the file
                InputStream input = new BufferedInputStream(url.openStream());
                OutputStream output = new FileOutputStream(mOutputFile);

                // Byte buffering and progress
                byte data[] = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    // publishing the progress....
                    publishProgress((int) total);
                    output.write(data, 0, count);
                }

                // Clean up streams
                output.flush();
                output.close();
                input.close();

            } catch (Exception e) {

                Log.e(TAG, "Could not download new version", e);
                return "error";

            }

            // Clean up old versions
            if (params.doCleanup) cleanUp(mContext, params.versionCode);

            // Return argument
            if (params.doInstall){
                return "install";
            } else {
                return "";
            }

        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
            mProgressDialog.show();

        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            super.onProgressUpdate(progress);
            mProgressDialog.setProgress(progress[0]);

        }

        @Override
        protected void onPostExecute(String result) {

            super.onPostExecute(result);

            //Dismiss dialog
            mProgressDialog.dismiss();

            //Install depending on result
            if (result.equals("install")){

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(mOutputFile), "application/vnd.android.package-archive");
                mContext.startActivity(intent);

            } else if (result.equals("error")){

                Toast.makeText(mContext, R.string.updater_error, Toast.LENGTH_LONG).show();

            }

        }

    }

    /**
     * Represents a task that can check for updates asynchronously
     */
    public static class CheckForUpdateAsync extends AsyncTask<String, Integer, UpdateResult> {

        /**
         * Callbacks used when checking for updates
         */
        public interface UpdateCallbacks {

            /**
             * Called when an update check completes
             * @param isAvailable whether a new version is available
             * @param currentVersionCode the application's current version code
             * @param newVersionCode the available update;s version code
             */
            void onUpdateChecked(boolean isAvailable, int currentVersionCode, int newVersionCode);

        }

        UpdateCallbacks mCallbacks = null;
        Context mContext;
        ProgressDialog mDialog = null;

        /**
         * Constructs a new update checking task
         * @param context the current context
         * @param isSilent whether to show a dialog during the update check
         * @param callbacks a callback to be invoked when the update check is complete
         */
        CheckForUpdateAsync(Context context, boolean isSilent, UpdateCallbacks callbacks)  {

            mContext = context;

            if (!isSilent) {
                mDialog = new ProgressDialog(context);
                mDialog.setMessage(mContext.getString(R.string.updater_checking));
            }

            mCallbacks = callbacks;

        }

        @Override
        protected void onPreExecute() {

            super.onPreExecute();
            if (mDialog != null) mDialog.show();

        }

        @Override
        protected UpdateResult doInBackground(String... params) {

            UpdateResult result = new UpdateResult();

            try {

                // Create a URL for the desired page
                URL url = new URL(mContext.getString(R.string.updater_versionurl));

                // Read all the text returned by the server
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                result = new UpdateResult(in);

                // Cose the reader
                in.close();

            } catch (Exception e) {

                //Toast.makeText(mContext, R.string.updater_errorversion, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Could not get version file", e);

            }

            return result;

        }

        @Override
        protected void onPostExecute(final UpdateResult result) {

            super.onPostExecute(result);

            // Dismiss dialog
            if (mDialog != null) mDialog.dismiss();

            // If version code on server greater than local
            try {

                int versionCode = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode;

                Log.d(TAG, "App version code: " + versionCode + ", Version file code: " + result.version);

                if (result.version > versionCode) {

                    if (mCallbacks != null) mCallbacks.onUpdateChecked(true, versionCode, result.version);

                    new AlertDialog.Builder(mContext)
                            //.setIcon(Theme.getResourceIdFromAttr(mContext, android.R.attr.alertDialogIcon))
                            .setTitle(R.string.update_available)
                            .setMessage(mContext.getResources().getString(R.string.updater_availablemsg) + "\n\n" + result.changeLog)
                            .setPositiveButton(R.string.updater_update, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    Updater.update(mContext, result.version);

                                }

                            })
                            .setNegativeButton(R.string.updater_cancel, null)
                            .show();

                } else {

                    if (mCallbacks != null) mCallbacks.onUpdateChecked(false, versionCode, result.version);
                    if (mDialog != null) Toast.makeText(mContext, R.string.updater_uptodate , Toast.LENGTH_LONG).show();

                }

            } catch (NameNotFoundException e) {

                // Should never happen
                e.printStackTrace();

            }

        }

    }

    private static class DownloadRequest {

        String apkUrl;
        String localApk;
        int versionCode;
        Boolean doInstall = true;
        Boolean doCleanup = true;

        public DownloadRequest(String apkUrl, String localApk, int versionCode){

            this.apkUrl = apkUrl;
            this.localApk = localApk;
            this.versionCode = versionCode;

        }

    }

    private static class UpdateResult {

        int version = 0;
        String changeLog = "";

        public UpdateResult(){

        }

        public UpdateResult(BufferedReader reader) throws IOException {

            // Parse version code, always the first line
            version = Integer.parseInt(reader.readLine());

            // Build changelog
            StringBuilder builder = new StringBuilder();

            // Remaining lines are all changelog, append newline character for each new line
            while (reader.ready() && (changeLog = reader.readLine()) != null) {
                builder.append(changeLog);
                builder.append("\n");
            }

            // Trim result and set to field
            changeLog = builder.toString().trim();

        }

    }

}