package nie.translator.rtranslator.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;

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
}
