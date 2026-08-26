/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package nie.translator.rtranslator.voice_translation.neural_networks.voice;

import static android.media.AudioManager.GET_DEVICES_INPUTS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.konovalov.vad.silero.VadSilero;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.ConversationService;


/**
 * Continuously records audio and notifies the {@link Recorder.Callback} when voice (or any
 * sound) is heard. Furthermore, when it calls the onVoice method, it passes a buffer obtained from the AudioRecord,
 * and the class that implements the onVoice method (BaseActivity) will perform the recognition of google precisely using the buffer passed,
 * consequently the input to the API of google comes from the AudioRecord of this class.
 *
 *
 * <p>The recorded audio format is always {@link AudioFormat#ENCODING_PCM_16BIT} and
 * {@link AudioFormat#CHANNEL_IN_MONO}. This class will automatically pick the right sample rate
 * for the device. Use {@link #getSampleRate()} to getGroup the selected value.</p>
 */
@SuppressLint("MissingPermission")
public class Recorder {
    private static final String TAG = "recorder";
    private final Global global;
    private volatile boolean isRecording;
    private volatile boolean isManualMode = false;
    public static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static int ENCODING;
    private static int VAD_FRAME_SIZE = 512;
    public static final int MAX_AMPLITUDE_THRESHOLD = 15000;
    public static final int DEFAULT_AMPLITUDE_THRESHOLD = 1500; //old: 2000
    public static final int MIN_AMPLITUDE_THRESHOLD = 400;
    public static final int MAX_SPEECH_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_SPEECH_TIMEOUT_MILLIS = 1300; //original: 2000
    public static final int MIN_SPEECH_TIMEOUT_MILLIS = 100;
    public static final int MAX_PREV_VOICE_DURATION = 1800;
    public static final int DEFAULT_PREV_VOICE_DURATION = 1300;
    public static final int MIN_PREV_VOICE_DURATION = 100;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 29 * 1000; //original: 30 * 1000
    private final Callback mCallback;
    private int sampleRate;
    @Nullable
    private final AudioRecord mAudioRecord;
    private int mPrevBufferMaxSize;   //the size of the mPrevBuffer (It depends on the settings of the app (prevVoiceDuration))
    private float[] mBuffer;  //PCM FLOAT data, used for Speech recognition and volume level notification
    private short[] mBufferShort;  //PCM 16bit data, used for VAD
    private int readSize;   //must be smaller than mBuffer.length or the circular mBuffer array will not work
    private int headIndex;
    private int tailIndex;
    private int startVoiceIndex;
    /**
     * The timestamp of the last time that voice is heard.
     */
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    /**
     * The timestamp when the current voice is started.
     */
    private long mVoiceStartedMillis;

    private final boolean useBluetoothHeadset;
    private AudioDeviceInfo connectedBleHeadset = null;
    private AudioDeviceCallback audioDeviceCallback;
    AudioManager audioManager;
    private VadSilero vad;
    /** How long stop() waits for the audio thread before escalating to interrupt(). */
    private static final long STOP_JOIN_TIMEOUT_MILLIS = 500;

    // ---- lifecycle: guarded by lifecycleLock, or volatile for lock-free reads ----
    private final Object lifecycleLock = new Object();
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private volatile Thread mThread;
    private volatile boolean running;
    private volatile boolean destroyed;
    private boolean pendingStart;


    public Recorder(Global global, boolean useBluetoothHeadset, @NonNull Callback callback, @Nullable ConversationService.BluetoothHeadsetCallback bluetoothHeadsetCallback, VadSilero vad) {
        this.useBluetoothHeadset = useBluetoothHeadset;
        this.global = global;
        headIndex = 0;
        tailIndex = 0;
        global.getMicSensitivity();
        global.getSpeechTimeout();
        global.getPrevVoiceDuration();
        mCallback = callback;
        this.vad = vad;

        // set the encoding
        if(Build.MANUFACTURER.equalsIgnoreCase("vivo")){
            ENCODING = AudioFormat.ENCODING_PCM_16BIT;   //this is to avoid a bug where on Vivo phones the audio recording wouldn't work
        }else{
            ENCODING = AudioFormat.ENCODING_PCM_FLOAT;
        }

        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            //throw new RuntimeException("Cannot instantiate Recorder");
            Log.e("Recorder error", "Cannot instantiate Recorder");
        }

        //initialize the bluetooth headset mic management
        if(useBluetoothHeadset) {
            this.audioManager = (AudioManager) global.getSystemService(Context.AUDIO_SERVICE);
            boolean success = setBLEHeadsetConnection();
            if(success) {
                if (bluetoothHeadsetCallback != null) {
                    bluetoothHeadsetCallback.onScoAudioConnected();
                }
            }
            this.audioDeviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if(connectedBleHeadset == null) {
                        boolean success = setBLEHeadsetConnection();
                        if (success) {
                            if (bluetoothHeadsetCallback != null) {
                                bluetoothHeadsetCallback.onScoAudioConnected();
                            }
                        }
                    }
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    boolean found = false;
                    for (AudioDeviceInfo removedDevice : removedDevices) {
                        if (removedDevice.equals(connectedBleHeadset)){
                            found = true;
                            break;
                        }
                    }
                    if(found) {
                        connectedBleHeadset = null;
                        audioManager.stopBluetoothSco();
                        if (bluetoothHeadsetCallback != null) {
                            bluetoothHeadsetCallback.onScoAudioDisconnected();
                        }
                    }
                }
            };
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
        }
    }

    // =====================================================================================
    //  Public API
    //
    //  To manage concurrency and avoid to synchronize the process voice thread
    //  (it's not recommended since it executes blocking readings), we execute public commands
    //  with a queue, executed at every start of the loop of the process voice thread, by that
    //  thread itself.
    // =====================================================================================

    public void start() {
        if (destroyed) return;
        synchronized (lifecycleLock) {
            stop();   // Stop recording if it is currently ongoing.
            if (mThread != null) {
                // A session is still unwinding. Defer instead of skipping: the exit hook
                // will run startLocked() the moment it releases the buffers.
                pendingStart = true;
            }else {
                executeStart();
            }
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            if(mThread != null) {
                pendingStart = false;    // cancels a deferred start
                running = false;      //this will stop the process voice (at the end of a cycle)
                executeStopAudioRecord();
            }
        }
    }

    /**
     * Dismisses the currently ongoing utterance without emitting it.
     * Asynchronous: takes effect on the next audio thread iteration.
     */
    public void dismiss() {
        post(this::executeDismiss);
    }

    /**
     * Closes the current utterance and delivers it through {@link Recorder.Callback#onVoice}.
     * Asynchronous: takes effect on the next audio thread iteration.
     */
    public void end() {
        post(this::executeEnd);
    }

    public boolean isManualMode() {
        return isManualMode;
    }

    /**
     * Switches between VAD-driven and manual capture. In manual mode the recorder is idle until
     * {@link #startRecording()} is called; outside it, capture runs continuously.
     */
    public void setManualMode(boolean manualMode) {
        synchronized (lifecycleLock) {
            if (isManualMode == manualMode) {
                return;
            }
            isManualMode = manualMode;
        }
        // Both branches tear down the current session first, and the audio thread emits
        // onVoiceEnd() on its way out if an utterance was in progress. No callback is
        // invoked from this thread, so there is no cross-thread callback race.
        if (manualMode) {
            Log.d(TAG, "manual mode activating");
            stop();
            Log.d(TAG, "manual mode activated");
        } else {
            start();
            Log.d(TAG, "manual mode deactivated");
        }
    }

    public boolean isOnHeadsetSco() {
        return connectedBleHeadset != null;
    }

    public void startRecording() {
        start();
    }

    public void stopRecording() {
        // Posted before running is cleared, so the final drain in the run loop's finally block
        // delivers the utterance even though stop() is about to kill the thread.
        end();
        stop();
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     * Reads a cached value, so it stays valid after {@link #destroy()}.
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Stops capture, releases the {@link AudioRecord} and unregisters the audio device callback.
     * Idempotent. Safe to call from any thread except the audio thread itself.
     */
    public void destroy() {
        synchronized (lifecycleLock) {
            if (destroyed) return;
            pendingStart = false;
            running = false;
            executeStopAudioRecord();      // call before setting destroyed, it checks that flag
            destroyed = true;
            if (useBluetoothHeadset && audioManager != null) {
                if (audioDeviceCallback != null) audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
                if (connectedBleHeadset != null) { audioManager.stopBluetoothSco(); connectedBleHeadset = null; }
            }
            if (mThread == null && mAudioRecord != null) mAudioRecord.release();
            commands.clear();
        }
    }

    // =====================================================================================
    //  Command plumbing
    // =====================================================================================

    private void post(Runnable command) {
        if (!running) {
            // Nothing is alive to drain it. A command posted in the narrow window between this
            // check and a concurrent stop() is discarded by the commands.clear() in start().
            return;
        }
        commands.add(command);
    }

    /** Runs on the audio thread only. */
    private void drainCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            try {
                command.run();
            } catch (Throwable t) {
                Log.e(TAG, "command failed", t);
            }
        }
    }

    // =====================================================================================
    //  Audio thread, Voice Process
    // =====================================================================================

    /** Caller must hold lifecycleLock. */
    private void executeStopAudioRecord() {
        if (mAudioRecord == null || destroyed) {
            return;
        }
        try {
            if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                mAudioRecord.stop();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "AudioRecord.stop() failed", e);
        }
    }

    /** Caller must hold lifecycleLock. */
    private void executeStart() {
        pendingStart = false;
        if (destroyed || mAudioRecord == null || mThread != null) return;
        commands.clear();
        running = true;
        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException e) {
            running = false;
            Log.e(TAG, "startRecording() failed", e);
            return;
        }
        if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            running = false;
            Log.e(TAG, "AudioRecord did not enter RECORDING state");
            return;
        }
        final ProcessVoice processVoice = new ProcessVoice();
        Thread t = new Thread(() -> {
            try {
                processVoice.run();
            } finally {
                onProcessVoiceExit();     // runs after its own finally block
            }
        }, "processVoice");
        mThread = t;
        t.start();
    }

    /**
     * Creates a new {@link AudioRecord}.
     *
     * @return A newly created {@link AudioRecord}, or null if it cannot be created (missing
     * permissions?).
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int minSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (minSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            this.sampleRate = sampleRate;
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL, ENCODING, minSizeInBytes);   //the option MIC produce better result than the option VOICE_RECOGNITION
            //audioRecord.setPreferredDevice()
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                int minReadSize = (minSizeInBytes/4)*2;
                readSize = (int) (VAD_FRAME_SIZE * Math.ceil((float) minReadSize / VAD_FRAME_SIZE));  //readSize will be the closed multiple of VAD_FRAME_SIZE that is > minReadSize
                mBuffer = new float[((MAX_SPEECH_LENGTH_MILLIS+1000)/1000)*sampleRate];  //the buffer size will be larger (by one second) than the audio data of duration MAX_SPEECH_LENGTH_MILLIS
                mBufferShort = new short[((MAX_SPEECH_LENGTH_MILLIS+1000)/1000)*sampleRate];
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    /**
     * Continuously processes the captured audio and notifies {@link #mCallback} of corresponding
     * events.
     * Always call the isHearing voice method and if it returns true and the time span from the last listening of the voice is greater than a tot (MAX_VALUE)
     * then call the onVoiceStarted method and then onVoice, otherwise only onVoice.
     */
    private class ProcessVoice implements Runnable {
        @Override
        public void run() {
            resetAudioState();
            try {
                while (running) {
                    drainCommands();
                    if (!running) {
                        break;
                    }
                    // process of voice
                    if (mAudioRecord != null) {
                        int prevVoiceLength;
                        if (isManualMode) {
                            prevVoiceLength = (int) (0.1 * sampleRate);  //if we are using manual mode we use a reduced prev voice duration
                        } else {
                            prevVoiceLength = (global.getPrevVoiceDuration() * sampleRate) / 1000;
                        }
                        int size;
                        int oldTailIndex = tailIndex;
                        boolean jumped;
                        if (tailIndex + readSize < mBuffer.length) {
                            size = readAudio(tailIndex, readSize);
                            if (size <= 0) break;  // Break thread on error or closed stream
                            tailIndex = tailIndex + size;
                            jumped = false;
                        } else {
                            size = readAudio(tailIndex, mBuffer.length - tailIndex);
                            if (size <= 0) break;  // Break thread on error or closed stream
                            tailIndex = 0;
                            int remaining = readSize - size;
                            if (remaining > 0) {
                                int size2 = readAudio(tailIndex, remaining);
                                if (size2 <= 0) break;  // Break thread on error or closed stream
                                tailIndex = size2;
                                size = size + size2;
                            }
                            jumped = true;
                        }
                        if ((oldTailIndex < headIndex && tailIndex > headIndex) || (oldTailIndex > headIndex && tailIndex > headIndex && jumped)) {  //if we overwrote the oldest data
                            headIndex = (tailIndex + 1) % mBuffer.length;  //we adjust the headIndex accordingly
                        }
                        //we notify volume level
                        notifyVolumeLevel(mBuffer, oldTailIndex, tailIndex);
                        //we do the rest of voice processing
                        final long now = System.currentTimeMillis();
                        if (isHearingVoice(mBufferShort, oldTailIndex, tailIndex)) {
                            if (mLastVoiceHeardMillis == Long.MAX_VALUE) {    // use Long's maximum limit to indicate that we have no voice
                                mVoiceStartedMillis = now;
                                isRecording = true;
                                mCallback.onVoiceStart();
                                if (getMBufferSize() > prevVoiceLength) {
                                    if (tailIndex - prevVoiceLength >= 0) {
                                        startVoiceIndex = tailIndex - prevVoiceLength;
                                    } else {
                                        startVoiceIndex = mBuffer.length + (tailIndex - prevVoiceLength);  //we do a jump
                                    }
                                } else {
                                    startVoiceIndex = headIndex;
                                }
                            }
                            mLastVoiceHeardMillis = now;
                            if (now - (mVoiceStartedMillis - global.getPrevVoiceDuration()) > MAX_SPEECH_LENGTH_MILLIS) {  //if we are listening voice for more than MAX_SPEECH_LENGTH_MILLIS
                                executeEnd();
                            }
                        } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                            if (now - mLastVoiceHeardMillis > global.getSpeechTimeout()) {  //if we had not heard voice for global.getSpeechTimeout() ms
                                executeEnd();
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "processVoice aborted", t);
            } finally {
                drainCommands();          // deliver a pending end() from stopRecording()
                if (isRecording) {        // never leave isRecording stuck true
                    isRecording = false;
                    mCallback.onVoiceEnd();
                }
                resetAudioState();
                running = false;
            }
        }
    }

    /** Audio thread only. */
    private void resetAudioState() {
        headIndex = 0;
        tailIndex = 0;
        startVoiceIndex = 0;
        //vadChunkFill = 0;
        mLastVoiceHeardMillis = Long.MAX_VALUE;
    }

    /** Runs on the dying audio thread, after ProcessVoice has released everything. */
    private void onProcessVoiceExit() {
        synchronized (lifecycleLock) {
            if (mThread != Thread.currentThread()) return;
            mThread = null;
            running = false;
            if (destroyed) {
                if (mAudioRecord != null) mAudioRecord.release();
            } else if (pendingStart) {
                executeStart();
            } else {
                executeStopAudioRecord();   // the loop may have exited without anyone calling stop(), so we stop mAudioRecord here
            }
        }
    }

    /**
     * Closes the current utterance and hands it to the callback. Audio thread only —
     * public callers reach this through {@link #end()}.
     */
    private void executeEnd() {
        if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
            return; // no utterance in progress; emitting here would ship stale buffer contents
        }
        final int voiceLength = getMBufferRangeSize(startVoiceIndex, tailIndex);
        final float[] data = new float[voiceLength];
        int circularIndex = startVoiceIndex;
        for (int i = 0; i < voiceLength; i++) {
            data[i] = mBuffer[circularIndex];
            circularIndex = (circularIndex + 1 == mBuffer.length) ? 0 : circularIndex + 1;
        }

        mLastVoiceHeardMillis = Long.MAX_VALUE;
        startVoiceIndex = 0;

        mCallback.onVoice(data, voiceLength);
        isRecording = false;
        mCallback.onVoiceEnd();
    }

    /** Audio thread only — public callers reach this through {@link #dismiss()}. */
    private void executeDismiss() {
        mLastVoiceHeardMillis = Long.MAX_VALUE;
    }

    private int getMBufferSize(){
        return getMBufferRangeSize(headIndex, tailIndex);
    }

    private int getMBufferRangeSize(int begin, int end){
        if(begin <= end){
            return end - begin;
        }else{    //(begin > end)
            return (mBuffer.length-begin) + end;
        }
    }

    private int readAudio(int offset, int size){
        if(ENCODING == AudioFormat.ENCODING_PCM_FLOAT){
            int outputSize = mAudioRecord.read(mBuffer, offset, size, AudioRecord.READ_BLOCKING);
            // Using the values just read in mBuffer we convert the values to mBufferShort in the ENCODING_PCM_16BIT format (used for VAD)
            // To do this, we iterate the section just wrote of mBuffer, convert each value from ENCODING_PCM_FLOAT to ENCODING_PCM_16BIT and insert these values in the corresponding section of mBufferShort.
            for(int i=offset; i<offset+outputSize; i++){
                //The range with ENCODING_PCM_16BIT is [-32768, 32767], while with ENCODING_PCM_FLOAT it is [-1, 1], so we convert accordingly
                mBufferShort[i] = (short) (mBuffer[i] * 32768);
            }
            return outputSize;
        }else{  //ENCODING == AudioFormat.ENCODING_PCM_16BIT
            int outputSize = mAudioRecord.read(mBufferShort, offset, size, AudioRecord.READ_BLOCKING);
            // Using the values just read in mBufferShort we convert the values to mBuffer in the ENCODING_PCM_FLOAT format (used for Speech recognition)
            // Tod do this we iterate the section just wrote of mBufferShort, convert each value from ENCODING_PCM_16BIT to ENCODING_PCM_FLOAT and insert these value in the corresponding section of mBuffer.
            for(int i=offset; i<offset+outputSize; i++){
                //The range with ENCODING_PCM_16BIT is [-32768, 32767], while with ENCODING_PCM_FLOAT it is [-1, 1], so we convert accordingly
                mBuffer[i] = (float) mBufferShort[i] / 32768;
            }
            return outputSize;
        }
    }

    private boolean isHearingVoice(short[] buffer, int begin, int end) {
        if(!isManualMode) {
            // We iterate circularly the buffer from the begin index to the end index, dividing the data into chunks with the correct length for the VAD.
            // We also check if the volume level surpasses the threshold
            int numberOfThreshold = 15;
            int count = begin;
            ArrayList<short[]> chunks = new ArrayList<>();
            chunks.add(new short[VAD_FRAME_SIZE]);
            int chunkCount = 0;
            while (count != end) {
                // fill the chunks
                if(chunkCount >= VAD_FRAME_SIZE){
                    chunks.add(new short[VAD_FRAME_SIZE]);
                    chunkCount = 0;
                }
                chunks.get(chunks.size()-1)[chunkCount] = buffer[count];
                chunkCount++;
                int amplitudeThreshold = global.getAmplitudeThreshold();
                // check the volume level
                int s = Math.abs(buffer[count]);
                if (s > amplitudeThreshold) {
                    numberOfThreshold--;
                }
                // increment the counter
                if (count < buffer.length - 1) {
                    count++;
                } else {
                    count = 0;
                }
            }
            // we execute the VAD for every chunk, and if one of them is recognized as voice the method returns true
            boolean isVoice = false;
            for (short[] chunk : chunks) {
                if(chunk.length == VAD_FRAME_SIZE && vad.isSpeech(chunk)) {
                    isVoice = true;
                    break;
                }
            }
            if (numberOfThreshold <= 0 && isVoice) {
                return true;
            } else {
                return false;
            }
        }else{
            return true;  //in this way if we are in manual mode the recording will run until we call end()
        }
    }

    private boolean isVolumeLevelHigh(float[] buffer, int begin, int end) {   //old method to measure threshold (not used)
        if(!isManualMode) {
            // We iterate circularly the mBuffer from the begin index to the end index, and if one of the values exceed the threshold the method returns true.
            // Also The range with the old ENCODING_PCM_16BIT was [-32768, 32767], while now with the new ENCODING_PCM_FLOAT it is [-1, 1],
            // so to convert the values of the new range into those of the old range (the threshold is based on the old values) I have to multiply them by 32767.
            int numberOfThreshold = 15;
            int count = begin;
            while (count != end) {
                float s = Math.abs(buffer[count]) * 32767;
                int amplitudeThreshold = global.getAmplitudeThreshold();
                if (s > amplitudeThreshold) {
                    numberOfThreshold--;
                }
                if (count < buffer.length - 1) {
                    count++;
                } else {
                    count = 0;
                }
            }
            if (numberOfThreshold <= 0) {
                return true;
            } else {
                return false;
            }
        }else{
            return true;  //in this way if we are in manual mode the recording will run until we call end()
        }
    }

    private void notifyVolumeLevel(float[] buffer, int begin, int end) {
        if(isRecording){
            float[] amplifiedBuffer = new float[getMBufferRangeSize(begin, end)];

            //we make a copy of the buffer with every value amplified and converted to an absolute value
            //so the range of every value from [-1, 1] will become [0, amplification]
            int count = begin;
            int linearCount = 0;
            float amplification = (32767f/global.getAmplitudeThreshold()) * 2;
            while (count != end) {
                amplifiedBuffer[linearCount] = (float) (Math.abs(buffer[count]) * amplification);
                if (count < buffer.length - 1) {
                    count++;
                } else {
                    count = 0;
                }
                linearCount++;
            }

            //we remove the nMinValuesToRemove lower values
            /*int nMinValuesToRemove = 1;
            int nMin = 0;
            while (nMin < nMinValuesToRemove) {
                float min = Float.MAX_VALUE;
                int minIndex = 0;
                for(int i=0; i<amplifiedBuffer.length; i++){
                    if(amplifiedBuffer[i] < min){
                        min = amplifiedBuffer[i];
                        minIndex = i;
                    }
                }
                amplifiedBuffer[minIndex] = 0;    //assign a 0 is equivalent to remove a number because it will not have an effect on the sum (and even on the average because we exclude this number in the number of elements)
                nMin++;
            }*/

            //we do the average of every value in amplifiedBuffer
            float sum = 0;
            for (int i=0; i<amplifiedBuffer.length; i++) {
                sum += amplifiedBuffer[i];
            }
            float average = sum / (amplifiedBuffer.length /*- nMinValuesToRemove*/);

            //Log.d("volume", "volume: " + average);

            //we cap the values between 0 and 1 (and we decrease the sensitivity in the [0.8, 1] range)
            if(average > 1){
                float surplus = average-1f;
                surplus = surplus / 5;
                average = 0.8f + surplus;
            }
            if(average > 1){
                average = 1;
            }
            if(average < 0){
                average = 0;
            }

            //Log.d("volume", "volume capped: " + average);
            Log.d("volume", "volume: " + average);

            mCallback.onVolumeLevel(average);
        }
    }

    private boolean setBLEHeadsetConnection(){
        AudioDeviceInfo[] allDeviceInfo = audioManager.getDevices(GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : allDeviceInfo) {
            int deviceType = device.getType();
            if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                if (mAudioRecord != null) {
                    audioManager.startBluetoothSco();
                    connectedBleHeadset = device;
                }
                return true;
            }
            if(deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET || deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP){  //untested
                if(mAudioRecord != null) {
                    boolean success = mAudioRecord.setPreferredDevice(device);
                    if (success) {
                        connectedBleHeadset = device;
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public static abstract class Callback {
        /**
         * Called when the recorder starts hearing voice.
         */
        public void onVoiceStart() {
            Log.e("recorder","onVoiceStart");
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The peersSize of the actual data in {@code data}.
         */
        public void onVoice(@NonNull float[] data, int size) {
            Log.e("recorder","onVoice");
        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onVoiceEnd() {
            Log.e("recorder","onVoiceEnd");
        }

        /**
         * Called continuously when we hear voice
         * @param volumeLevel a value between [0, 1] that represent the volume percentage of the audio captured by the microphone
         */
        public void onVolumeLevel(float volumeLevel){}
    }

    public static abstract class SimpleCallback extends Callback {
        @Override
        public void onVoiceStart() {
            super.onVoiceStart();
        }

        @Override
        public void onVoice(@NonNull float[] data, int size) {
            super.onVoice(data, size);
        }

        @Override
        public void onVoiceEnd() {
            super.onVoiceEnd();
        }
    }
}
