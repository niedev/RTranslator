package nie.translator.rtranslator.voice_translation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayDeque;

import javax.annotation.Nullable;

import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.TTS;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;

public class TTSEngine {
    public final static int DELAY_AFTER_TTS_INTERRUPTION_MS = 1500;
    private final ArrayDeque<Pair<GuiMessage, CustomLocale>> messageQueue = new ArrayDeque<>();
    @Nullable
    private Pair<GuiMessage, CustomLocale> executingMessage = null;
    private boolean speaking = false;
    @Nullable
    private final TTS tts;
    private boolean pause = false;
    private Context context;
    @Nullable
    private TTSEngineListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());


    public TTSEngine(Context context, @Nullable TTSEngineListener listener) {
        this.context = context;
        this.tts = new TTS(context, new TTS.InitListener() {  // tts initialization (to be improved, automatic package installation)
            @Override
            public void onInit() {
                if(TTSEngine.this.tts != null) {
                    TTSEngine.this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onDone(String utteranceId) {
                            if (listener != null) {
                                boolean isLast = messageQueue.isEmpty();
                                listener.onUtteranceFinished(getExecutingMessage(), isLast);
                                executingMessage = null;
                            }
                            speak();
                        }

                        @Override
                        public void onError(String utteranceId) {
                            stop();
                        }

                        @Override
                        public void onStart(String utteranceId) {

                        }

                        @Override
                        public void onStop(String utteranceId, boolean interrupted) {
                            super.onStop(utteranceId, interrupted);

                            /*
                             * Usually, this interruption is triggered by TalkBack TTS.
                             * In this case, we put our stopped message back in the TTS queue, to resume the normal
                             * execution after TalkBack is done speaking.
                             * The insertion back in the TTS queue, is done after a delay, because often, TalkBack says multiple things,
                             * and inserts each phrase in the TTS after it has been spoken (not all at once).
                             * If we don't do the delay, we risk to put the TTS phrase in the middle of the TTS queue,
                             * giving a buggy feeling to the user, which will hear TalkBack, then out phrase, then TalkBack again with the second part.
                             */

                            if (pause) return; // Ignore if we stopped it manually via the pause() button

                            if (interrupted) {
                                Log.i("tts_engine", "TTS interrupted by TalkBack/System");

                                // Unstick the queue
                                speaking = false;

                                // Put the interrupted message back at the VERY FRONT of our custom queue
                                if (executingMessage != null) {
                                    messageQueue.addFirst(executingMessage);
                                    executingMessage = null;
                                }

                                // Trigger speak() again after a delay (so TalkBack can queue all its hints).
                                // Because TalkBack is currently speaking, QUEUE_ADD will gracefully
                                // put this message in the TTS engine's queue directly behind TalkBack.
                                handler.removeCallbacksAndMessages(null);  // Clear any pending resumes to avoid duplicates
                                handler.postDelayed(() -> {
                                    tts.stop();
                                    speak();
                                }, DELAY_AFTER_TTS_INTERRUPTION_MS);
                            } else {
                                stop();
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(int reason) {

            }
        });
        this.listener = listener;

    }


    public boolean speak(GuiMessage message, CustomLocale language){
        messageQueue.addLast(new Pair<>(message, language));
        if (!messageQueue.isEmpty() && !speaking && !pause) {
            return speak();
        }
        return false;
    }

    private boolean speak(){
        return speak(TextToSpeech.QUEUE_ADD);
    }

    private boolean speak(int queueMode){
        if(!pause) {
            boolean wasSpeaking = speaking;
            speaking = true;
            Pair<GuiMessage, CustomLocale> pair = messageQueue.pollFirst();
            if (pair != null && tts != null) {
                if(listener != null) listener.onUtteranceStarting(pair.first, !wasSpeaking);
                executingMessage = pair;
                if (tts.getVoice() != null && pair.second.equals(new CustomLocale(tts.getVoice().getLocale()))) {
                    tts.speak(pair.first.getMessage().getText(), queueMode, null, String.valueOf(pair.first.getMessageID()));
                } else {
                    tts.setLanguage(pair.second, context);
                    tts.speak(pair.first.getMessage().getText(), queueMode, null, String.valueOf(pair.first.getMessageID()));
                }
                return true;
            }else{
                speaking = false;
                executingMessage = null;
            }
        }
        return false;
    }

    public void pause() {
        if(!pause) {
            Log.i("tts_engine", "tts engine paused");
            handler.removeCallbacksAndMessages(null);
            if(tts != null) tts.stop();
            executingMessage = null;
            pause = true;
        }
    }

    public void resume() {
        if(pause){
            Log.i("tts_engine", "tts engine resumed");
            handler.removeCallbacksAndMessages(null);
            pause = false;
            speak();
        }
    }

    public boolean isPaused(){
        return pause;
    }

    public boolean isEmpty(){
        return messageQueue.isEmpty();
    }

    public void stop(){
        if(tts != null) tts.stop();
        boolean notify = listener != null && (executingMessage != null || !messageQueue.isEmpty());
        GuiMessage oldExecutingMessage = getExecutingMessage();
        executingMessage = null;
        messageQueue.clear();
        if(notify){
            listener.onUtteranceFinished(oldExecutingMessage, true);
        }
        speaking = false;
    }

    @Nullable
    public GuiMessage getExecutingMessage() {
        if(executingMessage != null) {
            return executingMessage.first;
        }
        return null;
    }

    @Nullable
    public TTS getTts() {
        return tts;
    }

    public void shutdown() {
        if(tts != null) tts.shutdown();
    }

    public boolean isActive() {
        if(tts != null) return tts.isActive();
        return false;
    }

    public static abstract class TTSEngineListener {
        abstract void onUtteranceStarting(GuiMessage message, boolean first);
        abstract void onUtteranceFinished(@Nullable GuiMessage message, boolean last);
        abstract void onInitError(int reason);
    }
}
