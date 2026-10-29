package nie.translator.rtranslator.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.LoadingActivity;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.downloader.DownloadGroupInfo;
import nie.translator.rtranslator.downloader.DownloadInfo;
import nie.translator.rtranslator.downloader.DownloadManager;

public class DownloaderTools {
    public static boolean isMozillaDownload(DownloadGroupInfo download){
        try {
            return download.downloadsInfo[0].getName().contains("Mozilla_");
        }catch (Exception e){
            return false;
        }
    }

    /**
     * @return true if there is at least one Mozilla model downloaded
     */
    public static boolean checkMozillaModelsPresence(DownloadManager downloadManager){
        boolean found = false;
        for(DownloadGroupInfo downloadGroupInfo : downloadManager.getSavedDownloadStatus()){
            if(isMozillaDownload(downloadGroupInfo)){
                if(downloadGroupInfo.isAllDownloadCompleted()){
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * This method returns the index of the first incompleted download of the download group passed.
     *
     * @param downloadGroupInfo
     * @return the index of the first incompleted download, the value can go from 0 to DownloadGroupInfo.downloadsInfo.length.
     * This last case occurs only when all the downloads of the group are completed.
     */
    public static int findFirstIncompletedDownload(DownloadGroupInfo downloadGroupInfo){
        int index = 0;
        for(int i=0; i<downloadGroupInfo.downloadsInfo.length; i++){
            if(downloadGroupInfo.downloadsInfo[i].isAllCompleted()){
                index++;
            }
        }
        return index;
    }

    public static void updateDownloadGroupInfoPreference(Context context, DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
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

    public static void addDownloadGroupInfoPreference(Context context, DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
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

    public static void deleteDownloadGroupInfoPreference(Context context, DownloadGroupInfo downloadGroup){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
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

    public static void deleteDownloadedFiles(DownloadGroupInfo downloadGroup){
        // we delete the already downloaded files of this group of download
        for (int i = 0; i < downloadGroup.downloadsInfo.length; i++){
            File file = new File(downloadGroup.downloadsInfo[i].getDestinationCompletePath());
            if (file.exists()) {
                file.delete();
            }
            // in the case the download is an extracted download we delete the extracted (or partially extracted) internal folder and its content (instead of just a single file)
            String internalFolder = downloadGroup.downloadsInfo[i].getInternalFolder();
            if (internalFolder != null && !internalFolder.isEmpty()){
                String folderPath = downloadGroup.downloadsInfo[i].getDestinationPath() + "/" + internalFolder + "/";
                File folder = new File(folderPath);
                try {
                    FileUtils.deleteDirectory(folder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void restartInitialDownload(Global global, Activity activity){
        global.resetSpeechRecognizerError();
        //we set to false the integrityTested attribute of all the initial download infos saved in the shared preferences
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        String downloadsStatusString = sharedPreferences.getString("downloadsStatus", "");
        if (!downloadsStatusString.isEmpty()) {
            Gson gson = new Gson();
            ArrayList<DownloadGroupInfo> downloadGroupInfos = gson.fromJson(downloadsStatusString, new TypeToken<ArrayList<DownloadGroupInfo>>() {
            }.getType());
            if (downloadGroupInfos != null) {
                int index = downloadGroupInfos.indexOf(global.getInitialDownloadInfo());
                if(index != -1){
                    DownloadGroupInfo downloadGroupInfo = downloadGroupInfos.get(index);
                    for(DownloadInfo downloadInfo: downloadGroupInfo.downloadsInfo){
                        downloadInfo.setIntegrityTested(false);
                    }
                    updateDownloadGroupInfoPreference(global, downloadGroupInfo);
                }
            }
        }

        //we restart the download (only the corrupted files will be re-downloaded)
        global.setFirstStart(true);
        Intent intent = new Intent(activity, AccessActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        activity.finish();
    }
}
