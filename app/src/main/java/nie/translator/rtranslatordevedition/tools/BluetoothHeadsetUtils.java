package nie.translator.rtranslatordevedition.tools;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import java.util.List;

import com.bluetooth.communicator.tools.CustomCountDownTimer;

/**
 * This is a utility to detect bluetooth headset connection and establish audio connection
 * for android API >= 8. This includes a work around for  API < 11 to detect already connected headset
 * before the application starts. This work around would only fails if Sco audio
 * connection is accepted but the connected device is not a headset.
 *
 * @author Hoan Nguyen
 */

public abstract class BluetoothHeadsetUtils {
    private Context mContext;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothDevice mConnectedHeadset;

    private AudioManager mAudioManager;

    private boolean mIsCountDownOn;
    private boolean mIsStarting;
    private boolean mIsOnHeadsetSco;
    private boolean mIsStarted;

    private static final String TAG = "BluetoothHeadsetUtils"; //$NON-NLS-1$

    /**
     * Constructor
     *
     * @param context
     */
    public BluetoothHeadsetUtils(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Call this to start BluetoothHeadsetUtils functionalities.
     *
     * @return The return value of startBluetooth() or startBluetooth11()
     */
    public boolean start() {
        if (!mIsStarted) {
            mIsStarted = startBluetooth11();
        }
        return mIsStarted;
    }

    /**
     * Should call this on onResume or onDestroy.
     * Unregister broadcast receivers and stop Sco audio connection
     * and cancel count down.
     */
    public void stop() {
        if (mIsStarted) {
            mIsStarted = false;

            stopBluetooth11();
        }
    }

    /**
     * @return true if audio is connected through headset.
     */
    public boolean isOnHeadsetSco() {
        return mIsOnHeadsetSco;
    }

    public abstract void onHeadsetDisconnected();

    public abstract void onHeadsetConnected();

    public abstract void onScoAudioDisconnected();

    public abstract void onScoAudioConnected();

    /**
     * Register a headset profile listener
     *
     * @return false    if device does not support bluetooth or current platform does not supports
     * use of SCO for off call or error in getting profile proxy.
     */
    private boolean startBluetooth11() {
        ////d(TAG, "startBluetooth11"); //$NON-NLS-1$

        // Device support bluetooth
        if (mBluetoothAdapter != null) {
            if (mAudioManager.isBluetoothScoAvailableOffCall()) {
                // All the detection and audio connection are done in mHeadsetProfileListener
                return mBluetoothAdapter.getProfileProxy(mContext, mHeadsetProfileListener, BluetoothProfile.HEADSET);
            }
        }

        return false;
    }

    /**
     * Unregister broadcast receivers and stop Sco audio connection
     * and cancel count down.
     */
    protected void stopBluetooth11() {
        ////d(TAG, "stopBluetooth11"); //$NON-NLS-1$

        if (mIsCountDownOn) {
            mIsCountDownOn = false;
            mCountDown11.cancel();
        }

        if (mBluetoothHeadset != null) {
            // Need to call stopVoiceRecognition here when the app
            // change orientation or close with headset still turns on.
            mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);
            try {
                mContext.unregisterReceiver(mHeadsetBroadcastReceiver);
            }catch (Exception e){

            }
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mBluetoothHeadset = null;
        }
    }

    /**
     * Check for already connected headset and if so start audio connection.
     * Register for broadcast of headset and Sco audio connection states.
     */
    private BluetoothProfile.ServiceListener mHeadsetProfileListener = new BluetoothProfile.ServiceListener() {
        /**
         * This method is never called, even when we closeProfileProxy on onPause.
         * When or will it ever be called???
         */
        @Override
        public void onServiceDisconnected(int profile) {
            ////d(TAG, "Profile listener onServiceDisconnected"); //$NON-NLS-1$
            stopBluetooth11();
        }

        //@SuppressWarnings("synthetic-access")
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            ////d(TAG, "Profile listener onServiceConnected"); //$NON-NLS-1$

            // mBluetoothHeadset is just a headset profile,
            // it does not represent a headset device.
            mBluetoothHeadset = (BluetoothHeadset) proxy;

            // If a headset is connected before this application starts,
            // ACTION_CONNECTION_STATE_CHANGED will not be broadcast.
            // So we need to check for already connected headset.
            List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
            if (devices.size() > 0) {
                // Only one headset can be connected at a time,
                // so the connected headset is at index 0.
                connectScoAudio(devices.get(0));

                ////d(TAG, "Start count down"); //$NON-NLS-1$
            }

            IntentFilter intentFilter= new IntentFilter();
            // During the active life time of the app, a user may turn on and off the headset.
            // So register for broadcast of connection states.
            intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            // Calling startVoiceRecognition does not result in immediate audio connection.
            // So register for broadcast of audio connection states. This broadcast will
            // only be sent if startVoiceRecognition returns true.
            intentFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
            mContext.registerReceiver(mHeadsetBroadcastReceiver, intentFilter);
        }
    };

    private void connectScoAudio(BluetoothDevice bluetoothDevice) {
        mConnectedHeadset = bluetoothDevice;
        onHeadsetConnected();
        // Should not need count down timer, but just in case.
        // See comment below in mHeadsetBroadcastReceiver onReceive()
        mIsCountDownOn = true;
        mCountDown11.start();
    }

    /**
     * Handle headset and Sco audio connection states.
     */
    private BroadcastReceiver mHeadsetBroadcastReceiver = new BroadcastReceiver() {
        @SuppressWarnings("synthetic-access")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state;
            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
                ////d(TAG, "\nAction = " + action + "\nState = " + state); //$NON-NLS-1$ //$NON-NLS-2$
                if (state == BluetoothHeadset.STATE_CONNECTED) {

                    connectScoAudio((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));

                    ////d(TAG, "Start count down"); //$NON-NLS-1$
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    // Calling stopVoiceRecognition always returns false here
                    // as it should since the headset is no longer connected.
                    if (mIsCountDownOn) {
                        mIsCountDownOn = false;
                        mCountDown11.cancel();
                    }
                    mConnectedHeadset = null;

                    // override this if you want to do other thing when the device is disconnected.
                    onHeadsetDisconnected();

                    ////d(TAG, "Headset disconnected"); //$NON-NLS-1$
                }
            } else // audio
            {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
                ////d(TAG, "\nAction = " + action + "\nState = " + state); //$NON-NLS-1$ //$NON-NLS-2$
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    ////d(TAG, "\nHeadset audio connected");  //$NON-NLS-1$

                    mIsOnHeadsetSco = true;

                    if (mIsCountDownOn) {
                        mIsCountDownOn = false;
                        mCountDown11.cancel();
                    }

                    // override this if you want to do other thing when headset audio is connected.
                    onScoAudioConnected();
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    mIsOnHeadsetSco = false;

                    // The headset audio is disconnected, but calling
                    // stopVoiceRecognition always returns true here.
                    mBluetoothHeadset.stopVoiceRecognition(mConnectedHeadset);

                    // override this if you want to do other thing when headset audio is disconnected.
                    onScoAudioDisconnected();

                    ////d(TAG, "Headset audio disconnected"); //$NON-NLS-1$
                }
            }
        }
    };

    /**
     * Try to connect to audio headset in onTick.
     */
    private CustomCountDownTimer mCountDown11 = new CustomCountDownTimer(10000, 1000) {
        @SuppressWarnings("synthetic-access")
        @Override
        public void onTick(long millisUntilFinished) {
            // First stick calls always returns false. The second stick
            // always returns true if the countDownInterval is setSender to 1000.
            // It is somewhere in between 500 to a 1000.
            mBluetoothHeadset.startVoiceRecognition(mConnectedHeadset);

            ////d(TAG, "onTick startVoiceRecognition"); //$NON-NLS-1$
        }

        @SuppressWarnings("synthetic-access")
        @Override
        public void onFinish() {
            // Calls to startVoiceRecognition in onStick are not successful.
            // Should implement something to inform user of this failure
            mIsCountDownOn = false;
            ////d(TAG, "\nonFinish fail to connect to headset audio"); //$NON-NLS-1$
        }
    };

    public boolean isBluetoothScoAvailable() {
        return mAudioManager.isBluetoothScoOn();
    }

    public boolean isHeadsetConnected() {
        if (mBluetoothHeadset == null || mBluetoothHeadset.getConnectedDevices().size() == 0) {
            return false;
        } else {
            return true;
        }
    }

}
