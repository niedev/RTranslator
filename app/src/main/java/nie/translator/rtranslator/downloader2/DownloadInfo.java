package nie.translator.rtranslator.downloader2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DownloadInfo implements Parcelable{
    protected final String name;
    protected final String url;
    @NonNull
    protected final String destinationPath;  //destination folder (should not include the file name)
    protected final long size;  //size in kb (they are not exact, because this is used only to show the progress)
    protected int downloadId = -1;
    protected final boolean shouldTestIntegrity;
    protected final boolean shouldUnzip;
    private int currentProgress;
    private boolean downloadCompleted = false;
    private boolean unzipped = false;
    @Nullable
    private String internalFolder = null;
    private boolean integrityTested = false;
    private int currentError = -1;

    public DownloadInfo(String name, String url, String destinationPath, long sizeKb, boolean shouldTestIntegrity, boolean shouldUnzip) {
        this.name = name;
        this.url = url;
        this.destinationPath = destinationPath;
        this.size = sizeKb;
        this.shouldTestIntegrity = shouldTestIntegrity;
        this.shouldUnzip = shouldUnzip;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public String getDestinationCompletePath() {
        return destinationPath+"/"+name;
    }

    public long getSize() {
        return size;
    }

    public int getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(int downloadId) {
        this.downloadId = downloadId;
    }

    public boolean shouldTestIntegrity() {
        return shouldTestIntegrity;
    }

    public boolean shouldUnzip(){
        return shouldUnzip;
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

    public boolean isDownloading() {
        return !downloadCompleted;
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

    @NonNull
    @Override
    protected DownloadInfo clone() {
        Parcel parcel = Parcel.obtain();
        this.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DownloadInfo copy = DownloadInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return copy;
    }

    //parcel implementation
    public static final Creator<DownloadInfo> CREATOR = new Creator<DownloadInfo>() {
        @Override
        public DownloadInfo createFromParcel(Parcel in) {
            return new DownloadInfo(in);
        }

        @Override
        public DownloadInfo[] newArray(int size) {
            return new DownloadInfo[size];
        }
    };

    private DownloadInfo(Parcel in) {
        name = in.readString();
        url = in.readString();
        destinationPath = in.readString();
        size = in.readLong();
        downloadId = in.readInt();
        shouldTestIntegrity = in.readByte() != 0;
        shouldUnzip = in.readByte() != 0;
        currentProgress = in.readInt();
        downloadCompleted = in.readByte() != 0;
        unzipped = in.readByte() != 0;
        internalFolder = in.readString();
        integrityTested = in.readByte() != 0;
        currentError = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeString(name);
        parcel.writeString(url);
        parcel.writeString(destinationPath);
        parcel.writeLong(size);
        parcel.writeInt(downloadId);
        parcel.writeByte((byte) (shouldTestIntegrity ? 1 : 0));
        parcel.writeByte((byte) (shouldUnzip ? 1 : 0));
        parcel.writeInt(currentProgress);
        parcel.writeByte((byte) (downloadCompleted ? 1 : 0));
        parcel.writeByte((byte) (unzipped ? 1 : 0));
        parcel.writeString(internalFolder);
        parcel.writeByte((byte) (integrityTested ? 1 : 0));
        parcel.writeInt(currentError);
    }
}
