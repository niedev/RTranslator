/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nie.translator.rtranslator.access;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.Nullable;

public class Downloader{
    private DownloadManager downloadManager;
    private final Context context;

    public Downloader(Context context){
        this.context = context;
        downloadManager = context.getSystemService(DownloadManager.class);
    }

    public long downloadModel(String url, String filename){
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)).
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE).
                setDestinationInExternalFilesDir(context,null, filename).
                setTitle("RTranslator - "+filename);
        return downloadManager.enqueue(request);
    }

    public Uri getUriForDownloadedFile(long downloadId){
        return downloadManager.getUriForDownloadedFile(downloadId);
    }

    public Cursor query(long downloadId){
        return downloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
    }

    @Nullable
    public String getUrlFromDownload(long downloadId){
        Cursor result = query(downloadId);
        try {
            if (result == null) return null;
            int index = result.getColumnIndex(DownloadManager.COLUMN_URI);
            if(index > 0 && result.moveToFirst() && result.getCount() > 0) {
                return result.getString(index);
            }else{
                return null;
            }
        } finally {
            if (result != null) result.close();
        }
    }

    public int getRunningDownloadStatus(){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        long downloadId = sharedPreferences.getLong("currentDownloadId", -1);
        if(downloadId >= 0) {
            Cursor result = query(downloadId);
            try {
                if (result == null) return -1;
                int index = result.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (index > 0 && result.moveToFirst() && result.getCount() > 0) {
                    return result.getInt(index);
                }
            } finally {
                if (result != null) result.close();
            }
        }else if(downloadId == -3){
            return DownloadManager.STATUS_FAILED;
        }
        return -1;
    }

    public int findDownloadUrlIndex(long downloadId){
        String url = getUrlFromDownload(downloadId);
        if (url != null) {
            int urlIndex = -1;
            for (int i = 0; i < DownloadFragment.DOWNLOAD_URLS.length; i++) {
                if (DownloadFragment.DOWNLOAD_URLS[i].equals(url)) {
                    urlIndex = i;
                    break;
                }
            }
            return urlIndex;
        }
        return -1;
    }

    public int getDownloadProgress(int max){
        //here we return a value between 0 and max that represents the total prograss made so far with the download (for this we consider only the downloads, not the transfers)
        int totalSize = 0;
        for (int i=0; i<DownloadFragment.DOWNLOAD_SIZES.length; i++){
            totalSize = totalSize + DownloadFragment.DOWNLOAD_SIZES[i];
        }
        int lastDownloadSuccessIndex = -1;
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        int baseProgress = 0;
        String lastDownloadSuccess = sharedPreferences.getString("lastDownloadSuccess", "");
        if(lastDownloadSuccess.length()>0){
            for (int i = 0; i < DownloadFragment.DOWNLOAD_NAMES.length; i++) {
                if (DownloadFragment.DOWNLOAD_NAMES[i].equals(lastDownloadSuccess)) {
                    lastDownloadSuccessIndex = i;
                    break;
                }
            }
            if(lastDownloadSuccessIndex != -1){
                int baseSize = 0;
                for (int i=0; i<=lastDownloadSuccessIndex; i++){
                    baseSize = baseSize + DownloadFragment.DOWNLOAD_SIZES[i];
                }
                baseProgress = (baseSize*max)/totalSize;   //baseSize : totalSize = x : max  (where x is baseProgress)
            }
        }

        long downloadId = sharedPreferences.getLong("currentDownloadId", -1);
        int currentProgress = 0;
        if(downloadId >= 0) {
            int index = findDownloadUrlIndex(downloadId);
            if(index != -1 && index != lastDownloadSuccessIndex) {
                Cursor result = query(downloadId);
                try {
                    if (result == null) return baseProgress;
                    int indexBytesDownloaded = result.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    if (indexBytesDownloaded > 0 && result.moveToFirst() && result.getCount() > 0) {
                        int bytesDownloaded = result.getInt(indexBytesDownloaded);
                        int kbDownloaded = bytesDownloaded / 1000;
                        currentProgress = (kbDownloaded * max) / totalSize;
                    }
                } finally {
                    if (result != null) result.close();
                }
            }
        }

        return baseProgress + currentProgress;
    }

    public boolean cancelRunningDownload(){
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        long downloadId = sharedPreferences.getLong("currentDownloadId", -1);
        if(downloadId >= 0){
            int removedDownloads = downloadManager.remove(downloadId);
            if(removedDownloads == 1){
                return true;
            }
        }
        return false;
    }


}
