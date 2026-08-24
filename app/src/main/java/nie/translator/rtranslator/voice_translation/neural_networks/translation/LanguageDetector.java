package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import ai.djl.ModelException;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.FileTools;
import nie.translator.rtranslator.tools.TextTools;

public class LanguageDetector {
    // Load the C++ library
    static {
        System.loadLibrary("fasttext-lib");
    }


    private static final String TAG = "LanguageDetector";
    private static final String MODEL_FILE_NAME = "fasttext.ftz";
    private static final int MIN_WORDS = 7;

    private long nativePtr = 0;
    private Context context;
    private LanguageResourcesManager languageResourcesManager;
    private static final Map<String, String> specialWordsLanguages = new HashMap<>();
    static {
        specialWordsLanguages.put("ciao", "ita");
    }


    /**
     * Initializes the detector. Call this from a background thread during app startup.
     */
    public void initialize(Context context, LanguageResourcesManager languageResourcesManager) throws IOException, ModelException {
        //we eventually transfer the fasttext model file from the assets to the internal memory (because the fasttext djl lib can open model only via a path to internal or external memory)
        File outFileFastText = new File(context.getFilesDir(), "fasttext.ftz");
        if(!outFileFastText.exists()) {
            FileTools.copyAssetToInternalMemory(context, "fasttext.ftz");
        }

        File modelFile = new File(context.getFilesDir(), MODEL_FILE_NAME);

        this.context = context;
        this.languageResourcesManager = languageResourcesManager;
        this.nativePtr = initFastText();

        loadModel(nativePtr, modelFile.getAbsolutePath());
    }

    /**
     * Predicts the language of a given text string.
     */
    public void detectLanguage(String text, double confidenceThreshold, CustomLocale[] languages, @NonNull DetectLanguageListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (nativePtr == 0) {
                        Log.e(TAG, "Model not initialized.");
                        listener.onSuccess("und");
                        return;
                    }
                    String normalizedText = TextTools.normalizeTextForLid(text);
                    int wordsCount = TextTools.countWords(normalizedText);
                    if (wordsCount < MIN_WORDS) {
                        if(TextTools.countWords(normalizedText) == 1){
                            //we use specialWordsLanguages mapping
                            String langCode = identifySpecialWordLanguage(normalizedText);
                            if(!langCode.equals("und")){
                                for (CustomLocale lang: languages) {
                                    if(lang.getISO3Language().equals(langCode)){
                                        listener.onSuccess(langCode);
                                        return;
                                    }
                                }
                            }
                            //we use dictionaries for detection
                            for (CustomLocale language: languages) {
                                if(DictionaryTranslator.containsWord(normalizedText, language)){
                                    listener.onSuccess(language.getISO3Language());
                                    return;
                                }
                            }
                        }
                        // we use Lingua for detection
                        String langCode = languageResourcesManager.detectLanguageWithLingua(text);
                        if(!langCode.equals("und")){
                            listener.onSuccess(langCode);
                            return;
                        }
                    }
                    //we use FastText for detection
                    if(wordsCount <= 1) {
                        long time = System.currentTimeMillis();
                        String langCode = predictLanguage(nativePtr, text, confidenceThreshold);
                        android.util.Log.i("lid_performance", "FastText language identification done in: " + (System.currentTimeMillis() - time) + "ms");
                        listener.onSuccess(langCode);
                        return;
                    }
                    listener.onSuccess("und");

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to predict language", e);
                    listener.onSuccess("und");
                }
            }
        }).start();
    }

    /**
     * This method is used to overwrite the dictionaries result for very common words (that may be present in other languages dicts)
     * @return The language of the word
     */

    private String identifySpecialWordLanguage(String word){
        if(specialWordsLanguages.containsKey(word)){
            return specialWordsLanguages.get(word);
        }
        return "und";
    }

    public abstract static class DetectLanguageListener {
        public abstract void onSuccess(String languageCode);
    }

    public void close() {
        if (nativePtr != 0) {
            release(nativePtr);
            nativePtr = 0;
        }
    }


    private native long initFastText();
    private native void loadModel(long ptr, String path);
    private native String predictLanguage(long ptr, String text, double confidenceThreshold);
    private native void release(long ptr);
}
