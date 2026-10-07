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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import nie.translator.rtranslator.bluetooth.tools.BluetoothTools;
import nie.translator.rtranslator.bluetooth.tools.Timer;


@SuppressLint("MissingPermission")
class BluetoothConnectionServer extends nie.translator.rtranslator.bluetooth.BluetoothConnection {
    //costants
    public static final UUID CONNECTION_REQUEST_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0857350c7a66");
    public static final UUID CONNECTION_RESPONSE_UUID = UUID.fromString("fa87c0d0-adac-11de-8a39-0857350c7a64");
    public static final UUID CONNECTION_RESUMED_SEND_UUID = UUID.fromString("fa87c0d1-acbc-11de-8e32-0857350c7a41");
    public static final UUID CONNECTION_RESUMED_RECEIVE_UUID = UUID.fromString("fa87c0d1-afac-11de-8a39-0857350c7a43");
    public static final UUID MTU_REQUEST_UUID = UUID.fromString("fa87c0d4-afac-11de-8a39-0857350c7a60");
    public static final UUID MTU_RESPONSE_UUID = UUID.fromString("fa87c0d5-adac-11de-8a39-0857350c7a61");
    public static final UUID MESSAGE_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0830350c9a66");
    public static final UUID DATA_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a33-0830350c9a66");
    public static final UUID MESSAGE_RECEIVE_UUID = UUID.fromString("fa87c0d0-afac-11dc-8a39-0850350c8a66");
    public static final UUID DATA_RECEIVE_UUID = UUID.fromString("fa87c0d0-afac-11dd-8a32-0850350c8a66");
    public static final UUID READ_RESPONSE_MESSAGE_RECEIVED_UUID = UUID.fromString("fa87c0d0-aaac-11df-8a38-0897350c8a60");
    public static final UUID READ_RESPONSE_DATA_RECEIVED_UUID = UUID.fromString("fa87c0d0-aaac-11df-8a38-0897350c8f65");
    public static final UUID NAME_UPDATE_SEND_UUID = UUID.fromString("fa87c0d4-afab-11de-8a39-0857350c7a42");
    public static final UUID NAME_UPDATE_RECEIVE_UUID = UUID.fromString("fa87c0d1-afab-11de-8a39-0857350c7a40");
    public static final UUID DISCONNECTION_SEND_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0897350c5a66");
    public static final UUID DISCONNECTION_RECEIVE_UUID = UUID.fromString("fa87c0d0-adac-11de-8a38-0897350c5a65");
    //objects
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothManager bluetoothManager;
    private final BluetoothConnectionClient client;  // the client object is used to manage synchronization with the client to avoid adding a device that connects to the latter instead of us


    public BluetoothConnectionServer(final Context context, final String name, @NonNull final BluetoothAdapter bluetoothAdapter, final int strategy, final BluetoothConnectionClient client, final Callback callback) {
        super(context, name, bluetoothAdapter, strategy, callback);
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        this.disconnectionCallback = new Channel.DisconnectionCallback() {
            @Override
            public void onAlreadyDisconnected(Peer peer) {
                int index = channels.indexOf(peer);
                if (index != -1) {
                    channels.remove(index);
                }
            }

            @Override
            public void onServerDisconnectionSuccess(Peer peer){
                manageDisconnection(peer);
            }
        };

        this.client = client;
        initializeBluetoothGattServer();
    }

    public void initializeBluetoothGattServer(){
        bluetoothGattServer = bluetoothManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, final int newState) {
                super.onConnectionStateChange(device, status, newState);
                final Peer peer = new Peer(device, null, false);
                mainHandler.post(new Runnable() {   //anche se non serve si mette solo per questioni di simmetria col server a livello programmatico
                    @Override
                    public void run() {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d("bluetooth_communicator_server", "onConnectionStateChange, connected peer: " + device.getName());
                            synchronized (channelsLock) {
                                if(!client.getConnectedPeers().contains(peer)) {    // the client object is used to manage synchronization with the client to avoid adding a device that connects to the latter instead of us
                                    bluetoothGattServer.connect(device, false);  //this is not mandatory but will tell the server OS not to drop the connection, making it more stable. Plus, this is necessary to make cancelConnection work (this isn't documented, but it is well observed by many developers).

                                    int index = channels.indexOf(peer);

                                    if (index == -1) {  // in case the device is reconnecting and has changed its hw address (very likely)
                                        /* The comparison will thus be based on the name instead of the address (which is different in this case)
                                        * The name of the device however, usually is not present (null), we keep this check only for the rare exception.  */
                                       /* Usually, if the name in device.getName() is null, the index will still be -1, we create a new channel, like in a normal new connection,
                                        * we continue the normal handshake like we are creating a new connection. The handshake is the same between connection and reconnection up until
                                        * we receive a CONNECTION_RESUMED_RECEIVE_UUID instead of a CONNECTION_REQUEST_UUID, in this case, if this message is targeted to a new
                                        * channel that is doing a connection handshake, and we also have a channel that is reconnecting and has the same uniqueName passed in the resume message,
                                        * we will pass the important data (device, connection params, ecc.) to the old reconnecting channel and delete the new connecting channel (like a substitution)
                                        * and continue the reconnection handshake on the old reconnecting channel. */
                                        index = indexOfChannel(device.getName());
                                    }

                                    if (index == -1) {
                                        channels.add(new nie.translator.rtranslator.bluetooth.ServerChannel(context, peer, bluetoothAdapter));
                                        index = channels.size() - 1;
                                        ((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).setBluetoothGattServer(bluetoothGattServer);
                                        channels.get(index).getPeer().setHardwareConnected(true);

                                    } else {
                                        index = channels.indexOf(peer);
                                        if (index != -1) {
                                            ((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).setBluetoothGattServer(bluetoothGattServer);
                                            channels.get(index).getPeer().setHardwareConnected(true);
                                            if (channels.get(index).getPeer().isReconnecting()) {
                                                // the connection is recovering so we reset the timer, so in case of failure we will still have a disconnection
                                                channels.get(index).resetReconnectionTimer();
                                            }
                                        }

                                    }
                                    if (index != -1) {
                                        final Channel channel = channels.get(index);
                                        channel.startConnectionCompleteTimer(new Timer.Callback() {
                                            @Override
                                            public void onFinished() {
                                                mainHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        // means that the connection failed because it did not happen completely by the end of the timer
                                                        manageConnectionCompleteTimerExpiration(channel);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }
                            }

                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d("bluetooth_communicator_server", "onConnectionStateChange, disconnected peer: " + device.getName());
                            manageDisconnection(peer);
                        }
                    }
                });
            }

            @Override
            public synchronized void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, final int offset, final byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(device, null, true));

                            if (characteristic.getUuid().equals(CONNECTION_REQUEST_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, CONNECTION_REQUEST_UUID, peer: " + device.getName());
                                    if (!channels.get(index).getPeer().isDisconnecting()) {
                                        if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                                            String data = new String(value, StandardCharsets.UTF_8);
                                            channels.get(index).getPeer().setUniqueName(data);

                                            notifyConnectionRequest(channels.get(index));
                                            bluetoothGattServer.sendResponse(device, requestId, ACCEPT, offset, null);

                                        } else if (channels.get(index).getPeer().isReconnecting()) {
                                            bluetoothGattServer.sendResponse(device, requestId, REJECT, offset, null);  // it must be put before the disconnection otherwise we have errors
                                            stopReconnection(channels.get(index));

                                        } else {
                                            bluetoothGattServer.sendResponse(device, requestId, ACCEPT, offset, null);
                                        }
                                    } else {
                                        bluetoothGattServer.sendResponse(device, requestId, REJECT, offset, null);
                                    }
                                }

                            } else if (characteristic.getUuid().equals(CONNECTION_RESUMED_RECEIVE_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, CONNECTION_RESUMED_RECEIVE_UUID, peer: " + device.getName());
                                    if (!channels.get(index).getPeer().isDisconnecting()) {
                                        if (channels.get(index).getPeer().isReconnecting()) {
                                            if (!((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).notifyConnectionResumed()) {
                                                stopReconnection(channels.get(index));
                                            }
                                        } else {
                                            /* If a channel is not reconnecting but receives a CONNECTION_RESUMED_RECEIVE_UUID instead of a CONNECTION_REQUEST_UUID,
                                             * it means that the new channel in connection handshake phase is actually one of the reconnecting channels, but with a different
                                             * hw address (that has changed during the connection loss) and without a name passed in onConnectionStateChange (see there for more info).
                                             * So we have in fact 2 channels that point to the same device. The problem is that we had no way to identify the new connecting channel
                                             * as the same device of a reconnecting channel (different address and no uniqueName). But now we can use the uniqueName passed in this
                                             * message to identify the connecting channel, if it has the same uniqueName of a channel that is reconnecting, we will pass the important
                                             * data (device, connection params, ecc.) to the old reconnecting channel and delete the new connecting channel (like a substitution)
                                             * and continue the reconnection handshake on the old reconnecting channel.
                                             * If there is no correlation with a reconnecting channel or if the uniqueName is not passed by the client (old versions of the library
                                             * passed the value 1 instead), and the channel that receives the message is not connected (so it has not finished the handshake), we will
                                             * respond with a rejection.
                                             */
                                            String uniqueName = new String(value, StandardCharsets.UTF_8);
                                            int indexReconnecting = indexOfChannel(uniqueName);
                                            if(indexReconnecting != -1 && channels.get(indexReconnecting).getPeer().isReconnecting() && uniqueName.length() > 1){  //if uniqueName is 1 char long it means that the client is using an old version of the protocol that send the value 1 instead of the uniqueName (min 2 char long)
                                                Channel reconnectingChannel = channels.get(indexReconnecting);
                                                Channel placeholderChannel = channels.remove(index);
                                                // set up of timers
                                                placeholderChannel.resetConnectionCompleteTimer();
                                                reconnectingChannel.resetReconnectionTimer();
                                                reconnectingChannel.startConnectionCompleteTimer(new Timer.Callback() {
                                                    @Override
                                                    public void onFinished() {
                                                        // means that the connection failed because it did not happen completely by the end of the timer
                                                        manageConnectionCompleteTimerExpiration(reconnectingChannel);
                                                    }
                                                });
                                                // update data of reconnectingChannel
                                                reconnectingChannel.setSubMessagesLength(placeholderChannel.getSubMessagesLength());
                                                ((ServerChannel) reconnectingChannel).setBluetoothGattServer(bluetoothGattServer);
                                                Peer newPeer = (Peer) reconnectingChannel.getPeer().clone();
                                                newPeer.setUniqueName(uniqueName);
                                                newPeer.setDevice(device);
                                                newPeer.setHardwareConnected(true);
                                                notifyPeerUpdated(reconnectingChannel, newPeer);

                                                if (!((nie.translator.rtranslator.bluetooth.ServerChannel) reconnectingChannel).notifyConnectionResumed()) {
                                                    stopReconnection(reconnectingChannel);
                                                }

                                            }else if (!channels.get(index).getPeer().isConnected()) {
                                                /* means that the peer for which we accepted the connection request without having it in the list of channels is not starting a connection but is resetting it,
                                                 but we are not, so we disconnect, otherwise we would remain forever waiting for the connection request */
                                                channels.get(index).getPeer().setDisconnecting(true);
                                                if (!((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).notifyConnectionResumedRejected()) {
                                                    channels.get(index).disconnect(disconnectionCallback);
                                                }
                                            }
                                        }
                                    }
                                }

                            } else if (characteristic.getUuid().equals(MTU_REQUEST_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, MTU_REQUEST_UUID, peer: " + device.getName());
                                    if (!channels.get(index).getPeer().isDisconnecting()) {
                                        try {
                                            int maxMessageLength = value.length;
                                            BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                                            BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.MTU_RESPONSE_UUID);
                                            channels.get(index).setSubMessagesLength(maxMessageLength - 1); //we keep at least 1 byte as extra margin
                                            output.setValue(String.valueOf(maxMessageLength).getBytes(StandardCharsets.UTF_8));
                                            bluetoothGattServer.notifyCharacteristicChanged(channels.get(index).getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                                        } catch (Exception e) {
                                            channels.get(index).disconnect(disconnectionCallback);
                                        }
                                    }
                                }

                            } else if (characteristic.getUuid().equals(MESSAGE_RECEIVE_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, MESSAGE_RECEIVE_UUID, peer: " + device.getName());
                                    Peer sender = (Peer) channels.get(index).getPeer().clone();
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage subMessage = nie.translator.rtranslator.bluetooth.BluetoothMessage.createFromBytes(context, sender, value);
                                    if (subMessage != null) {
                                        if(!channels.get(index).getReceivedMessages().contains(subMessage)) {
                                            int messageIndex = channels.get(index).getReceivingMessages().indexOf(subMessage);
                                            if (messageIndex == -1) {
                                                channels.get(index).getReceivingMessages().add(subMessage);
                                                messageIndex = channels.get(index).getReceivingMessages().size() - 1;
                                            } else {
                                                channels.get(index).getReceivingMessages().get(messageIndex).addMessage(subMessage);
                                            }
                                            if (subMessage.getType() == nie.translator.rtranslator.bluetooth.BluetoothMessage.FINAL) {
                                                nie.translator.rtranslator.bluetooth.BluetoothMessage bluetoothMessage = channels.get(index).getReceivingMessages().remove(messageIndex);
                                                Message message = bluetoothMessage.convertInMessage();
                                                channels.get(index).addReceivedMessage(bluetoothMessage);
                                                if (message != null) {
                                                    Log.e("clientMessageReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                                    notifyMessageReceived(message);
                                                }
                                            }
                                        }
                                        //response
                                        byte[] responseData = BluetoothTools.concatBytes(subMessage.getId().getValue().getBytes(StandardCharsets.UTF_8), subMessage.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                                        bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData);
                                    }
                                }

                            } else if (characteristic.getUuid().equals(DATA_RECEIVE_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, DATA_RECEIVE_UUID, peer: " + device.getName());
                                    Peer sender = (Peer) channels.get(index).getPeer().clone();
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage subData = nie.translator.rtranslator.bluetooth.BluetoothMessage.createFromBytes(context, sender, value);
                                    if (subData != null) {
                                        if(!channels.get(index).getReceivedData().contains(subData)) {
                                            int dataIndex = channels.get(index).getReceivingData().indexOf(subData);
                                            if (dataIndex == -1) {
                                                channels.get(index).getReceivingData().add(subData);
                                                dataIndex = channels.get(index).getReceivingData().size() - 1;
                                            } else {
                                                channels.get(index).getReceivingData().get(dataIndex).addMessage(subData);
                                            }
                                            if (subData.getType() == nie.translator.rtranslator.bluetooth.BluetoothMessage.FINAL) {
                                                nie.translator.rtranslator.bluetooth.BluetoothMessage bluetoothMessage = channels.get(index).getReceivingData().remove(dataIndex);
                                                Message message = bluetoothMessage.convertInMessage();
                                                channels.get(index).addReceivedData(bluetoothMessage);
                                                if (message != null) {
                                                    Log.e("clientDataReceive", message.getText() + "-" + message.getSender().getDevice().getAddress());
                                                    notifyDataReceived(message);
                                                }
                                            }
                                        }
                                        //response
                                        byte[] responseData = BluetoothTools.concatBytes(subData.getId().getValue().getBytes(StandardCharsets.UTF_8), subData.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
                                        bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseData);
                                    }
                                }

                            } else if (characteristic.getUuid().equals(READ_RESPONSE_MESSAGE_RECEIVED_UUID)) {
                                Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, READ_RESPONSE_MESSAGE_RECEIVED_UUID, peer: " + device.getName());
                                int totalLength = nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH + nie.translator.rtranslator.bluetooth.BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                                String completeText = new String(value, StandardCharsets.UTF_8);
                                if (completeText.length() >= totalLength) {
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber id = new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, completeText.substring(0, nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH), nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH);
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber sequenceNumber = new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, completeText.substring(nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH, totalLength), nie.translator.rtranslator.bluetooth.BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage pendingSubMessage = channels.get(index).getPendingSubMessage();
                                    // if pendingSubMessage is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                                    if (pendingSubMessage != null && id.equals(pendingSubMessage.getId()) && sequenceNumber.equals(pendingSubMessage.getSequenceNumber())) {
                                        channels.get(index).onSubMessageWriteSuccess();
                                    }

                                }

                            } else if (characteristic.getUuid().equals(READ_RESPONSE_DATA_RECEIVED_UUID)) {
                                Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, READ_RESPONSE_DATA_RECEIVED_UUID, peer: " + device.getName());
                                int totalLength = nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH + nie.translator.rtranslator.bluetooth.BluetoothMessage.SEQUENCE_NUMBER_LENGTH;
                                String completeText = new String(value, StandardCharsets.UTF_8);
                                if (completeText.length() >= totalLength) {
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber id = new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, completeText.substring(0, nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH), nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH);
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber sequenceNumber = new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, completeText.substring(nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH, totalLength), nie.translator.rtranslator.bluetooth.BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage pendingSubData = channels.get(index).getPendingSubData();
                                    // if pendingSubData is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                                    if (pendingSubData != null && id.equals(pendingSubData.getId()) && sequenceNumber.equals(pendingSubData.getSequenceNumber())) {
                                        channels.get(index).onSubDataWriteSuccess();
                                    }

                                }

                            } else if (characteristic.getUuid().equals(NAME_UPDATE_RECEIVE_UUID)) {
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, NAME_UPDATE_RECEIVE_UUID, peer: " + device.getName());
                                    Peer newPeer = (Peer) channels.get(index).getPeer().clone();
                                    newPeer.setUniqueName(new String(value, StandardCharsets.UTF_8));
                                    notifyPeerUpdated(channels.get(index), newPeer);
                                }

                            } else if (characteristic.getUuid().equals(DISCONNECTION_RECEIVE_UUID)) {
                                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
                                if (index != -1) {
                                    Log.d("bluetooth_communicator_server", "onCharacteristicWriteRequest, DISCONNECTION_RECEIVE_UUID, peer: " + device.getName());
                                    channels.get(index).disconnect(disconnectionCallback);
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onCharacteristicReadRequest(final BluetoothDevice device, final int requestId, final int offset, final BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(device, null, true));

                            if (index != -1) {
                                try {
                                    nie.translator.rtranslator.bluetooth.BluetoothMessage pendingSubData = channels.get(index).getPendingSubData();
                                    if (pendingSubData != null) {     // if pendingSubData is null or does not match it means that the message has already been confirmed, or has yet to be confirmed, but we do nothing because this is only a repetition of a previous confirmation
                                        if (DATA_SEND_UUID.equals(characteristic.getUuid())) {
                                            Log.d("bluetooth_communicator_server", "onCharacteristicReadRequest, DATA_SEND_UUID, peer: " + device.getName());
                                            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, pendingSubData.getCompleteData());
                                        } else {
                                            throw new Exception();
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onNotificationSent(final BluetoothDevice device, final int status) {
                super.onNotificationSent(device, status);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (channelsLock) {
                            int index = channels.indexOf(new Peer(device, null, true));

                            if (index != -1) {
                                UUID sendingCharacteristic = ((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).getSendingCharacteristic();
                                if (CONNECTION_RESPONSE_UUID.equals(sendingCharacteristic)) {
                                    Log.d("bluetooth_communicator_server", "onNotificationSent, CONNECTION_RESPONSE_UUID, peer: " + device.getName());
                                    if (!channels.get(index).getPeer().isDisconnecting()) {
                                        notifyConnectionSuccess(channels.get(index));
                                    }

                                } else if (CONNECTION_RESUMED_SEND_UUID.equals(sendingCharacteristic)) {
                                    Log.d("bluetooth_communicator_server", "onNotificationSent, CONNECTION_RESUMED_SEND_UUID, peer: " + device.getName());
                                    if (!channels.get(index).getPeer().isDisconnecting()) {
                                        //connection resumed
                                        notifyConnectionResumed(channels.get(index));
                                    }

                                } else if (MESSAGE_SEND_UUID.equals(sendingCharacteristic)) {
                                    Log.d("bluetooth_communicator_server", "onNotificationSent, MESSAGE_SEND_UUID, peer: " + device.getName());
                                    if (status == BluetoothGatt.GATT_FAILURE) {
                                        channels.get(index).onSubMessageWriteFailed();
                                    }

                                } else if (DATA_SEND_UUID.equals(sendingCharacteristic)) {
                                    Log.d("bluetooth_communicator_server", "onNotificationSent, DATA_SEND_UUID, peer: " + device.getName());
                                    if (status == BluetoothGatt.GATT_FAILURE) {
                                        channels.get(index).onSubDataWriteFailed();
                                    }

                                } else if (DISCONNECTION_SEND_UUID.equals(sendingCharacteristic)) {
                                    Log.d("bluetooth_communicator_server", "onNotificationSent, DISCONNECTION_SEND_UUID, peer: " + device.getName());
                                    channels.get(index).disconnect(disconnectionCallback);

                                }
                            }
                        }
                    }
                });
            }

            @Override
            public void onMtuChanged(BluetoothDevice device, int mtu) {
                super.onMtuChanged(device, mtu);
                int index = channels.indexOf(new nie.translator.rtranslator.bluetooth.Peer(device, null, true));
                if (index != -1) {
                    Log.d("bluetooth_communicator_server", "onMtuChanged on server: "+mtu);
                    channels.get(index).setSubMessagesLength(mtu - BluetoothConnection.DATA_MARGIN);
                }
            }

            @Override
            public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
                super.onPhyUpdate(device, txPhy, rxPhy, status);
            }
        });


        BluetoothGattService service = new BluetoothGattService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic connectionRequest = new BluetoothGattCharacteristic(CONNECTION_REQUEST_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic connectionResponse = new BluetoothGattCharacteristic(CONNECTION_RESPONSE_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic connectionResumedSend = new BluetoothGattCharacteristic(CONNECTION_RESUMED_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic connectionResumedReceived = new BluetoothGattCharacteristic(CONNECTION_RESUMED_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic mtuRequest = new BluetoothGattCharacteristic(MTU_REQUEST_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic mtuResponse = new BluetoothGattCharacteristic(MTU_RESPONSE_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic messageSend = new BluetoothGattCharacteristic(MESSAGE_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic dataSend = new BluetoothGattCharacteristic(DATA_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic messageReceive = new BluetoothGattCharacteristic(MESSAGE_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic dataReceive = new BluetoothGattCharacteristic(DATA_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic readResponseReceived = new BluetoothGattCharacteristic(READ_RESPONSE_MESSAGE_RECEIVED_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic readResponseDataReceived = new BluetoothGattCharacteristic(READ_RESPONSE_DATA_RECEIVED_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic nameUpdateReceive = new BluetoothGattCharacteristic(NAME_UPDATE_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic nameUpdateSend = new BluetoothGattCharacteristic(NAME_UPDATE_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic disconnectionSend = new BluetoothGattCharacteristic(DISCONNECTION_SEND_UUID, BluetoothGattCharacteristic.PROPERTY_INDICATE, BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic disconnectionReceive = new BluetoothGattCharacteristic(DISCONNECTION_RECEIVE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(connectionRequest);
        service.addCharacteristic(connectionResponse);
        service.addCharacteristic(connectionResumedSend);
        service.addCharacteristic(connectionResumedReceived);
        service.addCharacteristic(mtuRequest);
        service.addCharacteristic(mtuResponse);
        service.addCharacteristic(messageSend);
        service.addCharacteristic(dataSend);
        service.addCharacteristic(messageReceive);
        service.addCharacteristic(dataReceive);
        service.addCharacteristic(readResponseReceived);
        service.addCharacteristic(readResponseDataReceived);
        service.addCharacteristic(nameUpdateReceive);
        service.addCharacteristic(nameUpdateSend);
        service.addCharacteristic(disconnectionSend);
        service.addCharacteristic(disconnectionReceive);
        bluetoothGattServer.addService(service);
    }

    public void acceptConnection(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    int index = channels.indexOf(peer);
                    if (index != -1) {
                        if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                            if (!((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).acceptConnection()) {
                                channels.get(index).disconnect(disconnectionCallback);
                            }
                        }
                    }
                }
            }
        });
    }

    public void rejectConnection(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (channelsLock) {
                    int index = channels.indexOf(peer);
                    if (index != -1) {
                        channels.get(index).resetConnectionCompleteTimer();
                        if (!channels.get(index).getPeer().isConnected() && !channels.get(index).getPeer().isReconnecting()) {
                            channels.get(index).getPeer().setDisconnecting(true);
                            if (!((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).rejectConnection()) {
                                channels.get(index).disconnect(disconnectionCallback);
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void stopReconnection(final Channel channel) {
        Log.d("bluetooth_communicator_server", "stopReconnection, peer: " + channel.getPeer().getName());
        channel.resetConnectionCompleteTimer();
        channel.resetReconnectionTimer();   // if it has not been called since the timer has expired

        channel.disconnect(new Channel.DisconnectionCallback() {
            @Override
            public void onAlreadyDisconnected(Peer peer) {
                notifyDisconnection(channel);
            }
        });    // to cancel a possible connection in progress
    }

    private void manageDisconnection(Peer peer){
        ArrayList<String> channelsNames = new ArrayList<>();
        for (Channel channel : channels) {
            channelsNames.add(channel.getPeer().getDevice().getAddress() + " ");
        }

        synchronized (channelsLock) {
            final int index = channels.indexOf(peer);
            if (index != -1) {
                Log.d("bluetooth_communicator_client", "manageDisconnection, peer: " + peer.getName());
                ((nie.translator.rtranslator.bluetooth.ServerChannel) channels.get(index)).setBluetoothGattServer(null);
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
                        if (channels.get(index).getPeer().isDisconnecting()) {
                            // we had a disconnection after the stopReconnection call (due to the latter method)
                            notifyDisconnection(channels.get(index));

                        } else {
                            // we had a disconnect between the hw connection and the complete connection
                            stopReconnection(channels.get(index));
                        }

                    } else {
                        // means that there has been a disconnect between the connection request and its acceptance.
                        // we delete the disconnected channel
                        channels.remove(index);
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
    public void pauseConnection() {
        super.pauseConnection();
        // clean up of BLE system connection resources
        close();
    }

    @Override
    public void resumeConnection() {
        super.resumeConnection();
        // recreation of BLE system connection resources
        initializeBluetoothGattServer();
    }

    private void manageConnectionCompleteTimerExpiration(Channel channel){
        // means that the connection failed because it did not happen completely by the end of the timer
        Log.d("bluetooth_communicator_server", "connectionCompleteTimer, expired, peer: " + channel.getPeer().getName());
        if (channel.getPeer().isReconnecting()) {
            stopReconnection(channel);
        } else {
            channel.disconnect(disconnectionCallback);
        }
    }

    public void close() {
        if(bluetoothGattServer != null) {
            bluetoothGattServer.close();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        close();
    }

    private void notifyConnectionRequest(Channel channel) {
        callback.onConnectionRequest((Peer) channel.getPeer().clone());
    }

    @Override
    protected void notifyConnectionSuccess(Channel channel) {
        channel.resetConnectionCompleteTimer();
        channel.getPeer().setConnected(true);
        callback.onConnectionSuccess((Peer) channel.getPeer().clone(), BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyMessageReceived(Message message) {
        callback.onMessageReceived(message, BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyDataReceived(Message data) {
        callback.onDataReceived(data, BluetoothCommunicator.SERVER);
    }

    @Override
    protected void notifyConnectionLost(final Channel channel) {
        channel.getPeer().setReconnecting(true, false);
        callback.onConnectionLost((Peer) channel.getPeer().clone());
        channel.startReconnectionTimer(new Timer.Callback() {
            @Override
            public void onFinished() {
                // reconnection failed
                Log.d("bluetooth_communicator_server", "reconnectionTimer, expired, peer: " + channel.getPeer().getName());
                stopReconnection(channel);
            }
        });
    }

    @Override
    protected void notifyConnectionResumed(Channel channel) {
        channel.resetConnectionCompleteTimer();
        int index = channels.indexOf(channel);
        if (index != -1) {
            channel.getPeer().setReconnecting(false, true);
            channels.set(index, channel);
            callback.onConnectionResumed((Peer) channels.get(index).getPeer().clone());
        }
    }

    @Override
    protected void notifyPeerUpdated(Channel channel, Peer newPeer) {
        Peer peerClone = (Peer) channel.getPeer().clone();
        channel.setPeer(newPeer);
        callback.onPeerUpdated(peerClone, newPeer);
    }


    @Override
    protected void notifyDisconnection(Channel channel) {
        channels.remove(channel);
        channel.getPeer().setReconnecting(false, false);  // we also set the reconnecting to false in case we were reconnecting before the disconnection took place
        callback.onDisconnected((Peer) channel.getPeer().clone());
    }
}
