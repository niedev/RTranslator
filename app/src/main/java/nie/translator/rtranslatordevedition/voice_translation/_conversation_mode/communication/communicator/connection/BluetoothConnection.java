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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.UUID;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;


public abstract class BluetoothConnection {
    //costanti
    public static final int ACCEPT = 0;
    public static final int REJECT = 1;
    public static final UUID APP_UUID = UUID.fromString("00001234-0000-1000-8000-00805F9B34FB");
    public static final int MTU = 247;
    public static final int STRATEGY_P2P = 0;
    public static final int STRATEGY_P2P_WITH_AUTO_RECONNECTION = 1;
    public static final int STRATEGY_P2P_WITH_MANUAL_RECONNECTION = 2;
    public static final int SUB_MESSAGES_LENGTH = 192;
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
        this.disconnectionCallback = new Channel.DisconnectionCallback() {
            @Override
            public void onAlreadyDisconnected(Peer peer) {
                int index = channels.indexOf(peer);
                if (index != -1) {
                    channels.remove(index);
                }
            }

            @Override
            public void onDisconnectionFailed() {
                notifyDisconnectionFailed();
            }
        };
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

                                @Override
                                public void onDisconnectionFailed() {
                                    if (disconnectionNotificationCallback != null) {
                                        disconnectionNotificationCallback.onDisconnectionNotificationSent();
                                    }
                                    notifyDisconnectionFailed();
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

    protected void notifyDisconnectionFailed() {
        callback.onDisconnectionFailed();
    }

    public static class Callback {
        public void onConnectionRequest(Peer peer) {
        }

        public void onConnectionSuccess(Peer peer, int source) {
        }

        public void onConnectionFailed(Peer peer, int errorCode) {
        }

        public void onConnectionLost(Peer peer) {
        }

        public void onConnectionResumed(Peer peer) {
        }

        public void onMessageReceived(Message message, int source) {
        }

        public void onDataReceived(Message data, int source) {
        }

        public void onPeerUpdated(Peer peer, Peer newPeer) {
        }

        public void onDisconnected(Peer peer) {
        }

        public void onDisconnectionFailed() {

        }
    }
}
