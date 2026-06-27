package nie.translator.rtranslator.downloader2;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Binder;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class DownloaderService extends Service {
    public static final String DOWNLOAD_INFOS = "nie.translator.rtranslator.downloader2.DOWNLOAD_INFOS";
    private final ArrayList<Downloader2> downloaders = new ArrayList<>();
    private final IBinder binder = new LocalBinder();
    private ArrayList<Downloader2.ClientCallback> clients = new ArrayList<>();

    public class LocalBinder extends Binder {
        DownloaderService getService() {
            return DownloaderService.this;
        }
    }

    public DownloaderService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        if(!downloadsStatusString.isEmpty()){
            //we eventually restore the state of all the unfinished downloads and resume their download
            Gson gson = new Gson();
            ArrayList<DownloadGroupInfo> downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>(){}.getType());
            if(downloadGroupInfos != null){
                for(DownloadGroupInfo groupInfo: downloadGroupInfos){
                    if(!groupInfo.isAllDownloadCompleted()){
                        startDownload(groupInfo);
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void registerClient(Downloader2.ClientCallback client) {
        if (!clients.contains(client)) {
            clients.add(client);
        }
    }

    public void unregisterClient(Downloader2.ClientCallback client) {
        clients.remove(client);
    }

    public ArrayList<Downloader2> getDownloaders() {
        return downloaders;
    }

    public void startAllDownloads(){
        for (DownloadGroupInfo download: getDownloadsStatus()){
            startDownload(download);
        }
    }

    public void startDownload(DownloadGroupInfo download) {
        int index = downloaders.indexOf(download);
        if(index == -1) {
            final AtomicReference<Downloader2> newDownloaderRef = new AtomicReference<>();
            final Downloader2 newDownloader = new Downloader2(download, this, new Downloader2.ClientCallback() {
                @Override
                public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                    updateDownloadGroupInfoPreference(downloadGroup);
                    notifyAllCompleted(downloadGroup);
                    synchronized (downloaders) {
                        downloaders.remove(newDownloaderRef.get());
                    }
                }

                @Override
                public void onUnzippingCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
                    updateDownloadGroupInfoPreference(downloadGroup);
                }

                @Override
                public void onIntegrityTestCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
                    updateDownloadGroupInfoPreference(downloadGroup);
                }

                @Override
                public void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
                    updateDownloadGroupInfoPreference(downloadGroup);
                    notifyCompleted(downloadGroup, download);
                }

                @Override
                public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                    notifyProgress(downloadGroup, download, totalProgress, progress, unzipping, testingIntegrity);
                }

                @Override
                public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
                    notifyError(downloadGroup, download, reason);
                    synchronized (downloaders) {
                        downloaders.remove(newDownloaderRef.get());
                    }
                }
            });
            newDownloaderRef.set(newDownloader);
            synchronized (downloaders) {
                downloaders.add(newDownloader);
                index = downloaders.size()-1;
            }
            addDownloadGroupInfoPreference(download);
        }
        downloaders.get(index).startDownloads();
    }

    public void pauseAllDownloads(){
        for (DownloadGroupInfo download: getDownloadsStatus()){
            pauseDownload(download);
        }
    }

    public void pauseDownload(DownloadGroupInfo download) {
        synchronized (downloaders) {
            int index = downloaders.indexOf(download);
            if (index != -1) {
                downloaders.get(index).pauseDownloads();
            }
        }
    }

    public void cancelAllDownloads(){
        for (DownloadGroupInfo download: getDownloadsStatus()){
            cancelDownload(download);
        }
    }

    public void cancelDownload(DownloadGroupInfo download) {
        synchronized (downloaders) {
            int index = downloaders.indexOf(download);
            if (index != -1) {
                downloaders.get(index).cancelDownloads();
                deleteDownloadGroupInfoPreference(download);
                synchronized (downloaders) {
                    downloaders.remove(index);
                }
            }
        }
    }

    public ArrayList<DownloadGroupInfo> getDownloadsStatus() {
        synchronized (downloaders) {
            ArrayList<DownloadGroupInfo> downloadGroupInfos = new ArrayList<>();
            for(Downloader2 downloader : downloaders){
                downloadGroupInfos.add(downloader.getDownloadGroupInfo());
            }
            return downloadGroupInfos;
        }
    }

    private void updateDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        ArrayList<DownloadGroupInfo> downloadGroupInfos = null;
        if(!downloadsStatusString.isEmpty()) {
            Gson gson = new Gson();
            downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {}.getType());
            int index = downloadGroupInfos.indexOf(downloadGroup);
            if(index != -1) {
                downloadGroupInfos.set(index, downloadGroup);
                String newDownloadsStatusString = gson.toJson(downloadGroupInfos);
                SharedPreferences.Editor editor;
                editor = sharedPreferences.edit();
                editor.putString("downloadsStatus", newDownloadsStatusString);
                editor.apply();
            }
        }
    }

    private void addDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        ArrayList<DownloadGroupInfo> downloadGroupInfos = null;
        if(!downloadsStatusString.isEmpty()) {
            Gson gson = new Gson();
            downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {}.getType());
            int index = downloadGroupInfos.indexOf(downloadGroup);
            if(index == -1) {
                downloadGroupInfos.add(downloadGroup);
                String newDownloadsStatusString = gson.toJson(downloadGroupInfos);
                SharedPreferences.Editor editor;
                editor = sharedPreferences.edit();
                editor.putString("downloadsStatus", newDownloadsStatusString);
                editor.apply();
            }
        }
    }

    private void deleteDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        ArrayList<DownloadGroupInfo> downloadGroupInfos = null;
        if(!downloadsStatusString.isEmpty()) {
            Gson gson = new Gson();
            downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {}.getType());
            int index = downloadGroupInfos.indexOf(downloadGroup);
            if(index != -1) {
                downloadGroupInfos.remove(index);
                String newDownloadsStatusString = gson.toJson(downloadGroupInfos);
                SharedPreferences.Editor editor;
                editor = sharedPreferences.edit();
                editor.putString("downloadsStatus", newDownloadsStatusString);
                editor.apply();
            }
        }
    }

    // Implementation of Downloader2.Callback methods
    // These methods will be called by individual Downloader2 instances

    public void notifyProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean testingIntegrity, boolean unzipping) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) { // Iterate over a copy to avoid ConcurrentModificationException
            client.onProgress(downloadGroup, download, totalProgress, progress, testingIntegrity, unzipping);
        }
    }

    public void notifyCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onCompleted(downloadGroup, download);
        }
        checkAndStopService();
    }

    public void notifyAllCompleted(DownloadGroupInfo downloadGroup) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onAllCompleted(downloadGroup);
        }
        checkAndStopService();
    }

    public void notifyError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onError(downloadGroup, download, reason);
        }
        checkAndStopService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized (downloaders) {
            downloaders.clear();
        }
        clients.clear();
    }


    private void checkAndStopService() {
        if (areAllDownloadsFinished()) {
            stopSelf();
        }
    }

    private boolean areAllDownloadsFinished() {
        synchronized (downloaders) {
            if (downloaders.isEmpty()) return true; // No downloads, so all finished
        }
        return false;
    }
}