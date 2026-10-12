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

package nie.translator.rtranslator.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Objects;

/**
 * This class represents a device that we can find, with which we can establish a connection, and communicate (obviously in that order).
 * <br /><br />
 * Usually there is no need to create a peer, in fact we should start using it when it is found (BluetoothCommunicator.onPeerFound), later we can use the
 * found peer to request a connection (BluetoothCommunicator.connect) to the latter and if the peer accepts the connection request
 * (BluetoothCommunicator.onConnectionSuccess) then we can start exchanging messages with him and eventually make a disconnection.
 * <br /><br />
 * To understand if one peer is equivalent to another we should compare the uniqueName of the two peers, this is because the device
 * can vary over time and name could have a homonym.
 */
@SuppressLint("MissingPermission")
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
    //private ArrayList<Callback> clientCallbacks = new ArrayList<>();
    private Handler mainHandler;

    /**
     * This constructor is used internally by BluetoothCommunicator, you shouldn't create a Peer but instead use the peers founded by
     * the discovery.
     *
     * @param device
     * @param uniqueName
     * @param isConnected
     */
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

    /**
     * Copy constructor.
     *
     * @param peer peer to copy
     */
    public Peer(Peer peer) {
        uniqueName = peer.uniqueName;
        name = peer.name;
        device = peer.device;
        isHardwareConnected = peer.isHardwareConnected;
        isConnected = peer.isConnected;
        isReconnecting = peer.isReconnecting;
        isRequestingReconnection = peer.isRequestingReconnection;
        isDisconnecting = peer.isDisconnecting;
        //clientCallbacks = peer.clientCallbacks;
        mainHandler = peer.mainHandler;
    }

    /**
     * If obj is a Peer this method compare the address of the devices of the peers if they have one (if not it return false).
     * If obj is a Channel it will do the same comparison with the peer of that channel (this is only for internal usage)
     * This method is for advanced usages, normally you should compare the uniqueName.
     *
     * @param obj
     * @return true if equal false if not or missing attributes
     */
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

        if (obj instanceof nie.translator.rtranslator.bluetooth.Channel) {
            nie.translator.rtranslator.bluetooth.Channel channel = (nie.translator.rtranslator.bluetooth.Channel) obj;
            return equals(channel.getPeer());

        }
        return false;
    }

    /**
     * return the bluetooth device of the peer.
     *
     * @return bluetooth device
     */
    public BluetoothDevice getDevice() {
        return device;
    }

    /**
     * Call bluetoothAdapter.getRemoteDevice() passing it the address of the device of this peer and return
     * what the method of getRemoteDevice return, this method is only for internal usage, you does't need to use it.
     *
     * @param bluetoothAdapter
     * @return remote device
     */
    public BluetoothDevice getRemoteDevice(BluetoothAdapter bluetoothAdapter) {
        return bluetoothAdapter.getRemoteDevice(device.getAddress());
    }

    /**
     * Returns the uniqueName of this peer, the uniqueName is the real name used for advertising and contains
     * the name + 4 random characters assigned when BluetoothCommunicator is created, this 4 random characters are saved
     * the first time they are generated and reused as long as the app is installed on the phone, this 4 random characters
     * are removed in the name, so the user of the library can ignore the uniqueName and use it only for know if a peer
     * matches another (the random characters are always the same, so if the name + the 4 random characters are equals
     * the two peers represents the same device.
     *
     * @return unique name
     */
    public String getUniqueName() {
        return uniqueName;
    }

    /**
     * Returns the normal name of the peer.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the bluetooth device for this peer.
     *
     * @param device
     */
    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    /**
     * Sets the unique name of this peer.
     *
     * @param uniqueName
     */
    public void setUniqueName(@NonNull String uniqueName) {
        if (uniqueName.length() >= 2) {
            this.uniqueName = uniqueName;
            this.name = uniqueName.substring(0, uniqueName.length() - 2);  // because the last two digits are an id
        }
    }

    /**
     * Return the normal name of this peer.
     *
     * @return name
     */
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

    /**
     * Returns true if the peer is hardware connected to us.<br />
     * For example this method returns false if this peer
     * is connected to us but has lost the connection and it is reconnecting, and return true if we have sent the
     * connection request to a peer but it hasn't answered yet, because for sending a connection request the devices has
     * to be already connected via hardware, but for the library they are not connected (when a peer refuse a connection
     * request the hardware connection is interrupted too).
     *
     * @return isHardwareConnected
     */
    public boolean isHardwareConnected() {
        return isHardwareConnected;
    }

    /**
     * Sets if the peer is hardware connected to us, this method should not be called by the user, but only from the library.
     *
     * @param hardwareConnected
     */
    public void setHardwareConnected(boolean hardwareConnected) {
        isHardwareConnected = hardwareConnected;
    }

    /**
     * Returns true if this peer is connected to us.
     *
     * @return isConnected
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Returns true if this peer is connected and is not reconnecting
     *
     * @return (isConnected && !isReconnecting)
     */
    public boolean isFullyConnected() {
        return isConnected && !isReconnecting;
    }

    /**
     * Sets if this peer is connected to us, this method should not be called by the user, but only from the library.
     *
     * @param connected
     */
    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    /**
     * Returns true if the peer is connected to us but has lost the connection and it is trying to reconnect
     *
     * @return isReconnecting
     */
    public boolean isReconnecting() {
        return isReconnecting;
    }

    /**
     * Sets if this peer is trying to reconnect with us, this method should not be called by the user, but only from the library.
     *
     * @param reconnecting
     * @param connected
     */
    public void setReconnecting(boolean reconnecting, boolean connected) {
        /*if (!isReconnecting && reconnecting) {
            notifyReconnecting();
        } else if (isReconnecting && !reconnecting && !isConnected && connected) {
            notifyReconnected();
        }*/
        isConnected = connected;
        isReconnecting = reconnecting;
    }

    /**
     * This method is for internal usage only
     *
     * @param requestingReconnection
     */
    public void setRequestingReconnection(boolean requestingReconnection) {
        if (isReconnecting || !requestingReconnection) {
            isRequestingReconnection = requestingReconnection;
        }
    }

    /**
     * This method resets the state of the Peer to:
     * isHardwareConnected = false;
     * isConnected = false;
     * isReconnecting = false;
     * isRequestingReconnection = false;
     * isDisconnecting = false;
     * This new state is like a Peer that is just been found.
     */
    public void resetState(){
        isHardwareConnected = false;
        isConnected = false;
        isReconnecting = false;
        isRequestingReconnection = false;
        isDisconnecting = false;
    }

    /**
     * This method is for internal usage only
     *
     * @return
     */
    public boolean isRequestingReconnection() {
        return isRequestingReconnection;
    }

    /**
     * Check if this peer is in the bonded devices of the phone
     *
     * @param bluetoothAdapter
     * @return is bonded
     */
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

    /**
     * Returns true if the device is disconnecting from us
     *
     * @return isDisconnecting
     */
    public boolean isDisconnecting() {
        return isDisconnecting;
    }

    /**
     * Sets if the devices is disconnecting from us, this method should not be called by the user, but only from the library.
     *
     * @param disconnecting
     */
    public void setDisconnecting(boolean disconnecting) {
        isDisconnecting = disconnecting;
    }


    /*public void addCallback(Callback callback) {
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

    }*/

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
