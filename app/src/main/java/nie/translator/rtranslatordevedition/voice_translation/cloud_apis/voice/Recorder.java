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

package nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.annotation.NonNull;
import java.util.ArrayDeque;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.tools.Timer;


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
public class Recorder {
    private Global global;
    private int timerNumber = 0;
    private boolean isListening;
    private boolean isRecording;
    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000, 44100, 22050, 11025};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int MAX_AMPLITUDE_THRESHOLD = 15000;
    public static final int DEFAULT_AMPLITUDE_THRESHOLD = 2000; //original: 1500
    public static final int MIN_AMPLITUDE_THRESHOLD = 400;
    private static final int SPEECH_TIMEOUT_MILLIS = 750; //original: 2000
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;
    private static final double PREV_VOICE_DURATION = 800;
    private Timer timer;
    private Callback mCallback;
    private AudioRecord mAudioRecord;
    private Thread mThread;
    private int mPrevBufferMaxSize;
    private ArrayDeque<byte[]> mPrevBuffer;
    private byte[] mBuffer;
    /**
     * The timestamp of the last time that voice is heard.
     */
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    /**
     * The timestamp when the current voice is started.
     */
    private long mVoiceStartedMillis;


    public Recorder(Global global, @NonNull Callback callback) {
        this.global = global;
        global.getMicSensitivity();
        mCallback = callback;
        mCallback.setRecorder(this);
        if(!(callback instanceof SimpleCallback)){
            // used to not stop speech recognition before the 15 second step
            timer = new Timer(15000, 1000, new Timer.Callback() {
                @Override
                public void onTick(long millisUntilEnd) {
                    // it stops here eventually (and not in onEnd) to stop it before the 15 seconds have elapsed
                    if (millisUntilEnd <= 1000 && !isRecording) {
                        mCallback.onListenEnd();
                    }
                }

                @Override
                public void onEnd() {
                    // here we avoid that listening does not last longer than 4 intervals (therefore the 60 seconds limit), should not be confused with MAX_SPEECH_LENGTH_MILLIS because that represents the limit of voice, not of listen
                    timerNumber++;
                    if (timerNumber == 4) {
                        timerNumber = 0;
                        mCallback.onListenEnd();
                    } else {
                        timer.start();
                    }
                }
            });
        }
    }

    /**
     * Starts recording audio.
     *
     * <p>The caller is responsible for calling {@link #stop()} later.</p>
     */
    public void start() {
        // Stop recording if it is currently ongoing.
        stop();
        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            throw new RuntimeException("Cannot instantiate Recorder");
        }
        // Start recording.
        mAudioRecord.startRecording();  // here doesn't work with callback
        // Start processing the captured audio.
        mThread = new Thread(new ProcessVoice(), "processVoice");
        mThread.start();
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            //mAudioRecord = null;
        }
        //mBuffer = null;
        dismiss();
        mCallback.onListenEnd();
    }

    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {  // that's why we always stop recognizing even when we have a final result
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     *
     * @return The sample rate of recorded audio.
     */
    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }

    /**
     * Creates a new {@link AudioRecord}.
     *
     * @return A newly created {@link AudioRecord}, or null if it cannot be created (missing
     * permissions?).
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord;
            /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            }else{
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            }*/
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mBuffer = new byte[sizeInBytes * 2];  //attention here
                mPrevBufferMaxSize = (int) Math.floor((((16f * sampleRate) / 8) * (PREV_VOICE_DURATION /1000)) / mBuffer.length);
                mPrevBuffer = new ArrayDeque<>();   // the prev buffer must contain PREV_VOICE_DURATION seconds of data prior to the buffer
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
            while (!Thread.currentThread().isInterrupted()) {
                final int size = mAudioRecord.read(mBuffer, 0, mBuffer.length);
                mPrevBuffer.addLast(mBuffer.clone());
                if (mPrevBuffer.size() > mPrevBufferMaxSize) {
                    mPrevBuffer.pollFirst();   // the excess buffer is eliminated, since the prevBuffer must only store the last buffers, the number is decided by prevBufferMaxSize
                }
                final long now = System.currentTimeMillis();
                if (isHearingVoice(mBuffer, size)) {
                    if (mLastVoiceHeardMillis == Long.MAX_VALUE) {    // use Long's maximum limit to indicate that we have no voice
                        mVoiceStartedMillis = now;
                        if (!isListening) {
                            mCallback.onListenStart();
                        }
                        mCallback.onVoiceStart();
                        // we send the previous section (PREV_VOICE_DURATION seconds) when the voice is recognized
                        while (mPrevBuffer.size() > 0) {
                            mCallback.onVoice(mPrevBuffer.pollFirst(), size);
                        }
                    } else {
                        mCallback.onVoice(mBuffer, size);
                    }
                    mLastVoiceHeardMillis = now;
                    if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                        end();
                        mCallback.onListenEnd();
                    }
                } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                    mCallback.onVoice(mBuffer, size);
                    if (now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                        end();
                    }
                }
            }
        }

        private void end() {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            for (int i = 0; i < size - 1; i += 2) {
                // The buffer has LINEAR16 in little endian.
                int s = buffer[i + 1];
                if (s < 0) s *= -1;
                s <<= 8;
                s += Math.abs(buffer[i]);
                int amplitudeThreshold = global.getAmplitudeThreshold();
                if (s > amplitudeThreshold) {
                    return true;
                }
            }
            return false;
        }

    }

    public static abstract class Callback {
        private Recorder recorder;

        void setRecorder(Recorder recorder) {
            this.recorder = recorder;
        }


        public void onListenStart() {
            if (recorder != null) {
                recorder.timer.cancel();
                recorder.timer.start();
                recorder.isListening = true;
            }
        }

        /**
         * Called when the recorder starts hearing voice.
         */
        public void onVoiceStart() {
            if (recorder != null) {
                recorder.isRecording = true;
            }
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The peersSize of the actual data in {@code data}.
         */
        public void onVoice(@NonNull byte[] data, int size) {

        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onVoiceEnd() {
            if (recorder != null) {
                recorder.isRecording = false;
            }
        }

        public void onListenEnd() {
            if (recorder != null) {
                if (recorder.isRecording) {
                    onVoiceEnd();
                }
                recorder.isListening = false;
                recorder.timer.cancel();
            }
        }
    }

    public static abstract class SimpleCallback extends Callback {
        @Override
        void setRecorder(Recorder recorder) {
            super.setRecorder(recorder);
        }

        @Override
        public void onListenStart() {
        }

        @Override
        public void onVoiceStart() {
        }

        @Override
        public void onVoice(@NonNull byte[] data, int size) {
        }

        @Override
        public void onVoiceEnd() {
        }

        @Override
        public void onListenEnd() {
        }
    }
}
