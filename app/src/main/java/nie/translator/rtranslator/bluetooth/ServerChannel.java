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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import nie.translator.rtranslator.bluetooth.tools.Timer;

@SuppressLint("MissingPermission")
class ServerChannel extends Channel {
    private BluetoothGattServer bluetoothGattServer;
    private final BluetoothAdapter bluetoothAdapter;
    private UUID sendingCharacteristic = null;
    private final Object gattServerLock;


    protected ServerChannel(Context context, @NonNull Peer peer, final BluetoothAdapter bluetoothAdapter, Object gattServerLock) {
        super(context, peer);
        this.bluetoothAdapter = bluetoothAdapter;
        this.gattServerLock = gattServerLock;
    }


    public void setBluetoothGattServer(BluetoothGattServer bluetoothGattServer) {
        synchronized (commandsLock) {
            this.bluetoothGattServer = bluetoothGattServer;
        }
    }

    @Override
    protected void sendSubMessage() {
        if (pendingSubMessages != null) {
            BluetoothMessage subMessageToSend = pendingSubMessages.peekFirst();
            if(!pendingSubMessages.isEmpty()) {
                if (subMessageToSend != null) {
                    Log.e("sub message send", "-");
                    startMessageTimer(new Timer.Callback() {
                        @Override
                        public void onFinished() {
                            /*
                             A confirmation of the success of the command sending the subMessage is not always the same as the confirmation of the sending of the subMessage.
                             This second timer resolve this problem, because will retry the submessage send until it receives a success of the subMessage.
                             */
                            synchronized (messagesLock) {
                                sendSubMessage();
                            }
                        }
                    });
                    Command command = new Command(Command.CommandType.NOTIFICATION, BluetoothConnectionServer.MESSAGE_SEND_UUID, subMessageToSend.getCompleteData());
                    // We check if the subMessage is already present in the commands before adding it.
                    // This is to avoid adding multiple times the same subMessage to the queue in cases its command is already retrying.
                    boolean found = checkBluetoothMessageInCommands(command, subMessageToSend);
                    if(!found) {
                        sendCommand(command);
                    }
                }else{
                    // if there are other subMessages for the message we are sending, we send the next one
                    pendingSubMessages.removeFirst();
                    sendSubMessage();
                }
            }
        }
    }

    @Override
    protected void sendSubData() {
        if (pendingSubData != null) {
            BluetoothMessage subDataToSend = pendingSubData.peekFirst();
            if(!pendingSubData.isEmpty()) {
                if (subDataToSend != null) {
                    Log.e("sub data send", "-");
                    startDataTimer(new Timer.Callback() {
                        @Override
                        public void onFinished() {
                            /* Same principle explained in the sendSubMessage() above*/
                            sendSubData();
                        }
                    });
                    Command command = new Command(Command.CommandType.NOTIFICATION, BluetoothConnectionServer.DATA_SEND_UUID, String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                    // We check if the subData is already present in the commands before adding it.
                    // This is to avoid adding multiple times the same subData read notification to the queue in cases its command is already retrying.
                    boolean found = checkCommandInCommands(command);
                    if(!found) {
                        sendCommand(command);
                    }
                }else{
                    // if there are other subData for the data we are sending, we send the next one
                    pendingSubData.removeFirst();
                    sendSubData();
                }
            }
        }
    }

    @Override
    protected void writeCommand() {
        new Thread() {
            @Override
            public void run() {
                synchronized (lock) {
                    super.run();
                    synchronized (commandsLock) {
                        boolean success = false;
                        if (bluetoothGattServer != null && getPeer().isFullyConnected() && !getPeer().isDisconnecting()) {
                            BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);

                            if (service != null) {
                                Command commandToSend = pendingCommands.peekFirst();
                                if (commandToSend != null) {
                                    if(commandToSend.type == Command.CommandType.NOTIFICATION) {
                                        synchronized (gattServerLock) {
                                            BluetoothGattCharacteristic output = service.getCharacteristic(commandToSend.characteristic);
                                            if (output != null) {
                                                output.setValue(commandToSend.data);
                                                sendingCharacteristic = commandToSend.characteristic;
                                                success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                                                Log.e("subServerCommand send", "-" + success);
                                            }
                                        }
                                    }
                                }else{
                                    // if there are other commands we send the next one immediately, and we exit,
                                    // otherwise we just exit without starting the retry timer
                                    // because the commands to execute are empty
                                    if(!pendingCommands.isEmpty()){
                                        pendingCommands.removeFirst();
                                        writeCommand();
                                    }
                                    return;
                                }
                            }
                        }
                        // Start of message timer, retry if it finishes.
                        // This retry timer is start only if the peer is fully connected, otherwise we will resume
                        // the sending only when the connection is fully restored (to avoid interfering with the handshake messages)
                        if(!success && getPeer().isFullyConnected() && !getPeer().isDisconnecting()) {
                            startCommandTimer(new Timer.Callback() {
                                @Override
                                public void onFinished() {
                                    writeCommand();
                                }
                            });
                        }
                    }
                }
            }
        }.start();
    }

    public UUID getSendingCharacteristic() {
        return sendingCharacteristic;
    }

    @Override
    public void readPhy() {
        synchronized (lock) {
            if (bluetoothGattServer != null && getPeer().isFullyConnected()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bluetoothGattServer.readPhy(getPeer().getRemoteDevice(bluetoothAdapter));
                }
            }
        }
    }

    public boolean acceptConnection() {   //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    synchronized (gattServerLock) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID);
                        if (output != null) {
                            output.setValue(String.valueOf(nie.translator.rtranslator.bluetooth.BluetoothConnection.ACCEPT).getBytes(StandardCharsets.UTF_8));
                            sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESPONSE_UUID;
                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                        }
                    }
                }
            }
            return success;
        }
    }

    public boolean rejectConnection() {  //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    synchronized (gattServerLock) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID);
                        if (output != null) {
                            output.setValue(String.valueOf(nie.translator.rtranslator.bluetooth.BluetoothConnection.REJECT).getBytes(StandardCharsets.UTF_8));
                            sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESPONSE_UUID;
                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                        }
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumed() {  //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    synchronized (gattServerLock) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID);
                        if (output != null) {
                            output.setValue(String.valueOf(nie.translator.rtranslator.bluetooth.BluetoothConnection.ACCEPT).getBytes(StandardCharsets.UTF_8));
                            sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID;
                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                        }
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumedRejected() {   //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    synchronized (gattServerLock) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID);
                        if (output != null) {
                            output.setValue(String.valueOf(nie.translator.rtranslator.bluetooth.BluetoothConnection.REJECT).getBytes(StandardCharsets.UTF_8));
                            sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID;
                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                        }
                    }
                }
            }
            return success;
        }
    }

    @Override
    public void notifyNameUpdated(String uniqueName) {
        Command command = new Command(Command.CommandType.NOTIFICATION, BluetoothConnectionServer.NAME_UPDATE_SEND_UUID, uniqueName.getBytes(StandardCharsets.UTF_8));
        sendCommand(command);
    }

    @Override
    public boolean notifyDisconnection(DisconnectionNotificationCallback disconnectionNotificationCallback) {
        synchronized (lock) {
            boolean success = false;
            if (super.notifyDisconnection(disconnectionNotificationCallback)) {
                // start of notify disconnection timer
                startNotifyDisconnectionTimer(new Timer.Callback() {
                    @Override
                    public void onFinished() {
                        disconnect(disconnectionNotificationCallback);
                    }
                });
                if (bluetoothGattServer != null && getPeer().isFullyConnected()) {
                    BluetoothGattService service = bluetoothGattServer.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                    if (service != null) {
                        synchronized (gattServerLock) {
                            BluetoothGattCharacteristic disconnectionSend = service.getCharacteristic(BluetoothConnectionServer.DISCONNECTION_SEND_UUID);
                            if (disconnectionSend != null) {
                                disconnectionSend.setValue(String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                                sendingCharacteristic = BluetoothConnectionServer.DISCONNECTION_SEND_UUID;
                                success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), disconnectionSend, true);
                            }
                        }
                    }
                }
            }
            return success;
        }
    }

    @Override
    public boolean disconnect(DisconnectionCallback disconnectionCallback) {
        synchronized (lock) {
            if (super.disconnect(disconnectionCallback)) {
                if (bluetoothGattServer != null) {
                    Log.d("bluetooth_communicator_server", "disconnect, peer: " + getPeer().getName());
                    bluetoothGattServer.cancelConnection(getPeer().getRemoteDevice(bluetoothAdapter));
                    disconnectionCallback.onServerDisconnectionSuccess(getPeer());  //here the resources are cleared and the disconnection notified to BluetoothCommunicator (there is no need to call any close method for the server side)
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void destroy() {
        synchronized (lock) {
            super.destroy();
            if (bluetoothGattServer != null) {
                bluetoothGattServer.cancelConnection(getPeer().getRemoteDevice(bluetoothAdapter));
            }
        }
    }
}
