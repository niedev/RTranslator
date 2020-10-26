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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation;

import android.os.Parcel;
import android.os.Parcelable;
import com.bluetooth.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiResult;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.CloudApiText;


public class ConversationMessage implements Parcelable, Cloneable {
    private Peer sender;
    private CloudApiText payload;

    public ConversationMessage(Peer sender, CloudApiText payload) {
        this.sender = sender;
        this.payload = payload;
    }

    public ConversationMessage(Peer sender){
        this.sender = sender;
    }

    public ConversationMessage(CloudApiText payload) {
        this.payload=payload;
    }

    public ConversationMessage(byte[] bytes) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(bytes, 0, bytes.length);
        parcel.setDataPosition(0); // This is extremely important!
        sender = parcel.readParcelable(Peer.class.getClassLoader());
        payload = (CloudApiResult) parcel.readSerializable();
        parcel.recycle();
    }

    public Peer getSender() {
        return sender;
    }

    public void setSender(Peer sender) {
        this.sender = sender;
    }

    public CloudApiText getPayload() {
        return payload;
    }

    public void setPayload(CloudApiResult payload) {
        this.payload = payload;
    }

    public byte[] toBytes(){
        Parcel parcel = Parcel.obtain();
        writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        parcel.recycle();
        return bytes;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    //parcel implementation
    public static final Creator<ConversationMessage> CREATOR = new Creator<ConversationMessage>() {
        @Override
        public ConversationMessage createFromParcel(Parcel in) {
            return new ConversationMessage(in);
        }

        @Override
        public ConversationMessage[] newArray(int size) {
            return new ConversationMessage[size];
        }
    };

    protected ConversationMessage(Parcel in) {
        sender = in.readParcelable(Peer.class.getClassLoader());
        payload = (CloudApiResult) in.readSerializable();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(sender, i);
        parcel.writeSerializable(payload);
    }
}
