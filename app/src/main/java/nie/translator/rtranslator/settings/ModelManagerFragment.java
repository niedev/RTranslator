package nie.translator.rtranslator.settings;

import static nie.translator.rtranslator.tools.DownloaderTools.checkMozillaModelsPresence;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.LoadingActivity;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.access.AccessActivity;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;
import nie.translator.rtranslator.tools.gui.SegmentProgressBar;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;
import worker8.com.github.radiogroupplus.RadioGroupPlus;

public class ModelManagerFragment extends Fragment {
    private enum ApplySettingsStage{
        TRANSLATION_MODEL,
        MOZILLA_FOR_VOICE,
        WHISPER_RAM_REDUCTION,
        TATOEBA,
        TRANSLATION_DICT
    }
    private static int WHISPER_RAM_CONSUMPTION_MB = 900;
    private static int WHISPER_RAM_CONSUMPTION_REDUCED_MB = 500;
    private static int MOZILLA_RAM_CONSUMPTION_MB = 100;  //todo: measure it better
    private static int HY_RAM_CONSUMPTION_MB = 1900;
    private static int MADLAD_RAM_CONSUMPTION_MB = 1800;
    private static int TATOEBA_RAM_CONSUMPTION_MB = 5;    //todo: measure it better
    private static int DICT_RAM_CONSUMPTION_MB = 140;
    private Activity activity;
    private Global global;
    private DownloadManager downloadManager;
    private HashMap<String, ResourceManager> resourceManagers = new HashMap<>();
    private DownloadManager.Callback downloadManagerCallback;
    // gui
    private RadioGroupPlus radioGroup;
    private ResourceManagerView hyManagerView;
    private ResourceManagerView madladManagerView;
    private ResourceManagerView tatoebaManagerView;
    private Button applyButton;
    private SegmentProgressBar barRam;
    private SwitchMaterial switchMozillaForVoiceModes;
    private SwitchMaterial switchWhisperReducedRam;
    private SwitchMaterial switchTatoeba;
    private SwitchMaterial switchTranslationDict;
    private TextView textRamUsage;
    private TextView textRamUsage2;
    private ImageView arrowMozilla;


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
        switchWhisperReducedRam = view.findViewById(R.id.switchWhisperReducedRam);
        switchTatoeba = view.findViewById(R.id.switchTatoeba);
        switchTranslationDict = view.findViewById(R.id.switchTranslationDict);
        textRamUsage = view.findViewById(R.id.textRamUsage);
        textRamUsage2 = view.findViewById(R.id.textRamUsage2);
        arrowMozilla = view.findViewById(R.id.arrowMozilla);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = requireActivity();
        global = (Global) activity.getApplication();

        //initialize download manager
        downloadManager = new DownloadManager(global);
        resourceManagers.put("hyManager", new ResourceManager(activity, global.getHyMtDownloadInfo(), hyManagerView, downloadManager));
        resourceManagers.put("madladManager", new ResourceManager(activity, global.getMadladDownloadInfo(), madladManagerView, downloadManager));
        resourceManagers.put("tatoebaManager", new ResourceManager(activity, global.getTatoebaDownloadInfo(), tatoebaManagerView, downloadManager));

        // initialize GUI based on shared preferences
        // model selection initialization
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        switch (mode) {
            case Translator.MOZILLA:
                radioGroup.check(R.id.radioMozilla);
                switchMozillaForVoiceModes.setActivated(false);
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
        switchMozillaForVoiceModes.setChecked(global.isUseMozillaForVoiceTranslation());
        switchWhisperReducedRam.setChecked(global.isWhisperReducedRam());
        switchTatoeba.setChecked(global.isUseTatoeba());
        switchTranslationDict.setChecked(global.isUseTranslationDictionaries());

        // ram consumption bar redline and orangeLine initialization
        barRam.setRedLine(100F - (float) (global.getRamThreshold() * 100) / global.getTotalRamSize());
        barRam.setOrangeLine(80F);

        // ram consumption bar initialization
        setRamUsageSystem(global.getTotalRamSize() - global.getMaxAllocatableRAM());
        updateRamUsageSpeechRecognition();
        updateRamUsageTranslation();
        updateRamUsageTranslationEnhancements();
        updateTextRamUsage();

        // initialize GUI listeners
        downloadManagerCallback = new DownloadManager.Callback() {
            @Override
            public void onServiceConnected() {
                ArrayList<DownloadGroupInfo> downloadsStatus = downloadManager.getDownloadsStatus();
                // we change the GUI based on current download status
                restoreGuiState(downloadsStatus);
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
        };
        arrowMozilla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(activity instanceof SettingsActivity) {
                    ((SettingsActivity) activity).startFragment(SettingsActivity.MOZILLA_MANAGER, null);
                }else if(activity instanceof AccessActivity){
                    ((AccessActivity) activity).startFragment(AccessActivity.MOZILLA_MANAGER, null);
                }
            }
        });
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if(checkedId == R.id.radioMozilla){
                switchMozillaForVoiceModes.setActivated(false);
            }else{
                switchMozillaForVoiceModes.setActivated(true);
            }
            updateRamUsageTranslation();
        });
        switchMozillaForVoiceModes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                //update the ram bar based on the new switch value
                updateRamUsageTranslation();
            }
        });
        switchWhisperReducedRam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(@NonNull CompoundButton buttonView, boolean isChecked) {
                //update the ram bar based on the new switch value
                updateRamUsageSpeechRecognition();
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
        boolean serviceStarted = downloadManager.subscribeAndResumeDownload(downloadManagerCallback);
        if(!serviceStarted){
            ArrayList<DownloadGroupInfo> downloadStatus = downloadManager.getSavedDownloadStatus();
            // we change the GUI based on current saved download status
            // normally we do this when the service starts, but if it won't start (paused download or other reasons)
            // we restore the GUI state based on the saved download state instead.
            restoreGuiState(downloadStatus);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        downloadManager.unsubscribe();
    }

    public void restoreGuiState(ArrayList<DownloadGroupInfo> downloadStatus){
        // we change the GUI based on current download status
        if(downloadStatus != null){
            for(DownloadGroupInfo download: downloadStatus){
                for(ResourceManager manager: resourceManagers.values()) {
                    if (download.equals(manager.getDownloadInfo())) {
                        ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
                        //todo: improve detection methods of status
                        if(download.isAllDownloadCompleted()){
                            state = ResourceManagerView.State.DOWNLOADED;
                        } else if (download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() <= 0) {
                            state = ResourceManagerView.State.EMPTY;
                        } else if (download.getRunningDownloadIndex() == -1 && download.getCurrentProgress() > 0) {
                            state = ResourceManagerView.State.PAUSED;
                        } else if (download.getRunningDownloadIndex() != -1) {
                            state = ResourceManagerView.State.DOWNLOADING;
                        }
                        manager.setStatus(state, download.getCurrentProgress());
                    }
                }
            }
        }
    }

    private void applySettings() {
        //todo: convert texts of gui in resource and translate those
        //todo: create and manage the GUI of the Dialog, showing progress and eventual errors

        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_loading, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {

            }
        });
        dialog.show();

        TextView textView = editDialogLayout.findViewById(R.id.textView);
        CardView okButton = editDialogLayout.findViewById(R.id.okButtonCard);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        textView.setText("Applying new settings...");
        applyTranslationModel(new Translator.GeneralListener() {
            @Override
            public void onSuccess() {
                applyMozillaForVoiceModes(new Translator.GeneralListener() {
                    @Override
                    public void onSuccess() {
                        applyWhisperRamReduction(new Translator.GeneralListener() {
                            @Override
                            public void onSuccess() {
                                applyTatoeba(new Translator.GeneralListener() {
                                    @Override
                                    public void onSuccess() {
                                        applyTranslationDict(new Translator.GeneralListener() {
                                            @Override
                                            public void onSuccess() {
                                                dialog.cancel();
                                                if(activity instanceof SettingsActivity) {
                                                    ((SettingsActivity) activity).startFragment(SettingsActivity.SETTINGS_FRAGMENT, null);
                                                } else if(activity instanceof AccessActivity){
                                                    startRTranslator();
                                                }
                                            }

                                            @Override
                                            public void onFailure(int[] reasons, long value) {
                                                int error = reasons[0];
                                                notifyApplyError(ApplySettingsStage.TRANSLATION_DICT, error, value, editDialogLayout);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onFailure(int[] reasons, long value) {
                                        int error = reasons[0];
                                        notifyApplyError(ApplySettingsStage.TATOEBA, error, value, editDialogLayout);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                int error = reasons[0];
                                notifyApplyError(ApplySettingsStage.WHISPER_RAM_REDUCTION, error, value, editDialogLayout);
                            }
                        });
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        int error = reasons[0];
                        notifyApplyError(ApplySettingsStage.MOZILLA_FOR_VOICE, error, value, editDialogLayout);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                int error = reasons[0];
                notifyApplyError(ApplySettingsStage.TRANSLATION_MODEL, error, value, editDialogLayout);
            }
        });
    }

    private void notifyApplyError(ApplySettingsStage stage, int error, long value, View dialogLayout){
        //todo: convert texts of gui in resource and translate those
        TextView textView = dialogLayout.findViewById(R.id.textView);
        CardView okButton = dialogLayout.findViewById(R.id.okButtonCard);
        android.widget.ProgressBar progressBar = dialogLayout.findViewById(R.id.progressBar);

        progressBar.setVisibility(View.INVISIBLE);
        okButton.setVisibility(View.VISIBLE);

        switch (error) {
            case ErrorCodes.ERROR_LOADING_MODEL: {
                //todo: manage better the error here (with an eventual fix, see loading activity management of this error for reference)
                textView.setText("Error loading the new model");
                break;
            }
            case ErrorCodes.NO_DOWNLOADED_RESOURCE: {
                switch (stage) {
                    case TRANSLATION_MODEL:
                        switch ((int) value) {
                            case Translator.MOZILLA:
                                textView.setText("You selected Mozilla as a translation model, but you haven't downloaded any of its language yet. Please download at least one language model before selecting Mozilla.");
                                break;
                            case Translator.HY_MT:
                                textView.setText("You selected HY-MT as a translation model, but it hasn't been downloaded yet. Please download the model before selecting it.");
                                break;
                            case Translator.MADLAD_CACHE:
                                textView.setText("You selected Madlad as a translation model, but it hasn't been downloaded yet. Please download the model before selecting it.");
                                break;
                        }
                        break;
                    case MOZILLA_FOR_VOICE:
                        textView.setText("You enabled the use of Mozilla models for WalkieTalkie and Conversation modes, but there are no downloaded Mozilla models. Please download them before enabling this option.");
                    case TATOEBA:
                        textView.setText("You enabled Tatoeba but it hasn't been downloaded yet. Please download Tatoeba before enabling it.");
                        break;
                    default:
                        textView.setText("Missing resource download");
                        break;
                }
                break;
            }
            default: {
                // general error dialog
                textView.setText("Unknown error, please retry.");
                break;
            }
        }
    }

    private void applyTranslationModel(Translator.GeneralListener listener){
        int checkRadioId = radioGroup.getCheckedRadioButtonId();
        if(checkRadioId == R.id.radioMozilla && global.getTranslationMode() != Translator.MOZILLA) {
            if(checkMozillaModelsPresence(downloadManager)){
                global.setTranslationMode(Translator.MOZILLA, listener);
            } else {
                listener.onFailure(new int[]{ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.MOZILLA);
            }
        } else if(checkRadioId == R.id.radioHY && global.getTranslationMode() != Translator.HY_MT) {
            ResourceManager hyManager = resourceManagers.get("hyManager");
            if(hyManager != null && downloadManager.checkDownloadCompleted(hyManager.getDownloadInfo())){
                global.setTranslationMode(Translator.HY_MT, listener);
            } else {
                listener.onFailure(new int[]{ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.HY_MT);
            }
        } else if(checkRadioId == R.id.radioMadlad && global.getTranslationMode() != Translator.MADLAD_CACHE) {
            boolean found = false;
            ResourceManager madladManager = resourceManagers.get("madladManager");
            if(madladManager != null && downloadManager.checkDownloadCompleted(madladManager.getDownloadInfo())){
                global.setTranslationMode(Translator.MADLAD_CACHE, listener);
            } else {
                listener.onFailure(new int[]{ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.MADLAD_CACHE);
            }
        }else {
            listener.onSuccess();
        }
    }

    private void applyMozillaForVoiceModes(Translator.GeneralListener listener){
        if(radioGroup.getCheckedRadioButtonId() != R.id.radioMozilla && switchMozillaForVoiceModes.isChecked()){
            // if there is at least one Mozilla mode downloaded we apply the new
            if(checkMozillaModelsPresence(downloadManager)) {
                global.setUseMozillaForVoiceTranslation(true, listener);
            }else{
                listener.onFailure(new int[]{ErrorCodes.NO_DOWNLOADED_RESOURCE}, 0);
            }
        }else{
            // if Mozilla is the selected model or the switch is false we save this preference as false without checks
            global.setUseMozillaForVoiceTranslation(false, listener);
        }

    }

    private void applyWhisperRamReduction(Translator.GeneralListener listener){
        global.setWhisperReducedRam(switchWhisperReducedRam.isChecked(), listener);
    }

    private void applyTatoeba(Translator.GeneralListener listener){
        ResourceManager tatoebaManager = resourceManagers.get("tatoebaManager");
        if(tatoebaManager != null && downloadManager.checkDownloadCompleted(tatoebaManager.getDownloadInfo())){
            global.setUseTatoeba(switchTatoeba.isChecked());
            listener.onSuccess();
        } else {
            listener.onFailure(new int[]{ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.HY_MT);
        }
    }

    private void applyTranslationDict(Translator.GeneralListener listener){
        global.setUseTranslationDictionaries(switchTranslationDict.isChecked());
        listener.onSuccess();
    }

    private void updateRamUsageSpeechRecognition() {
        if(switchWhisperReducedRam.isChecked()) {
            setRamUsageSpeechRecognition(WHISPER_RAM_CONSUMPTION_REDUCED_MB);
        }else{
            setRamUsageSpeechRecognition(WHISPER_RAM_CONSUMPTION_MB);
        }
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

    private void startRTranslator(){
        if (activity != null) {
            //modification of the firstStart
            global.setFirstStart(false);
            //start activity
            Intent intent = new Intent(activity, LoadingActivity.class);
            intent.putExtra("activity", "download");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            activity.finish();
        }
    }
}
