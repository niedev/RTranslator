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
import nie.translator.rtranslator.downloader2.DownloadInfoExtended;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class MozillaManagerFragment  extends Fragment {
    private SettingsActivity activity;
    private Global global;
    private DownloadManager downloadManager;
    private RecyclerView recyclerView;
    private MozillaLanguagesAdapter adapter;

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
        activity = (SettingsActivity) requireActivity();
        global = (Global) activity.getApplication();

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
        ArrayList<Translator.MozillaLanguageInfo> mozillaLanguages = global.getMozillaLanguages(true);
        ArrayList<Translator.MozillaLanguageInfo> mozillaInstalledLanguages = new ArrayList<>();
        ArrayList<DownloadGroupInfo> downloads = downloadManager.getSavedDownloadStatus();
        for(DownloadGroupInfo download: downloads){
            if(download.downloadsInfo.length > 0 && download.downloadsInfo[0].getName().contains("Mozilla_") && download.downloadsInfo[0].isAllCompleted()){
                try {
                    String langCode = download.downloadsInfo[0].getName().split("-")[1].split(".zip")[0];
                    CustomLocale lang = new CustomLocale(langCode);
                    int index = mozillaLanguages.indexOf(lang);
                    if(index != -1) {
                        mozillaInstalledLanguages.add(mozillaLanguages.remove(index));
                    }

                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
        String downloadFolder = global.getFilesDir().getAbsolutePath()+"/Translation/Mozilla/";
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        float compressionRatio = 1.4F;
        ArrayList<ResourceManager> mozillaModelsInstalled = new ArrayList<>();
        for(Translator.MozillaLanguageInfo langInfo: mozillaInstalledLanguages){
            String fileName = "Mozilla_"+langInfo.lang.getLanguage()+".zip";
            mozillaModelsInstalled.add(
                    new ResourceManager(
                            new DownloadGroupInfo(new DownloadInfoExtended[]{new DownloadInfoExtended(fileName, baseUrl+fileName, downloadFolder, langInfo.sizeKb, false, true)}),
                            downloadManager,
                            langInfo.lang.getDisplayNameWithoutTTS(),
                            "",
                            (int) (((float) langInfo.sizeKb /1000)*compressionRatio),
                            ResourceManagerView.State.DOWNLOADED,
                            100
                    )
            );
        }
        ArrayList<ResourceManager> mozillaModelsAvailable = new ArrayList<>();
        for(Translator.MozillaLanguageInfo langInfo: mozillaLanguages){
            String fileName = "Mozilla_"+langInfo.lang.getLanguage()+".zip";
            mozillaModelsAvailable.add(
                    new ResourceManager(
                            new DownloadGroupInfo(new DownloadInfoExtended[]{new DownloadInfoExtended(fileName, baseUrl+fileName, downloadFolder, langInfo.sizeKb, false, true)}),
                            downloadManager,
                            langInfo.lang.getDisplayNameWithoutTTS(),
                            "",
                            (int) (((float) langInfo.sizeKb /1000)*compressionRatio),
                            ResourceManagerView.State.EMPTY,
                            0
                    )
            );
        }

        adapter = new MozillaLanguagesAdapter(mozillaModelsInstalled, mozillaModelsAvailable);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        downloadManager.subscribeAndResumeDownload(new DownloadManager.Callback() {
            @Override
            public void onServiceConnected() {
                ArrayList<DownloadGroupInfo> downloadsStatus = downloadManager.getDownloadsStatus();
                // we change the GUI based on current download status
                if(downloadsStatus != null){
                    for(DownloadGroupInfo download: downloadsStatus){
                        if (isMozillaDownload(download)) {
                            ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
                            //todo: improve detection methods of status
                            if (!download.isAllDownloadCompleted() && download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() <= 0) {
                                state = ResourceManagerView.State.EMPTY;
                            } else if (!download.isAllDownloadCompleted() && download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() > 0) {
                                state = ResourceManagerView.State.PAUSED;
                            } else if (download.getRunningDownloadIndex() != -1) {
                                state = ResourceManagerView.State.DOWNLOADING;
                            } else if (download.isAllDownloadCompleted()) {
                                state = ResourceManagerView.State.DOWNLOADED;
                            }
                            adapter.setStatus(download, state, download.getCurrentProgress());
                        }
                    }
                }
            }

            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                if (isMozillaDownload(downloadGroup)) {
                    adapter.setProgress(downloadGroup, totalProgress);
                }
            }

            @Override
            public void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {

            }

            @Override
            public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                if (isMozillaDownload(downloadGroup)) {
                    adapter.setState(downloadGroup, ResourceManagerView.State.DOWNLOADED, true);
                }
            }

            @Override
            public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {

            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        downloadManager.unsubscribe();
    }

    private boolean isMozillaDownload(DownloadGroupInfo download){
        try {
            return download.downloadsInfo[0].getName().contains("Mozilla_");
        }catch (Exception e){
            return false;
        }
    }
}
