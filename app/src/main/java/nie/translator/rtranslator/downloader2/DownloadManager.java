package nie.translator.rtranslator.downloader2;

import static android.content.Context.BIND_ABOVE_CLIENT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

public class DownloadManager implements ServiceConnection {
    private final Context context;
    @Nullable
    private Callback callback;
    @Nullable
    private DownloaderService downloaderService;
    private final Downloader2.ClientCallback serviceCallback;
    private ArrayList<DownloadGroupInfo> downloadsToStart = new ArrayList<>();
    private boolean shouldStartAllDownloads = false;


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
     * This method will start the download service (if there are downloads to resume) and resume all the unfinished downloads
     */
    public void subscribeAndResumeDownload(@Nullable Callback callback) {
        if(this.callback == null) {
            this.callback = callback;
            boolean shouldStartService = areDownloadsRunning(false);
            if(shouldStartService) {
                startAndBindService();
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

    public void startDownload(DownloadGroupInfo downloadGroup){
        if(downloaderService == null && !downloadsToStart.contains(downloadGroup)){
            downloadsToStart.add(downloadGroup);
            if(!DownloaderService.running){
                startAndBindService();
            }
        }else if(downloaderService != null) {
            downloaderService.startDownload(downloadGroup);
        }
    }

    public boolean stopDownload(DownloadGroupInfo downloadGroup){
        if(downloaderService != null) {
            downloaderService.pauseDownload(downloadGroup);
            return true;
        }
        return false;
    }

    public boolean cancelDownload(DownloadGroupInfo downloadGroup){
        if(downloaderService != null) {
            downloaderService.cancelDownload(downloadGroup);
            return true;
        }
        return false;
    }

    public boolean startAllDownloads() {
        if(downloaderService == null && !shouldStartAllDownloads){
            shouldStartAllDownloads = true;
            if(!DownloaderService.running){
                startAndBindService();
            }
        } else if(downloaderService != null) {
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

    public boolean cancelAllDownloads() {
        if(downloaderService != null) {
            downloaderService.cancelAllDownloads();
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

    private void startAndBindService(){
        // start the service
        final Intent intent = new Intent(context, DownloaderService.class);
        context.startService(intent);
        //we bind to the service
        boolean result = context.bindService(new Intent(context, DownloaderService.class), this, BIND_ABOVE_CLIENT);
        Log.d("bind download", result ? "success" : "failed");
    }

    private boolean areDownloadsRunning(boolean includePaused){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        if (!downloadsStatusString.isEmpty()) {
            //we check if there are unfinished downloads that are not paused (or also paused ones if includePaused is true)
            Gson gson = new Gson();
            ArrayList<DownloadGroupInfo> downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {}.getType());
            if (downloadGroupInfos != null) {
                for (DownloadGroupInfo groupInfo : downloadGroupInfos) {
                    if (!groupInfo.isAllDownloadCompleted() && (includePaused || groupInfo.getRunningDownloadIndex() != -1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.downloaderService = ((DownloaderService.LocalBinder) iBinder).getService();
        downloaderService.registerClient(serviceCallback);
        if(callback != null) callback.onServiceConnected();
        for(DownloadGroupInfo downloadGroup: downloadsToStart) {
            downloaderService.startDownload(downloadGroup);
        }
        downloadsToStart.clear();
        if(shouldStartAllDownloads){
            downloaderService.startAllDownloads();
            shouldStartAllDownloads = false;
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.downloaderService = null;
    }

    public static abstract class Callback extends Downloader2.ClientCallback {
        public abstract void onServiceConnected();
    }
}
