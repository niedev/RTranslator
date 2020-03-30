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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Objects;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;

public class Peer implements Parcelable, Cloneable {
    @NonNull
    private String uniqueName;
    @NonNull
    private String name;
    private BluetoothDevice device;
    private boolean isHardwareConnected = false;
    private boolean isConnected;
    private boolean isReconnecting = false;
    private boolean isRequestingReconnection = false;
    private boolean isDisconnecting = false;
    private ArrayList<Callback> clientCallbacks = new ArrayList<>();
    private Handler mainHandler;

    public Peer(BluetoothDevice device, String uniqueName, boolean isConnected) {
        mainHandler = new Handler(Looper.getMainLooper());
        this.device = device;
        if (uniqueName == null || uniqueName.length() < 2) {
            this.uniqueName = "";
            this.name = "";
        } else {
            this.uniqueName = uniqueName;
            this.name = uniqueName.substring(0, uniqueName.length() - 2);  // because the last two digits are an id
        }
        this.isConnected = isConnected;
    }

    public Peer(Peer peer) {
        uniqueName = peer.uniqueName;
        name = peer.name;
        device = peer.device;
        isHardwareConnected = peer.isHardwareConnected;
        isConnected = peer.isConnected;
        isReconnecting = peer.isReconnecting;
        isRequestingReconnection = peer.isRequestingReconnection;
        isDisconnecting = peer.isDisconnecting;
        clientCallbacks = peer.clientCallbacks;
        mainHandler = peer.mainHandler;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Peer) {
            Peer peer = (Peer) obj;
            if (device != null && peer.getDevice() != null) {   // check that prioritizing the name is not a problem
                if (device.getAddress() != null && peer.getDevice().getAddress() != null) {
                    return device.getAddress().equals(peer.getDevice().getAddress());
                }
            }
        }

        if (obj instanceof Channel) {
            Channel channel = (Channel) obj;
            return equals(channel.getPeer());

        }
        return false;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public BluetoothDevice getRemoteDevice(BluetoothAdapter bluetoothAdapter) {
        return bluetoothAdapter.getRemoteDevice(device.getAddress());
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public String getName() {
        return name;
    }

    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    public void setUniqueName(@NonNull String uniqueName) {
        if (uniqueName.length() >= 2) {
            this.uniqueName = uniqueName;
            this.name = uniqueName.substring(0, uniqueName.length() - 2);  // because the last two digits are an id
        }
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isHardwareConnected() {
        return isHardwareConnected;
    }

    public void setHardwareConnected(boolean hardwareConnected) {
        isHardwareConnected = hardwareConnected;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isFullyConnected() {
        return isConnected && !isReconnecting;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public boolean isReconnecting() {
        return isReconnecting;
    }

    public void setReconnecting(boolean reconnecting, boolean connected) {
        if (!isReconnecting && reconnecting) {
            notifyReconnecting();
        } else if (isReconnecting && !reconnecting && !isConnected && connected) {
            notifyReconnected();
        }
        isConnected = connected;
        isReconnecting = reconnecting;
    }

    public void setRequestingReconnection(boolean requestingReconnection) {
        if (isReconnecting || !requestingReconnection) {
            isRequestingReconnection = requestingReconnection;
        }
    }

    public boolean isRequestingReconnection() {
        return isRequestingReconnection;
    }

    public boolean isBonded(BluetoothAdapter bluetoothAdapter) {
        ArrayList<BluetoothDevice> bondedDevices = new ArrayList<>(bluetoothAdapter.getBondedDevices());
        if (device != null) {
            for (int i = 0; i < bondedDevices.size(); i++) {
                if (bluetoothAdapter.getRemoteDevice(bondedDevices.get(i).getAddress()).getAddress().equals(device.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isDisconnecting() {
        return isDisconnecting;
    }

    public void setDisconnecting(boolean disconnecting) {
        isDisconnecting = disconnecting;
    }


    public void addCallback(Callback callback) {
        clientCallbacks.add(callback);
    }

    public int removeCallback(Callback callback) {
        clientCallbacks.remove(callback);
        return clientCallbacks.size();
    }

    public void notifyReconnecting() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onReconnecting();
                }
            }
        });
    }

    public void notifyReconnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onReconnected();
                }
            }
        });
    }

    public abstract static class Callback {
        public void onReconnecting() {
        }

        public void onReconnected() {
        }

    }

    //parcelable implementation
    public Peer(Parcel in) {
        uniqueName = Objects.requireNonNull(in.readString());
        name = uniqueName.substring(0, uniqueName.length() - 2);  // because the last two digits are an id
        device = in.readParcelable(BluetoothDevice.class.getClassLoader());
        isHardwareConnected = in.readByte() != 0;
        isConnected = in.readByte() != 0;
        isReconnecting = in.readByte() != 0;
        isRequestingReconnection = in.readByte() != 0;
        isDisconnecting = in.readByte() != 0;
    }

    /*public byte[] toBytes() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }*/

    public static final Creator<Peer> CREATOR = new Creator<Peer>() {
        @Override
        public Peer createFromParcel(Parcel in) {
            return new Peer(in);
        }

        @Override
        public Peer[] newArray(int size) {
            return new Peer[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(uniqueName);
        parcel.writeParcelable(device, i);
        parcel.writeByte((byte) (isHardwareConnected ? 1 : 0));
        parcel.writeByte((byte) (isConnected ? 1 : 0));
        parcel.writeByte((byte) (isReconnecting ? 1 : 0));
        parcel.writeByte((byte) (isRequestingReconnection ? 1 : 0));
        parcel.writeByte((byte) (isDisconnecting ? 1 : 0));
    }
}
