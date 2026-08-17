package nie.translator.rtranslator.downloader2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.annotation.Nonnull;

import nie.translator.rtranslator.settings.ResourceManager;

public class DownloadGroupInfo implements Parcelable{
    @NonNull
    public final DownloadInfo[] downloadsInfo;
    public int runningDownloadIndex = -1;
    private int currentProgress = -1;
    private boolean allDownloadCompleted = false;

    public DownloadGroupInfo(@Nonnull DownloadInfo[] downloadsInfo){
        this.downloadsInfo = downloadsInfo;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    public boolean isAllDownloadCompleted() {
        return allDownloadCompleted;
    }

    public void setAllDownloadCompleted(boolean allDownloadCompleted) {
        this.allDownloadCompleted = allDownloadCompleted;
    }

    public int getRunningDownloadIndex(){
        return runningDownloadIndex;
    }

    @Nullable
    public DownloadInfo getRunningDownload(){
        if(runningDownloadIndex != -1){
            return downloadsInfo[runningDownloadIndex];
        }
        return null;
    }

    public void setRunningDownloadIndex(int runningDownloadIndex) {
        this.runningDownloadIndex = runningDownloadIndex;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        DownloadInfo[] downloadsInfoTarget = null;
        if(obj instanceof Downloader2){
            downloadsInfoTarget = ((Downloader2) obj).getDownloadGroupInfo().downloadsInfo;
        } else if(obj instanceof DownloadGroupInfo){
            downloadsInfoTarget = ((DownloadGroupInfo) obj).downloadsInfo;
        } else if(obj instanceof ResourceManager){
            downloadsInfoTarget = ((ResourceManager) obj).getDownloadInfo().downloadsInfo;
        }
        //if downloadsInfoTarget contains the same downloads as downloadsInfo (order doesn't matter), we return true
        if(downloadsInfoTarget != null && downloadsInfo.length == downloadsInfoTarget.length){
            for(DownloadInfo info: downloadsInfo){
                boolean found = false;
                for(DownloadInfo infoTarget: downloadsInfoTarget){
                    if(info.getDestinationCompletePath().equals(infoTarget.getDestinationCompletePath())){
                        found = true;
                    }
                }
                if(!found) return false;
            }
            return true;
        }
        return false;
    }

    @NonNull
    @Override
    protected DownloadGroupInfo clone() {
        DownloadInfo[] downloadsInfoClone = new DownloadInfo[downloadsInfo.length];
        for(int i = 0; i<downloadsInfo.length; i++){
            downloadsInfoClone[i] = downloadsInfo[i].clone();
        }
        DownloadGroupInfo copy = new DownloadGroupInfo(downloadsInfoClone);
        copy.runningDownloadIndex = this.runningDownloadIndex;
        copy.currentProgress = this.currentProgress;
        copy.allDownloadCompleted = this.allDownloadCompleted;
        return copy;
    }

    //parcel implementation
    public static final Parcelable.Creator<DownloadGroupInfo> CREATOR = new Parcelable.Creator<DownloadGroupInfo>() {
        @Override
        public DownloadGroupInfo createFromParcel(Parcel in) {
            return new DownloadGroupInfo(in);
        }

        @Override
        public DownloadGroupInfo[] newArray(int size) {
            return new DownloadGroupInfo[size];
        }
    };

    private DownloadGroupInfo(Parcel in) {
        downloadsInfo = (DownloadInfo[]) in.readParcelableArray(DownloadInfo.class.getClassLoader());
        runningDownloadIndex = in.readInt();
        currentProgress = in.readInt();
        allDownloadCompleted = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeParcelableArray(downloadsInfo, 0);
        parcel.writeInt(runningDownloadIndex);
        parcel.writeInt(currentProgress);
        parcel.writeByte((byte) (allDownloadCompleted ? 1 : 0));
    }
}
