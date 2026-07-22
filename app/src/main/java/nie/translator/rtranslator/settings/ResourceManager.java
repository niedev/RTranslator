package nie.translator.rtranslator.settings;

import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;

public class ResourceManager {
    private DownloadGroupInfo downloadInfo;
    private ResourceManagerView view;
    private DownloadManager downloadManager;

    public ResourceManager(DownloadGroupInfo downloadInfo, ResourceManagerView view, DownloadManager downloadManager) {
        this.downloadInfo = downloadInfo;
        this.view = view;
        this.downloadManager = downloadManager;
        view.setListener(new ResourceManagerView.Listener() {
            @Override
            public void onDownloadClicked() {
                view.setState(ResourceManagerView.State.DOWNLOADING, true);
                downloadManager.startDownload(downloadInfo);
            }

            @Override
            public void onPauseClicked() {
                view.setState(ResourceManagerView.State.PAUSED, true);
                downloadManager.pauseDownload(downloadInfo);
            }

            @Override
            public void onResumeClicked() {
                view.setState(ResourceManagerView.State.DOWNLOADING, true);
                downloadManager.startDownload(downloadInfo);
            }

            @Override
            public void onDeletePressed() {
                view.setState(ResourceManagerView.State.EMPTY, true);
                downloadManager.cancelDownload(downloadInfo);
            }
        });
    }

    public void setStatus(ResourceManagerView.State state, int progress) {
        view.setState(state, false);
        view.setDownloadProgress(progress);
    }

    public void setState(ResourceManagerView.State state){
        view.setState(state, true);
    }

    public void setProgress(int progress){
        view.setDownloadProgress(progress);
    }

    public void setCompleted(){
        view.setState(ResourceManagerView.State.DOWNLOADED, true);
    }

    public void setError(){

    }

    public DownloadGroupInfo getDownloadInfo() {
        return downloadInfo;
    }

    public DownloadManager getDownloadManager() {
        return downloadManager;
    }

    public ResourceManagerView getView() {
        return view;
    }
}
