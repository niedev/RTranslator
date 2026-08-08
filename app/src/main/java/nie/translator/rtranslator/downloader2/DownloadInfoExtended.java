package nie.translator.rtranslator.downloader2;

import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DownloadInfoExtended extends DownloadInfo{
    private int currentProgress;
    private boolean downloadCompleted = false;
    private boolean unzipped = false;
    @Nullable
    private String internalFolder = null;
    private boolean integrityTested = false;
    private int currentError = -1;

    public DownloadInfoExtended(String name, String url, String destinationPath, long sizeKb, boolean shouldTestIntegrity, boolean shouldUnzip) {
        super(name, url, destinationPath, sizeKb, shouldTestIntegrity, shouldUnzip);
    }

    public int getCurrentError() {
        return currentError;
    }

    public void setCurrentError(int errorReason) {
        this.currentError = errorReason;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    public boolean isUnzipping(){
        return downloadCompleted && (!unzipped && shouldUnzip);
    }

    public boolean isTestingIntegrity() {
        return downloadCompleted && (unzipped || !shouldUnzip) && (!integrityTested && shouldTestIntegrity);
    }

    public boolean isDownloadCompleted() {
        return downloadCompleted;
    }

    public void setDownloadCompleted(boolean downloadCompleted) {
        this.downloadCompleted = downloadCompleted;
    }

    public boolean isUnzipped() {
        return unzipped;
    }

    public void setUnzipped(boolean unzipped) {
        this.unzipped = unzipped;
    }

    public boolean isIntegrityTested() {
        return integrityTested;
    }

    public void setIntegrityTested(boolean integrityTested) {
        this.integrityTested = integrityTested;
    }

    public boolean isAllCompleted(){
        boolean unzippingCompleted = !shouldUnzip || unzipped;
        boolean testingCompleted = !shouldTestIntegrity || integrityTested;
        return downloadCompleted && unzippingCompleted && testingCompleted;
    }

    @Nullable
    public String getInternalFolder() {
        return internalFolder;
    }

    public void setInternalFolder(@Nullable String internalFolder) {
        this.internalFolder = internalFolder;
    }



    //parcel implementation
    public static final Creator<DownloadInfoExtended> CREATOR = new Creator<DownloadInfoExtended>() {
        @Override
        public DownloadInfoExtended createFromParcel(Parcel in) {
            return new DownloadInfoExtended(in);
        }

        @Override
        public DownloadInfoExtended[] newArray(int size) {
            return new DownloadInfoExtended[size];
        }
    };

    private DownloadInfoExtended(Parcel in) {
        super(in);
        currentProgress = in.readInt();
        downloadCompleted = in.readByte() != 0;
        unzipped = in.readByte() != 0;
        integrityTested = in.readByte() != 0;
        currentError = in.readInt();
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeInt(currentProgress);
        parcel.writeByte((byte) (downloadCompleted ? 1 : 0));
        parcel.writeByte((byte) (unzipped ? 1 : 0));
        parcel.writeByte((byte) (integrityTested ? 1 : 0));
        parcel.writeInt(currentError);
    }
}
