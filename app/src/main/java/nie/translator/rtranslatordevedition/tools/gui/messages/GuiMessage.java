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

package nie.translator.rtranslatordevedition.tools.gui.messages;

import android.os.Parcel;
import android.os.Parcelable;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Message;

public class GuiMessage implements Parcelable {
    private Message message;
    private boolean isMine;
    private boolean isFinal;


    public GuiMessage(Message message, boolean isMine, boolean isFinal) {
        this.message=message;
        this.isMine = isMine;
        this.isFinal = isFinal;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public boolean isMine() {
        return isMine;
    }

    public void setMine(boolean mine) {
        isMine = mine;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    //parcel implementation
    public static final Creator<GuiMessage> CREATOR = new Creator<GuiMessage>() {   // this attribute may create problems with the one of ConversationMessage
        @Override
        public GuiMessage createFromParcel(Parcel in) {
            return new GuiMessage(in);
        }

        @Override
        public GuiMessage[] newArray(int size) {
            return new GuiMessage[size];
        }
    };

    private GuiMessage(Parcel in) {
        message = in.readParcelable(Message.class.getClassLoader());
        isMine = in.readByte() != 0;
        isFinal = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(message, i);
        parcel.writeByte((byte) (isMine ? 1 : 0));
        parcel.writeByte((byte) (isFinal ? 1 : 0));
    }
}
