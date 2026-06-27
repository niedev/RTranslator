package nie.translator.rtranslator.downloader2;


import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.downloader.Error;
import com.downloader.OnCancelListener;
import com.downloader.OnDownloadListener;
import com.downloader.OnPauseListener;
import com.downloader.OnProgressListener;
import com.downloader.OnStartOrResumeListener;
import com.downloader.PRDownloader;
import com.downloader.Progress;

import java.io.File;

import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;

public class Downloader2 {
    public static final int GENERAL_ERROR = 1;
    public static final int INTEGRITY_CHECK_FAILED = 2;
    private Context context;
    private final DownloadGroupInfo downloadGroupInfo;
    private ClientCallback callback;
    private int lastDownloadSuccessIndex;
    private boolean allCompleted = false;

    public Downloader2(DownloadGroupInfo downloadGroupInfo, Context context, ClientCallback callback) {
        this.downloadGroupInfo = downloadGroupInfo;
        this.downloadGroupInfo.setRunningDownloadIndex(-1);
        this.context = context;
        this.callback = callback;
    }

    public void startDownloads(){
        if(downloadGroupInfo.downloadsInfo.length > 0 && !downloadGroupInfo.isAllDownloadCompleted()) {
            if (downloadGroupInfo.getRunningDownloadIndex() == -1) {
                startDownload(0);
            }else{
                startDownload(downloadGroupInfo.getRunningDownloadIndex());
            }
        }
    }

    public void pauseDownloads(){
        // we cancel the current download (for now this is the best option, it is difficult (if not impossible) to pause without having access to the server)
        PRDownloader.cancel(downloadGroupInfo.downloadsInfo[downloadGroupInfo.getRunningDownloadIndex()].getDownloadId());
    }

    public void cancelDownloads(){
        int currentDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        if(currentDownloadIndex != -1) {
            // we cancel the current download
            PRDownloader.cancel(downloadGroupInfo.downloadsInfo[currentDownloadIndex].getDownloadId());
            // we delete the already downloaded files of this group of download
            for (int i = 0; i <= currentDownloadIndex; i++){
                File file = new File(downloadGroupInfo.downloadsInfo[currentDownloadIndex].getDestinationCompletePath());
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    public DownloadGroupInfo getDownloadGroupInfo() {
        return downloadGroupInfo;
    }


    private void startDownload(int index){
        downloadGroupInfo.setRunningDownloadIndex(index);
        int downloadId = PRDownloader.download(downloadGroupInfo.downloadsInfo[index].getUrl(), downloadGroupInfo.downloadsInfo[index].getDestinationPath(), downloadGroupInfo.downloadsInfo[index].getName())
                .build()
                .setOnStartOrResumeListener(new OnStartOrResumeListener() {
                    @Override
                    public void onStartOrResume() {

                    }
                })
                .setOnPauseListener(new OnPauseListener() {
                    @Override
                    public void onPause() {

                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel() {

                    }
                })
                .setOnProgressListener(new OnProgressListener() {
                    @Override
                    public void onProgress(Progress progress) {
                        //here we return a value between 0 and 100 that represents the total progress made so far with the download
                        int percentageTotalProgress = calculateTotalProgress(lastDownloadSuccessIndex, progress.currentBytes/1000);
                        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
                        int percentageProgress = calculateProgress(runningDownloadIndex, progress.currentBytes/1000);
                        downloadGroupInfo.setCurrentProgress(percentageTotalProgress);
                        if(runningDownloadIndex >= 0) {
                            downloadGroupInfo.downloadsInfo[runningDownloadIndex].setCurrentProgress(percentageProgress);
                        }
                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setDownloadCompleted(true);
                        callback.onProgress(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex], percentageTotalProgress, percentageProgress, false, false);
                    }
                })
                .start(new OnDownloadListener() {
                    @Override
                    public void onDownloadComplete() {
                        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
                        DownloadInfo finishedDownload = downloadGroupInfo.downloadsInfo[runningDownloadIndex];
                        int percentageTotalProgress = calculateTotalProgress(lastDownloadSuccessIndex+1, 0);
                        downloadGroupInfo.setCurrentProgress(percentageTotalProgress);
                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setCurrentProgress(100);
                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setDownloadCompleted(true);
                        callback.onDownloadCompleted(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex]);
                        if (finishedDownload.shouldTestIntegrity()) {
                            callback.onProgress(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex], percentageTotalProgress, 100, false, true);
                            NeuralNetworkApi.testModelIntegrity(finishedDownload.getDestinationCompletePath(), new NeuralNetworkApi.InitListener() {
                                @Override
                                public void onInitializationFinished() {
                                    downloadGroupInfo.downloadsInfo[runningDownloadIndex].setIntegrityTested(true);
                                    callback.onIntegrityTestCompleted(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex]);
                                    startNextDownload();
                                }

                                @Override
                                public void onError(int[] reasons, long value) {
                                    int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
                                    if(runningDownloadIndex >= 0){
                                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setCurrentError(INTEGRITY_CHECK_FAILED);
                                    }
                                    downloadGroupInfo.setRunningDownloadIndex(-1);
                                    callback.onError(downloadGroupInfo, finishedDownload, INTEGRITY_CHECK_FAILED);
                                }
                            });
                        }else{
                            startNextDownload();
                        }
                    }

                    @Override
                    public void onError(Error error) {
                        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
                        DownloadInfo download = downloadGroupInfo.downloadsInfo[runningDownloadIndex];
                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setCurrentError(GENERAL_ERROR);
                        downloadGroupInfo.setRunningDownloadIndex(-1);
                        callback.onError(downloadGroupInfo, download, GENERAL_ERROR);
                    }
                });
        downloadGroupInfo.downloadsInfo[index].setDownloadId(downloadId);
    }

    private void startNextDownload(){
        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        if (runningDownloadIndex < downloadGroupInfo.downloadsInfo.length - 1) {
            //we start the next download and notify the end of the current one
            lastDownloadSuccessIndex = runningDownloadIndex;
            callback.onCompleted(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex]);
            startDownload(runningDownloadIndex+1);
        } else {
            //we notify the end of all downloads
            allCompleted = true;
            callback.onAllCompleted(downloadGroupInfo);
        }
    }

    private int calculateTotalProgress(int lastDownloadSuccessIndex, long downloadedKb) {
        if(lastDownloadSuccessIndex <= downloadGroupInfo.downloadsInfo.length-1) {
            double totalSize = 0;
            for (DownloadInfo downloadInfo : downloadGroupInfo.downloadsInfo) {
                totalSize = totalSize + downloadInfo.getSize();
            }
            int baseProgress = 0;
            double baseSize = 0;
            for (int i = 0; i <= lastDownloadSuccessIndex; i++) {
                baseSize = baseSize + downloadGroupInfo.downloadsInfo[i].getSize();
            }
            baseProgress = (int) ((baseSize * 100) / totalSize);
            int currentProgress = (int) (((downloadedKb) * 100) / totalSize);
            return baseProgress + currentProgress;
        }else{
            return 100;
        }
    }

    private int calculateProgress(int downloadIndex, long downloadedKb){
        if(downloadIndex >= 0 && downloadIndex < downloadGroupInfo.downloadsInfo.length) {
            double totalSizeKb = downloadGroupInfo.downloadsInfo[downloadIndex].getSize();
            return (int) ((downloadedKb * 100) / totalSizeKb);
        }else{
            return 0;
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof DownloadGroupInfo) {
            return downloadGroupInfo.equals(obj);
        }
        return super.equals(obj);
    }

    /*public static abstract class Callback {
        public abstract void onAllDownloadComplete();
        public abstract void onDownloadComplete(DownloadInfo downloadInfo);
        public abstract void onProgress(DownloadInfo downloadInfo, int progress, boolean testingIntegrity, boolean unzipping);
        public abstract void onError(DownloadInfo downloadInfo, int reason);
    }*/

    public abstract static class ClientCallback {
        public abstract void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity);
        public void onDownloadCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download){}
        public void onUnzippingCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download){}
        public void onIntegrityTestCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download){}
        public abstract void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download);
        public abstract void onAllCompleted(DownloadGroupInfo downloadGroup);
        public abstract void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason);
    }

    public static class DownloadError {
        public DownloadInfo downloadInfo;
        public int reason;

        public DownloadError(DownloadInfo downloadInfo, int reason) {
            this.downloadInfo = downloadInfo;
            this.reason = reason;
        }
    }
}

