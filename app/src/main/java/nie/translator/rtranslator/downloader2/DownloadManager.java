package nie.translator.rtranslator.downloader2;

import static android.content.Context.BIND_ABOVE_CLIENT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class DownloadManager implements ServiceConnection {
    private final Context context;
    @Nullable
    private Callback callback;
    @Nullable
    private DownloaderService downloaderService;
    private final Downloader2.ClientCallback serviceCallback;
    private boolean serviceStarted = false;



    public DownloadManager(Context context) {
        this.context = context;
        this.serviceCallback = new Downloader2.ClientCallback() {
            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                if(callback != null){
                    callback.onProgress(downloadGroup, download, totalProgress, progress, unzipping, testingIntegrity);
                }
            }

            @Override
            public void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
                if(callback != null){
                    callback.onCompleted(downloadGroup, download);
                }
            }

            @Override
            public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                if(callback != null){
                    callback.onAllCompleted(downloadGroup);
                }
            }

            @Override
            public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
                if(callback != null){
                    callback.onError(downloadGroup, download, reason);
                }
            }
        };
    }

    /**
     * This method will start the download service and resume all the unfinished downloads
     */
    public void startService(){
        final Intent intent = new Intent(context, DownloaderService.class);
        //intent.putExtra("notification", notification);
        context.startService(intent);
        this.serviceStarted = true;
        if (callback != null) {   //if we have previously called subscribe before starting the service we will bind here
            boolean result = context.bindService(new Intent(context, DownloaderService.class), this, BIND_ABOVE_CLIENT);
            Log.d("bind download", result ? "success" : "failed");
        }
    }

    public void subscribe(@Nullable Callback callback) {
        if(this.callback == null) {
            this.callback = callback;
            if(serviceStarted) {  //if we have not started yet the service, we will bind after starting it, not now (this way the service will not stop when we unbind)
                boolean result = context.bindService(new Intent(context, DownloaderService.class), this, BIND_ABOVE_CLIENT);
                Log.d("bind download", result ? "success" : "failed");
            }
        }
    }

    public void unsubscribe() {
        if(callback != null) {
            if (downloaderService != null) {
                downloaderService.unregisterClient(serviceCallback);
            }
            context.unbindService(this);
            this.callback = null;
        }
    }

    public boolean startDownload(DownloadGroupInfo downloadGroup){
        if(downloaderService != null) {
            downloaderService.startDownload(downloadGroup);
            return true;
        }
        return false;
    }

    public boolean stopDownload(DownloadGroupInfo downloadGroup){
        if(downloaderService != null) {
            downloaderService.pauseDownload(downloadGroup);
            return true;
        }
        return false;
    }

    public boolean startAllDownloads() {
        if(downloaderService != null) {
            downloaderService.startAllDownloads();
            return true;
        }
        return false;
    }

    public boolean stopAllDownloads() {
        if(downloaderService != null) {
            downloaderService.pauseAllDownloads();
            return true;
        }
        return false;
    }

    public ArrayList<DownloadGroupInfo> getDownloadsStatus() {
        if (downloaderService != null) {
            return downloaderService.getDownloadsStatus();
        }
        return null;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.downloaderService = ((DownloaderService.LocalBinder) iBinder).getService();
        downloaderService.registerClient(serviceCallback);
        if(callback != null) callback.onServiceConnected();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.downloaderService = null;
    }

    public static abstract class Callback extends Downloader2.ClientCallback {
        public abstract void onServiceConnected();
    }
}
