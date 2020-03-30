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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.BluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothMessage;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.server.BluetoothConnectionServer;


public class BluetoothConnectionClient extends BluetoothConnection {
    public static final int ERROR = 0;
    public static final int CONNECTION_REJECTED = 1;
    private BluetoothGattCallback channelsCallback;
    private ConnectionDeque pendingConnections = new ConnectionDeque();


    public BluetoothConnectionClient(final Context context, String uniqueName, @NonNull final BluetoothAdapter bluetoothAdapter, final int strategy, final Callback callback) {
        super(context, uniqueName, bluetoothAdapter, strategy, callback);
        channelsCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(final BluetoothGatt gatt, int status, final int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {

                            synchronized (channelsLock) {
                                int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                                if (index != -1) {     // is used to manage synchronization with the server to avoid adding a device that connects to the latter instead of us
                                    refreshDeviceCache(gatt);  // is used to avoid cache problems
                                    channels.get(index).getPeer().setHardwareConnected(true);

                                    if (channels.get(index).getPeer().isReconnecting()) {
                                        channels.get(index).getPeer().setRequestingReconnection(false);
                                        // the connection is recovering so we reset the timer, so in case of failure we will still have a disconnection
                                        channels.get(index).resetReconnectionTimer();
                                    }
                                    gatt.discoverServices();

                                    final Channel channel = channels.get(index);
                                    channel.startConnectionCompleteTimer(new Channel.Timer.Callback() {
                                        @Override
                                        public void onFinished() {
                                            // means that the connection failed because it did not happen completely by the end of the timer
                                            if (channel.getPeer().isReconnecting()) {
                                                stopReconnection(channel);
                                            } else {
                                                channel.disconnect(disconnectionCallback);
                                            }
                                        }
                                    });
                                }
                            }

                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            ArrayList<String> channelsNames = new ArrayList<>();
                            for (Channel channel : channels) {
                                channelsNames.add(channel.getPeer().getDevice().getAddress() + " ");
                            }

                            synchronized (channelsLock) {
                                gatt.close();
                                int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                                if (index == -1) {  // in case the device of the channel that failed the reconnection has been changed by onReconnectingPeerFound
                                    Peer peer = pendingConnections.peekFirst();
                                    if (peer != null) {
                                        index = indexOfChannel(peer.getUniqueName());  // the comparison will thus be based on the name instead of the address (which is different in this case) (to be reviewed in case of problems)
                                    }
                                }

                                if (index != -1) {     // is used to manage synchronization with the server to avoid adding a device that connects to the latter instead of us
                                    ((ClientChannel) channels.get(index)).setBluetoothGatt(null);
                                    channels.get(index).getPeer().setHardwareConnected(false);
                                    if (channels.get(index).getPeer().isDisconnecting()) {
                                        channels.get(index).onDisconnected();
                                    }

                                    if (channels.get(index).getPeer().isConnected()) {
                                        if (channels.get(index).getPeer().isDisconnecting()) {
                                            // disconnection
                                            notifyDisconnection(channels.get(index));
                                        } else {
                                            // connection lost
                                            notifyConnectionLost(channels.get(index));
                                        }

                                    } else {
                                        if (channels.get(index).getPeer().isReconnecting()) {
                                            if (channels.get(index).getPeer().isRequestingReconnection()) {
                                                if (channels.get(index).getReconnectionTimer() != null && !channels.get(index).getReconnectionTimer().isFinished()) {
                                                    // pending connections update
                                                    pendingConnections.setFirst(channels.get(index).getPeer());
                                                    // reconnection
                                                    connect();
                                                }
                                            } else {
                                                if (channels.get(index).getPeer().isDisconnecting()) {
                                                    // we had a disconnection after the stopReconnection call (due to the latter method)
                                                    notifyDisconnection(channels.get(index));

                                                } else {
                                                    // we had a disconnect between the hw connection and the complete connection
                                                    stopReconnection(channels.get(index));

                                                }
                                            }
                                        } else {
                                            // connection failed
                                            notifyConnectionFailed(channels.get(index));
                                        }

                                    }
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                            if (index != -1) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (bluetoothAdapter.isLe2MPhySupported()) {
                                        gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_LE_2M, BluetoothDevice.PHY_OPTION_NO_PREFERRED);   // onPhyUpdate isn't always called so it's unreliable
                                    }
                                }
                                if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isDisconnecting()) {
                                    try {
                                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

                                        BluetoothGattService service = gatt.getService(BluetoothConnection.APP_UUID);
                                        BluetoothGattCharacteristic mtuResponse = service.getCharacteristic(BluetoothConnectionServer.MTU_RESPONSE_UUID);
                                        gatt.setCharacteristicNotification(mtuResponse, true);

                                        //request mtu
                                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.MTU_REQUEST_UUID);
                                        byte[] data = new byte[128];
                                        for (int i = 0; i < 128; i++) {
                                            data[i] = String.valueOf(1).getBytes(StandardCharsets.UTF_8)[0];
                                        }
                                        output.setValue(data);
                                        gatt.writeCharacteristic(output);
                                    } catch (Exception e) {
                                        //configuration failed
                                        channels.get(index).disconnect(disconnectionCallback);
                                    }
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onMtuChanged(final BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                            if (index != -1) {
                                if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isDisconnecting()) {
                                    try {
                                        BluetoothGattService service = gatt.getService(BluetoothConnection.APP_UUID);

                                        BluetoothGattCharacteristic connectionResponse = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID);
                                        gatt.setCharacteristicNotification(connectionResponse, true);

                                        BluetoothGattCharacteristic connectionResumedReceived = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID);
                                        gatt.setCharacteristicNotification(connectionResumedReceived, true);

                                        BluetoothGattCharacteristic messageReceive = service.getCharacteristic(BluetoothConnectionServer.MESSAGE_SEND_UUID);
                                        gatt.setCharacteristicNotification(messageReceive, true);

                                        BluetoothGattCharacteristic dataReceive = service.getCharacteristic(BluetoothConnectionServer.DATA_SEND_UUID);
                                        gatt.setCharacteristicNotification(dataReceive, true);

                                        BluetoothGattCharacteristic disconnectionReceive = service.getCharacteristic(BluetoothConnectionServer.DISCONNECTION_SEND_UUID);
                                        gatt.setCharacteristicNotification(disconnectionReceive, true);

                                        if (channels.get(index).getPeer().isReconnecting()) {
                                            // send the name (for cases where it has changed in the meantime) and the key (to avoid man in the middle during reconnection) with notifyConnectionResumed()
                                            if (!((ClientChannel) channels.get(index)).notifyConnectionResumed()) {
                                                throw new Exception();
                                            }
                                        } else {
                                            if (!((ClientChannel) channels.get(index)).requestConnection(getUniqueName())) {
                                                throw new Exception();
                                            }
                                        }
                                    } catch (Exception e) {
                                        //configuration failed
                                        channels.get(index).disconnect(disconnectionCallback);
                                    }
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                            if (characteristic.getUuid().equals(BluetoothConnectionServer.MTU_RESPONSE_UUID)) {
                                if (index != -1) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            int responseValue = Integer.valueOf(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                                            if (responseValue < (BluetoothConnection.SUB_MESSAGES_LENGTH + 8)) {
                                                gatt.requestMtu(MTU);
                                            } else {
                                                channelsCallback.onMtuChanged(gatt, MTU, BluetoothGatt.GATT_SUCCESS);
                                            }
                                        }
                                    });
                                }

                            }
                            if (characteristic.getUuid().equals(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID)) {
                                if (index != -1) {
                                    if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting() && !channels.get(index).getPeer().isDisconnecting()) {
                                        int responseValue = Integer.valueOf(new String(characteristic.getValue(), StandardCharsets.UTF_8));

                                        if (responseValue == BluetoothConnectionServer.ACCEPT) {
                                            notifyConnectionSuccess(channels.get(index));

                                        } else if (responseValue == BluetoothConnectionServer.REJECT) {
                                            notifyConnectionRejected(channels.get(index));

                                        }
                                    }
                                }

                            } else if (characteristic.getUuid().equals(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID)) {
                                if (index != -1) {
                                    if (channels.get(index).getPeer().isReconnecting() && !channels.get(index).getPeer().isDisconnecting()) {
                                        int responseValue = Integer.valueOf(new String(characteristic.getValue(), StandardCharsets.UTF_8));

                                        if (responseValue == BluetoothConnectionServer.ACCEPT) {
                                            // connection resumed
                                            notifyConnectionResumed(channels.get(index));

                                        } else if (responseValue == BluetoothConnectionServer.REJECT) {
                                            // reconnection failed
                                            stopReconnection(channels.get(index));

                                        }
                                    }
                                }

                            } else if (characteristic.getUuid().equals(BluetoothConnectionServer.NAME_UPDATE_SEND_UUID)) {
                                if (index != -1) {
                                    Peer newPeer = (Peer) channels.get(index).getPeer().clone();
                                    newPeer.setUniqueName(new String(characteristic.getValue(), StandardCharsets.UTF_8));
                                    notifyPeerUpdated(channels.get(index), newPeer);
                                }

                            } else if (characteristic.getUuid().equals(BluetoothConnectionServer.DISCONNECTION_SEND_UUID)) {
                                if (index != -1) {
                                    channels.get(index).disconnect(disconnectionCallback);
                                }
                            } else if (characteristic.getUuid().equals(BluetoothConnectionServer.MESSAGE_SEND_UUID)) {
                                if (index != -1) {
                                    channels.get(index).pausePendingMessage();
                                    Peer sender = (Peer) channels.get(index).getPeer().clone();
                                    BluetoothMessage subMessage = BluetoothMessage.createFromBytes(context, sender, characteristic.getValue());
                                    if (subMessage != null) {
                                        if(!channels.get(index).getReceivedMessages().contains(subMessage)) {
                                            int messageIndex = channels.get(index).getReceivingMessages().indexOf(subMessage);
                                            if (messageIndex == -1) {
                                                channels.get(index).getReceivingMessages().add(subMessage);
                                                messageIndex = channels.get(index).getReceivingMessages().size() - 1;
                                            } else {
                                                channels.get(index).getReceivingMessages().get(messageIndex).addMessage(subMessage);
                                            }
                                            if (subMessage.getType() == BluetoothMessage.FINAL) {
                                                BluetoothMessage bluetoothMessage = channels.get(index).getReceivingMessages().remove(messageIndex);
                                                Message message = bluetoothMessage.convertInMessage();
                                                channels.get(index).addReceivedMessage(bluetoothMessage);
                                                if (message != null) {
                                                    Log.e("clientMessageReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                                    notifyMessageReceived(message);
                                                }
                                            }
                                        }
                                        //response
                                        BluetoothGattService service = gatt.getService(BluetoothConnection.APP_UUID);
                                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.READ_RESPONSE_MESSAGE_RECEIVED_UUID);
                                        if (output != null) {
                                            byte[] responseData = Tools.concatBytes(subMessage.getId().getValue().getBytes(StandardCharsets.UTF_8), subMessage.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                                            output.setValue(responseData);
                                            gatt.writeCharacteristic(output);
                                        }
                                        channels.get(index).resumePendingMessage();
                                    }
                                }
                            } else if (characteristic.getUuid().equals(BluetoothConnectionServer.DATA_SEND_UUID)) {
                                if (index != -1) {
                                    channels.get(index).pausePendingData();
                                    gatt.readCharacteristic(characteristic);
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                Log.e("readResponse", "received");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));

                                //characteristic read
                                if (characteristic.getUuid().equals(BluetoothConnectionServer.DATA_SEND_UUID)) {
                                    //here the characteristic has also a value
                                    if (index != -1) {
                                        Peer sender = (Peer) channels.get(index).getPeer().clone();
                                        BluetoothMessage subData = BluetoothMessage.createFromBytes(context, sender, characteristic.getValue());
                                        if (subData != null) {
                                            if(!channels.get(index).getReceivedData().contains(subData)) {
                                                int dataIndex = channels.get(index).getReceivingData().indexOf(subData);
                                                if (dataIndex == -1) {
                                                    channels.get(index).getReceivingData().add(subData);
                                                    dataIndex = channels.get(index).getReceivingData().size() - 1;
                                                } else {
                                                    channels.get(index).getReceivingData().get(dataIndex).addMessage(subData);
                                                }
                                                if (subData.getType() == BluetoothMessage.FINAL) {
                                                    BluetoothMessage bluetoothMessage = channels.get(index).getReceivingData().remove(dataIndex);
                                                    Message message = bluetoothMessage.convertInMessage();
                                                    channels.get(index).addReceivedData(bluetoothMessage);
                                                    if (message != null) {
                                                        Log.e("clientDataReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                                        notifyDataReceived(message);
                                                    }
                                                }
                                            }
                                            //response
                                            BluetoothGattService service = gatt.getService(BluetoothConnection.APP_UUID);
                                            BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.READ_RESPONSE_DATA_RECEIVED_UUID);
                                            if (output != null) {
                                                byte[] responseData = Tools.concatBytes(subData.getId().getValue().getBytes(StandardCharsets.UTF_8), subData.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                                                output.setValue(responseData);
                                                gatt.writeCharacteristic(output);
                                            }
                                            channels.get(index).resumePendingData();
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(gatt.getDevice(), null, true));
                            if (index != -1) {
                                if (BluetoothConnectionServer.CONNECTION_REQUEST_UUID.equals(characteristic.getUuid())) {
                                    if (status == REJECT) {
                                        notifyConnectionRejected(channels.get(index));
                                    }

                                } else if (BluetoothConnectionServer.MESSAGE_RECEIVE_UUID.equals(characteristic.getUuid())) {
                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                        int totalLength = BluetoothMessage.ID_LENGTH + BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                                        String completeText = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                                        if (completeText.length() >= totalLength) {
                                            BluetoothMessage.SequenceNumber id = new BluetoothMessage.SequenceNumber(context, completeText.substring(0, BluetoothMessage.ID_LENGTH), BluetoothMessage.ID_LENGTH);
                                            BluetoothMessage.SequenceNumber sequenceNumber = new BluetoothMessage.SequenceNumber(context, completeText.substring(BluetoothMessage.ID_LENGTH, totalLength), BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                                            BluetoothMessage pendingSubMessage = channels.get(index).getPendingSubMessage();
                                            // if pendingSubMessage is null or does not match it means that the message has already been confirmed, or has yet to be confirmed,
                                            // but we do nothing because this is only a repetition of a previous confirmation
                                            if (pendingSubMessage != null && id.equals(pendingSubMessage.getId()) && sequenceNumber.equals(pendingSubMessage.getSequenceNumber())) {
                                                channels.get(index).onSubMessageWriteSuccess();
                                            }

                                        }
                                    } else {
                                        channels.get(index).onSubMessageWriteFailed();
                                    }

                                } else if (BluetoothConnectionServer.DATA_RECEIVE_UUID.equals(characteristic.getUuid())) {
                                    if (status == BluetoothGatt.GATT_SUCCESS) {
                                        int totalLength = BluetoothMessage.ID_LENGTH + BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                                        String completeText = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                                        if (completeText.length() >= totalLength) {
                                            BluetoothMessage.SequenceNumber id = new BluetoothMessage.SequenceNumber(context, completeText.substring(0, BluetoothMessage.ID_LENGTH), BluetoothMessage.ID_LENGTH);
                                            BluetoothMessage.SequenceNumber sequenceNumber = new BluetoothMessage.SequenceNumber(context, completeText.substring(BluetoothMessage.ID_LENGTH, totalLength), BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                                            BluetoothMessage pendingSubData = channels.get(index).getPendingSubData();
                                            // if pendingSubData is null or does not match it means that the message has already been confirmed, or has yet to be confirmed,
                                            // but we do nothing because this is only a repetition of a previous confirmation
                                            if (pendingSubData != null && id.equals(pendingSubData.getId()) && sequenceNumber.equals(pendingSubData.getSequenceNumber())) {
                                                channels.get(index).onSubDataWriteSuccess();
                                            }

                                        }
                                    } else {
                                        channels.get(index).onSubDataWriteFailed();
                                    }

                                } else if (BluetoothConnectionServer.DISCONNECTION_RECEIVE_UUID.equals(characteristic.getUuid())) {
                                    channels.get(index).disconnect(disconnectionCallback);

                                }
                            }
                        }
                    }
                });
            }
        };
    }

    public void connect(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (pendingConnections.addLast(peer)) {
                    if (pendingConnections.size() == 1) {
                        connect();
                    }
                }
            }
        });
    }

    private void reconnect(Peer peer) {
        if (pendingConnections.addLast((Peer) peer.clone())) {
            if (pendingConnections.size() == 1) {
                connect();
            }
        }
    }

    private void connect() {
        synchronized (channelsLock) {
            Peer peer = pendingConnections.peekFirst();
            if (peer != null) {
                int index = indexOfChannel(peer.getUniqueName());
                if (index == -1) {
                    // connection
                    channels.add(new ClientChannel(context, peer));
                    index = channels.size() - 1;
                    BluetoothGatt gatt = channels.get(index).getPeer().getRemoteDevice(bluetoothAdapter).connectGatt(context, false, channelsCallback, BluetoothDevice.TRANSPORT_LE);
                    if (gatt != null) {
                        ((ClientChannel) channels.get(index)).setBluetoothGatt(gatt);
                    } else {
                        // connection failed
                        notifyConnectionFailed(channels.get(index));
                    }

                } else if (channels.get(index).getPeer().isReconnecting()) {
                    // reconnection
                    BluetoothGatt gatt = channels.get(index).getPeer().getRemoteDevice(bluetoothAdapter).connectGatt(context, false, channelsCallback, BluetoothDevice.TRANSPORT_LE);
                    if (gatt != null) {
                        ((ClientChannel) channels.get(index)).setBluetoothGatt(gatt);
                    } else {
                        // reconnection failed
                        stopReconnection(channels.get(index));
                    }
                }
            }
        }
    }

    @Override
    public void readPhy(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    int index = channels.indexOf(peer);
                    if (index != -1) {
                        channels.get(index).readPhy();
                    }
                }
            }
        });
    }

    @Override
    public void updateName(final String uniqueName) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    setUniqueName(uniqueName);
                    for (Channel channel : channels) {
                        channel.notifyNameUpdated(uniqueName);
                    }
                }
            }
        });
    }

    @Override
    protected void stopReconnection(final Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.resetReconnectionTimer();   // if it has not been called since the timer has expired

        channel.getPeer().setRequestingReconnection(false);
        channel.disconnect(new Channel.DisconnectionCallback() {
            @Override
            public void onAlreadyDisconnected(Peer peer) {
                notifyDisconnection(channel);
            }

            @Override
            public void onDisconnectionFailed() {
                disconnectionCallback.onDisconnectionFailed();
            }
        });    // to cancel a possible connection in progress and to notify the disconnection
    }

    private boolean refreshDeviceCache(BluetoothGatt gatt) {
        try {
            Method localMethod = gatt.getClass().getMethod("refresh");
            return (Boolean) localMethod.invoke(gatt, new Object[0]);
        } catch (Exception localException) {
            Log.e("ble", "An exception occured while refreshing device");
        }
        return false;
    }

    public void onReconnectingPeerFound(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    int index = indexOfChannel(peer.getUniqueName());
                    if (index != -1 && !channels.get(index).getPeer().isHardwareConnected() && channels.get(index).getPeer().isReconnecting() && !channels.get(index).getPeer().isDisconnecting()) {
                        // channel update
                        Peer newPeer = (Peer) channels.get(index).getPeer().clone();
                        newPeer.setDevice(peer.getDevice());
                        notifyPeerUpdated(channels.get(index), newPeer);
                        if (!channels.get(index).getPeer().isRequestingReconnection()) {
                            channels.get(index).getPeer().setRequestingReconnection(true);
                            // reconnection
                            reconnect(channels.get(index).getPeer());
                        }
                    }
                }
            }
        });
    }


    @Override
    protected void notifyConnectionSuccess(Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.getPeer().setConnected(true);
        callback.onConnectionSuccess((Peer) channel.getPeer().clone(), BluetoothCommunicator.CLIENT);

        pendingConnections.removeFirst();   // remove the peer that ended the connection
    }

    private void notifyConnectionRejected(Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.disconnect(disconnectionCallback);
        callback.onConnectionFailed((Peer) channel.getPeer().clone(), CONNECTION_REJECTED);

        pendingConnections.removeFirst();   // remove the peer that ended the connection
    }

    private void notifyConnectionFailed(Channel channel) {
        channels.remove(channel);
        callback.onConnectionFailed((Peer) channel.getPeer().clone(), ERROR);

        pendingConnections.removeFirst();   // remove the peer that ended the connection
    }

    @Override
    protected void notifyMessageReceived(Message message) {
        callback.onMessageReceived(message, BluetoothCommunicator.CLIENT);
    }

    @Override
    protected void notifyDataReceived(Message data) {
        callback.onDataReceived(data, BluetoothCommunicator.CLIENT);
    }

    @Override
    protected void notifyConnectionLost(final Channel channel) {
        channel.getPeer().setReconnecting(true, false);
        callback.onConnectionLost((Peer) channel.getPeer().clone());
        channel.startReconnectionTimer(new Channel.Timer.Callback() {
            @Override
            public void onFinished() {
                // reconnection failed
                stopReconnection(channel);
            }
        });
    }

    @Override
    protected void notifyConnectionResumed(Channel channel) {
        channel.resetConnectionCompleteTimer();
        int index = channels.indexOf(channel);
        if (index != -1) {
            channels.set(index, channel);
            channel.getPeer().setReconnecting(false, true);
            callback.onConnectionResumed((Peer) channel.getPeer().clone());
        }

        pendingConnections.removeFirst();   // remove the peer that ended the connection
    }

    @Override
    protected void notifyPeerUpdated(Channel channel, Peer newPeer) {
        Peer peerClone = (Peer) channel.getPeer().clone();
        channel.setPeer(newPeer);
        callback.onPeerUpdated(peerClone, newPeer);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    protected void notifyDisconnection(Channel channel) {
        pendingConnections.remove(channel.getPeer());   // remove the peer in case it is trying to reconnect

        channels.remove(channel);
        channel.getPeer().setReconnecting(false, false);  // we also set the reconnecting to false in case we were reconnecting before the disconnection took place
        callback.onDisconnected((Peer) channel.getPeer().clone());
    }

    private class ConnectionDeque {
        private ArrayList<Peer> pendingConnections = new ArrayList<>();


        private boolean addLast(Peer peer) {
            if (!pendingConnections.contains(peer)) {
                pendingConnections.add(0, peer);
                return true;
            }
            return false;
        }

        private void remove(Peer peer) {
            int index = indexOf(peer);
            pendingConnections.remove(peer);
            if (index == pendingConnections.size() - 1) {
                connect();
            }
        }

        private void removeFirst() {
            if (pendingConnections.size() > 0) {
                pendingConnections.remove(pendingConnections.size() - 1);
                connect();
            }
        }

        private Peer peekFirst() {
            if (pendingConnections.size() > 0) {
                return pendingConnections.get(pendingConnections.size() - 1);
            } else {
                return null;
            }
        }

        private void set(int index, Peer peer) {
            pendingConnections.set(index, peer);
        }

        private void setFirst(Peer peer) {
            if (pendingConnections.size() > 0) {
                pendingConnections.set(pendingConnections.size() - 1, peer);
            }
        }

        private int size() {
            return pendingConnections.size();
        }

        private int indexOf(Peer peer) {
            int count = 0;
            for (Peer peer1 : pendingConnections) {
                if (peer1.equals(peer)) {
                    return count;
                }
                count++;
            }
            return -1;
        }
    }
}