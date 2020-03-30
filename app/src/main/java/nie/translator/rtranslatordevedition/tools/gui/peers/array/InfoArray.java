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

public class InfoArray implements PeerListArray {
    private static final int CONNECTED_HADER_WEIGHT = 0;
    private static final int CONNECTED_PEER_WEIGHT = 1;
    private static final int FOUND_HADER_WEIGHT = 2;
    private static final int FOUND_PEER_WEIGHT = 3;
    private WeightArray weightArray = new WeightArray();
    private Header foundHeader;
    private int numberOfFoundPeers = 0;


    public InfoArray(Context context, @NonNull Collection<? extends GuiPeer> c) {
        // this is called by entering only the connected peers, the unconnected peers will be added one by one with the search
        foundHeader = new Header(context.getResources().getString(R.string.header_found_peers));
        Header connectedHeader = new Header(context.getResources().getString(R.string.header_connected_peers));
        weightArray.add(new WeightElement(connectedHeader));
        for (GuiPeer element : c) {
            add(element);
        }
    }

    @Override
    public boolean add(Listable listable) {
        if (listable instanceof GuiPeer) {
            GuiPeer guiPeer = (GuiPeer) listable;
            if (guiPeer.isConnected() || guiPeer.isReconnecting() || guiPeer.isDisconnecting()) {
                return weightArray.add(new WeightElement(guiPeer, CONNECTED_PEER_WEIGHT));
            } else {
                if (numberOfFoundPeers == 0) {
                    weightArray.add(new WeightElement(foundHeader, FOUND_HADER_WEIGHT));
                }
                numberOfFoundPeers++;
                return weightArray.add(new WeightElement(guiPeer, FOUND_PEER_WEIGHT));
            }
        }
        return false;
    }

    @Override
    public void set(int index, Listable newListable) {
        if (newListable instanceof GuiPeer) {
            GuiPeer newPeer = (GuiPeer) newListable;
            GuiPeer oldPeer = null;
            if (weightArray.get(index).getElement() instanceof GuiPeer) {
                oldPeer = (GuiPeer) weightArray.get(index).getElement();
            }
            if (oldPeer != null) {
                if (newPeer.isConnected() || newPeer.isReconnecting() || newPeer.isDisconnecting()) {
                    weightArray.set(index, new WeightElement(newListable, CONNECTED_PEER_WEIGHT));
                    if (!(oldPeer.isConnected() || oldPeer.isReconnecting() || oldPeer.isDisconnecting())) {
                        numberOfFoundPeers--;
                        if (numberOfFoundPeers == 0) {
                            weightArray.remove(new WeightElement(foundHeader));
                        }
                    }
                } else {
                    weightArray.set(index, new WeightElement(newListable, FOUND_PEER_WEIGHT));
                    if (oldPeer.isConnected() || oldPeer.isReconnecting() || oldPeer.isDisconnecting()) {
                        if (numberOfFoundPeers == 0) {
                            weightArray.add(new WeightElement(foundHeader, FOUND_HADER_WEIGHT));
                        }
                        numberOfFoundPeers++;
                    }
                }
            }
        }
    }

    @Override
    public Listable get(int index) {
        return weightArray.get(index).getElement();
    }

    @Override
    public int indexOf(Listable listable) {
        if (listable instanceof GuiPeer) {
            GuiPeer guiPeer = (GuiPeer) listable;
            return weightArray.indexOf(new WeightElement(guiPeer));
        }
        return -1;
    }

    @Override
    public int size() {
        return weightArray.size();
    }

    @Override
    public boolean remove(Listable listable) {
        if (listable instanceof GuiPeer) {
            GuiPeer guiPeer = (GuiPeer) listable;
            int index = weightArray.indexOf(new WeightElement(guiPeer));
            if (index != -1) {
                GuiPeer removingPeer = (GuiPeer) weightArray.get(index).getElement();
                if (!(removingPeer.isConnected() || removingPeer.isReconnecting() || removingPeer.isDisconnecting())) {
                    numberOfFoundPeers--;
                    if (numberOfFoundPeers == 0) {
                        weightArray.remove(new WeightElement(foundHeader));
                    }
                }
                return weightArray.remove(new WeightElement(removingPeer));
            }
        }
        return false;
    }

    @Override
    public void clear() {
        for (int i = 0; i < weightArray.size(); i++) {
            if (weightArray.get(i).getElement() instanceof GuiPeer) {
                GuiPeer removingPeer = (GuiPeer) weightArray.get(i).getElement();
                if (!(removingPeer.isConnected() || removingPeer.isReconnecting() || removingPeer.isDisconnecting())) {
                    remove(removingPeer);
                }
            }
        }
    }
}
