package nie.translator.rtranslator.downloader2;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Binder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class DownloaderService extends Service {
    public static final String DOWNLOAD_INFOS = "nie.translator.rtranslator.downloader2.DOWNLOAD_INFOS";
    private final ArrayList<Downloader2> downloaders = new ArrayList<>();
    private final IBinder binder = new LocalBinder();
    private ArrayList<Downloader2.ClientCallback> clients = new ArrayList<>();
    private static final String GROUP_KEY_DOWNLOADS = "com.example.downloadapp.DOWNLOAD_GROUP";
    private static final String CHANNEL_ID = "service_background_notification";
    private static final int SUMMARY_ID = 1000; // Fixed ID for the overall average notification
    private Downloader2.ClientCallback downloaderCallback;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder summaryBuilder;
    private NotificationCompat.Builder subDownloadBuilder;
    private int lastSummaryProgress = -1;
    private long lastSummaryUpdateTime = 0;
    private final java.util.Map<Integer, Integer> lastChildProgresses = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> lastChildUpdateTimes = new java.util.HashMap<>();
    public static boolean running = false;

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
        PRDownloaderConfig config = PRDownloaderConfig.newBuilder()
                .setDatabaseEnabled(true)
                .build();
        PRDownloader.initialize(getApplicationContext(), config);
        downloaderCallback = new Downloader2.ClientCallback() {
            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                if(downloadGroup.getRunningDownload() != null) updateDownloadProgress(downloaders.indexOf(downloadGroup), downloadGroup.getRunningDownload().name, totalProgress, downloadGroup.getRunningDownloadIndex() == -1);
                notifyProgress(downloadGroup, download, totalProgress, progress, unzipping, testingIntegrity);
            }

            @Override
            public void onDownloadCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
                updateDownloadGroupInfoPreference(downloadGroup);
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
            public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                updateDownloadGroupInfoPreference(downloadGroup);
                notifyAllCompleted(downloadGroup);
                int i = downloaders.indexOf(downloadGroup);
                removeDownload(i);
            }

            @Override
            public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
                notifyError(downloadGroup, download, reason);
                int i = downloaders.indexOf(downloadGroup);
                removeDownload(i);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("download", "download service started");
        if(!running) {
            running = true;
            // Initialize the notification system
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            summaryBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setGroup(GROUP_KEY_DOWNLOADS)
                    .setGroupSummary(true)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            summaryBuilder.setContentTitle("Preparing downloads...")
                    .setProgress(100, 0, true); // Indeterminate before first update

            startForeground(SUMMARY_ID, summaryBuilder.build());

            //start or resume the downloads from the preferences (if they are not paused)
            SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
            if (!downloadsStatusString.isEmpty()) {
                //we eventually restore the state of all the unfinished downloads and resume their download
                Gson gson = new Gson();
                ArrayList<DownloadGroupInfo> downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {
                }.getType());
                if (downloadGroupInfos != null) {
                    for (DownloadGroupInfo groupInfo : downloadGroupInfos) {
                        if (!groupInfo.isAllDownloadCompleted()){
                            DownloadInfoExtended lastDownload = groupInfo.downloadsInfo[groupInfo.downloadsInfo.length-1];
                            if(lastDownload.isAllCompleted()){
                                // here we check if a download from the preferences is not marked as all completed
                                // but it is actually completed because the last download is completed (if there has been an error in the update of the preference during the completion of the download).
                                // In this case we simply mark it as all completed, update the preferences with the new status and move on.
                                groupInfo.setAllDownloadCompleted(true);
                                updateDownloadGroupInfoPreference(groupInfo);
                            }else if(groupInfo.getRunningDownloadIndex() != -1) {
                                startDownload(groupInfo);
                            }else{
                                // in this case the download is paused so we add it to the downloaders but without starting it (plus we create its notification)
                                int index;
                                synchronized (downloaders) {
                                    downloaders.add(new Downloader2(groupInfo, this, downloaderCallback));
                                    index = downloaders.size()-1;
                                }
                                int runningDownloadIndex = downloaders.get(index).findFirstIncompletedDownload();
                                if(runningDownloadIndex != -1) {
                                    DownloadInfoExtended runningDownload = downloaders.get(index).getDownloadGroupInfo().downloadsInfo[runningDownloadIndex];
                                    updateDownloadProgress(index, runningDownload.name, downloaders.get(index).getDownloadGroupInfo().getCurrentProgress(), true);
                                }
                            }
                        }
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
            final Downloader2 newDownloader = new Downloader2(download, this, downloaderCallback);
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
            if (index != -1 && downloaders.get(index).getDownloadGroupInfo().getRunningDownloadIndex() != -1) {
                DownloadGroupInfo downloadGroupInfo = downloaders.get(index).getDownloadGroupInfo();
                DownloadInfoExtended runningDownload = downloadGroupInfo.getRunningDownload();
                downloaders.get(index).pauseDownloads();
                updateDownloadProgress(index, runningDownload.name, downloadGroupInfo.getCurrentProgress(), true);
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
                removeDownload(index);
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
        Gson gson = new Gson();
        if(!downloadsStatusString.isEmpty()) {
            downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {}.getType());
        }
        if(downloadGroupInfos == null) {
            downloadGroupInfos = new ArrayList<>();
        }
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

    public void updateDownloadProgress(int downloadIndex, String filename, int progress, boolean paused) {
        int safeId = downloadIndex + 2000;  //Shift the index by an offset so it never equals 0 or conflicts with SUMMARY_ID

        long currentTime = System.currentTimeMillis();
        Integer lastProg = lastChildProgresses.get(safeId);
        Long lastTime = lastChildUpdateTimes.get(safeId);

        // Only update if progress percentage changed OR 500ms has passed (if paused is false)
        if (!paused && lastProg != null && lastProg == progress) return;
        if (!paused && lastTime != null && (currentTime - lastTime < 500) && progress < 100) return;

        // Save new states
        lastChildProgresses.put(safeId, progress);
        lastChildUpdateTimes.put(safeId, currentTime);

        String sortKey = String.format(java.util.Locale.US, "%04d", downloadIndex);

        // Build and update the specific Child Notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(paused ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download) // System download icon
                .setGroup(GROUP_KEY_DOWNLOADS)            // Assigns this to the group
                .setSortKey(sortKey)                      // Prevents continuous reordering of the notifications
                .setOnlyAlertOnce(true)                    // Prevents sound/vibration spam
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle((paused ? "Paused: " : "") + filename)
                .setContentText(progress + "%")
                .setProgress(100, progress, false).build();

        notificationManager.notify(safeId, notification);

        // Re-calculate average and update the Summary Notification
        updateSummaryNotification();

        // update the state of this download in the preferences (with the new progress)
        if(downloadIndex != -1) {
            DownloadGroupInfo downloadGroup = downloaders.get(downloadIndex).getDownloadGroupInfo();
            updateDownloadGroupInfoPreference(downloadGroup);
        }
    }

    private void updateSummaryNotification() {
        if (downloaders.isEmpty()) return;

        // Calculate average progress
        int totalProgress = 0;
        for (int i=0; i<downloaders.size(); i++){
            totalProgress += downloaders.get(i).getDownloadGroupInfo().getCurrentProgress();
        }
        int averageProgress = totalProgress / downloaders.size();

        String summaryTitle = downloaders.size() + " active downloads";
        String summaryText = "Average progress: " + averageProgress + "%";

        long currentTime = System.currentTimeMillis();

        // Only update if progress percentage changed OR 500ms has passed
        if (averageProgress == lastSummaryProgress) return;
        if (currentTime - lastSummaryUpdateTime < 500) return;

        lastSummaryProgress = averageProgress;
        lastSummaryUpdateTime = currentTime;

        // Modify the existing Summary Builder
        summaryBuilder.setContentTitle(summaryTitle)
                .setContentText(summaryText)
                .setProgress(100, averageProgress, false);

        // Push the lightweight update
        notificationManager.notify(SUMMARY_ID, summaryBuilder.build());
    }

    private void deleteDownloadNotification(int downloadIndex) {
        int safeId = downloadIndex + 2000;
        // Remove the child notification from the shade
        notificationManager.cancel(safeId);

        if (!downloaders.isEmpty()) {
            // Recalculate average progress based on remaining active downloads
            updateSummaryNotification();
        }
    }

    private void removeDownload(Downloader2 downloader){
        int index = downloaders.indexOf(downloader);
        if(index != -1){
            removeDownload(index);
        }
    }

    private void removeDownload(int index){
        synchronized (downloaders) {
            downloaders.remove(index);
        }
        deleteDownloadNotification(index);
        if(downloaders.isEmpty()){
            stopSelf();
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
        running = false;
        synchronized (downloaders) {
            downloaders.clear();
        }
        clients.clear();
        PRDownloader.shutDown();
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