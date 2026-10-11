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

import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.UUID;

import nie.translator.rtranslator.bluetooth.tools.Timer;

abstract class Channel {
    //timeouts of timers
    private final int RECONNECTION_TIMEOUT = 30000;
    private final int CONNECTION_COMPLETE_TIMEOUT = 10000;
    protected final int COMMAND_TIMEOUT = 1000;
    protected final int MESSAGE_TIMEOUT = 1500;
    private final int NOTIFY_DISCONNECTION_TIMEOUT = 5000;
    private final int DISCONNECTION_TIMEOUT = 4000;
    //variables and objects
    protected Context context;
    @NonNull
    private Peer peer;
    private nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber messageID;
    private nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber dataID;
    protected ArrayDeque<nie.translator.rtranslator.bluetooth.Message> pendingMessages = new ArrayDeque<>();
    protected ArrayDeque<nie.translator.rtranslator.bluetooth.Message> pendingData = new ArrayDeque<>();
    @Nullable
    protected ArrayDeque<nie.translator.rtranslator.bluetooth.BluetoothMessage> pendingSubMessages;  // when not null it contains all the sending subMessages of the current sending message
    @Nullable
    protected ArrayDeque<nie.translator.rtranslator.bluetooth.BluetoothMessage> pendingSubData;  // when not null it contains all the sending subData of the current sending data
    protected ArrayDeque<Command> pendingCommands = new ArrayDeque<>();
    private ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> receivingMessages = new ArrayList<>();
    private ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> receivingData = new ArrayList<>();
    private ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> receivedMessages = new ArrayList<>();
    private ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> receivedData = new ArrayList<>();
    private Timer connectionCompleteTimer;
    private Timer reconnectionTimer;
    private Timer commandTimer;
    private Timer messageTimer;
    private Timer dataTimer;
    private Timer notifyDisconnectionTimer;
    protected Handler mainHandler;
    @Nullable
    protected DisconnectionNotificationCallback disconnectionNotificationCallback;
    private boolean notifyingDisconnection = false;
    private boolean disconnecting = false;
    protected boolean messagesPaused = false;
    protected boolean dataPaused = false;
    protected int bluetoothMessagesLength;
    protected final Object lock = new Object();
    protected final Object messagesLock = new Object();
    protected final Object dataLock = new Object();
    protected final Object commandsLock = new Object();

    protected Channel(Context context, @NonNull Peer peer) {
        this.context = context;
        this.messageID= new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH);
        this.dataID= new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, nie.translator.rtranslator.bluetooth.BluetoothMessage.ID_LENGTH);
        this.peer = peer;
        this.bluetoothMessagesLength = 17;  // the minimum value possible (this will always be overwritten)
    }


    public void sendMessage(@NonNull Message message) {
        synchronized (messagesLock) {
            pendingMessages.addLast(message);
            if (pendingMessages.size() == 1 && pendingSubMessages == null) {  // if it is true then we are not writing any messages
                sendMessage();
            }
        }
    }

    public void sendData(@NonNull Message data) {
        synchronized (dataLock) {
            pendingData.addLast(data);
            if (pendingData.size() == 1 && pendingSubData == null) {  // if it is true then we are not writing any messages
                sendData();
            }
        }
    }

    public void sendMessage(){
        Message message = pendingMessages.peekFirst();
        if (message != null && pendingSubMessages == null) {    // if it is true then we are not writing any subMessages and the pendingMessages is not empty
            // division from the message and sending of the various parts
            pendingSubMessages = message.splitInBluetoothMessages(messageID, getBluetoothMessagesLength());
            messageID.increment();
            Log.e("messageSend", message.getText());
            sendSubMessage();
        }
    }

    public void sendData(){
        Message data = pendingData.peekFirst();
        if (data != null && pendingSubData == null) {    // if it is true then we are not writing any subData and the pendingData is not empty
            // division from the message and sending of the various parts
            pendingSubData = data.splitInBluetoothMessages(dataID, getBluetoothMessagesLength());
            dataID.increment();
            Log.e("dataSend", data.getText());
            sendSubData();
        }
    }

    protected abstract void sendSubMessage();

    protected abstract void sendSubData();

    protected void sendCommand(Command command){
        synchronized (commandsLock) {
            pendingCommands.addLast(command);
            if(pendingCommands.size() == 1) {
                writeCommand();
            }
        }
    }

    protected abstract void writeCommand();

    public void onCommandSent(boolean success, UUID characteristic){
        synchronized (commandsLock) {
            Command command = pendingCommands.peekFirst();
            if(command != null && command.characteristic.equals(characteristic)) {  //we check if the current sending command is targeted to the same characteristic that has been sent (not every characteristic uses the commands queue)
                // If success we remove the newly sent command and eventually write the next command.
                // If not success we retry the writing of the failed command.
                if (success) {
                    pendingCommands.pollFirst();
                }
                if (!pendingCommands.isEmpty()) {
                    writeCommand();
                }
            }
        }
    }

    protected boolean checkBluetoothMessageInCommands(Command command, BluetoothMessage bluetoothMessage){
        boolean found = false;
        synchronized (commandsLock) {
            // We check if the subMessage command is already present in the commands
            for (Command commandQueued : pendingCommands) {
                if (commandQueued.type == command.type && commandQueued.characteristic.equals(command.characteristic)) {
                    BluetoothMessage messageQueued = BluetoothMessage.createFromBytes(context, getPeer(), commandQueued.data);
                    if (messageQueued != null && bluetoothMessage.getId().equals(messageQueued.getId()) && bluetoothMessage.getSequenceNumber().equals(messageQueued.getSequenceNumber())) {
                        found = true;
                        break;
                    }
                }
            }
        }
        return found;
    }

    protected boolean checkCommandInCommands(Command command){
        boolean found = false;
        synchronized (commandsLock) {
            // We check if the subData is already present in the commands before adding it.
            // This is to avoid adding multiple times the same subData read notification to the queue in cases its command is already retrying.
            for (Command commandQueued : pendingCommands) {
                if (commandQueued.type == command.type && commandQueued.characteristic.equals(command.characteristic)) {
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    public void onSubMessageWriteSuccess() {
        synchronized (messagesLock) {
            BluetoothMessage subMessageSent = null;
            resetMessageTimer();  //this is useful only for the server
            if (pendingSubMessages != null) {
                subMessageSent = pendingSubMessages.pollFirst();  // remove the newly sent subMessage
            }
            if (subMessageSent != null && subMessageSent.getType() == BluetoothMessage.FINAL) {
                pendingSubMessages = null;   // remove the newly sent ConversationMessage
                pendingMessages.pollFirst();  // remove the newly sent Message
                sendMessage();  //we send the eventual next message
            } else {
                sendSubMessage();  //we send the next sub message
            }
        }
    }

    public void onSubDataWriteSuccess() {
        synchronized (dataLock) {
            BluetoothMessage subDataSent = null;
            resetDataTimer();  //this is useful only for the server
            if (pendingSubData != null) {
                subDataSent = pendingSubData.pollFirst();  // remove the newly sent subData
            }
            if (subDataSent != null && subDataSent.getType() == BluetoothMessage.FINAL) {
                pendingSubData = null;   // remove the newly sent ConversationMessage
                pendingData.pollFirst();  // remove the newly sent data
                sendData();  //we send the eventual next data message
            } else {
                sendSubData();  //we send the next sub data
            }
        }
    }

    public void onSubMessageWriteFailed() {
        sendSubMessage();  //we retry
    }

    public void onSubDataWriteFailed() {
        sendSubData();  //we retry
    }

    public void onConnectionLost(){
        // We stop the sending of the messages.
        // We need only the reset of the command timer because there are already checks in the writeCommand method.
        // This pause and resume are to avoid interfering with the handshake messages
        resetCommandTimer();
    }

    public void onConnectionResumed(){
        // We resume the sending of the messages.
        // This pause and resume are to avoid interfering with the handshake messages
        writeCommand();
    }



    public int getBluetoothMessagesLength() {
        return bluetoothMessagesLength;
    }

    public void setBluetoothMessagesLength(int bluetoothMessagesLength) {
        this.bluetoothMessagesLength = bluetoothMessagesLength;
    }

    /*public void pausePendingMessage() {
        synchronized (lock) {
            if (!messagesPaused) {
                // delete all runnables in messageHandler to prevent the pause from being activated again
                messageHandler.removeCallbacksAndMessages(null);
                messagesPaused = true;
            }
        }
    }

    public void resumePendingMessage() {
        synchronized (lock) {
            if (messagesPaused) {
                // delete all runnables in messageHandler to ensure that it can not be run multiple times
                messageHandler.removeCallbacksAndMessages(null);
                messageHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            messagesPaused = false;
                        }
                    }
                }, 1500);
            }
        }
    }

    public void pausePendingData() {
        synchronized (lock) {
            if (!dataPaused) {
                // delete all runnables in dataHandler to prevent the pause from being activated again
                dataHandler.removeCallbacksAndMessages(null);
                dataPaused = true;
            }
        }
    }

    public void resumePendingData() {
        synchronized (lock) {
            if (dataPaused) {
                // delete all runnables in dataHandler to ensure that it can not be run multiple times
                dataHandler.removeCallbacksAndMessages(null);
                dataHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            dataPaused = false;
                        }
                    }
                }, 1500);
            }
        }
    }*/

    public void setPeer(@NonNull Peer peer) {
        this.peer = peer;
    }

    public abstract void readPhy();

    public abstract void notifyNameUpdated(String name);

    public Timer getReconnectionTimer() {
        return reconnectionTimer;
    }

    @NonNull
    public Peer getPeer() {
        return peer;
    }

    @Nullable
    public nie.translator.rtranslator.bluetooth.BluetoothMessage getPendingSubMessage() {
        synchronized (messagesLock) {
            if (pendingSubMessages != null) {
                return pendingSubMessages.peekFirst();
            } else {
                return null;
            }
        }
    }

    @Nullable
    public nie.translator.rtranslator.bluetooth.BluetoothMessage getPendingSubData() {
        synchronized (dataLock) {
            if (pendingSubData != null) {
                return pendingSubData.peekFirst();
            } else {
                return null;
            }
        }
    }

    public ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> getReceivingMessages() {
        return receivingMessages;
    }

    public ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> getReceivingData() {
        return receivingData;
    }

    public ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> getReceivedMessages() {
        return receivedMessages;
    }

    public ArrayList<nie.translator.rtranslator.bluetooth.BluetoothMessage> getReceivedData() {
        return receivedData;
    }

    public void addReceivedMessage(nie.translator.rtranslator.bluetooth.BluetoothMessage message) {
        if(message.getId().isMax()){
            receivedMessages.clear();
        }else{
            receivedMessages.add(message);
        }
    }

    public void addReceivedData(nie.translator.rtranslator.bluetooth.BluetoothMessage data) {
        if(data.getId().isMax()){
            receivedData.clear();
        }else{
            receivedData.add(data);
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Channel) {
            return peer.equals(((Channel) obj).getPeer());
        }
        if (obj instanceof Peer) {
            return peer.equals(obj);
        }
        return false;
    }

    protected boolean notifyDisconnection(final DisconnectionNotificationCallback disconnectionNotificationCallback) {
        synchronized (lock) {
            if (!notifyingDisconnection) {
                notifyingDisconnection = true;
                getPeer().setDisconnecting(true);
                this.disconnectionNotificationCallback = disconnectionNotificationCallback;
                // stop sending commands
                // We need only the reset of the command timer because there are already checks in the writeCommand method.
                // Also, we don't manage the possible system conflicts of writing 2 characteristics due to possible commands in between the sending and the confirmation,
                // because it is very rare that a user is sending a message while quitting at the same time.
                // So for now we can skip this headache.
                resetCommandTimer();
                return true;
            }
            return false;
        }
    }

    public boolean disconnect(@Nullable final DisconnectionCallback callback) {
        synchronized (lock) {
            if (!disconnecting) {
                disconnecting = true;
                getPeer().setDisconnecting(true);
                // stop of notify disconnection timer
                resetNotifyDisconnectionTimer();
                // stop sending commands
                resetCommandTimer();
                resetDataTimer();
                resetMessageTimer();

                if (disconnectionNotificationCallback != null) {
                    disconnectionNotificationCallback.onDisconnectionNotificationSent();
                    disconnectionNotificationCallback = null;
                }
                if (!getPeer().isHardwareConnected() && callback != null) {
                    callback.onAlreadyDisconnected(getPeer());
                }
                return true;
            }
            return false;
        }
    }

    public void onDisconnected() {
        //insert here eventual channel release operations (before it was used to resent the disconnection timer)
    }

    public void destroy() {
        synchronized (lock) {
            resetCommandTimer();  // stop sending commands
            resetReconnectionTimer();
            resetConnectionCompleteTimer();
            resetNotifyDisconnectionTimer();
            synchronized (messagesLock) {
                pendingSubMessages = null;
            }
            synchronized (dataLock) {
                pendingSubData = null;
            }
            disconnectionNotificationCallback = null;
        }
    }


    public void startConnectionCompleteTimer(final Timer.Callback callback) {
        synchronized (lock) {
            connectionCompleteTimer = new Timer(CONNECTION_COMPLETE_TIMEOUT);
            connectionCompleteTimer.setCallback(callback);
            connectionCompleteTimer.start();
        }
    }

    public void resetConnectionCompleteTimer() {
        synchronized (lock) {
            if (connectionCompleteTimer != null) {
                connectionCompleteTimer.cancel();
                connectionCompleteTimer = null;
            }
        }
    }

    public void startReconnectionTimer(final Timer.Callback callback) {
        synchronized (lock) {
            reconnectionTimer = new Timer(RECONNECTION_TIMEOUT);
            reconnectionTimer.setCallback(callback);
            reconnectionTimer.start();
        }
    }

    public void resetReconnectionTimer() {
        synchronized (lock) {
            if (reconnectionTimer != null) {
                reconnectionTimer.cancel();
                reconnectionTimer = null;
            }
        }
    }

    protected void startCommandTimer(final Timer.Callback callback) {
        synchronized (commandsLock) {
            commandTimer = new Timer(COMMAND_TIMEOUT);
            commandTimer.setCallback(callback);
            commandTimer.start();
        }
    }

    private void resetCommandTimer() {
        synchronized (commandsLock) {
            if (commandTimer != null) {
                commandTimer.cancel();
                commandTimer = null;
            }
        }
    }

    protected void startMessageTimer(final Timer.Callback callback) {
        synchronized (messagesLock) {
            messageTimer = new Timer(MESSAGE_TIMEOUT);
            messageTimer.setCallback(callback);
            messageTimer.start();
        }
    }

    private void resetMessageTimer() {
        synchronized (messagesLock) {
            if (messageTimer != null) {
                messageTimer.cancel();
                messageTimer = null;
            }
        }
    }

    protected void startDataTimer(final Timer.Callback callback) {
        synchronized (dataLock) {
            dataTimer = new Timer(MESSAGE_TIMEOUT);
            dataTimer.setCallback(callback);
            dataTimer.start();
        }
    }

    private void resetDataTimer() {
        synchronized (dataLock) {
            if (dataTimer != null) {
                dataTimer.cancel();
                dataTimer = null;
            }
        }
    }

    protected void startNotifyDisconnectionTimer(final Timer.Callback callback) {
        synchronized (lock) {
            notifyDisconnectionTimer = new Timer(NOTIFY_DISCONNECTION_TIMEOUT);
            notifyDisconnectionTimer.setCallback(callback);
            notifyDisconnectionTimer.start();
        }
    }

    protected void resetNotifyDisconnectionTimer() {
        synchronized (lock) {
            if (notifyDisconnectionTimer != null) {
                notifyDisconnectionTimer.cancel();
                notifyDisconnectionTimer = null;
            }
        }
    }

    public static class Command {
        public enum CommandType {
            NOTIFICATION,
            READ,
            WRITE
        }

        public CommandType type;
        public UUID characteristic;
        public byte[] data;

        public Command(CommandType type, UUID characteristic, byte[] data){
            this.type = type;
            this.characteristic = characteristic;
            this.data = data;
        }
    }

    public static abstract class MessageCallback {
        public abstract void onMessageSent();
    }

    public static abstract class DisconnectionNotificationCallback extends DisconnectionCallback{
        public abstract void onDisconnectionNotificationSent();
    }

    public static abstract class DisconnectionCallback {
        public void onAlreadyDisconnected(Peer peer){}

        public void onClientDisconnectionSuccess(BluetoothGatt gatt) {}

        public void onServerDisconnectionSuccess(Peer peer) {}
    }
}
