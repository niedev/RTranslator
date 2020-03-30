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

package nie.translator.rtranslatordevedition.tools.gui.peers;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslatordevedition.tools.gui.peers.array.PeerListArray;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;

public class PeerListAdapter extends BaseAdapter {
    public static final int HEADER = 0;
    public static final int HOST = 1;
    public static final int HOST_CONNECTED = 3;
    public static final int HOST_RECENT = 4;
    private PeerListArray array;
    private LayoutInflater inflater;
    private Callback callback;
    private CustomAnimator animator = new CustomAnimator();
    private Activity activity;
    private RecentPeersDataManager recentPeersDataManager;
    private boolean isClickable = true;
    private boolean showToast = false;

    public PeerListAdapter(Activity activity, PeerListArray array, Callback callback) {
        this.array = array;
        recentPeersDataManager = ((Global) activity.getApplication()).getRecentPeersDataManager();
        this.callback = callback;
        if (array.size() > 0) {
            callback.onFirstItemAdded();
        }
        this.activity = activity;
        notifyDataSetChanged();
        inflater = activity.getLayoutInflater();
    }

    @Override
    public int getCount() {
        return array.size();
    }

    @Override
    public Object getItem(int i) {
        return array.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        Listable listable = get(position);
        if (listable instanceof Header) {
            return HEADER;
        }
        if (listable instanceof RecentPeer) {
            return HOST_RECENT;
        }
        if (listable instanceof GuiPeer) {
            GuiPeer peer = (GuiPeer) listable;
            if (peer.isConnected() || peer.isReconnecting()) {
                return HOST_CONNECTED;
            } else {
                return HOST;
            }
        }
        return -1;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        final Object item = getItem(position);
        int itemType = getItemViewType(position);

        if (itemType == HEADER) {
            String headerText = ((Header) item).getText();
            if (view == null) {
                view = inflater.inflate(R.layout.component_row_header, null);
            }
            ((TextView) view.findViewById(R.id.header_text)).setText(headerText);
        } else if (itemType == HOST_CONNECTED) {
            GuiPeer guiPeer = (GuiPeer) item;
            if (view == null) {
                view = inflater.inflate(R.layout.component_row_connected, null);
            }
            Bitmap image = guiPeer.getUserImage();
            if (image != null) {
                RoundedBitmapDrawable circlularImage = RoundedBitmapDrawableFactory.create(activity.getResources(), image);
                circlularImage.setCircular(true);
                ((ImageView) view.findViewById(R.id.user_image)).setImageDrawable(circlularImage);
            }else{
                ((ImageView) view.findViewById(R.id.user_image)).setImageResource(R.drawable.user_icon);
            }
            view.findViewById(R.id.exit_button).setOnClickListener(new ExitClickListener(guiPeer));
            if (guiPeer.isReconnecting()) {
                view.findViewById(R.id.offline_icon).setVisibility(View.VISIBLE);
            } else {
                view.findViewById(R.id.offline_icon).setVisibility(View.INVISIBLE);
            }
            ((TextView) view.findViewById(R.id.textRowConnected)).setText(guiPeer.getName());

        } else if (itemType == HOST) {
            GuiPeer guiPeer= (GuiPeer) item;
            String peerName = ((Peer) item).getName();
            if (view == null) {
                view = inflater.inflate(R.layout.component_row, null);
            }
            Bitmap image = guiPeer.getUserImage();
            if (image != null) {
                RoundedBitmapDrawable circlularImage = RoundedBitmapDrawableFactory.create(activity.getResources(), image);
                circlularImage.setCircular(true);
                ((ImageView) view.findViewById(R.id.user_image_list)).setImageDrawable(circlularImage);
            }else{
                ((ImageView) view.findViewById(R.id.user_image_list)).setImageResource(R.drawable.user_icon);
            }
            ((TextView) view.findViewById(R.id.textRow)).setText(peerName);

        } else if (itemType == HOST_RECENT) {
            final RecentPeer recentPeer = (RecentPeer) item;
            String peerName = recentPeer.getName();
            if (view == null) {
                view = inflater.inflate(R.layout.component_row_recent, null);
                // if you don't set it to false then clicking on the whole row won't work
                ((ImageButton) view.findViewById(R.id.threeDotsButton)).setFocusable(false);
            }
            Bitmap image = recentPeer.getUserImage();
            if (image != null) {
                RoundedBitmapDrawable circlularImage = RoundedBitmapDrawableFactory.create(activity.getResources(), image);
                circlularImage.setCircular(true);
                ((ImageView) view.findViewById(R.id.user_image_recent)).setImageDrawable(circlularImage);
            }else{
                ((ImageView) view.findViewById(R.id.user_image_recent)).setImageResource(R.drawable.user_icon);
            }
            ((ImageButton) view.findViewById(R.id.threeDotsButton)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showDeletePopup(recentPeer, view);
                }
            });
            if (recentPeer.isAvailable()) {
                ((TextView) view.findViewById(R.id.path)).setText(R.string.available);
            } else {
                ((TextView) view.findViewById(R.id.path)).setText(R.string.not_available);
            }
            ((TextView) view.findViewById(R.id.textRowRecent)).setText(peerName);

        }
        return view;
    }

    public synchronized void add(Listable listable) {
        if (array.size() == 0) {
            callback.onFirstItemAdded();
        }
        array.add(listable);
        notifyDataSetChanged();
    }

    public synchronized void set(int index, Listable item) {
        array.set(index, item);
        notifyDataSetChanged();
    }

    public Listable get(int i) {
        return array.get(i);
    }

    public int indexOf(Listable object) {
        return array.indexOf(object);
    }

    public int indexOfPeer(String uniqueName){
        for (int i = 0; i < array.size(); i++) {
            Listable listable = array.get(i);
            if (listable instanceof GuiPeer) {
                String uniqueName1 = ((GuiPeer) listable).getUniqueName();
                if (uniqueName1.length() > 0 && uniqueName1.equals(uniqueName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public synchronized int indexOfRecentPeer(RecentPeer recentPeer) {
        return array.indexOf(recentPeer);
    }

    public synchronized int indexOfRecentPeer(Peer peer) {
        for (int i = 0; i < array.size(); i++) {
            Listable listable = array.get(i);
            if (listable instanceof RecentPeer) {
                if(((RecentPeer) listable).getPeer().getDevice()!=null && ((RecentPeer) listable).getPeer().getDevice().getAddress()!=null){
                    if(peer.getDevice()!=null && peer.getDevice().getAddress()!=null){
                        if(((RecentPeer) listable).getPeer().getDevice().getAddress().equals(peer.getDevice().getAddress())){
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    public synchronized int indexOfRecentPeer(String uniqueName) {
        for (int i = 0; i < array.size(); i++) {
            Listable listable = array.get(i);
            if (listable instanceof RecentPeer) {
                String uniqueName1 = ((RecentPeer) listable).getUniqueName();
                if (uniqueName1.length() > 0 && uniqueName1.equals(uniqueName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public synchronized void remove(Listable peer) {
        if (array.remove(peer)) {
            notifyDataSetChanged();
        }
        if (array.size() == 0) {
            // deleting the listview
            callback.onLastItemRemoved();
        }
    }

    public synchronized void clear() {
        array.clear();
        notifyDataSetChanged();
        if (array.size() == 0) {
            callback.onLastItemRemoved();
        }
    }

    public int size() {
        return array.size();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public boolean isClickable() {
        return isClickable;
    }

    public void setClickable(boolean clickable, boolean showToast) {
        this.isClickable = clickable;
        this.showToast = showToast;
    }

    public boolean getShowToast() {
        return showToast;
    }

    public void setShowToast(boolean showToast) {
        this.showToast = showToast;
    }

    private void showDeletePopup(final RecentPeer peer, View view) {
        PopupMenu popup = new PopupMenu(activity, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.recent_row_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // deleting the RecentPeerEntity from the list of recent devices
                recentPeersDataManager.deleteRecentPeer(peer);
                // deleting the peer from the listView
                remove(peer);
                // insertion of the peer among the peers found for the case in which the recent device is available
                if (peer.isAvailable()) {
                    add(new GuiPeer(peer.getPeer(), null));
                }
                return true;
            }
        });
        popup.show();
    }

    public Callback getCallback() {
        return callback;
    }

    private class ExitClickListener implements View.OnClickListener {
        private GuiPeer peer;

        ExitClickListener(GuiPeer peer) {
            this.peer = peer;
        }

        @Override
        public void onClick(View view) {
            callback.onClickExit(peer);
            // see if the onDisconnected () callback is automatically called
        }
    }

    public static abstract class Callback {
        public void onFirstItemAdded() {
        }

        public void onLastItemRemoved() {
        }

        public void onClickNotAllowed(boolean showToast) {
        }

        public void onClickExit(Peer peer) {
        }
    }
}
