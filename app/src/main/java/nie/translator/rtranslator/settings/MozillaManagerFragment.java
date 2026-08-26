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

package nie.translator.rtranslator.settings;

import static nie.translator.rtranslator.tools.DownloaderTools.isMozillaDownload;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.DownloaderTools;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;

public class MozillaManagerFragment  extends Fragment {
    private Activity activity;
    private Global global;
    private DownloadManager downloadManager;
    private RecyclerView recyclerView;
    private MozillaLanguagesAdapter adapter;
    private DownloadManager.Callback downloadManagerCallback;
    private boolean guiStateRestored = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_mozilla_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recycler_view);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = requireActivity();
        global = (Global) activity.getApplication();

        downloadManagerCallback = new DownloadManager.Callback() {
            @Override
            public void onServiceConnected() {
                if(!guiStateRestored) {
                    ArrayList<DownloadGroupInfo> downloadsStatus = downloadManager.getDownloadsStatus();
                    // we change the GUI based on current download status
                    restoreGuiState(downloadsStatus);
                }
            }

            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                if (isMozillaDownload(downloadGroup)) {
                    adapter.setProgress(downloadGroup, totalProgress, unzipping, testingIntegrity);
                }
            }

            @Override
            public void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {

            }

            @Override
            public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                if (isMozillaDownload(downloadGroup)) {
                    adapter.setState(downloadGroup, ResourceManagerView.State.DOWNLOADED, false);
                }
            }

            @Override
            public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
                if(isMozillaDownload(downloadGroup)){
                    adapter.setError(downloadGroup, reason);
                }
            }
        };

        //initialize download manager
        downloadManager = new DownloadManager(global);
        //gui initialization
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
                dispatchChangeFinished(oldHolder, true);
                dispatchChangeFinished(newHolder, false);
                return true;
            }
        });
        //mRecyclerView.setItemAnimator(null);
        recyclerView.setHasFixedSize(true);

        // recyclerview initialization
        ArrayList<DownloadGroupInfo> downloads = downloadManager.getSavedDownloadStatus();
        ArrayList<Global.MozillaLanguageDownloadInfo> mozillaLanguageDownloadInfos = global.getMozillaLanguagesDownloadInfo(true);
        float compressionRatio = 1.4F;
        ArrayList<ResourceManager> mozillaModelsInstalled = new ArrayList<>();
        ArrayList<ResourceManager> mozillaModelsAvailable = new ArrayList<>();
        for(Global.MozillaLanguageDownloadInfo langDownloadInfo: mozillaLanguageDownloadInfos){
            int index = downloads.indexOf(langDownloadInfo.downloadGroupInfo);
            if(index != -1 && downloads.get(index).isAllDownloadCompleted()) {
                mozillaModelsInstalled.add(
                        new ResourceManager(
                                activity,
                                langDownloadInfo.downloadGroupInfo,
                                downloadManager,
                                langDownloadInfo.lang.getDisplayNameWithoutTTS(),
                                "",
                                (int) (((float) langDownloadInfo.downloadGroupInfo.downloadsInfo[0].getSize() / 1000) * compressionRatio),
                                ResourceManagerView.State.DOWNLOADED,
                                100,
                                false
                        )
                );
            } else {
                mozillaModelsAvailable.add(
                        new ResourceManager(
                                activity,
                                langDownloadInfo.downloadGroupInfo,
                                downloadManager,
                                langDownloadInfo.lang.getDisplayNameWithoutTTS(),
                                "",
                                (int) (((float) langDownloadInfo.downloadGroupInfo.downloadsInfo[0].getSize() / 1000) * compressionRatio),
                                ResourceManagerView.State.EMPTY,
                                0,
                                false
                        )
                );
            }
        }

        adapter = new MozillaLanguagesAdapter(mozillaModelsInstalled, mozillaModelsAvailable);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        boolean serviceStarted = downloadManager.subscribeAndResumeDownload(downloadManagerCallback);
        if(!serviceStarted){
            ArrayList<DownloadGroupInfo> downloadStatus = downloadManager.getSavedDownloadStatus();
            // we change the GUI based on current saved download status
            // normally we do this when the service starts, but if it won't start (paused download or other reasons)
            // we restore the GUI state based on the saved download state instead.
            restoreGuiState(downloadStatus);
        }
    }

    private void restoreGuiState(ArrayList<DownloadGroupInfo> downloadStatus){
        // we change the GUI based on current download status
        if(downloadStatus != null){
            guiStateRestored = true;
            for(DownloadGroupInfo download: downloadStatus){
                if (isMozillaDownload(download)) {
                    ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
                    int index = DownloaderTools.findFirstIncompletedDownload(download);
                    //todo: improve detection methods of status
                    if(download.isAllDownloadCompleted()){
                        state = ResourceManagerView.State.DOWNLOADED;
                    } else if (download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() <= 0) {
                        state = ResourceManagerView.State.EMPTY;
                    } else if (
                            (download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() > 0) ||
                            (download.getRunningDownloadIndex() != -1 && download.getRunningDownload().getCurrentError() != -1)  //in case of an error the status will be PAUSED
                    ) {
                        state = ResourceManagerView.State.PAUSED;
                    } else if (download.getRunningDownloadIndex() != -1) {
                        state = ResourceManagerView.State.DOWNLOADING;
                    }
                    if(index < download.downloadsInfo.length) {
                        adapter.setStatus(download, state, download.getCurrentProgress(), download.downloadsInfo[index].isUnzipping(), download.downloadsInfo[index].isTestingIntegrity());
                        adapter.setError(download, download.downloadsInfo[index].getCurrentError());
                    }else{
                        adapter.setStatus(download, state, download.getCurrentProgress(), false, false);
                    }
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        guiStateRestored = false;
        downloadManager.unsubscribe();
    }
}
