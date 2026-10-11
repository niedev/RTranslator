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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

import nie.translator.rtranslator.bluetooth.tools.BluetoothTools;
import nie.translator.rtranslator.bluetooth.tools.Timer;

@SuppressLint("MissingPermission")
class ClientChannel extends nie.translator.rtranslator.bluetooth.Channel {
    private BluetoothGatt bluetoothGatt;


    public ClientChannel(Context context, @NonNull Peer peer) {
        super(context, peer);
    }


    public void setBluetoothGatt(BluetoothGatt bluetoothGatt) {
        synchronized (lock) {
            synchronized (commandsLock) {
                this.bluetoothGatt = bluetoothGatt;
            }
        }
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
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
                    Command command = new Command(Command.CommandType.WRITE, BluetoothConnectionServer.MESSAGE_RECEIVE_UUID, subMessageToSend.getCompleteData());
                    // We check if the subData is already present in the commands before adding it.
                    // This is to avoid adding multiple times the same subData read notification to the queue in cases its command is already retrying.
                    boolean found = checkBluetoothMessageInCommands(command, subMessageToSend);
                    if(!found) sendCommand(command);
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
                    Command command = new Command(Command.CommandType.WRITE, BluetoothConnectionServer.DATA_RECEIVE_UUID, subDataToSend.getCompleteData());
                    // We check if the subData is already present in the commands before adding it.
                    // This is to avoid adding multiple times the same subData read notification to the queue in cases its command is already retrying.
                    boolean found = checkCommandInCommands(command);
                    if(!found) sendCommand(command);
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
                super.run();
                synchronized (commandsLock) {
                    boolean success = false;
                    if (bluetoothGatt != null && getPeer().isFullyConnected() && !getPeer().isDisconnecting()) {
                        BluetoothGattService service = bluetoothGatt.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);

                        if (service != null) {
                            Command commandToSend = pendingCommands.peekFirst();
                            if (commandToSend != null) {
                                if(commandToSend.type == Command.CommandType.WRITE) {
                                    BluetoothGattCharacteristic output = service.getCharacteristic(commandToSend.characteristic);
                                    if (output != null) {
                                        output.setValue(commandToSend.data);
                                        success = bluetoothGatt.writeCharacteristic(output);
                                        Log.e("subClientCommand send", "-" + success);
                                    }
                                }else if(commandToSend.type == Command.CommandType.READ) {
                                    success = bluetoothGatt.readCharacteristic(service.getCharacteristic(commandToSend.characteristic));
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
        }.start();
    }

    @Override
    public void readPhy() {
        synchronized (lock) {
            if (bluetoothGatt != null && getPeer().isFullyConnected()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bluetoothGatt.readPhy();
                }
            }
        }
    }

    public boolean requestConnection(String uniqueName) {  //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.CONNECTION_REQUEST_UUID);
                    if (output != null) {
                        output.setValue((uniqueName).getBytes(StandardCharsets.UTF_8));
                        success = bluetoothGatt.writeCharacteristic(output);
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumed(String uniqueName) {  //handshake commands must avoid the sendCommand queue (because of the isConnected check), also they don't need it because they are already sent sequentially
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.CONNECTION_RESUMED_RECEIVE_UUID);
                    if (output != null) {
                        output.setValue(uniqueName.getBytes(StandardCharsets.UTF_8));
                        success = bluetoothGatt.writeCharacteristic(output);
                    }
                }
            }
            return success;
        }
    }

    public void notifySubMessageReceived(BluetoothMessage subMessage){
        byte[] responseData = BluetoothTools.concatBytes(subMessage.getId().getValue().getBytes(StandardCharsets.UTF_8), subMessage.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
        Command command = new Command(Command.CommandType.WRITE, BluetoothConnectionServer.READ_RESPONSE_MESSAGE_RECEIVED_UUID, responseData);
        sendCommand(command);
    }

    public void notifySubDataReceived(BluetoothMessage subMessage){
        byte[] responseData = BluetoothTools.concatBytes(subMessage.getId().getValue().getBytes(StandardCharsets.UTF_8), subMessage.getSequenceNumber().getValue().getBytes(StandardCharsets.UTF_8));
        Command command = new Command(Command.CommandType.WRITE, BluetoothConnectionServer.READ_RESPONSE_DATA_RECEIVED_UUID, responseData);
        sendCommand(command);
    }

    public void notifyReadSubData(){
        Command command = new Command(Command.CommandType.READ, BluetoothConnectionServer.DATA_SEND_UUID, new byte[0]);
        sendCommand(command);
    }

    @Override
    public void notifyNameUpdated(String uniqueName) {
        Command command = new Command(Command.CommandType.WRITE, BluetoothConnectionServer.NAME_UPDATE_RECEIVE_UUID, uniqueName.getBytes(StandardCharsets.UTF_8));
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
                if (bluetoothGatt != null && getPeer().isFullyConnected()) {
                    BluetoothGattService service = bluetoothGatt.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.DISCONNECTION_RECEIVE_UUID);  //si invia la notifica di disconnessione
                        if (output != null) {
                            output.setValue(String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                            success = bluetoothGatt.writeCharacteristic(output);
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
                if (bluetoothGatt != null) {
                    Log.d("bluetooth_communicator_client", "disconnect, peer: " + getPeer().getName());
                    // canceling notifications
                    BluetoothGattService service = bluetoothGatt.getService(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic messageReceived = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.MESSAGE_SEND_UUID);
                        if (messageReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(messageReceived, false);
                        }
                        BluetoothGattCharacteristic dataReceived = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.DATA_SEND_UUID);
                        if (dataReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(dataReceived, false);
                        }

                        BluetoothGattCharacteristic disconnectionReceived = service.getCharacteristic(nie.translator.rtranslator.bluetooth.BluetoothConnectionServer.DISCONNECTION_SEND_UUID);
                        if (disconnectionReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(disconnectionReceived, false);
                        }
                    }

                    // actual disconnection
                    bluetoothGatt.disconnect();
                    disconnectionCallback.onClientDisconnectionSuccess(bluetoothGatt);  //here the bluetoothGatt.close() is called, the resources are cleared and the disconnection notified to BluetoothCommunicator
                    synchronized (commandsLock) {
                        bluetoothGatt = null;
                    }

                }
                return true;
            }
            return false;
        }
    }

    public void destroy() {
        synchronized (lock) {
            super.destroy();
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
        }
    }
}
