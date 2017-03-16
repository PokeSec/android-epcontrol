/*
 * EPCService.java : Main Service for EPControl
 *
 * This file is part of EPControl.
 *
 * Copyright (C) 2016  Jean-Baptiste Galet & Timothe Aeberhardt
 *
 * EPControl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * EPControl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with EPControl.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.pokesec.epcontrol;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import com.google.common.io.ByteStreams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;


import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EPCService extends Service {

    static {
        System.loadLibrary("python-wrapper");
    }

    public static EPCService mService;
    public static Thread creationThread;
    public AtomicBoolean isPythonInitialized = new AtomicBoolean(false);

    private native boolean initializePython(String filesPath);
    private native boolean onAction(String action, String dataString);
    private native void releasePython();

    /* Service binding related */
    private final IBinder mBinder = new EPCServiceBinder();
    private Messenger outMessenger;

    /* DownloadManager related */
    private DownloadManager downloadManager;
    private long downloadRef;
    private final String remoteAssets = "https://api.pokesec/data/assets/";
    private final BroadcastReceiver dmBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            File assetsDest = new File(getFilesDir() + "/assets.zip");
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
            if (id != downloadRef) {
                return;
            }
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(id);
            Cursor cursor = downloadManager.query(query);

            if (!cursor.moveToFirst()) {
                return;
            }

            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (DownloadManager.STATUS_SUCCESSFUL != cursor.getInt(statusIndex)) {
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                Log.w("EPCService", "Assets download failed for reason " + cursor.getInt(reasonIndex));
                if (assetsDest.exists())
                    initPython();
                else {
                    SystemClock.sleep(5000);
                    new DownloadAssetsTask().execute();
                }
                return;
            }

            int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            String downloadedPackageUriString = cursor.getString(uriIndex);
            try {
                ParcelFileDescriptor fd = downloadManager.openDownloadedFile(downloadRef);
                InputStream in = new FileInputStream(fd.getFileDescriptor());

                OutputStream out = new FileOutputStream(assetsDest);


                Cipher AESCipher = Cipher.getInstance("AES/CFB/NoPadding");
                byte[] sha256 = new byte[32];
                byte[] iv = new byte[16];
                in.read(sha256);
                in.read(iv);
                IvParameterSpec IVSpec = new IvParameterSpec(iv);
                byte[] encodedKey = "p0k3c0rp<3fp0c7b".getBytes();
                SecretKey key = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
                AESCipher.init(Cipher.DECRYPT_MODE, key, IVSpec);
                CipherInputStream cipherInputStream = new CipherInputStream(in, AESCipher);
                ByteStreams.copy(cipherInputStream, out);
                cipherInputStream.close();
                in.close();
                out.close();

                if (!Arrays.equals(FileUtils.sha256(assetsDest), sha256)) {
                    throw new GeneralSecurityException("Corrupted download");
                }

                Log.i("EPCService", "Successfully downloaded assets");
                EPCService.mService.initPython();
            }
            catch (IOException e)
            {
                assetsDest.delete();
                Log.e("EPCService", "Could not copy downloaded assets file", e);
                SystemClock.sleep(5000);
                new DownloadAssetsTask().execute();
            }
            catch (GeneralSecurityException e) {
                assetsDest.delete();
                Log.e("EPCService", "Could not decrypt downloaded assets file", e);
                SystemClock.sleep(5000);
                new DownloadAssetsTask().execute();
            }
            finally {
                downloadManager.remove(downloadRef);
            }
        }
    };

    @Override
    public IBinder onBind(Intent arg0) {
        Bundle extras = arg0.getExtras();
        Log.d("EPCService","onBind");
        // Get messager from the Activity
        if (extras != null) {
            Log.d("EPCService","onBind with extra");
            outMessenger = (Messenger) extras.get("MESSENGER");
        }
        return mBinder;
    }

    public class EPCServiceBinder extends Binder {
        EPCService getService() {
            return EPCService.this;
        }
    }

    public EPCService() {
        EPCService.mService = this;
    }

    @Override
    public void onCreate() {
        Log.d("EPCService", "onCreate start");
        HandlerThread dlThread = new HandlerThread("DownloadThread" , android.os.Process.THREAD_PRIORITY_BACKGROUND);
        dlThread.start();
        Looper looper = dlThread.getLooper();
        Handler handler = new Handler(looper);
        this.downloadManager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        registerReceiver(this.dmBroadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), null, handler);
        File assetsZip = new File(getFilesDir() + "/assets.zip");
        if (assetsZip.exists())
            new DownloadAssetsTask().execute(assetsZip);
        else
            new DownloadAssetsTask().execute();
        Log.d("EPCService", "onCreate done");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.d("EPCService", "onStartCommand start");
        if (intent == null || !isPythonInitialized.get())
            return START_STICKY;

        final String broadcastIntentAction;
        final String dataString;
        if (intent.hasExtra("broadcastIntentAction")){
            broadcastIntentAction = intent.getStringExtra("broadcastIntentAction");
        }
        else
            broadcastIntentAction = null;
        if (intent.hasExtra("dataString")) {
            dataString = intent.getStringExtra("dataString");
        }
        else
            dataString = null;

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    EPCService.creationThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                onAction(broadcastIntentAction, dataString);
                WakeLockManager.completeWakefulIntent(intent);
            }
        };

        t.setName("onStartCommand-" + broadcastIntentAction);
        t.start();
        Log.d("EPCService", "onStartCommand done");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dmBroadcastReceiver);
        if (!isPythonInitialized.get())
            return;
        releasePython();
    }

    private class DownloadAssetsTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... files) {
            if (files.length > 0)
                downloadAssets(files[0]);
            else
                downloadAssets();
            return null;
        }
    }

    public void downloadAssets() {
        this.downloadAssets(null);
    }

    public void downloadAssets(File assetsFile) {
        // Get Instance ID for download
        String instance_id;
        File userSettingsFile = new File(getFilesDir() + "/user-settings.json");
        if (!userSettingsFile.exists()) {
            Log.d("EPCService", "No token available to download assets");
            return;
        }
        StringBuilder userSettings = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(userSettingsFile));
            String line;

            while ((line = br.readLine()) != null) {
                userSettings.append(line);
                userSettings.append('\n');
            }
            br.close();
            JSONObject userSettingsJson = new JSONObject(userSettings.toString());
            instance_id = userSettingsJson.getString("INSTANCE_ID");
        }
        catch (IOException | JSONException e) {
            Log.e("EPCService", "Could not read user settings JSON");
            return;
        }

        if (BuildConfig.FLAVOR != "arm7") {
            Log.e("EPCService", "Unsupported arch, refusing to download assets");
            return;
        }

        // Set up DownloadManager
        Log.i("EPCService", "Preparing assets download with instance id " + instance_id);
        Uri uri = Uri.parse(this.remoteAssets + instance_id +
                "?os=android" + "&version=release" + "&arch=" + BuildConfig.FLAVOR);
        boolean isDownloading = false;
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(
                DownloadManager.STATUS_PAUSED|
                        DownloadManager.STATUS_PENDING|
                        DownloadManager.STATUS_RUNNING|
                        DownloadManager.STATUS_SUCCESSFUL
        );
        Cursor cur = downloadManager.query(query);
        int col = cur.getColumnIndex(
                DownloadManager.COLUMN_URI);
        for(cur.moveToFirst(); !cur.isAfterLast(); cur.moveToNext()) {
            isDownloading = isDownloading || (uri == Uri.parse(cur.getString(col)));
        }
        cur.close();

        if (!isDownloading) {
            Log.i("EPCService", "Downloading from " + this.remoteAssets + instance_id +
                    "?os=android" + "&version=release" + "&arch=" + BuildConfig.FLAVOR);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            request.setVisibleInDownloadsUi(false);
            if (assetsFile != null) {
                String hash = FileUtils.bytesToHex(FileUtils.sha256(assetsFile));
                Log.i("EPCService", "Adding header " + hash);
                request.addRequestHeader("If-None-Match", hash);
            }
            this.downloadRef = downloadManager.enqueue(request);
            Log.i("EPCService", "Assets download queued");
        }
        else
            Log.i("EPCService", "Assets already downloading");
    }

    public void initPython() {
        EPCService.creationThread = new Thread() {
            @Override
            public void run()
            {
                File debugMode = new File(getFilesDir() + "/.debug");
                File target = new File(getFilesDir() + "/assets/");
                File assetsZip = new File(getFilesDir() + "/assets.zip");
                if (!debugMode.exists()) {
                    FileUtils.recursiveDelete(target);
                    try {
                        FileUtils.unzip(assetsZip, target);
                    }
                    catch (IOException e) {
                        Log.e("EPCService", "Failed unzipping assets");
                        assetsZip.delete();
                        EPCService.mService.downloadAssets();
                        return;
                    }
                }

                File from = new File(getFilesDir() + "/assets/settings.json");
                File to = new File(getFilesDir() + "/settings.json");
                if(!from.renameTo(to)) {
                    Log.w("EPCService", "Failed moving settings to files/ dir");
                }

                from = new File(getFilesDir() + "/assets/trust.pem");
                to = new File(getFilesDir() + "/trust.pem");
                if(!from.renameTo(to)) {
                    Log.w("EPCService", "Failed moving CA cert to files/ dir");
                }

                if(initializePython(getFilesDir().getAbsolutePath())) {
                    isPythonInitialized.set(true);
                    Log.i("EPCService", "Successfully initialized Python");
                }
                else
                    Log.e("EPCService", "Failed initializing Python");
            }
        };
        EPCService.creationThread.start();
    }

    public void forceTasksRefresh() {
        if (!isPythonInitialized.get())
            return;
        Log.d("EPCService", "ForceTasksRefresh started");
        Thread t = new Thread() {
            @Override
            public void run() {
                onAction("ForceRefreshTasks", null);
            }
        };
        t.setName("ForceTasksRefresh");
        t.start();
        Log.d("EPCService", "started ForceTasksRefresh done");
    }
}
