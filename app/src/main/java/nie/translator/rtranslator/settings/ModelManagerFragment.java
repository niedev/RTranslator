package nie.translator.rtranslator.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.downloader2.DownloadInfoExtended;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;
import nie.translator.rtranslator.tools.gui.SegmentProgressBar;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import worker8.com.github.radiogroupplus.RadioGroupPlus;

public class ModelManagerFragment extends Fragment {
    private static int WHISPER_RAM_CONSUMPTION_MB = 900;
    private static int WHISPER_RAM_CONSUMPTION_REDUCED_MB = 500;
    private static int MOZILLA_RAM_CONSUMPTION_MB = 100;  //todo: measure it better
    private static int HY_RAM_CONSUMPTION_MB = 1900;
    private static int MADLAD_RAM_CONSUMPTION_MB = 1800;
    private static int TATOEBA_RAM_CONSUMPTION_MB = 5;    //todo: measure it better
    private static int DICT_RAM_CONSUMPTION_MB = 140;
    private SettingsActivity activity;
    private Global global;
    private DownloadManager downloadManager;
    private HashMap<String, ResourceManager> resourceManagers = new HashMap<>();
    // gui
    private RadioGroupPlus radioGroup;
    private ResourceManagerView hyManagerView;
    private ResourceManagerView madladManagerView;
    private ResourceManagerView tatoebaManagerView;
    private Button applyButton;
    private SegmentProgressBar barRam;
    private SwitchMaterial switchMozillaForVoiceModes;
    private SwitchMaterial switchTatoeba;
    private SwitchMaterial switchTranslationDict;
    private TextView textRamUsage;
    private TextView textRamUsage2;


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
        barRam = view.findViewById(R.id.barRam);
        switchMozillaForVoiceModes = view.findViewById(R.id.switchMozillaForVoiceModes);
        switchTatoeba = view.findViewById(R.id.switchTatoeba);
        switchTranslationDict = view.findViewById(R.id.switchTranslationDict);
        textRamUsage = view.findViewById(R.id.textRamUsage);
        textRamUsage2 = view.findViewById(R.id.textRamUsage2);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (SettingsActivity) requireActivity();
        global = (Global) activity.getApplication();

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

        // initialize GUI based on shared preferences
        // model selection initialization
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        switch (mode) {
            case Translator.MOZILLA:
                radioGroup.check(R.id.radioMozilla);
                break;
            case Translator.MADLAD:
            case Translator.MADLAD_CACHE:
                radioGroup.check(R.id.radioMadlad);
                break;
            case Translator.HY_MT:
                radioGroup.check(R.id.radioHY);
                break;
        }
        // switches initialization
        //todo: implement use mozilla for walkie talkie and conversation modes
        switchTatoeba.setChecked(global.isUseTatoeba());
        switchTranslationDict.setChecked(global.isUseTranslationDictionaries());

        // ram consumption bar redline and orangeLine initialization
        barRam.setRedLine(100F - (float) (global.getRamThreshold() * 100) / global.getTotalRamSize());
        barRam.setOrangeLine(80F);

        // ram consumption bar initialization
        setRamUsageSystem(global.getTotalRamSize() - global.getMaxAllocatableRAM());
        setRamUsageSpeechRecognition(WHISPER_RAM_CONSUMPTION_MB);
        updateRamUsageTranslation();
        updateRamUsageTranslationEnhancements();
        updateTextRamUsage();

        // initialize GUI listeners
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateRamUsageTranslation();
        });
        switchMozillaForVoiceModes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                //update the ram bar based on the new switch value
                updateRamUsageTranslation();
            }
        });
        switchTatoeba.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                updateRamUsageTranslationEnhancements();
            }
        });
        switchTranslationDict.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                updateRamUsageTranslationEnhancements();
            }
        });
        applyButton.setOnClickListener((v) -> {
            applySettings();
        });
        barRam.setSegmentProgressChangeListener(new SegmentProgressBar.SegmentChangeListener() {
            @Override
            public void onSegmentProgressChanged() {
                //update the RAM text
                updateTextRamUsage();
            }
        });
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
                                if (!download.isAllDownloadCompleted() && download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() <= 0) {
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
        downloadManager.unsubscribe();
    }

    private void applySettings() {

    }

    private void updateRamUsageTranslation(){
        int checkRadioId = radioGroup.getCheckedRadioButtonId();
        if(checkRadioId == R.id.radioMozilla) {
            setRamUsageTranslation(MOZILLA_RAM_CONSUMPTION_MB);
        } else if(checkRadioId == R.id.radioHY) {
            int ramUsed = HY_RAM_CONSUMPTION_MB;
            if (switchMozillaForVoiceModes.isChecked()) {
                ramUsed += MOZILLA_RAM_CONSUMPTION_MB;
            }
            setRamUsageTranslation(ramUsed);
        } else if(checkRadioId == R.id.radioMadlad) {
            int ramUsed = MADLAD_RAM_CONSUMPTION_MB;
            if (switchMozillaForVoiceModes.isChecked()) {
                ramUsed += MOZILLA_RAM_CONSUMPTION_MB;
            }
            setRamUsageTranslation(ramUsed);
        }
    }

    private void updateRamUsageTranslationEnhancements(){
        int ramConsumptionTranslationEnhancements = 0;
        if(switchTatoeba.isChecked()){
            ramConsumptionTranslationEnhancements += TATOEBA_RAM_CONSUMPTION_MB;
        }
        if(switchTranslationDict.isChecked()){
            ramConsumptionTranslationEnhancements += DICT_RAM_CONSUMPTION_MB;
        }
        setRamUsageTranslationEnhancements(ramConsumptionTranslationEnhancements);
    }

    private void setRamUsageSystem(long ramUsedMb){
        long totalRam = global.getTotalRamSize();
        long percentageUsed = (ramUsedMb * 100L)/totalRam;
        if(percentageUsed <= 0 && ramUsedMb > 0) percentageUsed = 1;
        barRam.setSegmentProgress(0, percentageUsed);
    }

    private void setRamUsageSpeechRecognition(long ramUsedMb){
        long totalRam = global.getTotalRamSize();
        long percentageUsed = (ramUsedMb * 100L)/totalRam;
        if(percentageUsed <= 0 && ramUsedMb > 0) percentageUsed = 1;
        barRam.setSegmentProgress(1, percentageUsed);
    }

    private void setRamUsageTranslation(long ramUsedMb){
        long totalRam = global.getTotalRamSize();
        long percentageUsed = (ramUsedMb * 100L)/totalRam;
        if(percentageUsed <= 0 && ramUsedMb > 0) percentageUsed = 1;
        barRam.setSegmentProgress(2, percentageUsed);
    }

    private void setRamUsageTranslationEnhancements(long ramUsedMb){
        long totalRam = global.getTotalRamSize();
        long percentageUsed = (ramUsedMb * 100L)/totalRam;
        if(percentageUsed <= 0 && ramUsedMb > 0) percentageUsed = 1;
        barRam.setSegmentProgress(3, percentageUsed);
    }

    private void updateTextRamUsage() {
        int totalRamGB = Math.round(((float) global.getTotalRamSize()) / 1000);
        float usedRAMPercentage = 0;
        for(SegmentProgressBar.Segment segment : barRam.getSegments()){
            usedRAMPercentage += segment.progress;
        }
        float usedRamGB = (usedRAMPercentage * totalRamGB) / 100;
        DecimalFormat df = new DecimalFormat("0.#");
        textRamUsage.setText(df.format(usedRamGB));
        textRamUsage2.setText("/" + totalRamGB + " GB");
        if(barRam.getRedLineValue() >= 0 && usedRAMPercentage > barRam.getRedLineValue()){
            textRamUsage.setTextColor(getResources().getColor(R.color.red));
        }else if(barRam.getOrangeLineValue() >= 0 && usedRAMPercentage > barRam.getOrangeLineValue()){
            textRamUsage.setTextColor(getResources().getColor(R.color.orange));
        }else{
            textRamUsage.setTextColor(textRamUsage2.getTextColors().getDefaultColor());
        }
    }
}
