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

package nie.translator.rtranslatordevedition.tools;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

public class TTS {
    //object
    private TextToSpeech tts;

    public TTS(Context context, final InitListener listener) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    /*List<EngineInfo> engineInfos=TTS.this.getEngines();   //method 1
                    boolean found=false;
                    for (int i=0;i<engineInfos.size() && !found;i++){
                        if(engineInfos.get(i).name.equals("com.google.android.tts")){
                            found=true;
                        }
                    }*/

                    boolean found = false;    //method 2
                    if (tts != null) {
                        ArrayList<TextToSpeech.EngineInfo> engines = new ArrayList<>(tts.getEngines());
                        for (int i = 0; i < engines.size() && !found; i++) {
                            if (engines.get(i).name.equals("com.google.android.tts")) {
                                found = true;
                            }
                        }
                        if (!found) {
                            tts = null;
                            listener.onError(ErrorCodes.MISSING_GOOGLE_TTS);
                        } else {
                            listener.onInit();
                        }
                        return;
                    }
                }
                tts = null;
                listener.onError(ErrorCodes.GOOGLE_TTS_ERROR);
            }
        }, "com.google.android.tts");
    }

    public boolean isActive() {
        return tts != null;
    }

    public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
        if (isActive()) {
            return tts.speak(text, queueMode, params, utteranceId);
        }
        return TextToSpeech.ERROR;
    }

    @Nullable
    public Voice getVoice() {
        if (isActive()) {
            return tts.getVoice();
        }
        return null;
    }

    @Nullable
    public Set<Voice> getVoices() {
        if (isActive()) {
            return tts.getVoices();
        }
        return null;
    }

    public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
        if (isActive()) {
            return tts.setOnUtteranceProgressListener(listener);
        }
        return TextToSpeech.ERROR;
    }

    public int setLanguage(CustomLocale loc, Context context) {
        if (isActive()) {
            return tts.setLanguage(new Locale(loc.getLocale().getLanguage()));
        }
        return TextToSpeech.ERROR;
    }

    public int stop() {
        if (isActive()) {
            return tts.stop();
        }
        return TextToSpeech.ERROR;
    }

    public void shutdown() {
        if (isActive()) {
            tts.shutdown();
        }
    }

    public interface InitListener {
        void onInit();

        void onError(int reason);
    }
}
