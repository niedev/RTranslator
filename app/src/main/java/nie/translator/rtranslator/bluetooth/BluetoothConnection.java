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

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.UUID;


abstract class BluetoothConnection {
    //costanti
    public static final int ACCEPT = 0;
    public static final int REJECT = 1;
    public static final UUID APP_UUID = UUID.fromString("00007B3D-0000-1000-8000-00805F9B34FB");   //16 bit UUID
    public static final int DATA_MARGIN = 6;
    public static final int PREFERRED_MTU = 247;

    //oggetti e variabili
    private String uniqueName;
    protected final Object channelsLock = new Object();
    protected BluetoothAdapter bluetoothAdapter;
    protected Callback callback;
    protected int strategy;
    protected Context context;
    protected Handler mainHandler;
    protected ArrayList<Channel> channels = new ArrayList<>();
    protected Channel.DisconnectionCallback disconnectionCallback;


    protected BluetoothConnection(final Context context, String uniqueName, BluetoothAdapter bluetoothAdapter, int strategy, Callback callback) {
        this.context = context;
        this.strategy = strategy;
        this.callback = callback;
        this.uniqueName = uniqueName;
        this.bluetoothAdapter = bluetoothAdapter;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public abstract void updateName(String uniqueName);

    public ArrayList<Peer> getConnectedPeers() {
        synchronized (channelsLock) {
            ArrayList<Peer> peers = new ArrayList<>();
            for (Channel channel : channels) {
                peers.add(channel.getPeer());
            }
            return clonePeerList(peers);
        }
    }

    public void sendMessage(final Message message, final Channel.MessageCallback messageCallback) {
        synchronized (channelsLock) {
            if (message != null) {
                final ArrayList<Channel> channels = new ArrayList<>(BluetoothConnection.this.channels);
                Peer receiver = message.getReceiver();
                if (receiver != null) {
                    for (int i = 0; i < channels.size(); i++) {
                        if (!receiver.getUniqueName().equals(channels.get(i).getPeer().getUniqueName())){
                            channels.remove(i);
                            i--;
                        }
                    }
                }
                sendMessage(channels, message, messageCallback);
            }
        }
    }

    private void sendMessage(final ArrayList<Channel> channels, final Message message, final Channel.MessageCallback messageCallback) {
        if (channels.size() > 0) {
            Channel channel = channels.remove(0);
            if (!channel.getPeer().isDisconnecting()) {     // before it was: channel.getPeer().isConnected() || channel.getPeer().isReconnecting(), but so we don't recover the messages sent while the connection is lost
                channel.writeMessage(message, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        sendMessage(channels, message, messageCallback);
                    }
                });
            } else {
                sendMessage(channels, message, messageCallback);  // the sending of the message is skipped
            }
        } else {  // means that we have sent the message to all channels
            messageCallback.onMessageSent();   // we notify that the message has been sent to all channels
        }
    }

    public void sendData(final Message data, final Channel.MessageCallback dataCallback) {
        synchronized (channelsLock) {
            if (data != null) {
                final ArrayList<Channel> channels = new ArrayList<>(BluetoothConnection.this.channels);
                Peer receiver = data.getReceiver();
                if (receiver != null) {
                    for (int i = 0; i < channels.size(); i++) {
                        if (!receiver.getUniqueName().equals(channels.get(i).getPeer().getUniqueName())){
                            channels.remove(i);
                            i--;
                        }
                    }
                }
                sendData(channels, data, dataCallback);
            }
        }
    }

    private void sendData(final ArrayList<Channel> channels, final Message data, final Channel.MessageCallback dataCallback) {
        if (channels.size() > 0) {
            Channel channel = channels.remove(0);
            if (!channel.getPeer().isDisconnecting()) {       // before it was: channel.getPeer().isConnected() || channel.getPeer().isReconnecting(), but so we don't recover the messages sent while the connection is lost
                channel.writeData(data, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        sendData(channels, data, dataCallback);
                    }
                });
            } else {
                sendData(channels, data, dataCallback);  // the sending of the message is skipped
            }
        } else {  // means that we have sent the message to all channels
            dataCallback.onMessageSent();   // we notify that the message has been sent to all channels
        }
    }

    public void disconnect(final Peer peer) {
        disconnect(peer, null);
    }

    public void disconnect(final Peer peer, @Nullable final Channel.DisconnectionNotificationCallback disconnectionNotificationCallback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    int index = channels.indexOf(peer);
                    if (index != -1) {
                        if (channels.get(index).getPeer().isReconnecting()) {
                            // canceling of reconnection
                            stopReconnection(channels.get(index));
                            if (disconnectionNotificationCallback != null) {
                                disconnectionNotificationCallback.onDisconnectionNotificationSent();
                            }

                        } else {
                            //disconnection
                            if (!channels.get(index).notifyDisconnection(new Channel.DisconnectionNotificationCallback() {
                                @Override
                                public void onDisconnectionNotificationSent() {
                                    if (disconnectionNotificationCallback != null) {
                                        disconnectionNotificationCallback.onDisconnectionNotificationSent();
                                    }
                                }
                            })) {
                                channels.get(index).disconnect(disconnectionCallback);  // onDisconnectionNotificationSent is still called by disconnect();
                            }

                        }
                    } else {
                        if (disconnectionNotificationCallback != null) {
                            disconnectionNotificationCallback.onDisconnectionNotificationSent();
                        }
                    }
                }
            }
        });
    }

    public void disconnectAll() {
        disconnectAll(null);
    }

    public void disconnectAll(@Nullable final Channel.DisconnectionNotificationCallback disconnectionNotificationCallback) {
        new Thread() {   //si esegue in un thread per evitare che i channels vengano cancellati tra una disconnessione e l'altra
            @Override
            public void run() {
                super.run();
                synchronized (channelsLock) {
                    final ArrayList<Channel> channels = new ArrayList<>(BluetoothConnection.this.channels);
                    if (channels.size() > 0) {
                        disconnect(channels.remove(0).getPeer(), new Channel.DisconnectionNotificationCallback() {
                            @Override
                            public void onDisconnectionNotificationSent() {
                                if (channels.size() > 0) {
                                    disconnect(channels.remove(0).getPeer(), this);
                                } else {  // means that we have sent the disconnect notification to all channels
                                    if (disconnectionNotificationCallback != null) {
                                        disconnectionNotificationCallback.onDisconnectionNotificationSent();  // we notify that the disconnection notification has been sent to all channels
                                    }
                                }
                            }
                        });
                    } else {  // means we don't have any channels
                        if (disconnectionNotificationCallback != null) {
                            disconnectionNotificationCallback.onDisconnectionNotificationSent();  // we notify that the disconnection notification has been sent to all channels
                        }
                    }
                }
            }
        }.start();
    }

    public abstract void readPhy(Peer peer);

    protected String getUniqueName() {
        return uniqueName;
    }

    protected void setUniqueName(String uniqueName) {
        this.uniqueName = uniqueName;
    }

    /**
     * @return list composed by unique names of reconnecting peers
     */
    public ArrayList<String> getReconnectingPeers() {
        synchronized (channelsLock) {
            ArrayList<String> reconnectingPeer = new ArrayList<>();
            for (Channel channel : channels) {
                if (channel.getPeer().isReconnecting()) {
                    reconnectingPeer.add(channel.getPeer().getUniqueName());
                }
            }
            return reconnectingPeer;
        }
    }

    public int indexOfChannel(String uniqueName) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).getPeer().getUniqueName().equals(uniqueName)) {
                return i;
            }
        }
        return -1;
    }

    private static ArrayList<Peer> clonePeerList(ArrayList<Peer> list) {
        ArrayList<Peer> clone = new ArrayList<>(list.size());
        for (Peer item : list) clone.add((Peer) item.clone());
        return clone;
    }

    /**
     * This is meant to be called when the BT is turned off.
     * This method will:
     * - Manually set the connection lost status to every connected peer (because the system callback will not work).
     * - Disconnect any peer that isn't connected or reconnecting.
     * - And clear some resources for the client and the server.
     */
    public void pauseConnection(){
        synchronized (channelsLock) {
            Log.d("bluetooth_communicator", "paused connection");
            final ArrayList<Channel> channelsCopy = new ArrayList<>(BluetoothConnection.this.channels);
            for(int i=0; i<channelsCopy.size(); i++){
                Channel channel = channelsCopy.get(i);
                if(channel.getPeer().isFullyConnected()) {
                    //we manually call notifyConnectionLost because if this method is called because the BT is off, the system callback of the connection lost will not be called.
                    notifyConnectionLost(channel);
                } else if(channel.getPeer().isReconnecting()) {
                    // we only reset the connectionCompleteTimer and set an eventual requestingReconnection to false (we keep the peer in reconnecting state and we do not change its reconnection timer)
                    channel.resetConnectionCompleteTimer();  //in case the reconnecting peer is hardware connected, but it hasn't fully completed the connection process (reconnecting = true, hardwareConnected = true, requestingConnection = false)
                    if(channel.getPeer().isRequestingReconnection()){
                        channel.getPeer().setRequestingReconnection(false);
                    }
                } else {   //any other cases (usually isDisconnecting)
                    // we disconnect the peer and remove it from the channels (this is why we use channelsCopy for the loop)
                    channel.disconnect(disconnectionCallback);
                }
            }
        }
    }

    /**
     * This is meant to be called when the BT is turned back on after a pauseConnection() call.
     * This method will restart the resources for the server.
     * N.B. The client will automatically initialize the resume process for the reconnecting peers
     * and initialize the resources for each channel when its connection will be resumed.
     */
    public void resumeConnection(){
        Log.d("bluetooth_communicator", "resumed connection");
    }

    public void destroy() {
        while (channels.size() > 0) {
            channels.remove(0).destroy();
        }
    }


    protected abstract void notifyConnectionSuccess(Channel channel);

    protected abstract void notifyMessageReceived(Message message);

    protected abstract void notifyDataReceived(Message message);

    protected abstract void notifyConnectionLost(final Channel channel);

    protected abstract void stopReconnection(Channel channel);

    protected abstract void notifyConnectionResumed(Channel channel);

    protected abstract void notifyPeerUpdated(Channel channel, Peer newPeer);

    protected abstract void notifyDisconnection(Channel channel);

    public static class Callback {
        /**
         * It means you have received a connection request from another device (peer) (that have called connect)
         * for accept the connection request and start connection call bluetoothCommunicator.acceptConnection(peer);
         * for refusing call bluetoothCommunicator.rejectConnection(peer); (the peer must be the peer argument of onConnectionRequest).
         *
         * @param peer the peer that have sent the connection request
         */
        public void onConnectionRequest(Peer peer) {
        }

        /**
         * This means that you have accepted the connection request using acceptConnection or the other
         * device has accepted your connection request and the connection is complete, from now on you
         * can send messages or data (or disconnection request) to this peer until onDisconnected.
         * <br /><br />
         * To send messages to all connected peers you need to create a message with a context, a header, represented by a single character string
         * (you can use a header to distinguish between different types of messages, or you can ignore it and use a random
         * character), the text of the message, or a series of bytes if you want to send any kind of data and the peer you want to send the message to
         * (must be connected to avoid errors), example: new Message(context,"a","hello world",peer);
         * If you want to send message to a specific peer you have to set the sender of the message with the corresponding peer.
         * <br /><br />
         * To send disconnection request to connected peer you need to call bluetoothCommunicator.disconnect(peer);
         *
         * @param peer the peer with you have established the connection
         * @param source if in this connection you are a client (BluetoothCommunicator.CLIENT) or a server (BluetoothCommunicator.SERVER), it can be ignored.
         */
        public void onConnectionSuccess(Peer peer, int source) {
        }

        /**
         * This means that our connection request is rejected or has other problems,
         * to know the cause of the failure see errorCode (BluetoothCommunicator.CONNECTION_REJECTED
         * means rejected connection and BluetoothCommunicator.ERROR means generic error).
         *
         * @param peer the peer with you have failed the connection
         * @param errorCode the core of the error for know if the cause is a rejection or a generic problem
         */
        public void onConnectionFailed(Peer peer, int errorCode) {
        }

        /**
         * This means that a connected peer has lost the connection with you and the library is trying
         * to restore it, in this case you can update the gui to notify this problem.
         * <br /><br />
         * You can still send messages in this situation, all sent messages are put in a queue
         * and sent as soon as the connection is restored.
         *
         * @param peer the peer with you have lost the connection
         */
        public void onConnectionLost(Peer peer) {
        }

        /**
         * Means that connection lost is resumed successfully.
         *
         * @param peer
         */
        public void onConnectionResumed(Peer peer) {
        }

        /**
         * Means that you have received a message containing TEXT, to know the sender you can call message.getSender() that return
         * the peer that have sent the message, you can ignore source, it indicate only if you have received the message
         * as client or as server.
         *
         * @param message received text message
         * @param source indicate only if you have received the message as clients or as servers, it can be ignored
         */
        public void onMessageReceived(Message message, int source) {
        }

        /**
         * Means that you have received a message containing DATA, to know the sender you can call message.getSender() that return
         * the peer that have sent the message, you can ignore source, it indicate only if you have received the message
         * as client or as server.
         *
         * @param data received data message
         * @param source indicate only if you have received the message as clients or as servers, it can be ignored
         */
        public void onDataReceived(Message data, int source) {
        }

        /**
         * It means that a founded peer (or connected peer) has changed (name or address or other things),
         * if you have a collection of founded peers, you need to replace peer with newPeer if you want to connect successfully to that peer.
         * <br /><br />
         * In case the peer updated is connected and you have saved connected peers you have to update the peer if you want to successfully
         * send a message or a disconnection request to that peer.
         *
         * @param peer
         * @param newPeer
         */
        public void onPeerUpdated(Peer peer, Peer newPeer) {
        }

        /**
         * This method is only for internal usage, do not override it.
         * <br /><br />
         * Instead override onDisconnected(Peer peer, int peersLeft).
         *
         * @param peer
         */
        void onDisconnected(Peer peer) {
        }
    }
}
