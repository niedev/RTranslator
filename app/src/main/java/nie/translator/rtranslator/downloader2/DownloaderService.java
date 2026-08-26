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

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.DownloaderTools;

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
    private int nextNotificationId = 2000;  //this will be incremented and used as a unique id for every downloader in this service instance, it will be used as unique id for the notifications

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
                if(downloadGroup.getRunningDownload() != null) updateDownloadProgress(downloadGroup, downloadGroup.getRunningDownload().name, totalProgress, downloadGroup.getRunningDownloadIndex() == -1, unzipping, testingIntegrity);
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
                updateDownloadGroupInfoPreference(downloadGroup);
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
                            DownloadInfo lastDownload = groupInfo.downloadsInfo[groupInfo.downloadsInfo.length-1];
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
                                Downloader2 downloader = new Downloader2(groupInfo, this, nextNotificationId++, downloaderCallback);
                                downloaders.add(downloader);
                                int runningDownloadIndex = downloader.findFirstIncompletedDownload();
                                if(runningDownloadIndex < downloader.getDownloadGroupInfo().downloadsInfo.length) {
                                    DownloadInfo runningDownload = downloader.getDownloadGroupInfo().downloadsInfo[runningDownloadIndex];
                                    updateDownloadProgress(downloader.getDownloadGroupInfo(), runningDownload.name, downloader.getDownloadGroupInfo().getCurrentProgress(), true, runningDownload.isUnzipping(), runningDownload.isTestingIntegrity());
                                }else{
                                    // the download group is completed even if for some errors the group is not marked as completed
                                    DownloadGroupInfo downloadGroup = downloader.getDownloadGroupInfo();
                                    downloadGroup.setAllDownloadCompleted(true);
                                    downloaderCallback.onAllCompleted(downloadGroup);
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
            final Downloader2 newDownloader = new Downloader2(download, this, nextNotificationId++, downloaderCallback);
            downloaders.add(newDownloader);
            index = downloaders.size()-1;
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
        int index = downloaders.indexOf(download);
        if (index != -1 && downloaders.get(index).getDownloadGroupInfo().getRunningDownloadIndex() != -1) {
            DownloadGroupInfo downloadGroupInfo = downloaders.get(index).getDownloadGroupInfo();
            DownloadInfo runningDownload = downloadGroupInfo.getRunningDownload();
            downloaders.get(index).pauseDownloads();
            updateDownloadProgress(downloadGroupInfo, runningDownload.name, downloadGroupInfo.getCurrentProgress(), true, runningDownload.isUnzipping(), runningDownload.isTestingIntegrity());
        }
    }

    public void cancelAllDownloads(){
        for (DownloadGroupInfo download: getDownloadsStatus()){
            cancelDownload(download);
        }
    }

    public void cancelDownload(DownloadGroupInfo download) {
        int index = downloaders.indexOf(download);
        if (index != -1) {
            downloaders.get(index).cancelDownloads();
            deleteDownloadGroupInfoPreference(download);
            removeDownload(index);
        }else{  //if the download is not currently running (usually when it is completed)
            // we delete the already downloaded files of this group of download
            DownloaderTools.deleteDownloadedFiles(download);
            // we delete the download status from the preferences
            deleteDownloadGroupInfoPreference(download);
        }
    }

    public ArrayList<DownloadGroupInfo> getDownloadsStatus() {
        ArrayList<DownloadGroupInfo> downloadGroupInfos = new ArrayList<>();
        for(Downloader2 downloader : downloaders){
            downloadGroupInfos.add(downloader.getDownloadGroupInfo());
        }
        ArrayList<DownloadGroupInfo> clone = new ArrayList<DownloadGroupInfo>(downloadGroupInfos.size());
        for (DownloadGroupInfo item : downloadGroupInfos) clone.add(item.clone());
        return clone;
    }

    private void updateDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        DownloaderTools.updateDownloadGroupInfoPreference(this, downloadGroup);
    }

    private void addDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        DownloaderTools.addDownloadGroupInfoPreference(this, downloadGroup);
    }

    private void deleteDownloadGroupInfoPreference(DownloadGroupInfo downloadGroup){
        DownloaderTools.deleteDownloadGroupInfoPreference(this, downloadGroup);
    }

    public void updateDownloadProgress(DownloadGroupInfo downloadGroup, String filename, int progress, boolean paused, boolean unzipping, boolean testingIntegrity) {
        int safeId = -1;
        int index = downloaders.indexOf(downloadGroup);
        if(index != -1) safeId = downloaders.get(index).getId();

        if(safeId != -1) {
            long currentTime = System.currentTimeMillis();
            Integer lastProg = lastChildProgresses.get(safeId);
            Long lastTime = lastChildUpdateTimes.get(safeId);

            // Only update if progress percentage changed OR 500ms has passed (if paused is false)
            if (!paused && lastProg != null && lastProg == progress) return;
            if (!paused && lastTime != null && (currentTime - lastTime < 500) && progress < 100)
                return;

            // Save new states
            lastChildProgresses.put(safeId, progress);
            lastChildUpdateTimes.put(safeId, currentTime);

            String sortKey = String.format(java.util.Locale.US, "%04d", safeId);
            String contentText = progress + "%";
            if (unzipping) {
                contentText = getString(R.string.unzipping);
            }
            if (testingIntegrity) {
                contentText = getString(R.string.testing_integrity);
            }

            // Build and update the specific Child Notification
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(paused ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download) // System download icon
                    .setGroup(GROUP_KEY_DOWNLOADS)            // Assigns this to the group
                    .setSortKey(sortKey)                      // Prevents continuous reordering of the notifications
                    .setOnlyAlertOnce(true)                    // Prevents sound/vibration spam
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle((paused ? getString(R.string.paused) : "") + filename)
                    .setContentText(contentText)
                    .setProgress(100, progress, false).build();

            notificationManager.notify(safeId, notification);

            // Re-calculate average and update the Summary Notification
            updateSummaryNotification();

            // update the state of this download in the preferences (with the new progress)
            updateDownloadGroupInfoPreference(downloaders.get(index).getDownloadGroupInfo());
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

    private void deleteDownloadNotification(int safeId) {
        if(safeId != -1) {
            // Remove the child notification from the shade
            notificationManager.cancel(safeId);

            if (!downloaders.isEmpty()) {
                // Recalculate average progress based on remaining active downloads
                updateSummaryNotification();
            }
        }
    }

    private void removeDownload(Downloader2 downloader){
        int index = downloaders.indexOf(downloader);
        if(index != -1){
            removeDownload(index);
        }
    }

    private void removeDownload(int index){
        if (index >= 0) {
            int safeId = downloaders.get(index).getId();
            downloaders.remove(index);
            deleteDownloadNotification(safeId);
            checkAndStopService();
        }
    }

    // Implementation of Downloader2.Callback methods
    // These methods will be called by individual Downloader2 instances

    public void notifyProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) { // Iterate over a copy to avoid ConcurrentModificationException
            client.onProgress(downloadGroup, download, totalProgress, progress, unzipping, testingIntegrity);
        }
    }

    public void notifyCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onCompleted(downloadGroup, download);
        }
    }

    public void notifyAllCompleted(DownloadGroupInfo downloadGroup) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onAllCompleted(downloadGroup);
        }
    }

    public void notifyError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
        for (Downloader2.ClientCallback client : new ArrayList<>(clients)) {
            client.onError(downloadGroup, download, reason);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        downloaders.clear();
        clients.clear();
        PRDownloader.shutDown();
    }


    private void checkAndStopService() {
        if (areAllDownloadsFinished()) {
            stopForeground(true);
            stopSelf();
        }
    }

    private boolean areAllDownloadsFinished() {
        return downloaders.isEmpty(); // No downloads, so all finished
    }
}