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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import nie.translator.rtranslator.bluetooth.tools.BluetoothTools;

/**
 * Message is used to send and receive messages using BluetoothCommunicator, in practice this class is a container for the messages that will be sent and received.
 * <br /><br />
 * In order to send a message the message object must always contain a header and the text or data (if it will be sent via BluetoothCommunicator.sendMessage text will be sent,
 * if it will be sent via BluetoothCommunicator.sendData then data will be sent).
 * If you want to specify a peer to send the message to (by default it is sent to all) then the peer must be set as receiver, you can do it in the constructor or through the Message.setReceiver method.
 * <br /><br />
 * To send a message there is no need to set the sender because the latter will not be sent, the receiver will recognize the sender through the channel in which he will receive the message,
 * in any case it is something completely transparent. The sender is used only to identify the sender of received messages, and upon receipt of a message
 * via the BluetoothCommunicator.onMessageReceived or onDataReceived method the sender will have already been automatically inserted into the message by the library, along with the text / data and header.
 * <br /><br />
 * Another way you can use the Message class is to represent messages (graphically, to save them, etc.), some Message constructors would not make sense for sending or receiving messages,
 * but they can be useful for use as representation.
 */
public class Message implements Parcelable, Cloneable {
    public static final int HEADER_LENGTH = 1;
    private Context context;
    @Nullable
    private Peer sender;  // if we are the sender, the sender can be null
    @Nullable
    private Peer receiver;  //if is null the message will be sent to all connected peers
    private String header;  // mandatory length: 1
    private byte[] data;
    private String textToTranslate;

    /**
     * @param context a context
     * @param header  must contain 1 character to avoid errors
     * @param text the text of the message (it will be sent by sendMessage)
     */
    public Message(Context context, String header, @NonNull String text) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     *
     * @param context a context
     * @param text the text of the message (it will be sent by sendMessage)
     */
    public Message(Context context, @NonNull String text) {
        this.context = context;
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    public Message(String textToTranslate, Context context, @NonNull String text) {
        this.textToTranslate = textToTranslate;
        this.context = context;
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param context a context
     * @param header must contain 1 character to avoid errors
     */
    public Message(Context context, String header, @NonNull byte[] data) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
    }

    /**
     *
     * @param context a context
     * @param data the data of the message (it will be sent by sendData)
     */
    public Message(Context context, @NonNull byte[] data) {
        this.context = context;
        this.data = data;
    }

    /**
     * @param context a context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, String header, String text, @Nullable Peer receiver) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = text.getBytes(StandardCharsets.UTF_8);
        this.receiver = receiver;
    }

    /**
     * @param context a context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, String header, @NonNull byte[] data, @Nullable Peer receiver) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
        this.receiver = receiver;
    }

    /**
     * @param context a context
     * @param header  must contain 1 character to avoid errors
     * @param text the text of the message (it will be sent by sendMessage)
     */
    public Message(Context context, @Nullable Peer sender, String header, @NonNull String text) {
        this.context = context;
        this.sender = sender;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    public Message(Context context, @Nullable Peer sender, @NonNull String text) {
        this.context = context;
        this.sender = sender;
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param context a context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, @Nullable Peer sender, String header, @NonNull byte[] data) {
        this.context = context;
        this.sender = sender;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
    }

    /**
     * @param context a context
     * @param sender the sender of the message
     * @param data the data of the message (it will be sent by sendData)
     */
    public Message(Context context, @Nullable Peer sender, @NonNull byte[] data) {
        this.context = context;
        this.sender = sender;
        this.data = data;
    }


    /**
     * Sets the header, the header is a single character that can be used to differentiate the types of message,
     * if you use a single type of message, just pick a random character for header and ignore it when receiving
     * text messages or data messages.
     *
     * @param header must contain 1 character to avoid errors
     */
    public void setHeader(String header) {
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
    }

    /**
     * Returns the header
     * @return header
     */
    public String getHeader() {
        return header;
    }

    /**
     * Returns the sender
     * @return sender
     */
    public Peer getSender() {
        return sender;
    }

    /**
     * Sets the sender.<br />
     * To send a message there is no need to set the sender because the latter will not be sent, the receiver will recognize the sender through the channel in which he will receive the message,
     * in any case it is something completely transparent. The sender is used only to identify the sender of received messages, and upon receipt of a message
     * via the BluetoothCommunicator.onMessageReceived or onDataReceived method the sender will have already been automatically inserted into the message by the library, along with the text / data and header.
     * A way this method can be useful is when you are using Message for representation (for the gui, for saving messages etc.).
     *
     * @param sender
     */
    public void setSender(@Nullable Peer sender) {
        this.sender = sender;
    }

    /**
     * Returns the receiver
     * @return receiver
     */
    @Nullable
    public Peer getReceiver() {
        return receiver;
    }

    /**
     * Sets the receiver.<br />
     * If you want to specify a peer to send the message to (by default it is sent to all) then the peer must be set as receiver, you can do it in the constructor or through the Message.setReceiver method.
     *
     * @param receiver
     */
    public void setReceiver(@Nullable Peer receiver) {
        this.receiver = receiver;
    }

    /**
     * Return the text of the message
     *
     * @return text
     */
    public String getText() {
        return new String(this.data, StandardCharsets.UTF_8);
    }

    /**
     * Sets the text of the message
     *
     * @param text
     */
    public void setText(String text) {
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Return the data of the message
     *
     * @return text
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Sets the data of the message
     *
     * @param data
     */
    public void setData(byte[] data) {
        this.data = data;
    }

    /**
     * This method is used only by the library, there is no need for you to use it because the split and the reassembly of a long message is handled by the library.
     *
     * @param id
     * @return the message splitted in more BluetoothMessages (or converted in one BluetoothMessage if the message is short enough)
     */
    public ArrayDeque<nie.translator.rtranslator.bluetooth.BluetoothMessage> splitInBluetoothMessages(nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber id) {
        int subDataLength = nie.translator.rtranslator.bluetooth.BluetoothConnection.SUB_MESSAGES_LENGTH - nie.translator.rtranslator.bluetooth.BluetoothMessage.TOTAL_LENGTH;
        ArrayDeque<byte[]> subDataArray = BluetoothTools.splitBytes(BluetoothTools.concatBytes(header.getBytes(StandardCharsets.UTF_8), data), subDataLength);

        nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber sequenceNumber = new nie.translator.rtranslator.bluetooth.BluetoothMessage.SequenceNumber(context, nie.translator.rtranslator.bluetooth.BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
        ArrayDeque<nie.translator.rtranslator.bluetooth.BluetoothMessage> bluetoothMessages = new ArrayDeque<>();
        while (subDataArray.peekFirst() != null) {
            byte[] subData = subDataArray.pollFirst();
            int type;
            if (subDataArray.peekFirst() == null) {
                type = nie.translator.rtranslator.bluetooth.BluetoothMessage.FINAL;
            } else {
                type = nie.translator.rtranslator.bluetooth.BluetoothMessage.NON_FINAL;
            }
            bluetoothMessages.addLast(new nie.translator.rtranslator.bluetooth.BluetoothMessage(context, id.clone(), sequenceNumber.clone(), type, subData));
            sequenceNumber.increment();
        }
        return bluetoothMessages;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    //parcel implementation
    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    private Message(Parcel in) {
        sender = in.readParcelable(Peer.class.getClassLoader());
        header = in.readString();
        in.readByteArray(this.data);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(sender, i);
        parcel.writeString(header);
        parcel.writeByteArray(this.data);
    }

    public String getTextToTranslate() {
        return textToTranslate;
    }

    public void setTextToTranslate(String textToTranslate) {
        this.textToTranslate = textToTranslate;
    }
}
