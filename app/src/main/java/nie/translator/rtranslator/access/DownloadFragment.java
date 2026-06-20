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

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.text.DecimalFormat;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.LoadingActivity;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.FileTools;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;

public class DownloadFragment extends Fragment {
    // Each model array: index 0 = primary (GitHub), 1+ = fallbacks (proxies/mirrors).
    // To add a HuggingFace mirror, add the URL to the desired model's array in makeMirrors().
    private static final String GITHUB_BASE = "https://github.com/niedev/RTranslator/releases/download/2.0.0/";
    private static final String PROXY_BASE = "https://gh-proxy.org/https://github.com/niedev/RTranslator/releases/download/2.0.0/";

    private static String[] makeMirrors(String filename) {
        return new String[]{GITHUB_BASE + filename, PROXY_BASE + filename};
    }

    public static final String[][] DOWNLOAD_URLS = {
            makeMirrors("NLLB_cache_initializer.onnx"),
            makeMirrors("NLLB_decoder.onnx"),
            makeMirrors("NLLB_embed_and_lm_head.onnx"),
            makeMirrors("NLLB_encoder.onnx"),
            makeMirrors("Whisper_cache_initializer.onnx"),
            makeMirrors("Whisper_cache_initializer_batch.onnx"),
            makeMirrors("Whisper_decoder.onnx"),
            makeMirrors("Whisper_detokenizer.onnx"),
            makeMirrors("Whisper_encoder.onnx"),
            makeMirrors("Whisper_initializer.onnx")
    };

    public static String getPrimaryUrl(int index) {
        return DOWNLOAD_URLS[index][0];
    }

    public static String getFallbackUrl(int index) {
        if (DOWNLOAD_URLS[index].length > 1) {
            return DOWNLOAD_URLS[index][1];
        }
        return null;
    }

    public static final String[] DOWNLOAD_NAMES = {
            "NLLB_cache_initializer.onnx",
            "NLLB_decoder.onnx",
            "NLLB_embed_and_lm_head.onnx",
            "NLLB_encoder.onnx",
            "Whisper_cache_initializer.onnx",
            "Whisper_cache_initializer_batch.onnx",
            "Whisper_decoder.onnx",
            "Whisper_detokenizer.onnx",
            "Whisper_encoder.onnx",
            "Whisper_initializer.onnx"
    };
    public static final int[] DOWNLOAD_SIZES = {   //the size of the models in Kb (they are not exact, because this is used only for show the progress in progressbar)
            24000,
            171000,
            500000,
            254000,
            14000,
            14000,
            173000,
            461,
            88000,
            69
    };
    private static final long INTERVAL_TIME_FOR_GUI_UPDATES_MS = 100;  //500
    private AccessActivity activity;
    private Global global;
    private Downloader downloader;
    private Thread guiUpdater;
    private android.os.Handler mainHandler;   // handler that can be used to post to the main thread

    //Gui components
    private ImageButton retryButton;
    private ImageButton pauseButton;
    private TextView downloadErrorText;
    private TextView transferErrorText;
    private TextView storageWarningText;
    private LinearProgressIndicator progressBar;
    private TextView progressDescriptionText;
    private TextView progressNumbersText;
    private TextView manualDownloadText;

    public DownloadFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        retryButton = view.findViewById(R.id.retryButton);
        downloadErrorText = view.findViewById(R.id.text_error_download);
        transferErrorText = view.findViewById(R.id.text_error_transfer);
        storageWarningText = view.findViewById(R.id.text_error_storage);
        progressBar = view.findViewById(R.id.progressBar);
        progressDescriptionText = view.findViewById(R.id.progress_description);
        pauseButton = view.findViewById(R.id.pauseButton);
        pauseButton.setTag("iconCancel");
        progressNumbersText = view.findViewById(R.id.progress_numbers);
        manualDownloadText = view.findViewById(R.id.manualDownloadText);
        manualDownloadText.setOnClickListener(v -> showManualDownloadDialog());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (AccessActivity) requireActivity();
        global = (Global) activity.getApplication();
        mainHandler = new android.os.Handler(Looper.getMainLooper());
        downloader = new Downloader(global);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(downloadErrorText.getVisibility() == View.VISIBLE){  //that means that we should restart the download
                    downloadErrorText.setVisibility(View.GONE);
                    transferErrorText.setVisibility(View.GONE);
                    retryButton.setVisibility(View.GONE);
                    retryCurrentDownload();
                }else if(transferErrorText.getVisibility() == View.VISIBLE){
                    downloadErrorText.setVisibility(View.GONE);
                    transferErrorText.setVisibility(View.GONE);
                    retryButton.setVisibility(View.GONE);
                    retryCurrentTransfer();
                }
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pauseButton.getTag().equals("iconCancel")){
                    //we pause the download
                    boolean success = downloader.cancelRunningDownload();
                    if(success) {
                        //we change the icon and tag
                        pauseButton.setImageResource(R.drawable.play_icon);
                        //pauseButton.setImageDrawable(global.getResources().getDrawable(R.drawable.play_icon, null));
                        pauseButton.setTag("iconPlay");
                    }
                }else{
                    int runningDownloadStatus = downloader.getRunningDownloadStatus();
                    if(runningDownloadStatus == -1 || runningDownloadStatus == DownloadManager.STATUS_FAILED || runningDownloadStatus == DownloadManager.STATUS_PAUSED) {
                        //we resume the download
                        retryCurrentDownload();
                    }
                    //we change the icon and tag
                    pauseButton.setImageResource(R.drawable.cancel_icon);
                    //pauseButton.setImageDrawable(global.getResources().getDrawable(R.drawable.cancel_icon, null));
                    pauseButton.setTag("iconCancel");
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if(global != null) {
            //if the internal or external free memory are low, we show a warning
            float requiredSize = 0;
            for (int i=0; i<DownloadFragment.DOWNLOAD_SIZES.length; i++){
                requiredSize = requiredSize + DownloadFragment.DOWNLOAD_SIZES[i];
            }
            requiredSize = requiredSize / 1000;   //we convert from Kb to Mb
            requiredSize = requiredSize + 800;   //we add a margin (because the transfer process requires more space)
            long ex = global.getAvailableExternalMemorySize();
            long in = global.getAvailableInternalMemorySize();
            if(global.getAvailableExternalMemorySize() < requiredSize || global.getAvailableInternalMemorySize() < requiredSize){
                //we show the warning
                storageWarningText.setVisibility(View.VISIBLE);
            }

            updateProgress();

            guiUpdater = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            if(downloadErrorText.getVisibility() == View.GONE && transferErrorText.getVisibility() == View.GONE && retryButton.getVisibility() == View.GONE && getContext() != null) {
                                boolean error = checkDownloadOrTransferErrors(false); //we check and show eventual errors in the download or transfer of the models
                                if (!error) {
                                    //we update the Gui according to the downloads status
                                    mainHandler.post(() -> {
                                        if(getContext() != null) {
                                            updateProgress();
                                            //we update the progressDescriptionText
                                            SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
                                            if(NeuralNetworkApi.isVerifying){
                                                String lastDownloadSuccess = sharedPreferences.getString("lastDownloadSuccess", "");
                                                String verifyingModelName = null;
                                                if(lastDownloadSuccess.isEmpty()){  //it means that we are checking integrity of the first download
                                                    verifyingModelName = DOWNLOAD_NAMES[0];
                                                }else{
                                                    int nameIndex = -1;
                                                    for (int i = 0; i < DownloadFragment.DOWNLOAD_NAMES.length; i++) {
                                                        if (DownloadFragment.DOWNLOAD_NAMES[i].equals(lastDownloadSuccess)) {
                                                            nameIndex = i;
                                                            break;
                                                        }
                                                    }
                                                    if(nameIndex >= 0){
                                                        verifyingModelName = DOWNLOAD_NAMES[nameIndex+1];
                                                    }
                                                }
                                                if(verifyingModelName != null){
                                                    progressDescriptionText.setText(getString(R.string.description_integrity_check, verifyingModelName));
                                                }
                                            }else {
                                                int indexOfRunningTransfer = getIndexOfRunningTransfer();
                                                if (indexOfRunningTransfer != -1) {
                                                    String downloadName = DOWNLOAD_NAMES[indexOfRunningTransfer];
                                                    downloadName = downloadName.replace(".onnx", "");
                                                    downloadName = downloadName.replace("_", " ");
                                                    progressDescriptionText.setText(getString(R.string.description_transfer, downloadName));
                                                } else {
                                                    long currentDownloadId = sharedPreferences.getLong("currentDownloadId", -1);
                                                    if (currentDownloadId >= 0) {
                                                        int indexOfRunningDownload = downloader.findDownloadUrlIndex(currentDownloadId);
                                                        if (indexOfRunningDownload >= 0) {
                                                            String downloadName = DOWNLOAD_NAMES[indexOfRunningDownload];
                                                            downloadName = downloadName.replace(".onnx", "");
                                                            downloadName = downloadName.replace("_", " ");
                                                            progressDescriptionText.setText(getString(R.string.description_download, downloadName));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    });
                                }
                            }

                            Thread.sleep(INTERVAL_TIME_FOR_GUI_UPDATES_MS);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
            long currentDownloadId = sharedPreferences.getLong("currentDownloadId", -1);

            if(currentDownloadId == -1){  //if we not yet started any download
                new Thread(() -> DownloadReceiver.internalCheckAndStartNextDownload(global, downloader, -1)).start();
                guiUpdater.start();

            }else if(currentDownloadId == -2) {  //if the downloads are all completed
                startRTranslator();
            }else{
                checkDownloadOrTransferErrors(true);
                guiUpdater.start();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(!guiUpdater.isInterrupted()) {
            guiUpdater.interrupt();
            //we cancel the storage warning (in this way when the user reopens the app the warning is shown only if the storage is still low)
            storageWarningText.setVisibility(View.GONE);
        }
    }

    private boolean checkDownloadOrTransferErrors(boolean changePauseButtonIcon){
        //we check and show errors for eventual download failure
        int downloadStatus = downloader.getRunningDownloadStatus();
        if(downloadStatus == DownloadManager.STATUS_FAILED){
            showDownloadError();
            return true;
        }
        if(downloadStatus == -1){
            if(changePauseButtonIcon) {
                mainHandler.post(() -> {
                    //we change the icon and tag of the pauseButton
                    pauseButton.setImageResource(R.drawable.play_icon);
                    pauseButton.setTag("iconPlay");
                });
            }
            return false;  //return true before
        }
        //we check and show an error for eventual transfer failure
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        String lastDownloadSuccess = sharedPreferences.getString("lastDownloadSuccess", "");
        String lastTransferSuccess = sharedPreferences.getString("lastTransferSuccess", "");
        String lastTransferFailure = sharedPreferences.getString("lastTransferFailure", "");
        if(!lastDownloadSuccess.isEmpty() && !lastDownloadSuccess.equals(lastTransferSuccess) && lastDownloadSuccess.equals(lastTransferFailure)){
            showTransferError();
            return true;
        }
        return false;
    }

    private void showDownloadError(){
        //we show the download error and the retry button
        mainHandler.post(() -> {
            downloadErrorText.setVisibility(View.VISIBLE);
            transferErrorText.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
            //we change the icon and tag of the pauseButton
            pauseButton.setImageResource(R.drawable.play_icon);
            pauseButton.setTag("iconPlay");
        });
    }

    private void showTransferError(){
        //we show the transfer error and the retry button
        mainHandler.post(() -> {
            downloadErrorText.setVisibility(View.GONE);
            transferErrorText.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        });
    }

    private void updateProgress(){
        //we update the progressbar
        int progress = downloader.getDownloadProgress(progressBar.getMax());
        progressBar.setProgress(progress, true);
        //we update the progressNumbersText
        float totalSize = 0;
        for (int i=0; i<DownloadFragment.DOWNLOAD_SIZES.length; i++){
            totalSize = totalSize + DownloadFragment.DOWNLOAD_SIZES[i];
        }
        totalSize = totalSize/1000000;   //we convert from Kb to Gb
        float downloadedGb = progress*totalSize/progressBar.getMax();    //progress : progressBar.getMax() = x : totalSize   (where x is downloadedGb)
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        progressNumbersText.setText(decimalFormat.format(downloadedGb)+" / "+decimalFormat.format(totalSize)+" GB");
    }

    private int getIndexOfRunningTransfer(){
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        String lastDownloadSuccess = sharedPreferences.getString("lastDownloadSuccess", "");
        String lastTransferSuccess = sharedPreferences.getString("lastTransferSuccess", "");
        String lastTransferFailure = sharedPreferences.getString("lastTransferFailure", "");
        if(!lastDownloadSuccess.isEmpty() && !lastDownloadSuccess.equals(lastTransferSuccess) && !lastDownloadSuccess.equals(lastTransferFailure)){
            int nameIndex = -1;
            for (int i = 0; i < DownloadFragment.DOWNLOAD_NAMES.length; i++) {
                if (DownloadFragment.DOWNLOAD_NAMES[i].equals(lastDownloadSuccess)) {
                    nameIndex = i;
                    break;
                }
            }
            return nameIndex;
        }
        return -1;
    }

    private void retryCurrentDownload(){
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        long currentDownloadId = sharedPreferences.getLong("currentDownloadId", -1);
        int urlIndex = downloader.findDownloadUrlIndex(currentDownloadId);
        if(urlIndex >= 0){
            if(downloader.getRunningDownloadStatus() != DownloadManager.STATUS_RUNNING) {
                //we restart the download
                long downloadId = downloader.downloadModel(getPrimaryUrl(urlIndex), DOWNLOAD_NAMES[urlIndex]);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong("currentDownloadId", downloadId);
                editor.apply();
            }
        }else{
            String lastDownloadSuccess = sharedPreferences.getString("lastDownloadSuccess", "");
            if(!lastDownloadSuccess.isEmpty()){
                //we find the index of the lastDownloadSuccess
                int nameIndex = -1;
                for (int i = 0; i < DownloadFragment.DOWNLOAD_NAMES.length; i++) {
                    if (DownloadFragment.DOWNLOAD_NAMES[i].equals(lastDownloadSuccess)) {
                        nameIndex = i;
                        break;
                    }
                }
                if(nameIndex != -1 && (nameIndex+1) < DOWNLOAD_URLS.length) {
                    //we restart the download
                    long downloadId = downloader.downloadModel(getPrimaryUrl(nameIndex+1), DOWNLOAD_NAMES[nameIndex+1]);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong("currentDownloadId", downloadId);
                    editor.apply();
                }
            }else{
                //we restart the first download
                long downloadId = downloader.downloadModel(getPrimaryUrl(0), DOWNLOAD_NAMES[0]);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong("currentDownloadId", downloadId);
                editor.apply();
            }
        }
    }

    private void retryCurrentTransfer(){
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        String lastTransferFailure = sharedPreferences.getString("lastTransferFailure", "");
        if(lastTransferFailure.length()>0){
            //we find the index of the lastDownloadSuccess
            int nameIndex = -1;
            for (int i = 0; i < DownloadFragment.DOWNLOAD_NAMES.length; i++) {
                if (DownloadFragment.DOWNLOAD_NAMES[i].equals(lastTransferFailure)) {
                    nameIndex = i;
                    break;
                }
            }
            if(nameIndex != -1) {
                //we restart the transfer
                File from = new File(global.getExternalFilesDir(null) + "/" + DownloadFragment.DOWNLOAD_NAMES[nameIndex]);
                File to = new File(global.getFilesDir() + "/" + DownloadFragment.DOWNLOAD_NAMES[nameIndex]);
                int finalNameIndex = nameIndex;
                FileTools.moveFile(from, to, new FileTools.MoveFileCallback() {
                    @Override
                    public void onSuccess() {
                        //we save the success of the transfer
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("lastTransferSuccess", DownloadFragment.DOWNLOAD_NAMES[finalNameIndex]);
                        editor.apply();

                        if (finalNameIndex < (DownloadFragment.DOWNLOAD_URLS.length - 1)) {  //if the download done is not the last one
                            //we start the next download
                            new Thread(() -> DownloadReceiver.internalCheckAndStartNextDownload(global, downloader, finalNameIndex)).start();
                        } else {
                            //we notify the completion of the download of all models
                            editor = sharedPreferences.edit();
                            editor.putLong("currentDownloadId", -2);
                            editor.apply();

                            startRTranslator();
                        }
                    }

                    @Override
                    public void onFailure() {
                        //we save the failure of the transfer
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("lastTransferFailure", DownloadFragment.DOWNLOAD_NAMES[finalNameIndex]);
                        editor.apply();
                    }
                });
            }
        }
    }

    private void showManualDownloadDialog(){
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.manual_download_instructions)).append("\n\n");
        for (int i = 0; i < DOWNLOAD_NAMES.length; i++) {
            message.append(DOWNLOAD_NAMES[i]).append("\n");
            for (int j = 0; j < DOWNLOAD_URLS[i].length; j++) {
                message.append("  ").append(DOWNLOAD_URLS[i][j]).append("\n");
            }
            message.append("\n");
        }
        message.append(getString(R.string.manual_download_path, global.getFilesDir().getPath()));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.manual_download_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.manual_download_copy, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("models", message.toString());
                    clipboard.setPrimaryClip(clip);
                })
                .setNeutralButton(R.string.manual_download_check, (dialog, which) -> {
                    // Trigger integrity check for all existing files
                    new Thread(() -> DownloadReceiver.internalCheckAndStartNextDownload(global, downloader, -1)).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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