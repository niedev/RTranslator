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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslatordevedition.R;

/** Is used to connect to the RecycleView, which functions as a ListView, a list of strings, which will be inserted in the ViewHolder layout and this will be inserted in the list**/
public class MessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int MINE = 0;
    private static final int NON_MINE = 1;
    private static final int PREVIEW = 2;
    private ArrayList<GuiMessage> mResults = new ArrayList<>();
    private Callback callback;

    public MessagesAdapter(ArrayList<GuiMessage> messages, @NonNull Callback callback) {
        this.callback = callback;
        if (messages != null) {
            if (messages.size() > 0) {
                callback.onFirstItemAdded();
            }
            mResults.addAll(messages);
            notifyItemRangeInserted(0, messages.size() - 1);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case MINE:
                return new SendHolder(LayoutInflater.from(parent.getContext()), parent);
            case NON_MINE:
                return new ReceivedHolder(LayoutInflater.from(parent.getContext()), parent);
            case PREVIEW:
                return new PreviewHolder(LayoutInflater.from(parent.getContext()), parent);
        }
        return new PreviewHolder(LayoutInflater.from(parent.getContext()), parent);  // to not return null
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MessageHolder) {
            GuiMessage message = mResults.get(position);
            if (holder instanceof ReceivedHolder && message.getMessage().getSender() != null) {
                ((ReceivedHolder) holder).text.setVisibility(View.GONE);
                ((ReceivedHolder) holder).containerSender.setVisibility(View.VISIBLE);
                ((ReceivedHolder) holder).sender.setText(message.getMessage().getSender().getName());
            }
            ((MessageHolder) holder).setText(message.getMessage().getText());
        }
    }

    @Override
    public int getItemViewType(int position) {
        GuiMessage message = mResults.get(position);
        if (message.isMine()) {
            if (message.isFinal()) {
                return MINE;
            } else {
                return PREVIEW;
            }
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
        return true;
    }

    public void addMessage(GuiMessage message) {
        if (getItemCount() == 0) {
            callback.onFirstItemAdded();
        }
        if (getPreviewIndex() == -1) {
            mResults.add(message);
            notifyItemInserted(getItemCount() - 1);
        } else {
            mResults.add(getItemCount() - 1, message);  // - 1 and not -2 because the getItemCount before the add () is already -1 compared to after the add ()
            notifyItemInserted(getItemCount() - 2);
        }
    }

    public void setMessage(int index, GuiMessage message) {
        mResults.set(index, message);
        notifyDataSetChanged();  // not animation
        //notifyItemChanged(index);  //animation
    }

    public GuiMessage getMessage(int index) {
        return mResults.get(index);
    }

    public GuiMessage getPreview() {
        if (getItemCount() > 0) {
            int lastIndex = getItemCount() - 1;
            if (getItemViewType(lastIndex) == PREVIEW) {
                return getMessage(lastIndex);
            }
        }
        return null;
    }

    public void setPreviewText(String text) {
        GuiMessage preview = getPreview();
        if (preview != null) {
            preview.getMessage().setText(text);
            notifyDataSetChanged();  // not animation
            //notifyItemChanged(getPreviewIndex());   //animation
        }
    }

    public int getPreviewIndex() {
        if (getItemCount() > 0) {
            int index = getItemCount() - 1;
            if (getItemViewType(index) == PREVIEW) {
                return index;
            }
        }
        return -1;
    }

    public int indexOf(GuiMessage message) {
        return mResults.indexOf(message);
    }

    public ArrayList<GuiMessage> getMessages() {
        return mResults;
    }

    /** The layout for each item in the RecicleView list*/
    private class ReceivedHolder extends RecyclerView.ViewHolder implements MessageHolder {
        TextView text;

        LinearLayout containerSender;
        TextView textSender;
        TextView sender;


        ReceivedHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_received, parent, false));
            text = itemView.findViewById(R.id.text_content);

            containerSender = itemView.findViewById(R.id.sender_container);
            textSender = itemView.findViewById(R.id.text_content2);
            sender = itemView.findViewById(R.id.text_sender);
        }

        @Override
        public void setText(String text) {
            this.textSender.setText(text);
            this.text.setText(text);
        }
    }

    /** The layout for each item in the RecicleView list*/
    private class SendHolder extends RecyclerView.ViewHolder implements MessageHolder {
        TextView text;
        CardView card;

        SendHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_send, parent, false));
            text = itemView.findViewById(R.id.text);
            card = itemView.findViewById(R.id.card);
        }

        @Override
        public void setText(String text) {
            this.text.setText(text);
        }
    }

    private class PreviewHolder extends RecyclerView.ViewHolder implements MessageHolder {
        TextView text;
        CardView card;

        PreviewHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_message_preview, parent, false));
            text = itemView.findViewById(R.id.textPreview);
            card = itemView.findViewById(R.id.cardPreview);
        }

        @Override
        public void setText(String text) {
            this.text.setText(text);
        }
    }

    interface MessageHolder {
        void setText(String text);
    }

    public interface Callback {
        void onFirstItemAdded();
    }
}
