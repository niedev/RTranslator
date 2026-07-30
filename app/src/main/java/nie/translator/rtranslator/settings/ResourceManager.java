package nie.translator.rtranslator.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;

public class ResourceManager implements MozillaLanguagesAdapter.ResourceManagerItem {
    @Nullable
    private String title = null;
    @Nullable
    private String description = null;
    private int modelSizeMb = 0;
    @NonNull
    private ResourceManagerView.State state = ResourceManagerView.State.EMPTY;
    private int progress;
    private final DownloadGroupInfo downloadInfo;
    @Nullable
    private ResourceManagerView view;
    private final DownloadManager downloadManager;
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
            setState(ResourceManagerView.State.EMPTY);
            downloadManager.cancelDownload(downloadInfo);
        }
    };

    public ResourceManager(DownloadGroupInfo downloadInfo, ResourceManagerView view, DownloadManager downloadManager) {
        this.downloadInfo = downloadInfo;
        this.view = view;
        this.downloadManager = downloadManager;
        view.setListener(viewListener);
    }

    public ResourceManager(DownloadGroupInfo downloadInfo, DownloadManager downloadManager, @Nullable String title, @Nullable String description, int modelSizeMb, @NonNull ResourceManagerView.State state, int progress) {
        this.downloadInfo = downloadInfo;
        this.downloadManager = downloadManager;
        this.title = title;
        this.description = description;
        this.modelSizeMb = modelSizeMb;
        this.state = state;
        this.progress = progress;
    }

    public void setStatus(ResourceManagerView.State state, int progress) {
        setState(state, false);
        setProgress(progress);
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

    public void setProgress(int progress){
        this.progress = progress;
        if (view != null) {
            view.setDownloadProgress(progress);
        }
    }

    public void setError(){

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

    public void setView(@Nullable ResourceManagerView view) {
        this.view = view;
        if(view != null) {
            view.setListener(viewListener);
            view.setTitle(title);
            view.setDescription(description);
            view.setModelSize(modelSizeMb);
            view.setState(state, false);
            view.setDownloadProgress(progress);
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
}
