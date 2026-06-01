package nie.translator.rtranslator.voice_translation;

import android.content.Context;
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
    private final ArrayDeque<Pair<GuiMessage, CustomLocale>> messageQueue = new ArrayDeque<>();
    @Nullable
    private GuiMessage executingMessage = null;
    private boolean speaking = false;
    @Nullable
    private final TTS tts;
    private boolean pause = false;
    private Context context;
    @Nullable
    private TTSEngineListener listener;


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
                                listener.onUtteranceFinished(executingMessage, isLast);
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
        if(!pause) {
            boolean wasSpeaking = speaking;
            speaking = true;
            Pair<GuiMessage, CustomLocale> pair = messageQueue.pollFirst();
            if (pair != null && tts != null) {
                if(listener != null) listener.onUtteranceStarting(pair.first, !wasSpeaking);
                executingMessage = pair.first;
                if (tts.getVoice() != null && pair.second.equals(new CustomLocale(tts.getVoice().getLocale()))) {
                    tts.speak(pair.first.getMessage().getText(), TextToSpeech.QUEUE_ADD, null, String.valueOf(pair.first.getMessageID()));
                } else {
                    tts.setLanguage(pair.second, context);
                    tts.speak(pair.first.getMessage().getText(), TextToSpeech.QUEUE_ADD, null, String.valueOf(pair.first.getMessageID()));
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
            if(tts != null) tts.stop();
            executingMessage = null;
            pause = true;
        }
    }

    public void resume() {
        if(pause){
            Log.i("tts_engine", "tts engine resumed");
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
        GuiMessage oldExecutingMessage = executingMessage;
        executingMessage = null;
        messageQueue.clear();
        if(notify){
            listener.onUtteranceFinished(oldExecutingMessage, true);
        }
        speaking = false;
    }

    @Nullable
    public GuiMessage getExecutingMessage() {
        return executingMessage;
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
