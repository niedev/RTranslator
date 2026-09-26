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

package nie.translator.rtranslator.tools.gui.messages;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslator.R;

/** Is used to connect to the RecycleView, which functions as a ListView, a list of strings, which will be inserted in the ViewHolder layout and this will be inserted in the list**/
public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int MINE = 0;
    private static final int NON_MINE = 1;
    //private static final int PREVIEW = 2;
    private ArrayList<GuiMessage> mResults = new ArrayList<>();
    private Callback callback;
    private long playingMessageID = -1;

    private static boolean showOriginalTranscriptionMsg;

    public MessagesAdapter(ArrayList<GuiMessage> messages, Application application, long playingMessageID, @NonNull Callback callback) {
        this.callback = callback;
        this.playingMessageID = playingMessageID;
        if (messages != null) {
            if (messages.size() > 0) {
                callback.onFirstItemAdded();
            }
            mResults.addAll(messages);
            notifyItemRangeInserted(0, messages.size() - 1);
        }
        showOriginalTranscriptionMsg = application.getSharedPreferences("default", Context.MODE_PRIVATE).getBoolean("ShowOriginalTranscriptionMsgPreference", false);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case MINE:
                return new SendHolder(LayoutInflater.from(parent.getContext()), parent);
            case NON_MINE:
                return new ReceivedHolder(LayoutInflater.from(parent.getContext()), parent);
        }
        return new SendHolder(LayoutInflater.from(parent.getContext()), parent);  // to not return null
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MessageHolder) {
            MessageHolder messageHolder = (MessageHolder) holder;
            final GuiMessage message = mResults.get(position);

            // Bind eventual sender text (only for ReceiverHolder)
            if (messageHolder instanceof ReceivedHolder) {
                if(message.getMessage().getSender() != null) {
                    ((ReceivedHolder) messageHolder).sender.setText(message.getMessage().getSender().getName());
                }else{
                    ((ReceivedHolder) messageHolder).sender.setVisibility(View.GONE);
                }
            }
            // Bind message text
            messageHolder.setText(message.getMessage().getTextToTranslate(), message.getMessage().getText());
            // Bind tts button status and listener
            messageHolder.setIsPlayingTTS(message.getMessageID() == playingMessageID);
            View.OnClickListener playListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null && v instanceof android.widget.ImageView) {
                        boolean play = false;
                        if(playingMessageID != message.getMessageID()) play = true;
                        callback.onTTSButtonClick(message, play);
                    }
                }
            };
            messageHolder.ttsButton.setOnClickListener(playListener);
        }
    }

    @Override
    public int getItemViewType(int position) {
        GuiMessage message = mResults.get(position);
        if (message.isMine()) {
            return MINE;
        } else {
            return NON_MINE;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return mResults.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public boolean onFailedToRecycleView(@NonNull RecyclerView.ViewHolder holder) {
        return super.onFailedToRecycleView(holder);
    }

    public void addMessage(GuiMessage message) {
        if (getItemCount() == 0) {
            callback.onFirstItemAdded();
        }
        mResults.add(message);
        notifyItemInserted(getItemCount() - 1);
    }

    public void setMessage(int index, GuiMessage message) {
        mResults.set(index, message);
        //notifyItemRangeChanged(0, getItemCount());
        notifyItemChanged(index);
    }

    public int getMessageIndex(long messageID){
        for(int i = 0; i < mResults.size(); i++){
            if(mResults.get(i).getMessageID() == messageID){
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public GuiMessage getMessage(long messageID){
        for(int i = 0; i < mResults.size(); i++){
            if(mResults.get(i).getMessageID() == messageID){
                return mResults.get(i);
            }
        }
        return null;
    }

    public GuiMessage getMessage(int index) {
        return mResults.get(index);
    }

    public int indexOf(GuiMessage message) {
        return mResults.indexOf(message);
    }

    public ArrayList<GuiMessage> getMessages() {
        return mResults;
    }

    public long getPlayingMessageID() {
        return playingMessageID;
    }

    public void setPlayingMessageID(long playingMessageID) {
        long oldPlayingMessageId = this.playingMessageID;
        this.playingMessageID = playingMessageID;
        if(oldPlayingMessageId != playingMessageID){
            if(oldPlayingMessageId != -1) {
                int index = getMessageIndex(oldPlayingMessageId);
                notifyItemChanged(index);
            }
            if(playingMessageID != -1){
                int index = getMessageIndex(playingMessageID);
                notifyItemChanged(index);
                //todo: I can insert a scroll to the playing message here
            }
        }
    }

    /** The layout for each item in the RecycleView list*/
    private static class ReceivedHolder extends MessageHolder {
        TextView sender;

        ReceivedHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_received, parent, false));
            sender = itemView.findViewById(R.id.text_sender);
        }
    }

    /** The layout for each item in the RecycleView list*/
    private static class SendHolder extends MessageHolder {
        SendHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_send, parent, false));

        }
    }

    private static abstract class MessageHolder extends RecyclerView.ViewHolder{
        protected TextView originalTextToBeTranslated;
        protected TextView text;
        protected ImageView ttsButton;

        public MessageHolder(@NonNull View itemView) {
            super(itemView);
            originalTextToBeTranslated = itemView.findViewById(R.id.original_text_to_be_translated);
            if (!showOriginalTranscriptionMsg) {
                originalTextToBeTranslated.setVisibility(View.GONE);
            }
            text = itemView.findViewById(R.id.text);
            ttsButton = itemView.findViewById(R.id.tts_button);
        }

        public void setText(String originalTextToBeTranslated, String text){
            if(originalTextToBeTranslated != null && !originalTextToBeTranslated.isEmpty()){
                this.originalTextToBeTranslated.setText(originalTextToBeTranslated);
            }else{
                this.originalTextToBeTranslated.setVisibility(View.GONE);
            }
            this.text.setText(text);
        }

        public boolean isPlayingTTS() {
            return ((int) ttsButton.getTag() == R.drawable.stop_icon);
        }

        public void setIsPlayingTTS(boolean playingTTS) {
            if(playingTTS) {
                ttsButton.setImageResource(R.drawable.stop_icon);
                ttsButton.setTag(R.drawable.stop_icon);
            }else{
                ttsButton.setImageResource(R.drawable.sound_icon);
                ttsButton.setTag(R.drawable.sound_icon);
            }
        }
    }

    public interface Callback {
        void onFirstItemAdded();
        void onTTSButtonClick(GuiMessage message, boolean play);
    }
}
