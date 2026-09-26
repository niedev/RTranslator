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

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityViewCommand;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.voice_translation.FullScreenTextActivity;

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
            messageHolder.bind(message, playingMessageID, playListener);
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

        @Override
        public void bind(GuiMessage message, long playingMessageID, View.OnClickListener playListener) {
            super.bind(message, playingMessageID, playListener);
            // Bind eventual sender text
            if(message.getMessage().getSender() != null) {
                sender.setText(message.getMessage().getSender().getName());
            }else{
                sender.setVisibility(View.GONE);
            }
        }
    }

    /** The layout for each item in the RecycleView list*/
    private static class SendHolder extends MessageHolder {
        SendHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_send, parent, false));
        }
    }

    private static abstract class MessageHolder extends RecyclerView.ViewHolder {
        @Nullable
        protected GuiMessage message;
        protected TextView originalTextToBeTranslated;
        protected TextView text;
        protected ImageView ttsButton;
        protected CardView container;

        @SuppressLint("ClickableViewAccessibility")
        public MessageHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.cardContainer);
            originalTextToBeTranslated = itemView.findViewById(R.id.original_text_to_be_translated);
            if (!showOriginalTranscriptionMsg) {
                originalTextToBeTranslated.setVisibility(View.GONE);
            }
            text = itemView.findViewById(R.id.text);
            ttsButton = itemView.findViewById(R.id.tts_button);

            // Gesture Detector for sighted users
            GestureDetectorCompat gestureDetector = new GestureDetectorCompat(itemView.getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    Toast.makeText(itemView.getContext(), "Double tap to open in full screen", Toast.LENGTH_SHORT).show();  //todo: convert the text to resource and translate it
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if(message != null) {
                        startFullScreenTextActivity(itemView.getContext(), message.getMessage().getText());
                    }
                    return true;
                }

                @Override
                public boolean onDown(MotionEvent e) {
                    // Must return true to consume the initial touch and detect subsequent taps
                    return true;
                }
            });

            // Attach touch listener to the container
            container.setOnTouchListener((v, event) -> {
                // Let the gesture detector handle the touch events
                return gestureDetector.onTouchEvent(event);
            });

            // Accessibility configuration for TalkBack users
            ViewCompat.replaceAccessibilityAction(
                    container,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    "open message in fullscreen", // Determines what Talkback TTS says   todo: convert the text to resource and translate it + test Talkback here
                    new AccessibilityViewCommand() {
                        @Override
                        public boolean perform(@NonNull View view, @Nullable CommandArguments arguments) {
                            if(message != null) {
                                startFullScreenTextActivity(itemView.getContext(), message.getMessage().getText());
                            }
                            return true;
                        }
                    }
            );
        }

        public void bind(GuiMessage message, long playingMessageID, View.OnClickListener playListener){
            this.message = message;
            // Bind message text
            setText(message.getMessage().getTextToTranslate(), message.getMessage().getText());
            // Bind tts button status and listener
            setIsPlayingTTS(message.getMessageID() == playingMessageID);
            ttsButton.setOnClickListener(playListener);
        }

        private void setText(String originalTextToBeTranslated, String text){
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

    private static void startFullScreenTextActivity(Context context, String text) {
        Intent intent = new Intent(context, FullScreenTextActivity.class);
        intent.putExtra(FullScreenTextActivity.EXTRA_TEXT, text);
        context.startActivity(intent);
    }

    public interface Callback {
        void onFirstItemAdded();
        void onTTSButtonClick(GuiMessage message, boolean play);
    }
}
