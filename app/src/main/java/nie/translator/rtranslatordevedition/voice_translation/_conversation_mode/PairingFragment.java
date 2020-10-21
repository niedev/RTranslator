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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode;

import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.FileLog;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.RequestDialog;
import nie.translator.rtranslatordevedition.tools.gui.WalkieTalkieButton;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslatordevedition.tools.gui.peers.GuiPeer;
import nie.translator.rtranslatordevedition.tools.gui.peers.Listable;
import nie.translator.rtranslatordevedition.tools.gui.peers.PeerListAdapter;
import nie.translator.rtranslatordevedition.tools.gui.peers.array.PairingArray;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.BluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.client.BluetoothConnectionClient;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;


public class PairingFragment extends PairingToolbarFragment {
    public static final int CONNECTION_TIMEOUT = 5000;
    private RequestDialog connectionRequestDialog;
    private RequestDialog connectionConfirmDialog;
    private ConstraintLayout constraintLayout;
    private Peer confirmConnectionPeer;
    private WalkieTalkieButton walkieTalkieButton;
    private ListView listViewGui;
    private Channel.Timer connectionTimer;
    @Nullable
    private PeerListAdapter listView;
    private TextView discoveryDescription;
    private TextView noDevices;
    private TextView noPermissions;
    private TextView noBluetoothLe;
    private final Object lock = new Object();
    private VoiceTranslationActivity.Callback communicatorCallback;
    private RecentPeersDataManager recentPeersDataManager;
    private CustomAnimator animator = new CustomAnimator();
    private Peer connectingPeer;

    public PairingFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        communicatorCallback = new VoiceTranslationActivity.Callback() {
            @Override
            public void onSearchStarted() {
                buttonSearch.setSearching(true, animator);
            }

            @Override
            public void onSearchStopped() {
                buttonSearch.setSearching(false, animator);
            }

            @Override
            public void onConnectionRequest(final GuiPeer peer) {
                super.onConnectionRequest(peer);
                if (peer != null) {
                    String time = DateFormat.getDateTimeInstance().format(new Date());
                    FileLog.appendLog("\nnearby " + time + ": received connection request from:" + peer.getUniqueName());
                    connectionRequestDialog = new RequestDialog(activity, getResources().getString(R.string.dialog_confirm_connection_request) + peer.getName() + " ?", 15000, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.acceptConnection(peer);
                        }
                    }, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.rejectConnection(peer);
                        }
                    });
                    connectionRequestDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            connectionRequestDialog = null;
                        }
                    });
                    connectionRequestDialog.show();
                }
            }

            @Override
            public void onConnectionSuccess(GuiPeer peer) {
                super.onConnectionSuccess(peer);
                connectingPeer = null;
                resetConnectionTimer();
                activity.setFragment(VoiceTranslationActivity.CONVERSATION_FRAGMENT);
            }

            @Override
            public void onConnectionFailed(GuiPeer peer, int errorCode) {
                super.onConnectionFailed(peer, errorCode);
                if (connectingPeer != null) {
                    if (connectionTimer != null && !connectionTimer.isFinished() && errorCode != BluetoothConnectionClient.CONNECTION_REJECTED) {
                        // the timer has not expired and the connection has not been refused, so we try again
                        activity.connect(peer);
                    } else {
                        // the timer has expired, so the failure is notified
                        clearFoundPeers();
                        startSearch();
                        activateInputs();
                        disappearLoading(true, null);
                        connectingPeer = null;
                        if (errorCode == BluetoothConnectionClient.CONNECTION_REJECTED) {
                            Toast.makeText(activity, peer.getName() + getResources().getString(R.string.error_connection_rejected), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(activity, getResources().getString(R.string.error_connection), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }

            @Override
            public void onPeerFound(GuiPeer peer) {
                super.onPeerFound(peer);
                synchronized (lock) {
                    if (listView != null) {
                        int recentIndex = listView.indexOfRecentPeer(peer.getUniqueName());
                        if (recentIndex == -1) {
                            BluetoothAdapter bluetoothAdapter = global.getBluetoothCommunicator().getBluetoothAdapter();
                            GuiPeer guiPeer = new GuiPeer(peer, null);
                            int index = listView.indexOfPeer(guiPeer.getUniqueName());
                            if (index == -1) {
                                listView.add(guiPeer);
                            } else {
                                Peer peer1 = (Peer) listView.get(index);
                                if (peer.isBonded(bluetoothAdapter)) {
                                    listView.set(index, guiPeer);
                                } else if (peer1.isBonded(bluetoothAdapter)) {
                                    listView.set(index, listView.get(index));
                                } else {
                                    listView.set(index, guiPeer);
                                }
                            }
                        } else {
                            RecentPeer recentPeer = (RecentPeer) listView.get(recentIndex);
                            recentPeer.setDevice(peer.getDevice());
                            listView.set(recentIndex, recentPeer);
                        }
                    }
                }
            }

            @Override
            public void onPeerUpdated(GuiPeer peer, GuiPeer newPeer) {
                super.onPeerUpdated(peer, newPeer);
                onPeerFound(newPeer);
            }

            @Override
            public void onPeerLost(GuiPeer peer) {
                synchronized (lock) {
                    if (listView != null) {
                        int recentIndex = listView.indexOfRecentPeer(peer);  // because we don't have the name but only the address of the device
                        if (recentIndex == -1) {
                            listView.remove(new GuiPeer(peer, null));
                        } else {
                            RecentPeer recentPeer = (RecentPeer) listView.get(recentIndex);
                            recentPeer.setDevice(null);
                            listView.set(recentIndex, recentPeer);
                        }

                        if (peer.equals(getConfirmConnectionPeer())) {
                            RequestDialog requestDialog = getConnectionConfirmDialog();
                            if (requestDialog != null) {
                                requestDialog.cancel();
                            }
                        }
                    }
                }
            }

            @Override
            public void onBluetoothLeNotSupported() {

            }

            @Override
            public void onMissingSearchPermission() {
                super.onMissingSearchPermission();
                clearFoundPeers();
                if (noPermissions.getVisibility() != View.VISIBLE) {
                    // appearance of the written of missing permission
                    listViewGui.setVisibility(View.GONE);
                    noDevices.setVisibility(View.GONE);
                    discoveryDescription.setVisibility(View.GONE);
                    noPermissions.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onSearchPermissionGranted() {
                super.onSearchPermissionGranted();
                if (noPermissions.getVisibility() == View.VISIBLE) {
                    // disappearance of the written of missing permission
                    noPermissions.setVisibility(View.GONE);
                    noDevices.setVisibility(View.VISIBLE);
                    discoveryDescription.setVisibility(View.VISIBLE);
                    initializePeerList();
                } else {
                    //reset list view
                    clearFoundPeers();
                }
                startSearch();
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pairing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout = view.findViewById(R.id.container);
        walkieTalkieButton = view.findViewById(R.id.buttonStart);
        listViewGui = view.findViewById(R.id.list_view);
        discoveryDescription = view.findViewById(R.id.discoveryDescription);
        noDevices = view.findViewById(R.id.noDevices);
        noPermissions = view.findViewById(R.id.noPermission);
        noBluetoothLe = view.findViewById(R.id.noBluetoothLe);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Toolbar toolbar = activity.findViewById(R.id.toolbarPairing);
        activity.setActionBar(toolbar);
        recentPeersDataManager = ((Global) activity.getApplication()).getRecentPeersDataManager();
        // we give the constraint layout the information on the system measures (status bar etc.), which has the fragmentContainer,
        // because they are not passed to it if started with a Transaction and therefore it overlaps the status bar because it fitsSystemWindows does not work
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(windowInsets.replaceSystemWindowInsets(windowInsets.getSystemWindowInsetLeft(),windowInsets.getSystemWindowInsetTop(),windowInsets.getSystemWindowInsetRight(),0));
        }

        // setting of listeners
        walkieTalkieButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (walkieTalkieButton.getState() == WalkieTalkieButton.STATE_SINGLE) {
                    activity.setFragment(VoiceTranslationActivity.WALKIE_TALKIE_FRAGMENT);
                }
            }
        });

        // setting of array adapter
        initializePeerList();
        listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                synchronized (lock) {
                    if (listView != null) {
                        // start the pop up and then connect to the peer
                        if (listView.isClickable()) {
                            Listable item = listView.get(i);
                            if (item instanceof Peer) {
                                Peer peer = (Peer) item;
                                connect(peer);
                            }
                            if (item instanceof RecentPeer) {
                                RecentPeer recentPeer = (RecentPeer) item;
                                if (recentPeer.isAvailable()) {
                                    connect(recentPeer.getPeer());
                                }
                            }
                        } else {
                            listView.getCallback().onClickNotAllowed(listView.getShowToast());
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // release buttons and eliminate any loading
        activateInputs();
        disappearLoading(true, null);
        // if you don't have permission to search, activate from here
        if (!Tools.hasPermissions(activity, VoiceTranslationActivity.REQUIRED_PERMISSIONS)) {
            startSearch();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //restore status
        /*if (activity.getConnectingPeersList().size() > 0) {
            deactivateInputs();
            appearLoading(null);
        } else {
            clearFoundPeers();
            disappearLoading(null);
            activateInputs();
        }*/
        clearFoundPeers();

        activity.addCallback(communicatorCallback);
        // if you have permission to search it is activated from here
        if (Tools.hasPermissions(activity, VoiceTranslationActivity.REQUIRED_PERMISSIONS)) {
            startSearch();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        activity.removeCallback(communicatorCallback);
        stopSearch();
        //communicatorCallback.onSearchStopped();
        if (connectingPeer != null) {
            activity.disconnect(connectingPeer);
            connectingPeer = null;
        }
    }

    private void connect(final Peer peer) {
        connectingPeer = peer;
        confirmConnectionPeer = peer;
        connectionConfirmDialog = new RequestDialog(activity, getResources().getString(R.string.dialog_confirm_connection) + peer.getName() + "?", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deactivateInputs();
                appearLoading(null);
                activity.connect(peer);
                startConnectionTimer();
            }
        }, null);
        connectionConfirmDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                confirmConnectionPeer = null;
                connectionConfirmDialog = null;
            }
        });
        connectionConfirmDialog.show();
    }

    @Override
    protected void startSearch() {
        int result = activity.startSearch();
        if (result != BluetoothCommunicator.SUCCESS) {
            if (result == BluetoothCommunicator.BLUETOOTH_LE_NOT_SUPPORTED && noBluetoothLe.getVisibility() != View.VISIBLE) {
                // appearance of the bluetooth le missing sign
                listViewGui.setVisibility(View.GONE);
                noDevices.setVisibility(View.GONE);
                discoveryDescription.setVisibility(View.GONE);
                noBluetoothLe.setVisibility(View.VISIBLE);
            } else if (result != VoiceTranslationActivity.NO_PERMISSIONS && result != BluetoothCommunicator.ALREADY_STARTED) {
                Toast.makeText(activity, getResources().getString(R.string.error_starting_search), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopSearch() {
        activity.stopSearch(connectingPeer == null);
    }

    private void activateInputs() {
        /*animator.createAnimatorColor(walkieTalkieButton.getBackground(), GuiTools.getColor(requireContext(), R.color.gray), GuiTools.getColor(requireContext(), R.color.accent_white), getResources().getInteger(R.integer.durationLong)).start();
        animator.createAnimatorColor(walkieTalkieButtonIcon.getDrawable(), GuiTools.getColor(requireContext(), R.color.white), GuiTools.getColor(requireContext(), R.color.primary), getResources().getInteger(R.integer.durationLong)).start();*/
        walkieTalkieButton.show();
        //click reactivation of listView
        setListViewClickable(true, true);
        walkieTalkieButton.setState(WalkieTalkieButton.STATE_SINGLE);
    }

    private void deactivateInputs() {
        /*animator.createAnimatorColor(walkieTalkieButton.getBackground(), walkieTalkieButton.getBackgroundTintList().getDefaultColor(), GuiTools.getColor(requireContext(), R.color.gray), getResources().getInteger(R.integer.durationLong)).start();
        animator.createAnimatorColor(walkieTalkieButtonIcon.getDrawable(), GuiTools.getColor(requireContext(), R.color.primary), GuiTools.getColor(requireContext(), R.color.white), getResources().getInteger(R.integer.durationLong)).start();*/
        walkieTalkieButton.hide();
        //click deactivation of listView
        setListViewClickable(false, true);
        walkieTalkieButton.setState(WalkieTalkieButton.STATE_CONNECTING);
    }

    public Peer getConfirmConnectionPeer() {
        return confirmConnectionPeer;
    }

    public RequestDialog getConnectionConfirmDialog() {
        return connectionConfirmDialog;
    }

    private void startConnectionTimer() {
        connectionTimer = new Channel.Timer(CONNECTION_TIMEOUT);
        connectionTimer.start();
    }

    private void resetConnectionTimer() {
        if (connectionTimer != null) {
            connectionTimer.cancel();
            connectionTimer = null;
        }
    }

    private void initializePeerList() {
        final PeerListAdapter.Callback callback = new PeerListAdapter.Callback() {
            @Override
            public void onFirstItemAdded() {
                super.onFirstItemAdded();
                discoveryDescription.setVisibility(View.GONE);
                noDevices.setVisibility(View.GONE);
                listViewGui.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLastItemRemoved() {
                super.onLastItemRemoved();
                listViewGui.setVisibility(View.GONE);
                if (noPermissions.getVisibility() != View.VISIBLE) {
                    discoveryDescription.setVisibility(View.VISIBLE);
                    noDevices.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onClickNotAllowed(boolean showToast) {
                super.onClickNotAllowed(showToast);
                Toast.makeText(activity, getResources().getString(R.string.error_cannot_interact_connection), Toast.LENGTH_SHORT).show();
            }
        };

        recentPeersDataManager.getRecentPeers(new RecentPeersDataManager.RecentPeersListener() {
            @Override
            public void onRecentPeersObtained(ArrayList<RecentPeer> recentPeers) {
                if (recentPeers.size() > 0) {
                    listView = new PeerListAdapter(activity, new PairingArray(activity,recentPeers), callback);
                } else {
                    listView = new PeerListAdapter(activity, new PairingArray(activity), callback);
                }
                listViewGui.setAdapter(listView);
            }
        });
    }

    public void clearFoundPeers() {
        if (listView != null) {
            listView.clear();
        }
    }

    public void setListViewClickable(boolean isClickable, boolean showToast) {
        if (listView != null) {
            listView.setClickable(isClickable, showToast);
        }
    }
}