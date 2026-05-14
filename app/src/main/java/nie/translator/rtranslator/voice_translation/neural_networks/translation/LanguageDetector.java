package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import ai.djl.ModelException;

public class LanguageDetector {
    // Load the C++ library
    static {
        System.loadLibrary("fasttext-lib");
    }


    private static final String TAG = "LanguageDetector";
    private static final String MODEL_FILE_NAME = "fasttext.ftz";

    private long nativePtr = 0;
    private Context context;

    /**
     * Initializes the detector. Call this from a background thread during app startup.
     */
    public void initialize(Context context) throws IOException, ModelException {
        File modelFile = new File(context.getFilesDir(), MODEL_FILE_NAME);

        this.context = context;
        this.nativePtr = initFastText();

        loadModel(nativePtr, modelFile.getAbsolutePath());
    }

    /**
     * Predicts the language of a given text string.
     */
    public void detectLanguage(String text, double confidenceThreshold, @NonNull DetectLanguageListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (nativePtr == 0) {
                        Log.e(TAG, "Model not initialized.");
                        listener.onSuccess("und");
                    }
                    listener.onSuccess(predictLanguage(nativePtr, text, confidenceThreshold));

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "Failed to predict language", e);
                    listener.onSuccess("und");
                }
            }
        }).start();
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
