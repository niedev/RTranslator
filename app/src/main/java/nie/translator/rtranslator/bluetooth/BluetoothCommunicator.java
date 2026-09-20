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

package nie.translator.rtranslator.bluetooth;

import android.annotation.SuppressLint;
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
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nie.translator.rtranslator.bluetooth.tools.BluetoothTools;

/**
 * This class allows you to communicate in P2P mode between two or more android devices.
 * It also automatically implements (they are active by default) reconnection in case of temporary connection loss, reliable message sending,
 * splitting and rebuilding of long messages, sending raw data in addition to text messages and a message queue in order to always send the messages (and always in the right order)
 * even in case of connection problems (they will be sent as soon as the connection is restored)
 * <br /><br />
 * First create a bluetooth communicator object, it is the object that handles all operations of bluetooth low energy library, if you want to manage
 * the bluetooth connections in multiple activities I suggest you to save this object as an attribute of a custom class that extends Application and
 * create a getter so you can access to bluetoothCommunicator from any activity or service with:
 *  <pre>{@code
 * ((custom class name) getApplication()).getBluetoothCommunicator();
 * }</pre>
 * Next step is to initialize bluetoothCommunicator, the parameters are: a context, the name by which the other devices will see us (limited to 18 characters
 * and can be only characters listed in BluetoothTools.getSupportedUTFCharacters(context) because the number of bytes for advertising beacon is limited) and the strategy
 * (for now the only supported strategy is BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION)
 * <pre>{@code
 * bluetoothCommunicator = new BluetoothCommunicator(this, "device name", BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION);
 * }</pre>
 * Then add the bluetooth communicator callback, the callback will listen for all events of bluetooth communicator:
 * <pre>{@code
 * bluetoothCommunicator.addCallback(new BluetoothCommunicator.Callback() {
 *     @Override
 *     public void onBluetoothLeNotSupported() {
 *         super.onBluetoothLeNotSupported();
 *
 *         Notify that bluetooth low energy is not compatible with this device
 *     }
 *
 *     @Override
 *     public void onAdvertiseStarted() {
 *         super.onAdvertiseStarted();
 *
 *         Notify that advertise has started, if you want to do something after the start of advertising do it here, because
 *         after startAdvertise there is no guarantee that advertise is really started (it is delayed)
 *     }
 *
 *     @Override
 *     public void onDiscoveryStarted() {
 *         super.onDiscoveryStarted();
 *
 *         Notify that discovery has started, if you want to do something after the start of discovery do it here, because
 *         after startDiscovery there is no guarantee that discovery is really started (it is delayed)
 *     }
 *
 *     @Override
 *     public void onAdvertiseStopped() {
 *         super.onAdvertiseStopped();
 *
 *         Notify that advertise has stopped, if you want to do something after the stop of advertising do it here, because
 *         after stopAdvertising there is no guarantee that advertise is really stopped (it is delayed)
 *     }
 *
 *     @Override
 *     public void onDiscoveryStopped() {
 *         super.onDiscoveryStopped();
 *
 *         Notify that discovery has stopped, if you want to do something after the stop of discovery do it here, because
 *         after stopDiscovery there is no guarantee that discovery is really stopped (it is delayed)
 *     }
 *
 *     @Override
 *     public void onPeerFound(Peer peer) {
 *         super.onPeerFound(peer);
 *
 *         Here for example you can save peer in a list or anywhere you want and when the user
 *         choose a peer you can call bluetoothCommunicator.connect(peer founded) but if you want to
 *         use a peer for connect you have to have peer updated (see onPeerUpdated or onPeerLost), if you use a
 *         non updated peer the connection might fail
 *         instead if you want to immediate connect where peer is found you can call bluetoothCommunicator.connect(peer) here
 *     }
 *
 *     @Override
 *     public void onPeerLost(Peer peer){
 *         super.onPeerLost(peer);
 *
 *         It means that a peer is out of range or has interrupted the advertise,
 *         here you can delete the peer lost from a eventual collection of founded peers
 *     }
 *
 *     @Override
 *     public void onPeerUpdated(Peer peer,Peer newPeer){
 *         super.onPeerUpdated(peer,newPeer);
 *
 *         It means that a founded peer (or connected peer) has changed (name or address or other things),
 *         if you have a collection of founded peers, you need to replace peer with newPeer if you want to connect successfully to that peer.
 *
 *         In case the peer updated is connected and you have saved connected peers you have to update the peer if you want to successfully
 *         send a message or a disconnection request to that peer.
 *     }
 *
 *     @Override
 *     public void onConnectionRequest(Peer peer){
 *         super.onConnectionRequest(peer);
 *
 *         It means you have received a connection request from another device (peer) (that have called connect)
 *         for accept the connection request and start connection call bluetoothCommunicator.acceptConnection(peer);
 *         for refusing call bluetoothCommunicator.rejectConnection(peer); (the peer must be the peer argument of onConnectionRequest)
 *     }
 *
 *     @Override
 *     public void onConnectionSuccess(Peer peer,int source){
 *         super.onConnectionSuccess(peer,source);
 *
 *         This means that you have accepted the connection request using acceptConnection or the other
 *         device has accepted your connection request and the connection is complete, from now on you
 *         can send messages or data (or disconnection request) to this peer until onDisconnected
 *
 *         To send messages to all connected peers you need to create a message with a context, a header, represented by a
 *         single character string (you can use a header to distinguish between different types of messages, or you can ignore
 *         it and use a random character), the text of the message, or a series of bytes if you want to send any kind of data
 *         and the peer you want to send the message to (must be connected to avoid errors), example:
 *         new Message(context,"a","hello world",peer); If you want to send message to a specific peer you have to set
 *         the sender of the message with the corresponding peer.
 *
 *         To send disconnection request to connected peer you need to call bluetoothCommunicator.disconnect(peer);
 *     }
 *
 *     @Override
 *     public void onConnectionFailed(Peer peer,int errorCode){
 *         super.onConnectionFailed(peer,errorCode);
 *
 *         This means that your connection request is rejected or has other problems,
 *         to know the cause of the failure see errorCode (BluetoothCommunicator.CONNECTION_REJECTED
 *         means rejected connection and BluetoothCommunicator.ERROR means generic error)
 *     }
 *
 *     @Override
 *     public void onConnectionLost(Peer peer){
 *         super.onConnectionLost(peer);
 *
 *         This means that a connected peer has lost the connection with you and the library is trying
 *         to restore it, in this case you can update the gui to notify this problem.
 *
 *         You can still send messages in this situation, all sent messages are put in a queue
 *         and sent as soon as the connection is restored
 *     }
 *
 *     @Override
 *     public void onConnectionResumed(Peer peer){
 *         super.onConnectionResumed(peer);
 *
 *         Means that connection lost is resumed successfully
 *     }
 *
 *     @Override
 *     public void onMessageReceived(Message message,int source){
 *         super.onMessageReceived(message,source);
 *
 *         Means that you have received a message containing TEXT, for know the sender you can call message.getSender() that return
 *         the peer that have sent the message, you can ignore source, it indicate only if you have received the message
 *         as client or as server
 *     }
 *
 *     @Override
 *     public void onDataReceived(Message data,int source){
 *         super.onDataReceived(data,source);
 *
 *         Means that you have received a message containing DATA, for know the sender you can call message.getSender() that return
 *         the peer that have sent the message, you can ignore source, it indicate only if you have received the message
 *         as client or as server
 *     }
 *
 *     @Override
 *     public void onDisconnected(Peer peer,int peersLeft){
 *         super.onDisconnected(peer,peersLeft);
 *
 *         Means that the peer is disconnected, peersLeft indicate the number of connected peers remained
 *     }
 * });
 * }</pre>
 * Finally you can start discovery and/or advertising:
 * <pre>{@code
 * bluetoothCommunicator.startAdvertising();
 * bluetoothCommunicator.startDiscovery();
 * }</pre>
 * All other actions that can be done are explained with the comments in the code of callback I wrote before.
 * <br /><br />
 * To use this library add these permissions to your manifest:
 * <pre>{@code
 * <uses-permission android:name="android.permission.BLUETOOTH"/>
 * <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
 * <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
 * }</pre>
 */
@SuppressLint("MissingPermission")
public class BluetoothCommunicator {
    // constants
    public static final int CLIENT = 0;
    public static final int SERVER = 1;
    public static final int SUCCESS = 0;
    public static final int CONNECTION_REJECTED = 1;
    public static final int ERROR = -1;
    public static final int BLUETOOTH_OFF = -8;
    public static final int ALREADY_STARTED = -3;
    public static final int ALREADY_STOPPED = -4;
    public static final int NOT_MAIN_THREAD = -5;
    public static final int DESTROYING = -6;
    public static final int BLUETOOTH_LE_NOT_SUPPORTED = -7;
    public static final int STRATEGY_P2P_WITH_RECONNECTION = 2;
    // variables
    private boolean changeableBluetoothState = false;
    private boolean advertising = false;
    private boolean discovering = false;
    private boolean turningOnBluetooth = false;
    private boolean turningOffBluetooth = false;
    private boolean initializingConnection = false;
    private boolean destroying = false;
    private int strategy;
    private int originalBluetoothState = -1;
    private String uniqueName;
    private String originalName;
    private ArrayDeque<nie.translator.rtranslator.bluetooth.Message> pendingMessages = new ArrayDeque<>();
    private ArrayDeque<nie.translator.rtranslator.bluetooth.Message> pendingData = new ArrayDeque<>();
    // objects
    private Context context;
    @Nullable
    private BluetoothAdapter bluetoothAdapter;
    @Nullable
    private nie.translator.rtranslator.bluetooth.BluetoothConnectionServer connectionServer;
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


    /**
     * Constructor of BluetoothCommunicator
     *
     * @param context
     * @param name the name by which the other devices will see us (limited to 18 characters
     * and can be only characters listed in BluetoothTools.getSupportedUTFCharacters(context) because the number of bytes for advertising beacon is limited)
     * @param strategy (for now the only supported strategy is BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION)
     * @param bluetoothChange indicates if BluetoothCommunicator can turn bluetooth on or off programmatically, to turn it on when is needed
     * and to restart it to force disconnections in case of errors
     */
    public BluetoothCommunicator(final Context context, String name, int strategy, boolean bluetoothChange) {
        this.context = context;
        this.strategy = strategy;
        this.uniqueName = name + BluetoothTools.generateBluetoothNameId(context);
        this.changeableBluetoothState = bluetoothChange;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        // this receiver will listen for bluetooth activation / deactivation to manage the initial auto activation of bluetooth
        // and the restore of the bluetooth initial state during the destruction of BluetoothCommunicator,
        // it is also used to initialize the bluetooth adapter after the user (or this library) enable bluetooth.
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
                                        releaseResourcesAndRestoreBluetoothStatus();
                                    } else {
                                        stopAdvertising(false);
                                        stopDiscovery(false);
                                        if (turningOffBluetooth) {
                                            turningOffBluetooth = false;
                                        } else {
                                            originalBluetoothState = BluetoothAdapter.STATE_OFF;
                                        }
                                    }
                                }

                            } else if (state == BluetoothAdapter.STATE_ON) {
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
                                    } else {
                                        originalBluetoothState = BluetoothAdapter.STATE_ON;
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
                if (result.getScanRecord() != null && connectionClient != null) {
                    String uniqueName = result.getScanRecord().getDeviceName();
                }
                switch (callbackType) {
                    case ScanSettings.CALLBACK_TYPE_ALL_MATCHES: {
                        BluetoothDevice device1 = result.getDevice();
                        if (result.getScanRecord() != null && connectionClient != null) {
                            String uniqueName = result.getScanRecord().getDeviceName();
                            if (uniqueName != null && uniqueName.length() > 0) {
                                nie.translator.rtranslator.bluetooth.Peer peerFound = new nie.translator.rtranslator.bluetooth.Peer(device1, uniqueName, false);
                                if (connectionClient.getReconnectingPeers().contains(peerFound.getUniqueName())) {
                                    connectionClient.onReconnectingPeerFound(peerFound);
                                } else {
                                    notifyPeerFound(peerFound);
                                }
                            }
                        }
                        break;
                    }
                    case ScanSettings.CALLBACK_TYPE_FIRST_MATCH: {
                        BluetoothDevice device1 = result.getDevice();
                        if (result.getScanRecord() != null && connectionClient != null) {
                            String uniqueName = result.getScanRecord().getDeviceName();
                            if (uniqueName != null && uniqueName.length() > 0) {
                                nie.translator.rtranslator.bluetooth.Peer peerFound = new nie.translator.rtranslator.bluetooth.Peer(device1, uniqueName, false);
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
                        notifyPeerLost(new nie.translator.rtranslator.bluetooth.Peer(device1, null, false));
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
                if(changeableBluetoothState) {
                    bluetoothAdapter.enable();
                }
            }

        }
    }

    /**
     * Constructor of BluetoothCommunicator that adds a callback (it can also be added with addCallback
     *
     * @param context context
     * @param name the name by which the other devices will see us (limited to 18 characters
     * and can be only characters listed in BluetoothTools.getSupportedUTFCharacters(context) because the number of bytes for advertising beacon is limited)
     * @param strategy (for now the only supported strategy is BluetoothCommunicator.STRATEGY_P2P_WITH_RECONNECTION)
     * @param bluetoothChange indicates if BluetoothCommunicator can turn bluetooth on or off programmatically, to turn it on when is needed
     * and to restart it to force disconnections in case of errors
     * @param callback callback added, like in addCallback
     */
    public BluetoothCommunicator(final Context context, String name, int strategy, boolean bluetoothChange, Callback callback) {
        this(context,name,strategy, bluetoothChange);
        addCallback(callback);
    }


    private void initializeConnection() {
        if (bluetoothAdapter != null) {
            nie.translator.rtranslator.bluetooth.BluetoothConnection.Callback connectionCallback = new nie.translator.rtranslator.bluetooth.BluetoothConnection.Callback() {
                @Override
                public void onConnectionRequest(nie.translator.rtranslator.bluetooth.Peer peer) {
                    super.onConnectionRequest(peer);
                    notifyConnectionRequest(peer);
                }

                @Override
                public void onConnectionSuccess(nie.translator.rtranslator.bluetooth.Peer peer, int source) {
                    super.onConnectionSuccess(peer, source);
                    notifyConnectionSuccess(peer, source);
                }

                @Override
                public void onConnectionLost(nie.translator.rtranslator.bluetooth.Peer peer) {
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
                public void onPeerUpdated(nie.translator.rtranslator.bluetooth.Peer peer, nie.translator.rtranslator.bluetooth.Peer newPeer) {
                    super.onPeerUpdated(peer, newPeer);
                    notifyPeerUpdated(peer, newPeer);
                }

                @Override
                public void onConnectionResumed(nie.translator.rtranslator.bluetooth.Peer peer) {
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
                public void onConnectionFailed(nie.translator.rtranslator.bluetooth.Peer peer, int errorCode) {
                    super.onConnectionFailed(peer, errorCode);
                    notifyConnectionFailed(peer, errorCode);
                }

                @Override
                public void onMessageReceived(nie.translator.rtranslator.bluetooth.Message message, int source) {
                    super.onMessageReceived(message, source);
                    if (message.getSender().getUniqueName().length() > 0) {
                        notifyMessageReceived(message, source);
                    }
                }

                @Override
                public void onDataReceived(nie.translator.rtranslator.bluetooth.Message data, int source) {
                    super.onMessageReceived(data, source);
                    if (data.getSender().getUniqueName().length() > 0) {
                        notifyDataReceived(data, source);
                    }
                }

                @SuppressWarnings("StatementWithEmptyBody")
                @Override
                public void onDisconnected(nie.translator.rtranslator.bluetooth.Peer peer) {
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
            };
            // we create a client that will take care of sending any connection requests and managing those connections
            connectionClient = new BluetoothConnectionClient(context, uniqueName, bluetoothAdapter, strategy, connectionCallback);
            // we create a server that will take care of receiving any connection requests and managing those connections
            connectionServer = new nie.translator.rtranslator.bluetooth.BluetoothConnectionServer(context, uniqueName, bluetoothAdapter, strategy, connectionClient, connectionCallback);
        }
    }


    /**
     * This method start advertising, so this device can be discovered by other devices that use this library and can receive a connection request,
     * this method will eventually turn on bluetooth if it is off (BluetoothCommunicator will restore bluetooth status when both advertising and discovery are stopped)
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STARTED if BluetoothCommunicator is already advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error), DESTROYING if destroy() is called before
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
                            } else if(changeableBluetoothState){
                                //turn on bluetooth
                                turningOnBluetooth = true;
                                bluetoothAdapter.enable();
                                ret = SUCCESS;
                            }else{
                                ret = BLUETOOTH_OFF;
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
                } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
                    return BLUETOOTH_LE_NOT_SUPPORTED;
                }else{
                    return ERROR;
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
                    .addServiceUuid(new ParcelUuid(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID))
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
     * This method stop advertising, this method will eventually turn off bluetooth if BluetoothCommunicator turned on before and if discovery is off
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STOPPED if BluetoothCommunicator is not advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error)
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
                        if (!advertising && !discovering && tryRestoreBluetoothStatus && changeableBluetoothState && getConnectedPeersList().size() == 0) {
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
            } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }else{
                return ERROR;
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

    /**
     * This method will check if BluetoothLe is supported by the device (rarely it can indicate a general bluetooth error, not an incompatibility with bluetooth le)
     *
     * @return SUCCESS if bluetooth le is supported, BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int isBluetoothLeSupported() {
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported()) {
            return SUCCESS;
        } else {
            return ERROR;
        }
    }

    /**
     * This method start discovery so BluetoothCommunicator can discover advertising devices and notify them with onPeerFound,
     * this method will eventually turn on bluetooth if it is off (BluetoothCommunicator will restore bluetooth status when both advertising and discovery are stopped)
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STARTED if BluetoothCommunicator is already advertising, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error), DESTROYING if destroy() is called before
     **/
    public int startDiscovery() {
        // If we're already discovering, stop it
        synchronized (bluetoothLock) {
            if (!destroying) {
                if (bluetoothAdapter != null && connectionClient != null) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        if (!discovering) {
                            //start discovery
                            int ret;
                            if (bluetoothAdapter.isEnabled()) {
                                if (connectionClient.getReconnectingPeers().size() == 0) {
                                    ret = executeStartDiscovery();
                                } else {
                                    ret = SUCCESS;
                                }
                            } else if(changeableBluetoothState){
                                //turn on bluetooth
                                turningOnBluetooth = true;
                                bluetoothAdapter.enable();
                                ret = SUCCESS;
                            }else{
                                ret = BLUETOOTH_OFF;
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
                } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
                    return BLUETOOTH_LE_NOT_SUPPORTED;
                }else{
                    return ERROR;
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
                    .setServiceUuid(new ParcelUuid(nie.translator.rtranslator.bluetooth.BluetoothConnection.APP_UUID))
                    .build());
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setMatchMode(ScanSettings.MATCH_MODE_STICKY)  //old: MATCH_MODE_AGGRESSIVE
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setReportDelay(0)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)  //old: ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST
                    .build();
            BluetoothLeScanner scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner != null) {
                scanner.startScan(scanFilters, scanSettings, discoveryCallback);
                return SUCCESS;
            } else {
                return ERROR;
            }
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method stop discovery, this method will eventually turn off bluetooth if BluetoothCommunicator turned on before and if advertising is off
     * <br /><br />
     * This method must always be done from the main thread or it will return NOT_MAIN_THREAD without doing anything.
     *
     * @return SUCCESS if everithing is OK, ALREADY_STOPPED if BluetoothCommunicator is not discovering, NOT_MAIN_THREAD if this method is not called
     * from the main thread, BLUETOOTH_LE_NOT_SUPPORTED if the device not supports bluetooth le (or rarely for a general bluetooth error)
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
                        if (!discovering && !advertising && tryRestoreBluetoothStatus && changeableBluetoothState && getConnectedPeersList().size() == 0) {
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
            } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }else{
                return ERROR;
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
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method will send the text contained in message to the peer contained in the receiver attribute of the message (only if that peer is connected), if receiver is not set
     * the message will be sent to all the connected peers
     *
     * @param message message to be sent
     */
    public void sendMessage(final nie.translator.rtranslator.bluetooth.Message message) {
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
            final nie.translator.rtranslator.bluetooth.Message message = pendingMessages.peekFirst();
            if (message != null) {
                connectionClient.sendMessage(message, new nie.translator.rtranslator.bluetooth.Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendMessage(message, new nie.translator.rtranslator.bluetooth.Channel.MessageCallback() {
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

    /**
     * This method will send the data contained in message to the peer contained in the receiver attribute of the message (only if that peer is connected), if receiver is not set
     * the message will be sent to all the connected peers
     *
     * @param data message to be sent
     */
    public void sendData(final nie.translator.rtranslator.bluetooth.Message data) {
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
            final nie.translator.rtranslator.bluetooth.Message data = pendingData.peekFirst();
            if (data != null) {
                connectionClient.sendData(data, new nie.translator.rtranslator.bluetooth.Channel.MessageCallback() {
                    @Override
                    public void onMessageSent() {
                        connectionServer.sendData(data, new nie.translator.rtranslator.bluetooth.Channel.MessageCallback() {
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

    /**
     * this method set the name with which you would be seen by other devices, the name must be limited to 18 characters
     * and can be only characters listed in BluetoothTools.getSupportedUTFCharacters(context) because the number of bytes for advertising beacon is limited,
     * there is no controls for this so if you not follow these restrictions BluetoothCommunicator will not work correctly.
     * <br /><br />
     * To the name will be added 4 random symbols in a completely transparent way (this 4 symbols will exixts only inside BluetoothCommunicator, which removes them before the name gets on the outside),
     * this allows to have a unique identification (for BluetoothCommunicator, not for the user) even for peers with the same name
     *
     * @param name
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int setName(String name) {
        if (bluetoothAdapter != null && connectionServer != null && connectionClient != null) {
            this.uniqueName = name + BluetoothTools.generateBluetoothNameId(context);
            connectionServer.updateName(uniqueName);
            connectionClient.updateName(uniqueName);
            if (isAdvertising()) {
                bluetoothAdapter.setName(uniqueName);
            }
            return SUCCESS;
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method will return the name + 4 symbols used by BluetoothCommunicator to unique identify a peer (in case there are more peers with the same name)
     * @return uniqueName
     */
    public String getUniqueName() {
        return uniqueName;
    }

    /**
     * This method will return the list of all connected peers
     * @return list of connected peers
     */
    public ArrayList<nie.translator.rtranslator.bluetooth.Peer> getConnectedPeersList() {
        ArrayList<nie.translator.rtranslator.bluetooth.Peer> connectedPeers = new ArrayList<>();
        if (connectionServer != null && connectionClient != null) {
            connectedPeers.addAll(connectionServer.getConnectedPeers());
            connectedPeers.addAll(connectionClient.getConnectedPeers());
        }
        return connectedPeers;
    }

    /**
     * This method must be used after you have received a connection request to accept it and complete the connection (the connection is complete when
     * onConnectionSuccess is called)
     *
     * @param peer the peer that has sent the connection request that we want to accept
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int acceptConnection(nie.translator.rtranslator.bluetooth.Peer peer) {
        if (connectionServer != null) {
            connectionServer.acceptConnection((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
            return SUCCESS;
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method must be used after you have received a connection request to reject it and cancel the connection.
     *
     * @param peer the peer that has sent the connection request that you want to accept
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int rejectConnection(nie.translator.rtranslator.bluetooth.Peer peer) {
        if (connectionServer != null) {
            connectionServer.rejectConnection((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
            return SUCCESS;
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method is used to know if BluetoothCommunicator is advertising
     *
     * @return advertising
     */
    public boolean isAdvertising() {
        return advertising;
    }

    /**
     * This method is used to know if BluetoothCommunicator is discovering
     * @return discovering
     */
    public boolean isDiscovering() {
        return discovering;
    }

    /**
     * This method must be used to send a connection request to a founded peer, successfully you can know if it has accepted or rejected the connection listening
     * onConnectionSuccess and onConnectionFailed
     *
     * @param peer found peer you want to connect with
     * @return SUCCESS if everything is gone OK, BLUETOOTH_LE_NOT_SUPPORTED if bluetooth le is not supported (or rarely if we had a generic bluetooth problem)
     * or DESTROYING if destroy() is called before
     */
    public int connect(final nie.translator.rtranslator.bluetooth.Peer peer) {
        if (!destroying) {
            if (connectionClient != null) {
                connectionClient.connect((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
                return SUCCESS;
            } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED) {
                return BLUETOOTH_LE_NOT_SUPPORTED;
            } else {
                return ERROR;
            }
        } else {
            return DESTROYING;
        }
    }

    public int readPhy(nie.translator.rtranslator.bluetooth.Peer peer) {
        if (connectionClient != null && connectionServer != null) {
            connectionServer.readPhy((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
            connectionClient.readPhy((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
            return SUCCESS;
        } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    /**
     * This method return the bluetooth adapter used by BluetoothCommunicator
     *
     * @return bluetoothAdapter
     */
    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * This method disconnect the peer passed to the argument (it must be connected or nothing happens), the disconnection is completed
     * when onDisconnected is called with that peer as argument.
     *
     * @param peer connected peer you want to disconnect from
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int disconnect(final nie.translator.rtranslator.bluetooth.Peer peer) {
        synchronized (bluetoothLock) {
            if (connectionClient != null && connectionServer != null && bluetoothAdapter != null) {
                connectionServer.disconnect((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
                connectionClient.disconnect((nie.translator.rtranslator.bluetooth.Peer) peer.clone());
                if (!advertising && !discovering && getConnectedPeersList().size() == 0) {
                    if (originalBluetoothState == BluetoothAdapter.STATE_OFF && changeableBluetoothState) {
                        turningOffBluetooth = true;
                        bluetoothAdapter.disable();
                    }
                }
                return SUCCESS;
            } else if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
                return BLUETOOTH_LE_NOT_SUPPORTED;
            }else{
                return ERROR;
            }
        }
    }

    /**
     * This method will call disconnect for all the connected peers
     *
     * @return SUCCESS if bluetooth le is supported by the device or BLUETOOTH_LE_NOT_SUPPORTED if not (or rarely if we had a generic bluetooth problem)
     */
    public int disconnectFromAll() {
        if (connectionClient != null && connectionServer != null) {
            connectionServer.disconnectAll(new nie.translator.rtranslator.bluetooth.Channel.DisconnectionNotificationCallback() {
                @Override
                public void onDisconnectionNotificationSent() {
                    connectionClient.disconnectAll();
                }
            });
            return SUCCESS;
        }
        if(isBluetoothLeSupported() == BLUETOOTH_LE_NOT_SUPPORTED){
            return BLUETOOTH_LE_NOT_SUPPORTED;
        }else{
            return ERROR;
        }
    }

    private void releaseResourcesAndRestoreBluetoothStatus(){
        // release all the resources and restore the bluetooth status
        pendingMessages = new ArrayDeque<>();
        pendingData = new ArrayDeque<>();
        context.unregisterReceiver(broadcastReceiver);
        stopAdvertising(true);
        stopDiscovery(true);
        if (destroyCallback != null) {
            destroyCallback.onDestroyed();
        }
    }

    /**
     * This method must be called when you not use anymore BluetoothCommunicator for release resources, in the case you had saved BluetoothCommunicator in Global and/or
     * you will use BluetoothCommunicator for the entire life of application you can avoid the call to this method
     * @param callback
     */
    public void destroy(DestroyCallback callback) {
        if (bluetoothAdapter != null && connectionClient != null && connectionServer != null) {
            destroying = true;
            destroyCallback = callback;
            connectionClient.destroy();
            connectionServer.destroy();
            if(changeableBluetoothState) {
                bluetoothAdapter.disable();  //this is used to restart (or keep turned off) bluetooth before the destruction of BluetoothCommunicator via the broadcastReceiver
            }else{
                releaseResourcesAndRestoreBluetoothStatus();
            }
        }
    }

    public interface DestroyCallback {
        void onDestroyed();
    }


    /**
     * With this method you can add the callback for listening all the events of BluetoothCommunicator
     *
     * @param callback callback for listening all the events of BluetoothCommunicator
     */
    public void addCallback(Callback callback) {
        clientCallbacks.add(callback);
    }

    /**
     * This remote will remove the callback you pass as argument from the list of callback of this class, so this callback will not receive method call anymore
     * @param callback callback for listening all the events of BluetoothCommunicator
     */
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

    private void notifyPeerFound(final nie.translator.rtranslator.bluetooth.Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerFound(peer);
                }
            }
        });
    }

    private void notifyPeerLost(final nie.translator.rtranslator.bluetooth.Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerLost(peer);
                }
            }
        });
    }

    private void notifyConnectionRequest(final nie.translator.rtranslator.bluetooth.Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionRequest(peer);
                }
            }
        });
    }

    private void notifyConnectionSuccess(final nie.translator.rtranslator.bluetooth.Peer peer, final int source) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionSuccess(peer, source);
                }
            }
        });
    }

    private void notifyConnectionFailed(final nie.translator.rtranslator.bluetooth.Peer peer, final int errorCode) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionFailed(peer, errorCode);
                }
            }
        });
    }

    private void notifyConnectionResumed(final nie.translator.rtranslator.bluetooth.Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionResumed(peer);
                }
            }
        });
    }

    private void notifyConnectionLost(final nie.translator.rtranslator.bluetooth.Peer peer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onConnectionLost(peer);
                }
            }
        });
    }

    private void notifyMessageReceived(final nie.translator.rtranslator.bluetooth.Message message, final int source) {
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

    private void notifyPeerUpdated(final nie.translator.rtranslator.bluetooth.Peer peer, final nie.translator.rtranslator.bluetooth.Peer newPeer) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < clientCallbacks.size(); i++) {
                    clientCallbacks.get(i).onPeerUpdated(peer, newPeer);
                }
            }
        });
    }

    private void notifyDisconnection(final nie.translator.rtranslator.bluetooth.Peer peer, final int peersLeft) {
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

    public static abstract class Callback extends nie.translator.rtranslator.bluetooth.BluetoothConnection.Callback {
        /**
         * Notify that advertise has started, if you want to do something after the start of advertising do it here, because
         * after startAdvertise there is no guarantee that advertise is really started (it is delayed)
         */
        public void onAdvertiseStarted() {
        }

        /**
         * Notify that discovery has started, if you want to do something after the start of discovery do it here, because
         * after startDiscovery there is no guarantee that discovery is really started (it is delayed)
         */
        public void onDiscoveryStarted() {
        }

        /**
         * Notify that advertise has stopped, if you want to do something after the stop of advertising do it here, because
         * after stopAdvertising there is no guarantee that advertise is really stopped (it is delayed)
         */
        public void onAdvertiseStopped() {
        }

        /**
         * Notify that discovery has stopped, if you want to do something after the stop of discovery do it here, because
         * after stopDiscovery there is no guarantee that discovery is really stopped (it is delayed)
         */
        public void onDiscoveryStopped() {
        }

        /**
         * Notify that a Peer is found with the discovery.
         * <br /><br />
         * Here for example you can save peer in a list or anywhere you want and when the user
         * choose a peer you can call bluetoothCommunicator.connect(peer founded) but if you want to
         * use a peer for connect you have to have peer updated (see onPeerUpdated or onPeerLost), if you use a
         * non updated peer the connection might fail
         * instead if you want to immediate connect where peer is found you can call bluetoothCommunicator.connect(peer) here.
         *
         * @param peer founded peer
         */
        public void onPeerFound(nie.translator.rtranslator.bluetooth.Peer peer) {
        }

        /**
         * Notify that a peer is out of range or has interrupted the advertise.
         * <br /><br />
         * Here you can delete the peer lost from a eventual collection of founded peers.
         *
         * @param peer
         */
        public void onPeerLost(nie.translator.rtranslator.bluetooth.Peer peer) {
        }

        /**
         * Notify that a peer is disconnected, peersLeft indicate the number of connected peers remained
         *
         * @param peer disconnected peer
         * @param peersLeft remaining peers connected
         */
        public void onDisconnected(Peer peer, int peersLeft) {
        }

        /**
         * notify that bluetooth low energy is not compatible with this device (that for now is never been called)
         */
        public void onBluetoothLeNotSupported() {
        }
    }
}
