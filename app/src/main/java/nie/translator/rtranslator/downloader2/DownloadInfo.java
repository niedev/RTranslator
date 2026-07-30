package nie.translator.rtranslator.downloader2;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class DownloadInfo implements Parcelable {
    protected final String name;
    protected final String url;
    protected final String destinationPath;  //destination folder (should not include the file name)
    protected final long size;  //size in kb (they are not exact, because this is used only to show the progress)
    protected int downloadId = -1;
    protected final boolean shouldTestIntegrity;
    protected final boolean shouldUnzip;


    DownloadInfo(){
        this.name = "";
        this.url = "";
        this.destinationPath = "";
        this.size = 0;
        this.shouldTestIntegrity = false;
        this.shouldUnzip = false;
    }
    public DownloadInfo(String name, String url, String destinationPath, long size, boolean shouldTestIntegrity, boolean shouldUnzip) {
        this.name = name;
        this.url = url;
        this.destinationPath = destinationPath;
        this.size = size;
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

    DownloadInfo(Parcel in) {
        name = in.readString();
        url = in.readString();
        destinationPath = in.readString();
        size = in.readLong();
        downloadId = in.readInt();
        shouldTestIntegrity = in.readByte() != 0;
        shouldUnzip = in.readByte() != 0;
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
    }
}
