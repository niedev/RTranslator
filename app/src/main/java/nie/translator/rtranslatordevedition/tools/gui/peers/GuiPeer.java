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

package nie.translator.rtranslatordevedition.tools.gui.peers;

import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;

import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;

public class GuiPeer extends Peer implements Listable {
    private byte[] userImage;

    public GuiPeer(Peer peer, @Nullable Bitmap userImage) {
        super(peer);
        if (userImage != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            userImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            this.userImage = stream.toByteArray();
        }
    }

    public GuiPeer(BluetoothDevice device, String uniqueName, boolean isConnected) {
        super(device, uniqueName, isConnected);
    }

    public GuiPeer(BluetoothDevice device, String uniqueName, boolean isConnected, @Nullable Bitmap userImage) {
        super(device, uniqueName, isConnected);
        if (userImage != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            userImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            this.userImage = stream.toByteArray();
        }
    }

    public GuiPeer(BluetoothDevice device, String uniqueName, boolean isConnected, @Nullable byte[] userImage) {
        super(device, uniqueName, isConnected);
        this.userImage = userImage;
    }

    public Bitmap getUserImage() {
        if (userImage != null) {
            return Tools.convertBytesToBitmap(userImage);
        } else {
            return null;
        }
    }

    public byte[] getUserImageData() {
        return userImage;
    }

    public void setUserImage(Bitmap userImage) {
        if (userImage != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            userImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            this.userImage = stream.toByteArray();
        } else {
            this.userImage = null;
        }
    }

    //parcelable implementation
    public GuiPeer(Parcel in) {
        super(in);
        in.readByteArray(userImage);
    }

    public static final Parcelable.Creator<GuiPeer> CREATOR = new Parcelable.Creator<GuiPeer>() {
        @Override
        public GuiPeer createFromParcel(Parcel in) {
            return new GuiPeer(in);
        }

        @Override
        public GuiPeer[] newArray(int size) {
            return new GuiPeer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        super.writeToParcel(parcel, i);
        parcel.writeByteArray(userImage);
    }
}
