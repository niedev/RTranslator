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

package nie.translator.rtranslatordevedition.tools.gui.peers.array;

import android.content.Context;
import androidx.annotation.NonNull;
import java.util.Collection;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.WeightArray;
import nie.translator.rtranslatordevedition.tools.gui.WeightElement;
import nie.translator.rtranslatordevedition.tools.gui.peers.GuiPeer;
import nie.translator.rtranslatordevedition.tools.gui.peers.Header;
import nie.translator.rtranslatordevedition.tools.gui.peers.Listable;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeer;

public class PairingArray implements PeerListArray {
    private static final int RECENT_HADER_WEIGHT = 0;
    private static final int RECENT_PEER_WEIGHT = 1;
    private static final int FOUND_HADER_WEIGHT = 2;
    private static final int FOUND_PEER_WEIGHT = 3;
    private WeightArray weightArray = new WeightArray();
    private int numberOfRecentPeers = 0;
    private int numberOfFoundPeers = 0;
    private Header recentsHeader;
    private Header foundHeader;


    public PairingArray(Context context, @NonNull Collection<? extends Listable> c) {
        super();
        recentsHeader = new Header(context.getResources().getString(R.string.header_recents_peers));
        foundHeader = new Header(context.getResources().getString(R.string.header_found_peers));
        // this is called by entering only the connected peers, the unconnected peers will be added one by one with the search
        for (Listable element : c) {
            add(element);
        }

    }

    public PairingArray(Context context) {
        super();
        recentsHeader = new Header(context.getResources().getString(R.string.header_recents_peers));
        foundHeader = new Header(context.getResources().getString(R.string.header_found_peers));
    }

    @Override
    public boolean add(Listable listable) {
        if (listable instanceof RecentPeer) {
            if (numberOfRecentPeers == 0) {
                weightArray.add(new WeightElement(recentsHeader, RECENT_HADER_WEIGHT));
            }
            numberOfRecentPeers++;
            weightArray.add(new WeightElement(listable, RECENT_PEER_WEIGHT));
            return true;
        }
        if (listable instanceof GuiPeer) {
            if (numberOfFoundPeers == 0) {
                weightArray.add(new WeightElement(foundHeader, FOUND_HADER_WEIGHT));
            }
            numberOfFoundPeers++;
            weightArray.add(new WeightElement(listable, FOUND_PEER_WEIGHT));
            return true;
        }
        return false;
    }

    @Override
    public void set(int index, Listable newListable) {
        if (newListable instanceof GuiPeer) {
            if (weightArray.get(index).getElement() instanceof GuiPeer) {
                weightArray.set(index, new WeightElement(newListable, FOUND_PEER_WEIGHT));
            }
        }
    }

    @Override
    public Listable get(int index) {
        return weightArray.get(index).getElement();
    }

    @Override
    public int size() {
        return weightArray.size();
    }

    @Override
    public int indexOf(Listable guiPeer) {
        return weightArray.indexOf(new WeightElement(guiPeer));
    }

    @Override
    public boolean remove(Listable listable) {
        boolean isRemoved = false;
        if (listable instanceof GuiPeer || listable instanceof RecentPeer) {
            isRemoved = weightArray.remove(new WeightElement(listable));
            if (isRemoved) {
                if (listable instanceof RecentPeer) {
                    numberOfRecentPeers--;
                    if (numberOfRecentPeers == 0) {
                        weightArray.remove(new WeightElement(recentsHeader, RECENT_HADER_WEIGHT));
                    }
                } else {
                    numberOfFoundPeers--;
                    if (numberOfFoundPeers == 0) {
                        weightArray.remove(new WeightElement(foundHeader, FOUND_HADER_WEIGHT));
                    }
                }
            }
        }
        return isRemoved;
    }

    @Override
    public void clear() {
        for (int i = 0; i < size(); i++) {
            Listable item = get(i);
            if (item instanceof GuiPeer) {
                remove(item);
            } else if (item instanceof RecentPeer) {
                ((RecentPeer) item).setDevice(null);
            }
        }
    }
}
