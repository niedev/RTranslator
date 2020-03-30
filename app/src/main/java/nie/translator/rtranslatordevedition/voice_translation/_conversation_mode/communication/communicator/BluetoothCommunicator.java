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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.Channel;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.client.BluetoothConnectionClient;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.server.BluetoothConnectionServer;


public class BluetoothCommunicator {
    // constants
    public static final int CLIENT = 0;
    public static final int SERVER = 1;
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ALREADY_STARTED = -3;
    public static final int ALREADY_STOPPED = -4;
    public static final int NOT_MAIN_THREAD = -5;
    public static final int DESTROYING = -6;
    public static final int BLUETOOTH_LE_NOT_SUPPORTED = -7;
    // variables
    private boolean advertising = false;
    private boolean discovering = false;
    private boolean turningOnBluetooth = false;
    private boolean turningOffBluetooth = false;
    private boolean restartingBluetooth = false;
    private boolean initializingConnection = false;
    private boolean destroying = false;
    private int strategy;
    private int originalBluetoothState = -1;
    private String uniqueName;
    private String originalName;
    private ArrayDeque<Message> pendingMessages = new ArrayDeque<>();
    private ArrayDeque<Message> pendingData = new ArrayDeque<>();
    // objects
    private Context context;
    @Nullable
    private BluetoothAdapter bluetoothAdapter;
    @Nullable
    private BluetoothConnectionServer connectionServer;
    @Nullable
    private BluetoothConnectionClient connectionClient;
    private ArrayList<Callback> clientCallbacks = new ArrayList<>();
    private Handler mainHandler;
    private AdvertiseCallback advertiseCallback;
    private ScanCallback discoveryCallback;
    private DestroyCallback destroyCallback;
    private final Object messagesLock = new Object();
    private final Object dataLock = new Object();
    private final Object bluetoothLock = new Object();
    private BroadcastReceiver broadcastReceiver;


    public BluetoothCommunicator(final Context context, String name, int strategy) {
        this.context = context;
        this.strategy = strategy;
        this.uniqueName = name + Tools.generateBluetoothNameId(context);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        // verify that it is called only when bluetooth is actually turned on again
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (bluetoothLock) {
                            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);

                            if (state == BluetoothAdapter.STATE_OFF) {
                                if (bluetoothAdapter != null) {
                                    if (destroying) {
                                        // release all the resources and restore the bluetooth status
                                        pendingMessages = new ArrayDeque<>();
                                        pendingData = new ArrayDeque<>();
                                        context.unregisterReceiver(broadcastReceiver);
                                        stopAdvertising(true);
                                        stopDiscovery(true);
                                        if (destroyCallback != null) {
                                            destroyCallback.onDestroyed();
                                        }
                                    } else {
                                        if (restartingBluetooth) {
                                            restartingBluetooth = false;
                                            bluetoothAdapter.enable();
                                        } else {
                                            stopAdvertising(false);
                                            stopDiscovery(false);
                                        }
                                        if (turningOffBluetooth) {
                                            turningOffBluetooth = false;
                                        } else {
                                            originalBluetoothState = BluetoothAdapter.STATE_OFF;
                                        }
                                    }
                                }

                            } else if (state == BluetoothAdapter.STATE_ON) {   // verify that it is called only when bluetooth is actually turned on again
                                bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                                if (bluetoothAdapter != null) {
                                    if (initializingConnection) {
                                        initializingConnection = false;
                                        initializeConnection();
                                    }
                                    if ((connectionServer != null && connectionServer.getReconnectingPeers().size() > 0) || advertising) {
                                        executeStartAdvertising();
                                    }
                                    if ((connectionClient != null && connectionClient.getReconnectingPeers().size() > 0) || discovering) {
                                        executeStartDiscovery();
                                    }
                                    if (turningOnBluetooth) {
                                        turningOnBluetooth = false;
                                        if (restartingBluetooth) {
                                            restartingBluetooth = false;
                                            bluetoothAdapter.disable();
                                        }
                                    } else {
                                        if (restartingBluetooth) {
                                            restartingBluetooth = false;
                                        } else {
                                            originalBluetoothState = BluetoothAdapter.STATE_ON;
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
            }
        };
        context.registerReceiver(broadcastReceiver, intentFilter);
        mainHandler = new Handler(Looper.getMainLooper());

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
            }
        };
        discoveryCallback = new ScanCallback() {
            @Override
            public synchronized void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                switch (callbackType) {
                    case ScanSettings.CALLBACK_TYPE_FIRST_MATCH: {
                        BluetoothDevice device1 = result.getDevice();
                        if (result.getScanRecord() != null && connectionClient != null) {
                            String uniqueName = result.getScanRecord().getDeviceName();
                            if (uniqueName != null && uniqueName.length() > 0) {
                                Peer peerFound = new Peer(device1, uniqueName, false);
                                if (connectionClient.getReconnectingPeers().contains(peerFound.getUniqueName())) {
                                    connectionClient.onReconnectingPeerFound(peerFound);
                                } else {
                                    notifyPeerFound(peerFound);
                                }
                            }
                        }
                        break;
                    }
                    case ScanSettings.CALLBACK_TYPE_MATCH_LOST: {
                        BluetoothDevice device1 = result.getDevice();
                        notifyPeerLost(new Peer(device1, null, false));
                        break;
                    }
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                originalBluetoothState = BluetoothAdapter.STATE_ON;
                initializeConnection();
            } else {
                originalBluetoothState = BluetoothAdapter.STATE_OFF;
                initializingConnection = true;
                bluetoothAdapter.enable();
            }

        }
    }


    private void initializeConnection() {
        if (bluetoothAdapter != null) {
            BluetoothConnection.Callback connectionCallback = new BluetoothConnection.Callback() {
                @Override
                public void onConnectionRequest(Peer peer) {
                    super.onConnectionRequest(peer);
                    notifyConnectionRequest(peer);
                }

                @Override
                public void onConnectionSuccess(Peer peer, int source) {
                    super.onConnectionSuccess(peer, source);
                    notifyConnectionSuccess(peer, source);
                }

                @Override
                public void onConnectionLost(Peer peer) {
                    super.onConnectionLost(peer);
                    synchronized (BluetoothCommunicator.this) {
                        if (connectionServer != null && connectionClient != null) {
                            if (connectionServer.getReconnectingPeers().size() > 0 && !advertising) {
                                executeStartAdvertising();
                            }
                            if (connectionClient.getReconnectingPeers().size() > 0) {
                                if (discovering) {
                                    executeStopDiscovery();
                                    executeStartDiscovery();
                                } else {
                                    executeStartDiscovery();
                                }
                            }
                            notifyConnectionLost(peer);
                        }
                    }
                }

                @Override
                public void onPeerUpdated(Peer peer, Peer newPeer) {
                    super.onPeerUpdated(peer, newPeer);
                    notifyPeerUpdated(peer, newPeer);
                }

                @Override
                public void onConnectionResumed(Peer peer) {
                    super.onConnectionResumed(peer);
                    synchronized (BluetoothCommunicator.this) {
                        if (connectionServer != null && connectionClient != null) {
                            if (connectionServer.getReconnectingPeers().size() == 0 && !advertising) {
                                executeStopAdvertising();
                            }
                            if (connectionClient.getReconnectingPeers().size() == 0 && !discovering) {
                                executeStopDiscovery();
                            }
                            notifyConnectionResumed(peer);
                        }
                    }
                }

                @Override
                public void onConnectionFailed(Peer peer, int errorCode) {
                    super.onConnectionFailed(peer, errorCode);
                    notifyConnectionFailed(peer, errorCode);
                }

                @Override
                public void onMessageReceived(Message message, int source) {
                    super.onMessageReceived(message, source);
                    if (message.getSender().getUniqueName().length() > 0) {
                        notifyMessageReceived(message, source);
                    }
                }

                @Override
                public void onDataReceived(Message data, int source) {
                    super.onMessageReceived(data, source);
                    if (data.getSender().getUniqueName().length() > 0) {
                        notifyDataReceived(data, source);
                    }
                }

                @SuppressWarnings("StatementWithEmptyBody")
                @Override
                public void onDisconnected(Peer peer) {
                    super.onDisconnected(peer);
                    if (connectionServer != null && connectionClient != null) {
                        int peersLeft = connectionServer.getConnectedPeers().size() + connectionClient.getConnectedPeers().size();
                        if (connectionServer.getReconnectingPeers().size() == 0 && !advertising) {
                            executeStopAdvertising();
                        }
                        if (connectionClient.getReconnectingPeers().size() == 0 && !discovering) {
                            executeStopDiscovery();
                        }
                        if (peersLeft == 0) {
                            // reset the queued messages to be sent
                            pendingMessages = new ArrayDeque<>();
                            pendingData= new ArrayDeque<>();
                        }
                        notifyDisconnection(peer, peersLeft);
                    }
                }

                @Override
                public void onDisconnectionFailed() {
                    super.onDisconnectionFailed();
                    //restart of bluetooth
                    synchronized (bluetoothLock) {
                        if (bluetoothAdapter != null) {
                            restartingBluetooth = true;
                            if (bluetoothAdapter.isEnabled()) {
                                bluetoothAdapter.disable();
                            } else {
                                if (!turningOnBluetooth) {
                                    turningOnBluetooth = true;
                                    bluetoothAdapter.enable();
                                }
                            }
                        }
                    }
                }
            };
            // we create a client that will take care of sending any connection requests and managing those connections
            connectionClient = new BluetoothConnectionClient(context, uniqueName, bluetoothAdapter, strategy, connectionCallback);
            // we create a server that will take care of receiving any connection requests and managing those connections
            connectionServer = new BluetoothConnectionServer(context, uniqueName, bluetoothAdapter, strategy, connectionClient, connectionCallback);
        }
    }


    /**
     * This method must always be done from the main thread
     **/
    public int startAdvertising() {
        synchronized (bluetoothLock) {
            if (!destroying) {
                if (bluetoothAdapter != null && connectionServer != null) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        if (!advertising) {
                            //start advertising
                            int ret;
                            if (bluetoothAdapter.isEnabled()) {
                                if (connectionServer.getReconnectingPeers().size() == 0) {
                                    ret = executeStartAdvertising();
                                } else {
                                    ret = isBluetoothLeSupported();
                                }
                            } else {
                                //turn on bluetooth
                                turningOnBluetooth = true;
                                bluetoothAdapter.enable();
                                ret = SUCCESS;
                            }
                            if (ret == SUCCESS) {
                                advertising = true;
                                notifyAdvertiseStarted();
                            }
                            return ret;
                        } else {
                            return ALREADY_STARTED;
                        }
                    } else {
                        return NOT_MAIN_THREAD;
                    }
                } else {
                    return BLUETOOTH_LE_NOT_SUPPORTED;
                }
            } else {
                return DESTROYING;
            }
        }
    }

    private int executeStartAdvertising() {
        int advertisementSupportedCode = isBluetoothLeSupported();
        if (advertisementSupportedCode == SUCCESS) {
            //name update
            originalName = bluetoothAdapter.getName();
            bluetoothAdapter.setName(uniqueName);
            //start advertizing
            AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)  //alto
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)    //alto
                    .setConnectable(true)
                    .setTimeout(0)
                    .build();
            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .addServiceUuid(new ParcelUuid(BluetoothConnection.APP_UUID))
                    .setIncludeDeviceName(true)
                    .build();

            BluetoothLeAdvertiser advertiser = Objects.requireNonNull(bluetoothAdapter).getBluetoothLeAdvertiser();
            if (advertiser != null) {
                advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
                return SUCCESS;
            } else {
                return ERROR;
            }
        } else {
            return advertisementSupportedCode;
        }
    }

    /**
     * This method must always be done from the main thread
     **/
    public int stopAdvertising(boolean tryRestoreBluetoothStatus) {
        synchronized (bluetoothLock) {
            if (connectionServer != null && bluetoothAdapter != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    if (advertising) {
                        //stop advertising
                        int ret;
                        if (connectionServer.getReconnectingPeers().size() == 0) {
                            ret = executeStopAdvertising();
                        } else {
                            ret = isBluetoothLeSupported();
                        }
                        if (ret == SUCCESS) {
                            advertising = false;
                            notifyAdvertiseStopped();
                        }
                        // possible bluetooth shutdown
                        if (!advertising && !discovering && tryRestoreBluetoothStatus && getConnectedPeersList().size() == 0) {
                            if (originalBluetoothState == BluetoothAdapter.STATE_OFF) {
                                turningOffBluetooth = true;
                                bluetoothAdapter.disable();
                            }
                        }
                        return ret;

                    } else {
                        return ALREADY_STOPPED;
                    }
                } else {
                    return NOT_MAIN_THREAD;
                }
            } else {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }
        }
    }

    private int executeStopAdvertising() {
        int advertisementSupportedCode = isBluetoothLeSupported();
        if (advertisementSupportedCode == SUCCESS) {
            //name restore
            bluetoothAdapter.setName(originalName);
            BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            if (advertiser != null) {
                advertiser.stopAdvertising(advertiseCallback);
                return SUCCESS;
            } else {
                return ERROR;
            }
        } else {
            return advertisementSupportedCode;
        }
    }

    public int isBluetoothLeSupported() {
        if (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported()) {
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    /**
     * This method must always be done from the main thread
     **/
    public int startDiscovery() {
        // If we're already discovering, stop it
        synchronized (bluetoothLock) {
            if (!destroying) {
                if (bluetoothAdapter != null && connectionClient != null) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        if (!discovering) {
                            //start advertising
                            int ret;
                            if (bluetoothAdapter.isEnabled()) {
                                if (connectionClient.getReconnectingPeers().size() == 0) {
                                    ret = executeStartDiscovery();
                                } else {
                                    ret = SUCCESS;
                                }
                            } else {
                                //turn on bluetooth
                                turningOnBluetooth = true;
                                bluetoothAdapter.enable();
                                ret = SUCCESS;
                            }
                            if (ret == SUCCESS) {
                                discovering = true;
                                notifyDiscoveryStarted();
                            }
                            return ret;
                        } else {
                            return ALREADY_STARTED;
                        }
                    } else {
                        return NOT_MAIN_THREAD;
                    }
                } else {
                    return BLUETOOTH_LE_NOT_SUPPORTED;
                }
            } else {
                return DESTROYING;
            }
        }
    }

    private int executeStartDiscovery() {
        if (bluetoothAdapter != null) {
            ArrayList<ScanFilter> scanFilters = new ArrayList<>();
            scanFilters.add(new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(BluetoothConnection.APP_UUID))
                    .build());
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setReportDelay(0)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                    .build();
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null) {
                scanner.startScan(scanFilters, scanSettings, discoveryCallback);
                return SUCCESS;
            } else {
                return ERROR;
            }
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    /**
     * This method must always be done from the main thread
     **/
    public int stopDiscovery(boolean tryRestoreBluetoothStatus) {
        synchronized (bluetoothLock) {
            if (bluetoothAdapter != null && connectionClient != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    if (discovering) {
                        //stop advertising
                        int ret;
                        if (connectionClient.getReconnectingPeers().size() == 0) {
                            ret = executeStopDiscovery();
                        } else {
                            ret = SUCCESS;
                        }
                        if (ret == SUCCESS) {
                            discovering = false;
                            notifyDiscoveryStopped();
                        }
                        //possible bluetooth shutdown
                        if (!discovering && !advertising && tryRestoreBluetoothStatus && getConnectedPeersList().size() == 0) {
                            if (originalBluetoothState == BluetoothAdapter.STATE_OFF) {
                                turningOffBluetooth = true;
                                bluetoothAdapter.disable();
                            }
                        }
                        return ret;
                    } else {
                        return ALREADY_STARTED;
                    }
                } else {
                    return NOT_MAIN_THREAD;
                }
            } else {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }
        }
    }

    private int executeStopDiscovery() {
        if (bluetoothAdapter != null) {
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null) {
                scanner.stopScan(discoveryCallback);
                return SUCCESS;
            } else {
                return ERROR;
            }
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }


    public void sendMessage(final Message message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (messagesLock) {
                    pendingMessages.addLast(message);
                    if (pendingMessages.size() == 1) {  // if it is true then we are not writing any messages
                        sendMessage();
                    }
                }
            }
        });
    }

    private void sendMessage() {
        if (connectionClient != null && connectionServer != null) {
            final Message message = pendingMessages.peekFirst();
            if (message != null) {
                connectionClient.sendMessage(message, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendMessage(message, new Channel.MessageCallback() {
                            @Override
                            public void onMessageSent() {   // means that we have sent the message to all the client and server channels
                                pendingMessages.pollFirst();  // remove the newly sent ConversationMessage
                                sendMessage();  // send any other messages
                            }
                        });
                    }
                });
            }
        }
    }

    public void sendData(final Message data) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (dataLock) {
                    pendingData.addLast(data);
                    if (pendingData.size() == 1) {  // if it is true then we are not writing any messages
                        sendData();
                    }
                }
            }
        });
    }

    private void sendData() {
        if (connectionClient != null && connectionServer != null) {
            final Message data = pendingData.peekFirst();
            if (data != null) {
                connectionClient.sendData(data, new Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendData(data, new Channel.MessageCallback() {
                            @Override
                            public void onMessageSent() {   // means that we have sent the message to all the client and server channels
                                pendingData.pollFirst();  // remove the newly sent ConversationMessage
                                sendData();  // send any other messages
                            }
                        });
                    }
                });
            }
        }
    }

    public int setName(String name) {
        if (bluetoothAdapter != null && connectionServer != null && connectionClient != null) {
            this.uniqueName = name + Tools.generateBluetoothNameId(context);
            connectionServer.updateName(uniqueName);
            connectionClient.updateName(uniqueName);
            if (isAdvertising()) {
                bluetoothAdapter.setName(uniqueName);
            }
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    public ArrayList<Peer> getConnectedPeersList() {
        ArrayList<Peer> connectedPeers = new ArrayList<>();
        if (connectionServer != null && connectionClient != null) {
            connectedPeers.addAll(connectionServer.getConnectedPeers());
            connectedPeers.addAll(connectionClient.getConnectedPeers());
        }
        return connectedPeers;
    }

    public int acceptConnection(Peer peer) {
        if (connectionServer != null) {
            connectionServer.acceptConnection((Peer) peer.clone());
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    public int rejectConnection(Peer peer) {
        if (connectionServer != null) {
            connectionServer.rejectConnection((Peer) peer.clone());
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    public boolean isAdvertising() {
        return advertising;
    }

    public boolean isDiscovering() {
        return discovering;
    }

    public int connect(final Peer peer) {
        if (!destroying) {
            if (connectionClient != null) {
                connectionClient.connect((Peer) peer.clone());
                return SUCCESS;
            } else {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }
        } else {
            return DESTROYING;
        }
    }

    public int readPhy(Peer peer) {
        if (connectionClient != null && connectionServer != null) {
            connectionServer.readPhy((Peer) peer.clone());
            connectionClient.readPhy((Peer) peer.clone());
            return SUCCESS;
        } else {
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public int disconnect(final Peer peer) {
        synchronized (bluetoothLock) {
            if (connectionClient != null && connectionServer != null && bluetoothAdapter != null) {
                connectionServer.disconnect((Peer) peer.clone());
                connectionClient.disconnect((Peer) peer.clone());
                if (!advertising && !discovering && getConnectedPeersList().size() == 0) {
                    if (originalBluetoothState == BluetoothAdapter.STATE_OFF) {
                        turningOffBluetooth = true;
                        bluetoothAdapter.disable();
                    }
                }
                return SUCCESS;
            } else {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }
        }
    }

    public int disconnectFromAll() {
        if (connectionClient != null && connectionServer != null) {
            connectionServer.disconnectAll(new Channel.DisconnectionNotificationCallback() {
                @Override
                public void onDisconnectionNotificationSent() {
                    connectionClient.disconnectAll();
                }
            });
            return SUCCESS;
        }
        return BLUETOOTH_LE_NOT_SUPPORTED;
    }

    public void destroy(DestroyCallback callback) {
        if (bluetoothAdapter != null && connectionClient != null && connectionServer != null) {
            destroying = true;
            destroyCallback = callback;
            connectionClient.destroy();
            connectionServer.destroy();
            bluetoothAdapter.disable();
        }
    }

    public interface DestroyCallback {
        void onDestroyed();
    }


    public void addCallback(Callback callback) {
        clientCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        clientCallbacks.remove(callback);
    }


    private void notifyAdvertiseStarted() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onAdvertiseStarted();
        }
    }

    private void notifyDiscoveryStarted() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDiscoveryStarted();
        }
    }

    private void notifyAdvertiseStopped() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onAdvertiseStopped();
        }
    }

    private void notifyDiscoveryStopped() {
        for (int i = 0; i < clientCallbacks.size(); i++) {
            clientCallbacks.get(i).onDiscoveryStopped();
        }
    }

    private void notifyPeerFound(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerFound(peer);
                }
            }
        });
    }

    private void notifyPeerLost(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerLost(peer);
                }
            }
        });
    }

    private void notifyConnectionRequest(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionRequest(peer);
                }
            }
        });
    }

    private void notifyConnectionSuccess(final Peer peer, final int source) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionSuccess(peer, source);
                }
            }
        });
    }

    private void notifyConnectionFailed(final Peer peer, final int errorCode) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionFailed(peer, errorCode);
                }
            }
        });
    }

    private void notifyConnectionResumed(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionResumed(peer);
                }
            }
        });
    }

    private void notifyConnectionLost(final Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionLost(peer);
                }
            }
        });
    }

    private void notifyMessageReceived(final Message message, final int source) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onMessageReceived(message, source);
                }
            }
        });
    }

    private void notifyDataReceived(final Message data, final int source) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onDataReceived(data, source);
                }
            }
        });
    }

    private void notifyPeerUpdated(final Peer peer, final Peer newPeer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerUpdated(peer, newPeer);
                }
            }
        });
    }

    private void notifyDisconnection(final Peer peer, final int peersLeft) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onDisconnected(peer, peersLeft);
                }
            }
        });
    }

    private void notifyBluetoothLeNotSupported() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onBluetoothLeNotSupported();
                }
            }
        });
    }

    public static abstract class Callback extends BluetoothConnection.Callback {
        public void onAdvertiseStarted() {
        }

        public void onDiscoveryStarted() {
        }

        public void onAdvertiseStopped() {
        }

        public void onDiscoveryStopped() {
        }

        public void onPeerFound(Peer peer) {
        }

        public void onPeerLost(Peer peer) {
        }

        public void onDisconnected(Peer peer, int peersLeft) {
        }

        public void onBluetoothLeNotSupported() {
        }
    }
}
