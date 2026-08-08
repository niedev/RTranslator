package nie.translator.rtranslator.downloader2;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.downloader.Error;
import com.downloader.OnCancelListener;
import com.downloader.OnDownloadListener;
import com.downloader.OnPauseListener;
import com.downloader.OnProgressListener;
import com.downloader.OnStartOrResumeListener;
import com.downloader.PRDownloader;
import com.downloader.Progress;
import com.downloader.Status;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import nie.translator.rtranslator.tools.DownloaderTools;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;

public class Downloader2 {
    public static final int GENERAL_ERROR = 1;
    public static final int UNZIP_FAILED = 2;
    public static final int INTEGRITY_CHECK_FAILED = 3;
    private Context context;
    private final DownloadGroupInfo downloadGroupInfo;
    private ClientCallback callback;
    private int lastDownloadSuccessIndex = -1;
    private Handler mainHandler;

    public Downloader2(DownloadGroupInfo downloadGroupInfo, Context context, ClientCallback callback) {
        this.downloadGroupInfo = downloadGroupInfo;
        this.mainHandler = new android.os.Handler(Looper.getMainLooper());
        //this.downloadGroupInfo.setRunningDownloadIndex(-1);
        this.context = context;
        this.callback = callback;
    }

    public void startDownloads(){
        if(downloadGroupInfo.downloadsInfo.length > 0 && !downloadGroupInfo.isAllDownloadCompleted()) {
            // eventual resume of the download based on the previous ones that have been completed
            // (or start from 0 if none of the previous ones are completed)
            int startIndex = downloadGroupInfo.getRunningDownloadIndex() != -1 ? downloadGroupInfo.getRunningDownloadIndex() : findFirstIncompletedDownload();
            DownloadInfoExtended startDownload = downloadGroupInfo.downloadsInfo[startIndex];
            if(startIndex > 0) lastDownloadSuccessIndex = startIndex-1;
            downloadGroupInfo.setRunningDownloadIndex(startIndex);
            int percentageTotalProgress = calculateTotalProgress(lastDownloadSuccessIndex+1, 0);
            if(!startDownload.isDownloadCompleted()){
                startDownload(startIndex);
            }else {
                if (startDownload.shouldUnzip() && !startDownload.isUnzipped()) {
                    unzipRunningDownloadAndTestIntegrity(percentageTotalProgress);
                } else {
                    if (startDownload.shouldTestIntegrity() && !startDownload.isIntegrityTested()) {
                        testRunningDownloadIntegrity(percentageTotalProgress, startDownload.getInternalFolder());
                    } else {
                        startNextDownload();
                    }
                }
            }
        }
    }

    public void pauseDownloads(){
        int currentDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        if(currentDownloadIndex != -1) {
            PRDownloader.pause(downloadGroupInfo.downloadsInfo[currentDownloadIndex].getDownloadId());
            downloadGroupInfo.setRunningDownloadIndex(-1);
        }
    }

    public void cancelDownloads(){
        int currentDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        if(currentDownloadIndex != -1) {
            // we cancel the current download
            PRDownloader.cancel(downloadGroupInfo.downloadsInfo[currentDownloadIndex].getDownloadId());
        }
        // we delete the already downloaded files of this group of download
        DownloaderTools.deleteDownloadedFiles(downloadGroupInfo);
    }

    public DownloadGroupInfo getDownloadGroupInfo() {
        return downloadGroupInfo;
    }


    private void startDownload(int index){
        downloadGroupInfo.setRunningDownloadIndex(index);
        int downloadId = downloadGroupInfo.downloadsInfo[index].getDownloadId();
        if(downloadId != -1){
            if(PRDownloader.getStatus(downloadId) == Status.PAUSED){
                PRDownloader.resume(downloadId);  //resume if the download was paused during this lifetime of the app
                return;
            }
        }
        //start the download from scratch or resume it after the app has been restarted
        downloadId = PRDownloader.download(downloadGroupInfo.downloadsInfo[index].getUrl(), downloadGroupInfo.downloadsInfo[index].getDestinationPath(), downloadGroupInfo.downloadsInfo[index].getName())
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
                            callback.onProgress(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex], percentageTotalProgress, percentageProgress, false, false);
                        }
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
                        if(finishedDownload.shouldUnzip()){
                            unzipRunningDownloadAndTestIntegrity(percentageTotalProgress);
                        }else {
                            if (finishedDownload.shouldTestIntegrity()) {
                                testRunningDownloadIntegrity(percentageTotalProgress, null);
                            } else {
                                startNextDownload();
                            }
                        }
                    }

                    @Override
                    public void onError(Error error) {
                        Log.e("download error", (downloadGroupInfo.getRunningDownload() != null ? downloadGroupInfo.getRunningDownload().getName() : "") + "    " + error.toString());
                        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
                        DownloadInfo download = downloadGroupInfo.downloadsInfo[runningDownloadIndex];
                        downloadGroupInfo.downloadsInfo[runningDownloadIndex].setCurrentError(GENERAL_ERROR);
                        downloadGroupInfo.setRunningDownloadIndex(-1);
                        callback.onError(downloadGroupInfo, download, GENERAL_ERROR);
                    }
                });
        downloadGroupInfo.downloadsInfo[index].setDownloadId(downloadId);
    }

    private void unzipRunningDownloadAndTestIntegrity(int percentageTotalProgress){
        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        DownloadInfo finishedDownload = downloadGroupInfo.downloadsInfo[runningDownloadIndex];
        callback.onProgress(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex], percentageTotalProgress, 100, true, false);
        unpackZip(finishedDownload.destinationPath, finishedDownload.name, new UnpackListener() {
            @Override
            public void onSuccess(String topInternalFolder) {
                downloadGroupInfo.downloadsInfo[runningDownloadIndex].setUnzipped(true);
                downloadGroupInfo.downloadsInfo[runningDownloadIndex].setInternalFolder(topInternalFolder);
                if(finishedDownload.shouldTestIntegrity()) {
                    testRunningDownloadIntegrity(percentageTotalProgress, topInternalFolder);
                }else{
                    startNextDownload();
                }
            }

            @Override
            public void onFailure() {
                callback.onError(downloadGroupInfo, finishedDownload, UNZIP_FAILED);
            }
        });
    }

    private void testRunningDownloadIntegrity(int percentageTotalProgress, @Nullable String internalFolder) {
        int runningDownloadIndex = downloadGroupInfo.getRunningDownloadIndex();
        DownloadInfo finishedDownload = downloadGroupInfo.downloadsInfo[runningDownloadIndex];
        callback.onProgress(downloadGroupInfo, downloadGroupInfo.downloadsInfo[runningDownloadIndex], percentageTotalProgress, 100, false, true);
        NeuralNetworkApi.InitListener listener = new NeuralNetworkApi.InitListener() {
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
        };
        if(internalFolder == null && !finishedDownload.shouldUnzip()) {  //it is null only when we downloaded a file, not a .zip folder
            NeuralNetworkApi.testModelIntegrity(finishedDownload.getDestinationCompletePath(), listener);
        }else if(internalFolder != null && finishedDownload.shouldUnzip){  //if it is not null it means that we should test the models inside the top folder of an extracted .zip file
            String folderPath;
            if(!internalFolder.isEmpty()){
                folderPath = finishedDownload.getDestinationPath() + "/" + internalFolder + "/";
            }else{
                folderPath = finishedDownload.getDestinationPath();
            }
            NeuralNetworkApi.testFolderIntegrity(folderPath, listener);
        }else{
            listener.onError(new int[]{GENERAL_ERROR}, 0);
        }
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
            downloadGroupInfo.setAllDownloadCompleted(true);
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

    public int findFirstIncompletedDownload(){
        return DownloaderTools.findFirstIncompletedDownload(downloadGroupInfo);
    }

    private void unpackZip(String path, String zipname, UnpackListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream is;
                ZipInputStream zis;
                boolean success = true;
                Path zipPath = Paths.get(path + zipname);
                String topFolder = null;
                if(new File(zipPath.toString()).exists()) {  //if the zip file is not present, very likely it has been deleted after the unzip and there has been some error in updating the status, so we consider the unpack successful.
                    try {
                        String filename;
                        is = Files.newInputStream(zipPath);
                        zis = new ZipInputStream(new BufferedInputStream(is));
                        ZipEntry ze;
                        byte[] buffer = new byte[1024];
                        int count;

                        while ((ze = zis.getNextEntry()) != null) {
                            filename = ze.getName();

                            if (topFolder == null && filename.contains("/")) {
                                topFolder = filename.substring(0, filename.indexOf("/"));
                            }

                            // Need to create directories if not exists, or
                            // it will generate an Exception...
                            File destFile = new File(path + filename);
                            if (ze.isDirectory()) {
                                File fmd = new File(path + filename);
                                fmd.mkdirs();
                                zis.closeEntry();
                                continue;
                            } else if (destFile.getParentFile() != null) {
                                // in the case the zip file does not indicate its internal folders as entries but only the files with the internal folders as path,
                                // in this way we create recursively all the missing folders in the file path
                                destFile.getParentFile().mkdirs();
                            }

                            FileOutputStream fout = new FileOutputStream(path + filename);

                            while ((count = zis.read(buffer)) != -1) {
                                fout.write(buffer, 0, count);
                            }

                            fout.close();
                            zis.closeEntry();
                        }

                        zis.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        success = false;
                    }

                    if (success) {
                        try {
                            Files.delete(zipPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                            success = false;
                        }
                    }
                }

                if(topFolder == null) topFolder = "";

                if(success){
                    String finalTopFolder = topFolder;
                    mainHandler.post(() -> {
                        if(listener != null) listener.onSuccess(finalTopFolder);
                    });
                }else {
                    mainHandler.post(() -> {
                        if(listener != null) listener.onFailure();
                    });
                }
            }
        }).start();
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

    public abstract static class UnpackListener {
        public abstract void onSuccess(String topInternalFolder);
        public abstract void onFailure();
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

