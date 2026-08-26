package nie.translator.rtranslator.settings;

import static nie.translator.rtranslator.tools.DownloaderTools.checkMozillaModelsPresence;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.util.Log;
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
import nie.translator.rtranslator.tools.DownloaderTools;
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
    private boolean guiStateRestored = false;


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
        restoreGuiPreferenceState();

        // initialize GUI listeners
        downloadManagerCallback = new DownloadManager.Callback() {
            @Override
            public void onServiceConnected() {
                if(!guiStateRestored) {
                    ArrayList<DownloadGroupInfo> downloadsStatus = downloadManager.getDownloadsStatus();
                    // we change the GUI based on current download status
                    restoreGuiDownloadState(downloadsStatus);
                }
            }

            @Override
            public void onProgress(DownloadGroupInfo downloadGroup, DownloadInfo download, int totalProgress, int progress, boolean unzipping, boolean testingIntegrity) {
                for(ResourceManager manager: resourceManagers.values()) {
                    if (downloadGroup.equals(manager.getDownloadInfo())) {
                        manager.setProgress(totalProgress, unzipping, testingIntegrity);
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
                        manager.setError(reason);
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
            if(checkedId == R.id.radioMozilla){  //eventual deactivation of switchMozillaForVoiceModes if Mozilla is the model selected
                switchMozillaForVoiceModes.setChecked(false);
                switchMozillaForVoiceModes.setEnabled(false);
            }else{
                switchMozillaForVoiceModes.setEnabled(true);
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
            restoreGuiDownloadState(downloadStatus);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        guiStateRestored = false;
        downloadManager.unsubscribe();
    }

    public void restoreGuiPreferenceState(){
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
        if(mode == Translator.MOZILLA){  //eventual deactivation of switchMozillaForVoiceModes if Mozilla is the model selected
            switchMozillaForVoiceModes.setChecked(false);
            switchMozillaForVoiceModes.setEnabled(false);
        }else{
            switchMozillaForVoiceModes.setEnabled(true);
        }

        // ram consumption bar redline and orangeLine initialization
        barRam.setRedLine(100F - (float) (global.getRamThreshold() * 100) / global.getTotalRamSize());
        barRam.setOrangeLine(80F);

        // ram consumption bar initialization
        setRamUsageSystem(global.getTotalRamSize() - global.getMaxAllocatableRAM());
        updateRamUsageSpeechRecognition();
        updateRamUsageTranslation();
        updateRamUsageTranslationEnhancements();
        updateTextRamUsage();
    }

    private void restoreGuiDownloadState(ArrayList<DownloadGroupInfo> downloadStatus){
        // we change the GUI based on current download status
        if(downloadStatus != null){
            guiStateRestored = true;
            for(DownloadGroupInfo download: downloadStatus){
                for(ResourceManager manager: resourceManagers.values()) {
                    if (download.equals(manager.getDownloadInfo())) {
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
                            manager.setStatus(state, download.getCurrentProgress(), download.downloadsInfo[index].isUnzipping(), download.downloadsInfo[index].isTestingIntegrity());
                            manager.setError(download.downloadsInfo[index].getCurrentError());
                        }else{
                            manager.setStatus(state, download.getCurrentProgress(), false, false);
                        }
                    }
                }
            }
        }
    }

    public boolean checkSettingsChanged(){
        // check translation models
        int checkRadioId = radioGroup.getCheckedRadioButtonId();
        if(checkRadioId == R.id.radioMozilla && global.getTranslationMode() != Translator.MOZILLA) {
            return true;
        } else if(checkRadioId == R.id.radioHY && global.getTranslationMode() != Translator.HY_MT) {
            return true;
        } else if(checkRadioId == R.id.radioMadlad && global.getTranslationMode() != Translator.MADLAD_CACHE) {
            return true;
        }
        // check Mozilla for voice modes
        if(global.isUseMozillaForVoiceTranslation() != switchMozillaForVoiceModes.isChecked()){
            return true;
        }
        // check Whisper RAM reduction
        if(global.isWhisperReducedRam() != switchWhisperReducedRam.isChecked()){
            return true;
        }
        // check Tatoeba
        if(global.isUseTatoeba() != switchTatoeba.isChecked()){
            return true;
        }
        // check translation dictionaries
        if(global.isUseTranslationDictionaries() != switchTranslationDict.isChecked()){
            return true;
        }
        return false;
    }

    public void applySettings() {
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_loading, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
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

        textView.setText(getString(R.string.applying_settings));
        applyTranslationStatus(new Translator.GeneralListener() {
            @Override
            public void onSuccess() {
                applyWhisperRamReduction(new Translator.GeneralListener() {
                    @Override
                    public void onSuccess() {
                        // before the end we will update the languages, this is already done in the case of:
                        // - change of translation model
                        // - enabling or disabling Mozilla for voice translation modes
                        // but it is always necessary in the case of:
                        // - download and delete of Mozilla models (if Mozilla is selected as translation model or voice translation modes model)
                        // Since this is a fairly light operation and I don't want to track every change to the Mozilla models, we will execute it at every applySettings
                        global.updateLanguagesAndResources(new Translator.GeneralListener() {
                            @Override
                            public void onSuccess() {
                                dialog.cancel();
                                if (activity instanceof SettingsActivity) {
                                    ((SettingsActivity) activity).startFragment(SettingsActivity.SETTINGS_FRAGMENT, null);
                                } else if (activity instanceof AccessActivity) {
                                    startRTranslator();
                                }
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
                ApplySettingsStage stage = ApplySettingsStage.values()[reasons[0]];
                int error = reasons[1];
                notifyApplyError(stage, error, value, editDialogLayout);
            }
        });
        // N.B. We execute the apply method and its checks even if the settings are unchanged because the user could have deleted a resource enabled in the preferences
        // (the global.setResource will ignore a non changed resource anyway, but this way the checks of the apply methods will be executed)
    }

    private void notifyApplyError(ApplySettingsStage stage, int error, long value, View dialogLayout){
        TextView textView = dialogLayout.findViewById(R.id.textView);
        CardView okButton = dialogLayout.findViewById(R.id.okButtonCard);
        android.widget.ProgressBar progressBar = dialogLayout.findViewById(R.id.progressBar);

        progressBar.setVisibility(View.INVISIBLE);
        okButton.setVisibility(View.VISIBLE);

        switch (error) {
            case ErrorCodes.ERROR_LOADING_MODEL: {
                //todo: manage better the error here (with an eventual fix, see loading activity management of this error for reference)
                textView.setText(getString(R.string.error_models_loading_short));
                break;
            }
            case ErrorCodes.NO_DOWNLOADED_RESOURCE: {
                switch (stage) {
                    case TRANSLATION_MODEL:
                        switch ((int) value) {
                            case Translator.MOZILLA:
                                textView.setText(getString(R.string.error_missing_mozilla));
                                break;
                            case Translator.HY_MT:
                                textView.setText(getString(R.string.error_missing_hy));
                                break;
                            case Translator.MADLAD_CACHE:
                                textView.setText(getString(R.string.error_missing_madlad));
                                break;
                        }
                        break;
                    case MOZILLA_FOR_VOICE:
                        textView.setText(getString(R.string.error_missing_mozilla_voice));
                    case TATOEBA:
                        textView.setText(getString(R.string.error_missing_tatoeba));
                        break;
                    default:
                        textView.setText(getString(R.string.error_missing_general));
                        break;
                }
                break;
            }
            default: {
                // general error dialog
                Log.e("error", "Unknown error apply: "+error);
                textView.setText(getString(R.string.error_apply_unknown));
                break;
            }
        }
    }

    private void applyTranslationStatus(Translator.GeneralListener listener){
        //check eventual translation model errors
        int checkRadioId = radioGroup.getCheckedRadioButtonId();
        int model = Translator.MOZILLA;
        if(checkRadioId == R.id.radioMozilla) {
            model = Translator.MOZILLA;
            if(!checkMozillaModelsPresence(downloadManager)){
                listener.onFailure(new int[]{ApplySettingsStage.TRANSLATION_MODEL.ordinal(), ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.MOZILLA);
                return;
            }
        } else if(checkRadioId == R.id.radioHY) {
            model = Translator.HY_MT;
            ResourceManager hyManager = resourceManagers.get("hyManager");
            if(hyManager == null || !downloadManager.checkDownloadCompleted(hyManager.getDownloadInfo())){
                listener.onFailure(new int[]{ApplySettingsStage.TRANSLATION_MODEL.ordinal(), ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.HY_MT);
                return;
            }
        } else if(checkRadioId == R.id.radioMadlad) {
            model = Translator.MADLAD_CACHE;
            ResourceManager madladManager = resourceManagers.get("madladManager");
            if(madladManager == null || !downloadManager.checkDownloadCompleted(madladManager.getDownloadInfo())){
                listener.onFailure(new int[]{ApplySettingsStage.TRANSLATION_MODEL.ordinal(), ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.MADLAD_CACHE);
                return;
            }
        }
        //check eventual Mozilla for voices mode errors
        if(radioGroup.getCheckedRadioButtonId() != R.id.radioMozilla && switchMozillaForVoiceModes.isChecked()){
            // if there is at least one Mozilla mode downloaded we apply the new setting
            if(!checkMozillaModelsPresence(downloadManager)) {
                listener.onFailure(new int[]{ApplySettingsStage.MOZILLA_FOR_VOICE.ordinal(), ErrorCodes.NO_DOWNLOADED_RESOURCE}, 0);
                return;
            }
        }
        //check eventual tatoeba errors
        if(switchTatoeba.isChecked()){
            ResourceManager tatoebaManager = resourceManagers.get("tatoebaManager");
            if(tatoebaManager == null || !downloadManager.checkDownloadCompleted(tatoebaManager.getDownloadInfo())){
                listener.onFailure(new int[]{ApplySettingsStage.TATOEBA.ordinal(), ErrorCodes.NO_DOWNLOADED_RESOURCE}, Translator.HY_MT);
            }
        }
        //apply translation status
        global.setTranslationStatus(model, switchMozillaForVoiceModes.isChecked(), switchTatoeba.isChecked(), switchTranslationDict.isChecked(), new Translator.GeneralListener() {
            @Override
            public void onSuccess() {
                listener.onSuccess();
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                listener.onFailure(new int[]{ApplySettingsStage.TRANSLATION_MODEL.ordinal(), reasons[0]}, value);
            }
        });
    }

    private void applyWhisperRamReduction(Translator.GeneralListener listener){
        global.setWhisperReducedRam(switchWhisperReducedRam.isChecked(), listener);
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
