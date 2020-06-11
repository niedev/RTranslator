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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothMessage;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.tools.BluetoothTools;


public class Message implements Parcelable, Cloneable {
    public static final int HEADER_LENGTH = 1;
    private Context context;
    @Nullable
    private Peer sender;  // if we are the sender, the sender can be null
    @Nullable
    private Peer receiver;  //se è null e il messaggio sta per essere inviato verrà inviato a tutti
    private String header;  // mandatory length: 1
    private byte[] data;

    /**
     * @param context
     * @param header  must contain 1 character to avoid errors
     * @param text
     */
    public Message(Context context, String header, @NonNull String text) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    public Message(Context context, @NonNull String text) {
        this.context = context;
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, String header, @NonNull byte[] data) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
    }

    public Message(Context context, @NonNull byte[] data) {
        this.context = context;
        this.data = data;
    }

    /**
     * @param context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, String header, String text, @Nullable Peer receiver) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = text.getBytes(StandardCharsets.UTF_8);
        this.receiver = receiver;
    }

    /**
     * @param context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, String header, @NonNull byte[] data, @Nullable Peer receiver) {
        this.context = context;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
        this.receiver = receiver;
    }

    /**
     * @param context
     * @param header  must contain 1 character to avoid errors
     * @param text
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
     * @param context
     * @param header  must contain 1 character to avoid errors
     */
    public Message(Context context, @Nullable Peer sender, String header, @NonNull byte[] data) {
        this.context = context;
        this.sender = sender;
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
        this.data = data;
    }

    public Message(Context context, @Nullable Peer sender, @NonNull byte[] data) {
        this.context = context;
        this.sender = sender;
        this.data = data;
    }


    /**
     * @param header must contain 1 character to avoid errors
     */
    public void setHeader(String header) {
        this.header = BluetoothTools.fixLength(context, header, HEADER_LENGTH, BluetoothTools.FIX_TEXT);
    }

    public String getHeader() {
        return header;
    }

    public Peer getSender() {
        return sender;
    }

    public void setSender(@Nullable Peer sender) {
        this.sender = sender;
    }

    public String getText() {
        return new String(this.data, StandardCharsets.UTF_8);
    }

    public void setText(String text) {
        this.data = text.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public ArrayDeque<BluetoothMessage> splitInBluetoothMessages(BluetoothMessage.SequenceNumber id) {
        int subDataLength = BluetoothConnection.SUB_MESSAGES_LENGTH - BluetoothMessage.TOTAL_LENGTH;
        ArrayDeque<byte[]> subDataArray = BluetoothTools.splitBytes(BluetoothTools.concatBytes(header.getBytes(StandardCharsets.UTF_8), data), subDataLength);

        BluetoothMessage.SequenceNumber sequenceNumber = new BluetoothMessage.SequenceNumber(context,BluetoothMessage.SEQUENCE_NUMBER_LENGTH);
        ArrayDeque<BluetoothMessage> bluetoothMessages = new ArrayDeque<>();
        while (subDataArray.peekFirst() != null) {
            byte[] subData = subDataArray.pollFirst();
            int type;
            if (subDataArray.peekFirst() == null) {
                type = BluetoothMessage.FINAL;
            } else {
                type = BluetoothMessage.NON_FINAL;
            }
            bluetoothMessages.addLast(new BluetoothMessage(context, id.clone(), sequenceNumber.clone(), type, subData));
            sequenceNumber.increment();
        }
        return bluetoothMessages;
    }

    /*public byte[] toBytes() {
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }*/

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

    @Nullable
    public Peer getReceiver() {
        return receiver;
    }

    public void setReceiver(@Nullable Peer receiver) {
        this.receiver = receiver;
    }
}
