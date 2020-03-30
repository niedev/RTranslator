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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication;

import android.bluetooth.BluetoothAdapter;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.peers.GuiPeer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.BluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;


public class ConversationBluetoothCommunicator {
    private BluetoothCommunicator bluetoothCommunicator;
    private ArrayList<Callback> clientCallbacks = new ArrayList<>();
    private Global global;
    private Handler mainHandler;
    private ArrayList<Peer> connectingPeers = new ArrayList<>();
    private ArrayList<GuiPeer> connectedPeers = new ArrayList<>();


    public ConversationBluetoothCommunicator(final Global global, String name, int strategy) {
        this.global = global;
        mainHandler = new Handler(Looper.getMainLooper());
        bluetoothCommunicator = new BluetoothCommunicator(global, name, strategy);
        BluetoothCommunicator.Callback bluetoothCommunicatorCallback = new BluetoothCommunicator.Callback() {
            @Override
            public void onAdvertiseStarted() {
                if (bluetoothCommunicator.isDiscovering()) {
                    notifySearchStarted();
                }
            }

            @Override
            public void onDiscoveryStarted() {
                if (bluetoothCommunicator.isAdvertising()) {
                    notifySearchStarted();
                }
            }

            @Override
            public void onAdvertiseStopped() {
                if (!bluetoothCommunicator.isDiscovering()) {
                    notifySearchStopped();
                }
            }

            @Override
            public void onDiscoveryStopped() {
                if (!bluetoothCommunicator.isAdvertising()) {
                    notifySearchStopped();
                }
            }

            @Override
            public void onPeerFound(final Peer peer) {
                // if exist the corresponding recent peer, we take the image from him
                global.getRecentPeersDataManager().getRecentPeerByName(peer.getUniqueName(), new RecentPeersDataManager.RecentPeerListener() {
                    @Override
                    public void onRecentPeerObtained(RecentPeer recentPeer) {
                        GuiPeer guiPeer;
                        if (recentPeer != null) {
                            guiPeer = new GuiPeer(peer, recentPeer.getUserImage());
                        } else {
                            guiPeer = new GuiPeer(peer, null);
                        }
                        notifyPeerFound(guiPeer);
                    }
                });
            }

            @Override
            public void onPeerLost(Peer peer) {
                notifyPeerLost(new GuiPeer(peer, null));
            }

            @Override
            public void onConnectionRequest(Peer peer) {
                notifyConnectionRequest(new GuiPeer(peer, null));
            }

            @Override
            public void onConnectionSuccess(final Peer peer, final int source) {
                connectingPeers.remove(peer);
                GuiPeer guiPeer = new GuiPeer(peer, null);
                connectedPeers.add(guiPeer);
                if (source == BluetoothCommunicator.CLIENT) {
                    sendID(peer);
                }

                // if exist the corresponding recent peer, we take the image from him
                global.getRecentPeersDataManager().getRecentPeerByName(peer.getUniqueName(), new RecentPeersDataManager.RecentPeerListener() {
                    @Override
                    public void onRecentPeerObtained(RecentPeer recentPeer) {
                        int index = connectedPeers.indexOf(peer);
                        if (index != -1 && recentPeer != null) {
                            GuiPeer oldPeer = (GuiPeer) connectedPeers.get(index).clone();
                            connectedPeers.get(index).setUserImage(recentPeer.getUserImage());
                            notifyPeerUpdated(oldPeer, connectedPeers.get(index));
                        }
                    }
                });

                notifyConnectionSuccess(guiPeer);
            }

            @Override
            public void onConnectionFailed(Peer peer, int errorCode) {
                connectingPeers.remove(peer);
                notifyConnectionFailed(new GuiPeer(peer, null), errorCode);
            }

            @Override
            public void onConnectionLost(Peer peer) {
                int index = connectedPeers.indexOf(peer);
                if (index != -1) {
                    connectedPeers.set(index, new GuiPeer(peer, connectedPeers.get(index).getUserImage()));
                    notifyConnectionLost(connectedPeers.get(index));
                }
            }

            @Override
            public void onConnectionResumed(Peer peer) {
                int index = connectedPeers.indexOf(peer);   // the mac of the peer is different from the connectedPeer
                if (index != -1) {
                    connectedPeers.set(index, new GuiPeer(peer, connectedPeers.get(index).getUserImage()));
                    notifyConnectionResumed(connectedPeers.get(index));
                }
            }

            @Override
            public void onMessageReceived(final Message message, int source) {
                switch (message.getHeader()) {
                    case "m": {
                        notifyMessageReceived(message);
                        break;
                    }
                    case "d": {
                        if (source == BluetoothCommunicator.SERVER) {
                            sendID(message.getSender());
                        } else if (source == BluetoothCommunicator.CLIENT) {
                            sendImage(message.getSender());
                        }
                        global.getRecentPeersDataManager().getRecentPeer(message.getText(), new RecentPeersDataManager.RecentPeerListener() {
                            @Override
                            public void onRecentPeerObtained(RecentPeer recentPeer) {
                                Bitmap image = null;
                                if (recentPeer != null) {
                                    image = recentPeer.getUserImage();
                                }
                                // addition of the peer in recent devices in db or update if it is already present
                                global.getRecentPeersDataManager().insertRecentPeer(message.getText(), message.getSender().getUniqueName(), image);
                            }
                        });
                        break;
                    }
                }
            }

            @Override
            public void onDataReceived(final Message data, int source) {
                super.onDataReceived(data, source);
                switch (data.getHeader()) {
                    case "i": {
                        // adding the peer image to recent devices
                        if (source == BluetoothCommunicator.SERVER) {
                            sendImage(data.getSender());
                        }
                        global.getRecentPeersDataManager().getRecentPeers(new RecentPeersDataManager.RecentPeersListener() {
                            @Override
                            public void onRecentPeersObtained(ArrayList<RecentPeer> recentPeers) {
                                Bitmap image = null;
                                if (!data.getText().equals("null")) {
                                    image = Tools.convertBytesToBitmap(data.getData());
                                }
                                for (RecentPeer recentPeer : recentPeers) {
                                    if (data.getSender().getUniqueName().equals(recentPeer.getUniqueName())) {
                                        global.getRecentPeersDataManager().insertRecentPeer(recentPeer.getDeviceID(), recentPeer.getUniqueName(), image);
                                    }
                                }
                                int index = connectedPeers.indexOf(data.getSender());
                                if (index != -1) {
                                    GuiPeer clonePeer = (GuiPeer) connectedPeers.get(index).clone();
                                    connectedPeers.get(index).setUserImage(image);
                                    notifyPeerUpdated(clonePeer, connectedPeers.get(index));
                                }
                            }
                        });
                        break;
                    }
                }
            }

            @Override
            public void onPeerUpdated(final Peer peer, final Peer newPeer) {
                if (!peer.getUniqueName().equals(newPeer.getUniqueName())) {
                    // update the peer name on recent devices
                    global.getRecentPeersDataManager().getRecentPeers(new RecentPeersDataManager.RecentPeersListener() {
                        @Override
                        public void onRecentPeersObtained(ArrayList<RecentPeer> recentPeers) {
                            for (RecentPeer recentPeer : recentPeers) {
                                if (peer.getUniqueName().equals(recentPeer.getUniqueName())) {
                                    global.getRecentPeersDataManager().insertRecentPeer(recentPeer.getDeviceID(), newPeer.getUniqueName(), recentPeer.getUserImage());
                                }
                            }
                        }
                    });
                }
                int index = connectedPeers.indexOf(peer);
                if (index != -1) {
                    GuiPeer clonePeer = (GuiPeer) connectedPeers.get(index).clone();
                    connectedPeers.set(index, new GuiPeer(newPeer, connectedPeers.get(index).getUserImage()));
                    notifyPeerUpdated(clonePeer, connectedPeers.get(index));
                }
            }

            @Override
            public void onDisconnected(Peer peer, int peersLeft) {
                connectedPeers.remove(new GuiPeer(peer, null));   // peers during reconnection can change macs without notification if reconnection fails
                notifyDisconnection(new GuiPeer(peer, null), peersLeft);
            }

            @Override
            public void onBluetoothLeNotSupported() {
                notifyBluetoothLeNotSupported();
            }
        };
        bluetoothCommunicator.addCallback(bluetoothCommunicatorCallback);
    }

    public synchronized int startSearch() {
        int advertisingCode = bluetoothCommunicator.startAdvertising();
        int discoveringCode = bluetoothCommunicator.startDiscovery();
        if (advertisingCode == discoveringCode) {
            return advertisingCode;
        }
        if (advertisingCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED || discoveringCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED) {
            return BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED;
        }
        if (advertisingCode == BluetoothCommunicator.SUCCESS || discoveringCode == BluetoothCommunicator.SUCCESS) {
            if (advertisingCode == BluetoothCommunicator.ALREADY_STARTED || discoveringCode == BluetoothCommunicator.ALREADY_STARTED) {
                return BluetoothCommunicator.SUCCESS;
            }
        }
        return BluetoothCommunicator.ERROR;
    }

    public synchronized int stopSearch(boolean tryRestoreBluetoothStatu) {
        int advertisingCode = bluetoothCommunicator.stopAdvertising(tryRestoreBluetoothStatu);
        int discoveringCode = bluetoothCommunicator.stopDiscovery(tryRestoreBluetoothStatu);
        if (advertisingCode == discoveringCode) {
            return advertisingCode;
        }
        if (advertisingCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED || discoveringCode == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED) {
            return BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED;
        }
        if (advertisingCode == BluetoothCommunicator.SUCCESS || discoveringCode == BluetoothCommunicator.SUCCESS) {
            if (advertisingCode == BluetoothCommunicator.ALREADY_STOPPED || discoveringCode == BluetoothCommunicator.ALREADY_STOPPED) {
                return BluetoothCommunicator.SUCCESS;
            }
        }
        return BluetoothCommunicator.ERROR;
    }

    public synchronized boolean isBluetoothLeSupported() {
        if (bluetoothCommunicator.isBluetoothLeSupported() == BluetoothCommunicator.SUCCESS) {
            return true;
        } else {
            return false;
        }
    }

    public void sendMessage(Message message) {
        message.setHeader("m");
        bluetoothCommunicator.sendMessage(message);
    }

    private void sendID(final Peer receiver) {
        global.getMyID(new Global.MyIDListener() {
            @Override
            public void onSuccess(String id) {
                bluetoothCommunicator.sendMessage(new Message(global, "d", id,receiver));
            }
        });
    }

    private void sendImage(Peer receiver) {
        Bitmap imageBitmap = Tools.getBitmapFromFile(new File(global.getFilesDir(), "user_image"));
        if (imageBitmap != null) {
            Bitmap compressedImage = Tools.getResizedBitmap(global, imageBitmap, 160);
            bluetoothCommunicator.sendData(new Message(global, "i", Tools.convertBitmapToBytes(compressedImage, 70), receiver));
        } else {
            bluetoothCommunicator.sendData(new Message(global, "i", "null", receiver));
        }
    }

    public void setName(String name) {
        bluetoothCommunicator.setName(name);
    }

    public ArrayList<GuiPeer> getConnectedPeersList() {
        return Tools.cloneList(connectedPeers);
    }

    private int indexOfConnectedPeer(String uniqueName) {
        for (int i = 0; i < connectedPeers.size(); i++) {
            if (connectedPeers.get(i).getUniqueName().equals(uniqueName)) {
                return i;
            }
        }
        return -1;
    }

    public void acceptConnection(Peer peer) {
        bluetoothCommunicator.acceptConnection(peer);
    }

    public void rejectConnection(Peer peer) {
        bluetoothCommunicator.rejectConnection(peer);
    }

    public boolean isSearching() {
        return bluetoothCommunicator.isAdvertising() && bluetoothCommunicator.isDiscovering();
    }

    public void connect(Peer peer) {
        connectingPeers.add(peer);
        bluetoothCommunicator.connect(peer);
    }

    public ArrayList<Peer> getConnectingPeers() {
        return connectingPeers;
    }

    public int readPhy(GuiPeer peer) {
        return bluetoothCommunicator.readPhy(peer);
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothCommunicator.getBluetoothAdapter();
    }

    public int disconnect(Peer peer) {
        if (bluetoothCommunicator.getConnectedPeersList().contains(peer)) {
            notifyDisconnecting(new GuiPeer(peer, null));
        }
        return bluetoothCommunicator.disconnect(peer);
    }

    public int disconnectFromAll() {
        ArrayList<Peer> connectedPeers = bluetoothCommunicator.getConnectedPeersList();
        for (Peer peer : connectedPeers) {
            notifyDisconnecting(new GuiPeer(peer, null));
        }
        return bluetoothCommunicator.disconnectFromAll();
    }

    public void destroy(BluetoothCommunicator.DestroyCallback callback) {
        bluetoothCommunicator.destroy(callback);
    }


    public void addCallback(final Callback callback) {
        clientCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        clientCallbacks.remove(callback);
    }


    private void notifySearchStarted() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onSearchStarted();
        }
    }

    private void notifySearchStopped() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onSearchStopped();
        }
    }

    private void notifyPeerFound(final GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onPeerFound(peer);
        }
    }

    private void notifyPeerLost(GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onPeerLost(peer);
        }
    }

    private void notifyConnectionRequest(GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onConnectionRequest(peer);
        }
    }

    private void notifyConnectionSuccess(final GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onConnectionSuccess(peer);
        }
    }

    private void notifyConnectionFailed(GuiPeer peer, int errorCode) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onConnectionFailed(peer, errorCode);
        }
    }

    private void notifyConnectionResumed(GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onConnectionResumed(peer);
        }
    }

    private void notifyConnectionLost(GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onConnectionLost(peer);
        }
    }

    private void notifyMessageReceived(Message message) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onMessageReceived(message);
        }
    }

    private void notifyPeerUpdated(final GuiPeer peer, final GuiPeer newPeer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onPeerUpdated(peer, newPeer);
        }
    }

    private void notifyDisconnecting(GuiPeer peer) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDisconnecting(peer);
        }
    }

    private void notifyDisconnection(GuiPeer peer, int peersLeft) {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDisconnected(peer, peersLeft);
        }
    }

    private void notifyBluetoothLeNotSupported() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onBluetoothLeNotSupported();
        }
    }

    public static abstract class Callback {
        public void onSearchStarted() {
        }

        public void onSearchStopped() {
        }

        public void onPeerFound(GuiPeer peer) {
        }

        public void onPeerLost(GuiPeer peer) {
        }

        public void onConnectionRequest(GuiPeer peer) {
        }

        public void onConnectionSuccess(GuiPeer peer) {
        }

        public void onConnectionFailed(GuiPeer peer, int errorCode) {
        }

        public void onConnectionLost(GuiPeer peer) {
        }

        public void onConnectionResumed(GuiPeer peer) {
        }

        public void onMessageReceived(Message message) {
        }

        public void onPeerUpdated(GuiPeer peer, GuiPeer newPeer) {
        }

        public void onDisconnecting(GuiPeer peer) {
        }

        public void onDisconnected(GuiPeer peer, int peersLeft) {
        }

        public void onBluetoothLeNotSupported() {
        }
    }
}