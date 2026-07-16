package nie.translator.rtranslator.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.downloader2.DownloadInfoExtended;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class ModelManagerFragment extends Fragment {
    private SettingsActivity activity;
    private Global global;
    private DownloadManager downloadManager;
    private HashMap<String, ResourceManager> resourceManagers = new HashMap<>();
    // gui
    private RadioGroup radioGroup;
    private ResourceManagerView hyManagerView;
    private ResourceManagerView madladManagerView;
    private ResourceManagerView tatoebaManagerView;
    private Button applyButton;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_models_manager, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        radioGroup = view.findViewById(R.id.model_radios);
        hyManagerView = view.findViewById(R.id.modelHy);
        madladManagerView = view.findViewById(R.id.modelMadlad);
        tatoebaManagerView = view.findViewById(R.id.resourceTatoeba);
        applyButton = view.findViewById(R.id.buttonApply);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (SettingsActivity) requireActivity();
        global = (Global) activity.getApplication();

        //initialize check with the current mode
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        switch (mode) {
            case Translator.MOZILLA:
                radioGroup.check(R.id.mozilla_radio);
                break;
            case Translator.NLLB:
            case Translator.NLLB_CACHE:
                radioGroup.check(R.id.nllb_radio);
                break;
            case Translator.MADLAD:
            case Translator.MADLAD_CACHE:
                radioGroup.check(R.id.madlad_radio);
                break;
            case Translator.HY_MT:
                radioGroup.check(R.id.hy_radio);
                break;
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId){
                case R.id.mozilla_radio:

                    break;
                case R.id.nllb_radio:

                    break;
                case R.id.madlad_radio:

                    break;
                case R.id.hy_radio:

                    break;
            }
        });
        applyButton.setOnClickListener((v) -> {

        });

        //initialize download manager
        downloadManager = new DownloadManager(global);
        String downloadFolder = global.getFilesDir().getAbsolutePath();
        String baseUrl = "https://github.com/niedev/OnnxModelsEnhancer/releases/download/v1.0.0-beta/";
        resourceManagers.put("hyManager", new ResourceManager(new DownloadGroupInfo(new DownloadInfoExtended[]{
                new DownloadInfoExtended(
                        "HY-MT.zip",
                        baseUrl + "HY-MT.zip",
                        downloadFolder + "/Translation/",
                        1790000,
                        true,
                        true)
        }), hyManagerView, downloadManager));
        resourceManagers.put("madladManager", new ResourceManager(new DownloadGroupInfo(new DownloadInfoExtended[]{
                new DownloadInfoExtended(
                        "Madlad.zip",
                        baseUrl + "Madlad.zip",
                        downloadFolder + "/Translation/",
                        1380000,
                        true,
                        true)

        }), madladManagerView, downloadManager));
        resourceManagers.put("tatoebaManager", new ResourceManager(new DownloadGroupInfo(new DownloadInfoExtended[]{
                new DownloadInfoExtended(
                        "Tatoeba.zip",
                        baseUrl + "Tatoeba.zip",
                        downloadFolder + "/Translation/",
                        596000,
                        true,
                        true)

        }), tatoebaManagerView, downloadManager));
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
                        for(ResourceManager manager: resourceManagers.values()) {
                            if (download.equals(manager.getDownloadInfo())) {
                                ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
                                //todo: improve detection methods of status
                                if (!download.isAllDownloadCompleted() && download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() == 0) {
                                    state = ResourceManagerView.State.EMPTY;
                                } else if (!download.isAllDownloadCompleted() && download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() > 0) {
                                    state = ResourceManagerView.State.PAUSED;
                                } else if (download.getRunningDownloadIndex() != -1) {
                                    state = ResourceManagerView.State.DOWNLOADING;
                                } else if (download.isAllDownloadCompleted()) {
                                    state = ResourceManagerView.State.DOWNLOADED;
                                }
                                manager.setStatus(state, download.getCurrentProgress());
                            }
                        }
                    }
                }
            }

            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                for(ResourceManager manager: resourceManagers.values()) {
                    if (downloadGroup.equals(manager.getDownloadInfo())) {
                        manager.setProgress(totalProgress);
                    }
                }
            }

            @Override
            public void onCompleted(DownloadGroupInfo downloadGroup, DownloadInfo download) {

            }

            @Override
            public void onAllCompleted(DownloadGroupInfo downloadGroup) {
                for(ResourceManager manager: resourceManagers.values()) {
                    if (downloadGroup.equals(manager.getDownloadInfo())) {
                        manager.setState(ResourceManagerView.State.DOWNLOADED);
                    }
                }
            }

            @Override
            public void onError(DownloadGroupInfo downloadGroup, DownloadInfo download, int reason) {
                for(ResourceManager manager: resourceManagers.values()) {
                    if (downloadGroup.equals(manager.getDownloadInfo())) {
                        manager.setError();
                    }
                }
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}
