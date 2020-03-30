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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import java.nio.charset.StandardCharsets;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothMessage;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.server.BluetoothConnectionServer;


public class ClientChannel extends Channel {
    private BluetoothGatt bluetoothGatt;


    public ClientChannel(Context context, @NonNull Peer peer) {
        super(context, peer);
    }


    public void setBluetoothGatt(BluetoothGatt bluetoothGatt) {
        synchronized (lock) {
            this.bluetoothGatt = bluetoothGatt;
        }
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    @Override
    protected void writeSubMessage() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                synchronized (lock) {
                    boolean success = false;
                    if (bluetoothGatt != null && !messagesPaused && getPeer().isFullyConnected()) {
                        BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);

                        if (service != null) {
                            if (pendingMessage != null) {
                                BluetoothMessage subMessageToSend = pendingMessage.peekFirst();
                                if (subMessageToSend != null) {   // if there are other subMessages for the message we are sending, we send the next one
                                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.MESSAGE_RECEIVE_UUID);
                                    if (output != null) {
                                        output.setValue(subMessageToSend.getCompleteData());
                                        success = bluetoothGatt.writeCharacteristic(output);
                                        Log.e("subClientMessage send", "-" + success);
                                    }
                                }
                            } else {
                                success = true;
                            }
                        }
                    }
                    final boolean finalSuccess = success;
                    if (finalSuccess) {
                        //start of message timer
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
                super.run();
                synchronized (lock) {
                    boolean success = false;
                    if (bluetoothGatt != null && !dataPaused && getPeer().isFullyConnected()) {
                        BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);

                        if (service != null) {
                            if (pendingData != null) {
                                BluetoothMessage subDataToSend = pendingData.peekFirst();
                                if (subDataToSend != null) {   // if there are other subMessages for the message we are sending, we send the next one
                                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.DATA_RECEIVE_UUID);
                                    if (output != null) {
                                        output.setValue(subDataToSend.getCompleteData());
                                        success = bluetoothGatt.writeCharacteristic(output);
                                        Log.e("subClientData send", "-" + success);
                                    }
                                }
                            } else {
                                success = true;
                            }
                        }
                    }
                    final boolean finalSuccess = success;
                    if (finalSuccess) {
                        //start of data timer
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

    public boolean requestConnection(String uniqueName) {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_REQUEST_UUID);
                    if (output != null) {
                        output.setValue((uniqueName).getBytes(StandardCharsets.UTF_8));
                        success = bluetoothGatt.writeCharacteristic(output);
                    }
                }
            }
            return success;
        }
    }

    public boolean notifyConnectionResumed() {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.CONNECTION_RESUMED_RECEIVE_UUID);
                    if (output != null) {
                        output.setValue(String.valueOf(1).getBytes(StandardCharsets.UTF_8));
                        success = bluetoothGatt.writeCharacteristic(output);
                    }
                }
            }
            return success;
        }
    }

    @Override
    public boolean notifyNameUpdated(String uniqueName) {
        synchronized (lock) {
            boolean success = false;
            if (bluetoothGatt != null && getPeer().isFullyConnected()) {
                BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.NAME_UPDATE_RECEIVE_UUID);
                    if (output != null) {
                        output.setValue(uniqueName.getBytes(StandardCharsets.UTF_8));
                        success = bluetoothGatt.writeCharacteristic(output);
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
                if (bluetoothGatt != null && getPeer().isFullyConnected()) {
                    BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic output = service.getCharacteristic(BluetoothConnectionServer.DISCONNECTION_RECEIVE_UUID);  //si invia la notifica di disconnessione
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
                    // canceling notifications
                    BluetoothGattService service = bluetoothGatt.getService(BluetoothConnection.APP_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic messageReceived = service.getCharacteristic(BluetoothConnectionServer.MESSAGE_SEND_UUID);
                        if (messageReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(messageReceived, false);
                        }
                        BluetoothGattCharacteristic dataReceived = service.getCharacteristic(BluetoothConnectionServer.DATA_SEND_UUID);
                        if (dataReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(dataReceived, false);
                        }

                        BluetoothGattCharacteristic disconnectionReceived = service.getCharacteristic(BluetoothConnectionServer.DISCONNECTION_SEND_UUID);
                        if (disconnectionReceived != null) {
                            bluetoothGatt.setCharacteristicNotification(disconnectionReceived, false);
                        }
                    }

                    // actual disconnection
                    bluetoothGatt.disconnect();
                    bluetoothGatt = null;
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
