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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.server;

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
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothMessage;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;


public class ServerChannel extends Channel {
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothAdapter bluetoothAdapter;
    private UUID sendingCharacteristic = null;


    protected ServerChannel(Context context, @NonNull Peer peer, final BluetoothAdapter bluetoothAdapter) {
        super(context, peer);
        this.bluetoothAdapter = bluetoothAdapter;
    }


    public void setBluetoothGattServer(BluetoothGattServer bluetoothGattServer) {
        this.bluetoothGattServer = bluetoothGattServer;
    }

    @Override
    protected void writeSubMessage() {
            new Thread() {
                @Override
                public void run() {
                    synchronized (lock) {
                        super.run();
                        boolean success = false;
                        if (bluetoothGattServer != null && !messagesPaused && getPeer().isFullyConnected()) {
                            BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);

                            if (service != null) {
                                if (pendingMessage != null) {
                                    BluetoothMessage subMessageToSend = pendingMessage.peekFirst();
                                    if (subMessageToSend != null) {    // if there are other subMessages for the message we are sending, we send the next one
                                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.MESSAGE_SEND_UUID);
                                        if (output != null) {
                                            output.setValue(subMessageToSend.getCompleteData());
                                            sendingCharacteristic = BluetoothConnectionServer.MESSAGE_SEND_UUID;
                                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                                            Log.e("subServerMessage send", "-" + success);
                                        }
                                    }
                                } else {
                                    success = true;
                                }
                            }
                        }
                        final boolean finalSuccess = success;
                        if (finalSuccess) {
                            // start of message timer
                            startMessageTimer(new Timer.Callback() {
                                @Override
                                public void onFinished() {
                                    onSubMessageWriteFailed();
                                }
                            });
                        } else {
                            writeSubMessage();
                        }
                    }
                }
            }.start();
    }

    @Override
    protected void writeSubData() {
            new Thread() {
                @Override
                public void run() {
                    synchronized (lock) {
                        super.run();
                        boolean success = false;
                        if (bluetoothGattServer != null && !dataPaused && getPeer().isFullyConnected()) {
                            BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);

                            if (service != null) {
                                if (pendingData != null) {
                                    BluetoothMessage subDataToSend = pendingData.peekFirst();
                                    if (subDataToSend != null) {   // if there are other subMessages for the message we are sending, we send the next one
                                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.DATA_SEND_UUID);
                                        if (output != null) {
                                            //output.setValue(subDataToSend);
                                            output.setValue(String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                                            sendingCharacteristic = BluetoothConnectionServer.DATA_SEND_UUID;
                                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                                            Log.e("subServerData send", "-" + success);
                                        }
                                    }
                                } else {
                                    success = true;
                                }
                            }
                        }
                        final boolean finalSuccess = success;
                        if (finalSuccess) {
                            // start of data timer
                            startDataTimer(new Timer.Callback() {
                                @Override
                                public void onFinished() {
                                    onSubDataWriteFailed();
                                }
                            });
                        } else {
                            writeSubData();
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

    public boolean acceptConnection() {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID);
                    if (output != null) {
                        output.setValue(String.valueOf(BluetoothConnection.ACCEPT).getBytes(StandardCharsets.UTF_8));
                        sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESPONSE_UUID;
                        success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                    }
                }
            }
            return success;
        }
    }

    public boolean rejectConnection() {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESPONSE_UUID);
                    if (output != null) {
                        output.setValue(String.valueOf(BluetoothConnection.REJECT).getBytes(StandardCharsets.UTF_8));
                        sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESPONSE_UUID;
                        success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumed() {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID);
                    if (output != null) {
                        output.setValue(String.valueOf(BluetoothConnection.ACCEPT).getBytes(StandardCharsets.UTF_8));
                        sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID;
                        success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumedRejected() {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null) {
                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID);
                    if (output != null) {
                        output.setValue(String.valueOf(BluetoothConnection.REJECT).getBytes(StandardCharsets.UTF_8));
                        sendingCharacteristic = BluetoothConnectionServer.CONNECTION_RESUMED_SEND_UUID;
                        success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyNameUpdated(String name) {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGattServer != null && getPeer().isFullyConnected()) {
                BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.NAME_UPDATE_SEND_UUID);
                    if (output != null) {
                        output.setValue(name);
                        sendingCharacteristic = BluetoothConnectionServer.NAME_UPDATE_SEND_UUID;
                        success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), output, true);
                    }
                }
            }
            return success;
        }
    }

    @Override
    public boolean notifyDisconnection(DisconnectionNotificationCallback disconnectionNotificationCallback) {
        synchronized (lock) {
            boolean success = false;
            if (super.notifyDisconnection(disconnectionNotificationCallback)) {
                if (bluetoothGattServer != null && getPeer().isFullyConnected()) {
                    BluetoothGattService service = bluetoothGattServer.getService(BluetoothConnection.APP_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic disconnectionSend = service.getCharacteristic(BluetoothConnectionServer.DISCONNECTION_SEND_UUID);
                        if (disconnectionSend != null) {
                            disconnectionSend.setValue(String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                            sendingCharacteristic = BluetoothConnectionServer.DISCONNECTION_SEND_UUID;
                            success = bluetoothGattServer.notifyCharacteristicChanged(getPeer().getRemoteDevice(bluetoothAdapter), disconnectionSend, true);
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
                    bluetoothGattServer.cancelConnection(getPeer().getRemoteDevice(bluetoothAdapter));
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
                bluetoothGattServer.close();
            }
        }
    }
}
