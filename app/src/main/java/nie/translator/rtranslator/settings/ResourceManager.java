package nie.translator.rtranslator.settings;

import static nie.translator.rtranslator.tools.DownloaderTools.checkMozillaModelsPresence;
import static nie.translator.rtranslator.tools.DownloaderTools.isMozillaDownload;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Lists;

import java.util.ArrayList;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class ResourceManager implements MozillaLanguagesAdapter.ResourceManagerItem {
    @Nullable
    private String title = null;
    @Nullable
    private String description = null;
    private int modelSizeMb = 0;
    @NonNull
    private ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
    private int progress;
    private boolean progressUnzipping = false;
    private boolean progressTesting = false;
    private int error = -1;
    private final DownloadGroupInfo downloadInfo;
    @Nullable
    private ResourceManagerView view;
    @Nullable
    private Listener clientListener;
    private final DownloadManager downloadManager;
    private final Activity activity;
    private boolean animateDeletion = true;
    private final ResourceManagerView.Listener viewListener = new ResourceManagerView.Listener() {
        @Override
        public void onDownloadClicked() {
            setState(ResourceManagerView.State.DOWNLOADING);
            downloadManager.startDownload(downloadInfo);
        }

        @Override
        public void onPauseClicked() {
            setState(ResourceManagerView.State.PAUSED);
            downloadManager.pauseDownload(downloadInfo);
        }

        @Override
        public void onResumeClicked() {
            setState(ResourceManagerView.State.DOWNLOADING);
            downloadManager.startDownload(downloadInfo);
        }

        @Override
        public void onDeletePressed() {
            DownloadGroupInfo hyMtDownloadInfo = ((Global) activity.getApplication()).getHyMtDownloadInfo();
            DownloadGroupInfo madladDownloadInfo = ((Global) activity.getApplication()).getMadladDownloadInfo();
            if(isMozillaDownload(downloadInfo) || downloadInfo.equals(hyMtDownloadInfo) || downloadInfo.equals(madladDownloadInfo)) {
                //this is to prevent the user from deleting all the translation models
                int numberOfModelsAvailable = 0;
                for (DownloadGroupInfo downloadGroupInfo : downloadManager.getSavedDownloadStatus()) {
                    if (isMozillaDownload(downloadGroupInfo)) {
                        if (downloadGroupInfo.isAllDownloadCompleted()) {
                            numberOfModelsAvailable++;
                            break;
                        }
                    }
                }
                if (downloadManager.checkDownloadCompleted(hyMtDownloadInfo)) {
                    numberOfModelsAvailable++;
                }
                if (downloadManager.checkDownloadCompleted(madladDownloadInfo)) {
                    numberOfModelsAvailable++;
                }
                if (numberOfModelsAvailable <= 1) {
                    showCannotDeleteDialog();
                    return;
                }
            }
            showDeleteDialog();
        }

        @Override
        public void onErrorPressed() {
            showErrorInfoDialog();
        }
    };

    public ResourceManager(Activity activity, DownloadGroupInfo downloadInfo, ResourceManagerView view, DownloadManager downloadManager) {
        this.activity = activity;
        this.downloadInfo = downloadInfo;
        this.view = view;
        this.downloadManager = downloadManager;
        view.setListener(viewListener);
    }

    public ResourceManager(Activity activity, DownloadGroupInfo downloadInfo, DownloadManager downloadManager, @Nullable String title, @Nullable String description, int modelSizeMb, @NonNull ResourceManagerView.State state, int progress, boolean animateDeletion) {
        this.activity = activity;
        this.downloadInfo = downloadInfo;
        this.downloadManager = downloadManager;
        this.title = title;
        this.description = description;
        this.modelSizeMb = modelSizeMb;
        this.state = state;
        this.progress = progress;
        this.animateDeletion = animateDeletion;
    }

    public void setStatus(ResourceManagerView.State state, int progress, boolean unzipping, boolean testing) {
        setState(state, false);
        setProgress(progress, unzipping, testing);
    }

    public void setState(ResourceManagerView.State state){
        setState(state, true);
    }

    public void setState(ResourceManagerView.State state, boolean animate){
        this.state = state;
        if(view != null) {
            view.setState(state, animate);
        }
    }

    public void setProgress(int progress, boolean unzipping, boolean testing){
        this.progress = progress;
        this.progressUnzipping = unzipping;
        this.progressTesting = testing;
        if (view != null) {
            view.setDownloadProgress(progress, unzipping, testing);
        }
    }

    public void setError(int reason){
        if(reason != -1) this.state = ResourceManagerView.State.PAUSED;
        this.error = reason;
        if(view != null){
            if(reason != -1) view.setState(ResourceManagerView.State.PAUSED, false);
            view.setError(reason);
        }
    }

    public DownloadGroupInfo getDownloadInfo() {
        return downloadInfo;
    }

    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    @Nullable
    public ResourceManagerView getView() {
        return view;
    }

    public void setView(@Nullable ResourceManagerView view, @Nullable Listener listener) {
        this.view = view;
        if(view != null) {
            this.clientListener = listener;
        }else{
            this.clientListener = null;
        }
        if(view != null) {
            view.setListener(viewListener);
            view.setTitle(title);
            view.setDescription(description);
            view.setModelSize(modelSizeMb);
            view.setState(state, false);
            view.setDownloadProgress(progress, progressUnzipping, progressTesting);
            view.setError(error);
        }
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public int getModelSizeMb() {
        return modelSizeMb;
    }

    public ResourceManagerView.State getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof ResourceManager){
            return downloadInfo.equals(((ResourceManager) obj).downloadInfo);
        }
        return downloadInfo.equals(obj);
    }

    public void showDeleteDialog(){
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_delete, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        CardView continueButton = editDialogLayout.findViewById(R.id.okButtonCard);
        CardView cancelButton = editDialogLayout.findViewById(R.id.cancelButtonCard);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                setState(ResourceManagerView.State.EMPTY, animateDeletion);
                downloadManager.cancelDownload(downloadInfo);
                if(clientListener != null) clientListener.onResourceDeleted();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }

    public void showCannotDeleteDialog(){
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_error, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        TextView textView = editDialogLayout.findViewById(R.id.textView);
        CardView okButton = editDialogLayout.findViewById(R.id.okButtonCard);

        textView.setText(activity.getString(R.string.error_delete_last_download));

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    public void showErrorInfoDialog(){
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_error, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        TextView textView = editDialogLayout.findViewById(R.id.textView);
        CardView okButton = editDialogLayout.findViewById(R.id.okButtonCard);

        textView.setText(activity.getResources().getText(R.string.error_download));

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    public interface Listener {
        void onResourceDeleted();
    }
}
