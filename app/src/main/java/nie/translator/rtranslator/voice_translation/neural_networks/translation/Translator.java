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

package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.content.Context;
import android.content.SharedPreferences;
import android.icu.text.BreakIterator;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.databases.tatoeba.LinksData;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.FileTools;
import nie.translator.rtranslator.tools.TextTools;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.nn.CacheContainerNative;
import nie.translator.rtranslator.tools.nn.TensorUtils;
import nie.translator.rtranslator.tools.nn.Utils;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.ConversationMessage;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApi;
import nie.translator.rtranslator.voice_translation.neural_networks.NeuralNetworkApiResult;


public class Translator extends NeuralNetworkApi {
    public static final int NLLB = 0;
    public static final int NLLB_CACHE = 6;
    public static final int MADLAD = 3;
    public static final int MADLAD_CACHE = 5;
    public static final int MOZILLA = 7;
    public static final int HY_MT = 8;
    private int mode;
    private Tokenizer tokenizer;
    private OrtEnvironment onnxEnv;
    private OrtSession encoderSession;
    private OrtSession decoderSession;
    private OrtSession cacheInitSession;
    private OrtSession embedAndLmHeadSession;
    private OrtSession embedSession;
    private final Map<String, String> nllbLanguagesCodes = new HashMap<>();
    private final Map<String, HyLanguageInfo> hyLanguagesInfo = new HashMap<>();
    private static final double EOS_PENALTY = 0.0;
    private static final double WORD_REWARD = 0.5;  //When this is > 0 the LENGTH_ALPHA should be 0, deactivating length normalization, because this already do that job, but it can also generate positive scores, which will break classic length normalization
    private static final float PATIENCE = 1.5F;
    private static final float LENGTH_ALPHA = 0.0F;  //The length penalty strength for length normalization of beams probabilities in beam search, 0 means no length normalization, 1 is similar of division by length, higher value will favourite longer results
    private static final double SIBLING_PENALTY = 0.2;
    private static final float PARTIAL_RESULT_MARGIN = 0.2F;
    @Nullable
    private GuiMessage lastInputText;
    @Nullable
    private GuiMessage lastOutputText;
    private long currentResultID = 0;
    private ArrayList<TranslateListener> callbacks = new ArrayList<>();
    private android.os.Handler mainHandler;   // handler that can be used to post to the main thread
    private ArrayDeque<DataContainer> dataToTranslate = new ArrayDeque<>();
    private final Object lock = new Object();
    private final Object langResourcesLock = new Object();
    private final int EMPTY_BATCH_SIZE = 1;
    private boolean translatingMessages = false;
    private boolean translating = false;
    private static final String[] mozillaLanguages = new String[]{
            "zh",
            "it",
            "fr",
            "de",
            "ko",
            "ja",
            "en"
    };
    private LanguageResourcesManager languageResourcesManager;


    public Translator(@NonNull Global global, int mode, GeneralListener initListener) {
        this.global = global;
        this.mode = mode;
        mainHandler = new android.os.Handler(Looper.getMainLooper());
        initializeNllbLanguagesCodes(global);
        initializeHyLanguagesInfo(global);

        initialize(global, mode, false, initListener);
    }

    private void initialize(@NonNull Global global, int mode, boolean restart, GeneralListener initListener){
        String encoderPath = "";
        String decoderPath = "";
        String vocabPath = "";
        String embedAndLmHeadPath = "";
        String cacheInitializerPath = "";

        if(mode == NLLB || mode == NLLB_CACHE) {
            //8 bit
            encoderPath = global.getFilesDir().getPath() + "/NLLB_encoder.onnx";
            decoderPath = global.getFilesDir().getPath() + "/NLLB_decoder.onnx";
            vocabPath = global.getFilesDir().getPath() + "/sentencepiece_bpe.model";
            embedAndLmHeadPath = global.getFilesDir().getPath() + "/NLLB_embed_and_lm_head.onnx";
            cacheInitializerPath = global.getFilesDir().getPath() + "/NLLB_cache_initializer.onnx";
            //4 bit
            /*encoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/NLLB" + "/nllb_encoder_4bit.onnx";
            decoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/NLLB" + "/nllb_decoder_4bit.onnx";
            vocabPath = global.getFilesDir().getPath() + "/sentencepiece_bpe.model";
            embedAndLmHeadPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/NLLB" + "/nllb_embed_and_lm_head_4bit.onnx";
            cacheInitializerPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/NLLB" + "/nllb_cache_initializer_4bit.onnx";*/
        }else if(mode == MADLAD || mode == MADLAD_CACHE){  //madlad
            //8 bit
            encoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int8WO/madlad_encoder_8bit.onnx";
            decoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int8WO/madlad_decoder_8bit.onnx";
            vocabPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/spiece.model";
            embedAndLmHeadPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/madlad_embed_8bit.onnx";
            cacheInitializerPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int8WO/madlad_cache_initializer_8bit.onnx";
            //4 bit
            /*encoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int4_16/madlad_encoder_4bit.onnx";
            decoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int4_16/madlad_decoder_4bit.onnx";
            vocabPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/spiece.model";
            embedAndLmHeadPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/madlad_embed_8bit.onnx";
            cacheInitializerPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Madlad" + "/Int4_16/madlad_cache_initializer_4bit.onnx";*/
        }else {  //hy-mt
            decoderPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/HY-MT" + "/model_int8_final.onnx";
            vocabPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/HY-MT" + "/tokenizer.json";
        }

        String finalDecoderPath = decoderPath;
        String finalEncoderPath = encoderPath;
        String finalCacheInitializerPath = cacheInitializerPath;
        String finalEmbedAndLmHeadPath = embedAndLmHeadPath;
        String finalVocabPath = vocabPath;
        final Thread t = new Thread("textTranslation") {
            public void run() {
                onnxEnv = OrtEnvironment.getEnvironment();
                //we transfer the vocab file from the assets to the internal memory (because the tokenizer can open vocab only via a path to internal or external memory)
                File outFile = new File(global.getFilesDir(), "sentencepiece_bpe.model");
                if(!outFile.exists()) {
                    FileTools.copyAssetToInternalMemory(global, "sentencepiece_bpe.model");
                }

                CustomLocale firstTextLanguage = global.getFirstTextLanguage(true);
                CustomLocale secondTextLanguage = global.getSecondTextLanguage(true);
                CustomLocale firstLanguage = global.getFirstLanguage(true);
                CustomLocale secondLanguage = global.getSecondLanguage(true);

                try {
                    if(!restart){
                        languageResourcesManager = new LanguageResourcesManager(global, mode, firstTextLanguage, secondTextLanguage, firstLanguage, secondLanguage);
                    }

                    if(mode == MADLAD || mode == MADLAD_CACHE) {
                        tokenizer = new Tokenizer(finalVocabPath, Tokenizer.MADLAD);
                    }else if(mode == NLLB || mode == NLLB_CACHE) {
                        tokenizer = new Tokenizer(finalVocabPath, Tokenizer.NLLB);
                    }else if(mode == HY_MT) {
                        tokenizer = new Tokenizer(finalVocabPath, Tokenizer.HY_MT);
                    }

                    if(mode == MOZILLA) {
                        if(restart) languageResourcesManager.loadAllMozillaResources();
                    }else{
                        final OrtSession.SessionOptions.OptLevel optDefaultLevel = OrtSession.SessionOptions.OptLevel.EXTENDED_OPT;
                        boolean arena = true;

                        OrtSession.SessionOptions decoderOptions = new OrtSession.SessionOptions();
                        decoderOptions.setMemoryPatternOptimization(arena);
                        decoderOptions.setCPUArenaAllocator(arena);
                        decoderOptions.setOptimizationLevel(optDefaultLevel);
                        decoderSession = onnxEnv.createSession(finalDecoderPath, decoderOptions);

                        OrtSession.SessionOptions encoderOptions = new OrtSession.SessionOptions();
                        encoderOptions.setMemoryPatternOptimization(arena);
                        encoderOptions.setCPUArenaAllocator(arena);
                        encoderOptions.setOptimizationLevel(optDefaultLevel);
                        if(mode != HY_MT) encoderSession = onnxEnv.createSession(finalEncoderPath, encoderOptions);

                        OrtSession.SessionOptions cacheInitOptions = new OrtSession.SessionOptions();
                        cacheInitOptions.setMemoryPatternOptimization(arena);
                        cacheInitOptions.setCPUArenaAllocator(arena);
                        cacheInitOptions.setOptimizationLevel(optDefaultLevel);
                        if(mode != HY_MT) cacheInitSession = onnxEnv.createSession(finalCacheInitializerPath, cacheInitOptions);

                        OrtSession.SessionOptions embedAndLmHeadOptions = new OrtSession.SessionOptions();
                        embedAndLmHeadOptions.setMemoryPatternOptimization(arena);
                        embedAndLmHeadOptions.setCPUArenaAllocator(arena);
                        embedAndLmHeadOptions.setOptimizationLevel(optDefaultLevel);
                        if (mode == MADLAD_CACHE) {
                            embedSession = onnxEnv.createSession(finalEmbedAndLmHeadPath, embedAndLmHeadOptions);
                        } else if(mode != HY_MT){
                            embedAndLmHeadSession = onnxEnv.createSession(finalEmbedAndLmHeadPath, embedAndLmHeadOptions);
                        }

                        decoderOptions.close();
                        encoderOptions.close();
                        cacheInitOptions.close();
                        embedAndLmHeadOptions.close();
                    }

                    mainHandler.post(() -> initListener.onSuccess());

                } catch (Exception e) {
                    e.printStackTrace();
                    mainHandler.post(() -> initListener.onFailure(new int[]{ErrorCodes.ERROR_LOADING_MODEL},0));
                }
            }
        };
        t.start();
    }

    private void destroy(GeneralListener listener){
        final Thread t = new Thread("textTranslation") {
            public void run() {
                try {
                    if (mode == NLLB || mode == NLLB_CACHE || mode == MADLAD || mode == MADLAD_CACHE) {
                        encoderSession.close();
                        decoderSession.close();
                        cacheInitSession.close();
                        if (mode == MADLAD_CACHE) {
                            embedSession.close();
                        } else {
                            embedAndLmHeadSession.close();
                        }
                        mainHandler.post(() -> listener.onSuccess());
                    } else if(mode == HY_MT) {
                        decoderSession.close();
                    } else if(mode == MOZILLA){
                        unloadAllMozillaResources(listener);
                    }
                    onnxEnv.close();
                } catch (OrtException e) {
                    e.printStackTrace();
                    mainHandler.post(() -> listener.onFailure(new int[]{ErrorCodes.ERROR_LOADING_MODEL},0));
                }
            }
        };
        t.start();
    }

    public void restart(int mode, GeneralListener listener){
        destroy(new GeneralListener() {
            @Override
            public void onSuccess() {
                initialize(global, mode, true, new GeneralListener() {
                    @Override
                    public void onSuccess() {
                        Translator.this.mode = mode;
                        listener.onSuccess();
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        listener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                listener.onFailure(reasons, value);
            }
        });
    }

    public void translate(final String textToTranslate, final CustomLocale languageInput, final CustomLocale languageOutput, int beamSize, boolean saveResults) {
        final Thread t = new Thread("textTranslation") {
            public void run() {
                translating = true;
                performTextTranslation(textToTranslate, languageInput, languageOutput, beamSize, saveResults, null);
                translating = false;
            }
        };
        t.start();
    }

    public void translate(final String textToTranslate, final CustomLocale languageInput, final CustomLocale languageOutput, int beamSize, boolean saveResults, final TranslateListener responseListener) {
        final Thread t = new Thread("textTranslation") {
            public void run() {
                performTextTranslation(textToTranslate, languageInput, languageOutput, beamSize, saveResults, responseListener);
            }
        };
        t.start();
    }

    public abstract static class TranslateListener extends TranslatorListener {
        public static enum ResultType {
            NORMAL,
            TATOEBA,
            DICTIONARY
        }
        public abstract void onTranslatedText(String textToTranslate, String TranslatedText, @Nullable String[] synonyms, long resultID, boolean isFinal, ResultType resultType, CustomLocale languageOfText);
    }

    public void translateMessage(final ConversationMessage conversationMessageToTranslate, final CustomLocale languageOutput, int beamSize, final TranslateMessageListener responseListener) {  // what the thread does
        Thread t = new Thread("messageTranslationPerformer") {
            public void run() {
                synchronized (lock) {
                    dataToTranslate.addLast(new DataContainer(conversationMessageToTranslate, languageOutput, beamSize, responseListener));
                    if (dataToTranslate.size() >= 1 && !translatingMessages) {
                        translateMessage();
                    }
                }
            }
        };
        t.start();
    }

    private void translateMessage(){
        translatingMessages = true;
        Translator.DataContainer data = dataToTranslate.pollFirst();
        if(data != null) {
            final String text = data.conversationMessageToTranslate.getPayload().getText();
            final CustomLocale languageInput = data.conversationMessageToTranslate.getPayload().getLanguage();
            if (!languageInput.equals(data.languageOutput)) {
                Peer sender = data.conversationMessageToTranslate.getSender();
                loadSrcLangResourcesForPeer(languageInput, sender, new GeneralListener() {
                    @Override
                    public void onSuccess() {
                        performTextTranslation(text, languageInput, data.languageOutput, data.beamSize, false, new TranslateListener() {
                            @Override
                            public void onTranslatedText(String textToTranslate, String text, String[] synonyms, long resultID, boolean isFinal, ResultType resultType, CustomLocale languageOfText) {
                                data.conversationMessageToTranslate.getPayload().setText(text);
                                data.conversationMessageToTranslate.getPayload().setLanguage(data.languageOutput);
                                mainHandler.post(() -> data.responseListener.onTranslatedMessage(data.conversationMessageToTranslate, resultID, isFinal));
                                //we translate the next message in the queue
                                if (dataToTranslate.size() >= 1) {
                                    translateMessage();
                                } else {
                                    translatingMessages = false;
                                }
                            }

                            @Override
                            public void onFailure(int[] reasons, long value) {
                                data.responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0);

                                //we translate the next message in the queue
                                if (dataToTranslate.size() >= 1) {
                                    translateMessage();
                                } else {
                                    translatingMessages = false;
                                }
                            }
                        });
                    }
                });
            } else {  // means that the language to be translated corresponds to ours
                data.responseListener.onTranslatedMessage(data.conversationMessageToTranslate, incrementCurrentResultID(), true);
                //we translate the next message in the queue
                if (dataToTranslate.size() >= 1) {
                    translateMessage();
                } else {
                    translatingMessages = false;
                }
            }
        }else{
            //we translate the next message in the queue
            if (dataToTranslate.size() >= 1) {
                translateMessage();
            } else {
                translatingMessages = false;
            }
        }
    }

    public abstract static class TranslateMessageListener extends TranslatorListener {
        public abstract void onTranslatedMessage(ConversationMessage conversationMessage, long messageID, boolean isFinal);
    }

    private static class DataContainer{
        private ConversationMessage conversationMessageToTranslate;
        private CustomLocale languageOutput;
        private int beamSize;
        private final TranslateMessageListener responseListener;


        private DataContainer(ConversationMessage conversationMessageToTranslate, CustomLocale languageOutput, int beamSize, TranslateMessageListener responseListener){
            this.conversationMessageToTranslate = conversationMessageToTranslate;
            this.languageOutput = languageOutput;
            this.responseListener = responseListener;
            this.beamSize = beamSize;
        }
    }

    public void setLastInputText(@Nullable GuiMessage lastInputText){
        this.lastInputText = lastInputText;
    }

    @Nullable
    public GuiMessage getLastInputText() {
        return lastInputText;
    }

    @Nullable
    public GuiMessage getLastOutputText() {
        return lastOutputText;
    }

    public void resetLastOutput(){
        lastOutputText = null;
    }

    public void resetLastInputOutput(){
        lastInputText = null;
        lastOutputText = null;
    }

    public boolean isTranslating(){
        return translating;
    }

    public void detectLanguage(final NeuralNetworkApiResult result, boolean forceResult, final DetectLanguageListener responseListener) {
        float confidenceThreshold = 0.5F;
        if(forceResult){
            confidenceThreshold = 0.01F;
        }
        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(new LanguageIdentificationOptions.Builder().setConfidenceThreshold(confidenceThreshold).build());
        languageIdentifier.identifyLanguage(result.getText())
                .addOnSuccessListener(
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(@Nullable String languageCode) {
                                if (languageCode == null || languageCode.equals("und")) {
                                    responseListener.onFailure(new int[ErrorCodes.LANGUAGE_UNKNOWN], 0);
                                    Log.i("language detection", "Can't identify language.");
                                } else {
                                    result.setLanguage(new CustomLocale(languageCode));
                                    responseListener.onDetectedText(result);
                                    Log.i("language detection", "Language: " + languageCode);
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be loaded or other internal error.
                                e.printStackTrace();
                                responseListener.onFailure(new int[ErrorCodes.ERROR_LOADING_MODEL], 0);
                            }
                        });
    }

    public void detectLanguage(final NeuralNetworkApiResult firstResult, final NeuralNetworkApiResult secondResult, boolean forceResult, final DetectMultiLanguageListener responseListener) {
        float confidenceThreshold = 0.5F;
        if(forceResult){
            confidenceThreshold = 0.01F;
        }
        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(new LanguageIdentificationOptions.Builder().setConfidenceThreshold(confidenceThreshold).build());
        languageIdentifier.identifyLanguage(firstResult.getText())
                .addOnSuccessListener(
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(@Nullable String languageCode) {
                                boolean firstResultFailed = false;
                                if (languageCode == null || languageCode.equals("und")) {
                                    firstResultFailed = true;
                                    Log.i("language detection", "Can't identify language.");
                                } else {
                                    firstResult.setLanguage(new CustomLocale(languageCode));
                                    Log.i("language detection", "Language: " + languageCode);
                                }
                                detectSecondLanguage(firstResult, secondResult, forceResult, firstResultFailed, responseListener);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Model couldn’t be loaded or other internal error.
                                e.printStackTrace();
                                detectSecondLanguage(firstResult, secondResult, forceResult, true, responseListener);
                            }
                        });
    }

    private void detectSecondLanguage(final NeuralNetworkApiResult firstResult, final NeuralNetworkApiResult secondResult, boolean forceResult, boolean firstResultFailed, final DetectMultiLanguageListener responseListener){
        float confidenceThreshold = 0.5F;
        if(forceResult){
            confidenceThreshold = 0.01F;
        }
        LanguageIdentifier languageIdentifier = LanguageIdentification.getClient(
                new LanguageIdentificationOptions.Builder().setConfidenceThreshold(confidenceThreshold).build());
        languageIdentifier.identifyLanguage(secondResult.getText())
                .addOnSuccessListener(new OnSuccessListener<String>() {
                    @Override
                    public void onSuccess(String languageCode) {
                        if (languageCode == null || languageCode.equals("und")) {  //detection of second result failed
                            Log.i("language detection", "Can't identify language.");
                            if (firstResultFailed) {  //detection of first result failed
                                responseListener.onFailure(new int[]{ErrorCodes.BOTH_RESULTS_FAIL}, 0);
                            }else{   //detection of first result success
                                responseListener.onDetectedText(firstResult, secondResult, ErrorCodes.SECOND_RESULT_FAIL);
                            }
                        }else{  //detection of second result success
                            Log.i("language detection", "Language: " + languageCode);
                            secondResult.setLanguage(new CustomLocale(languageCode));
                            if (firstResultFailed) {  //detection of first result failed
                                responseListener.onDetectedText(firstResult, secondResult, ErrorCodes.FIRST_RESULT_FAIL);
                            }else{    //detection of first result success
                                responseListener.onDetectedText(firstResult, secondResult, ErrorCodes.BOTH_RESULTS_SUCCESS);
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {  //detection of second result failed
                        if (firstResultFailed) {  //detection of first result failed
                            responseListener.onFailure(new int[]{ErrorCodes.BOTH_RESULTS_FAIL}, 0);
                        }else{
                            responseListener.onDetectedText(firstResult, secondResult, ErrorCodes.SECOND_RESULT_FAIL);
                        }
                    }
                });
    }

    public abstract static class DetectLanguageListener extends TranslatorListener {
        public abstract void onDetectedText(NeuralNetworkApiResult result);
    }

    public abstract static class DetectMultiLanguageListener extends TranslatorListener {
        public abstract void onDetectedText(NeuralNetworkApiResult firstResult, NeuralNetworkApiResult secondResult, int message);
    }

    public void loadLanguageResources(@NonNull CustomLocale srcLang, @NonNull CustomLocale tgtLang, Global.RTranslatorMode rtranslatorMode, @Nullable GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of language resource loading
                    languageResourcesManager.loadLanguageResources(srcLang, tgtLang, rtranslatorMode);
                    //we notify the success of the loading
                    if(listener != null) mainHandler.post(() -> listener.onSuccess());
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void loadSrcLangResourcesForPeer(CustomLocale lang, Peer peer, @Nullable GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of language resource loading
                    languageResourcesManager.loadSrcLangResourcesForPeer(lang, peer);
                    //we notify the success of the loading
                    if(listener != null) mainHandler.post(() -> listener.onSuccess());
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void loadTgtLangResourcesForConversation(CustomLocale lang, @Nullable GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of language resource loading
                    languageResourcesManager.loadTgtLangResourcesForConversation(lang);
                    //we notify the success of the loading
                    if(listener != null) mainHandler.post(() -> listener.onSuccess());
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void updatePeer(Peer oldPeer, Peer newPeer){
        languageResourcesManager.updatePeer(oldPeer, newPeer);
    }

    public void loadAllMozillaResources(GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of the resources unloading
                    languageResourcesManager.loadAllMozillaResources();
                    if(listener != null) listener.onSuccess();
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void loadAllTatoebaResources(GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of the resources unloading
                    languageResourcesManager.loadAllTatoebaResources();
                    if(listener != null) listener.onSuccess();
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void loadAllTranslationDictionariesResources(GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of the resources unloading
                    languageResourcesManager.loadAllTranslationDictionariesResources();
                    if(listener != null) listener.onSuccess();
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void unloadAllMozillaResources(GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                //execution of the resources unloading
                languageResourcesManager.unloadAllMozillaResources();
                if(listener != null) listener.onSuccess();
            }
        }).start();
    }

    public void unloadAllTatoebaResources(){
        synchronized (langResourcesLock) {
            //execution of the resources unloading
            languageResourcesManager.unloadAllTatoebaResources();
        }
    }

    public void unloadAllTranslationDictionariesResources(){
        synchronized (langResourcesLock) {
            //execution of the resources unloading
            languageResourcesManager.unloadAllTranslationDictionariesResources();
        }
    }

    public void unloadSrcLangResourcesForPeer(Peer peer, @Nullable GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of language resource unloading
                    languageResourcesManager.unloadSrcLangResourcesForPeer(peer);
                    //we notify the success of the unloading
                    if(listener != null) mainHandler.post(() -> listener.onSuccess());
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public void unloadAllLangResourcesForConversation(@Nullable GeneralListener listener){
        new Thread(() -> {
            synchronized (langResourcesLock) {
                try {
                    //execution of language resource unloading
                    languageResourcesManager.unloadAllLangResourcesForConversation();
                    //we notify the success of the unloading
                    if(listener != null) mainHandler.post(() -> listener.onSuccess());
                } catch (Exception e) {
                    Log.e("resources", e.getMessage());
                    if(listener != null) mainHandler.post(() -> listener.onFailure(new int[]{0}, 0)); //todo: implementare una vera gestione degli errori
                }
            }
        }).start();
    }

    public LanguageResourcesManager getLanguageResourcesManager() {
        return languageResourcesManager;
    }

    public int getMode(){
        return mode;
    }

    public void addCallback(final TranslateListener callback) {
        callbacks.add(callback);
    }

    public void removeCallback(TranslateListener callback) {
        callbacks.remove(callback);
    }

    private void notifyResult(String textToTranslate, String text, @Nullable String[] synonyms, long resultID, boolean isFinal, TranslateListener.ResultType resultType, CustomLocale languageOfText) {
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onTranslatedText(textToTranslate, text, synonyms, resultID, isFinal, resultType, languageOfText);
        }
    }

    private void notifyError(int[] reasons, long value) {
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onFailure(reasons, value);
        }
    }

    private void performTextTranslation(final String textToTranslate, final CustomLocale inputLanguage, final CustomLocale outputLanguage, int beamSize, boolean saveResults, @Nullable final TranslateListener responseListener) {
        try {
            long initTime = System.currentTimeMillis();
            String finalResult = null;
            android.util.Log.i("result", "Translation input: " + textToTranslate);
            if(saveResults) {
                lastInputText = new GuiMessage(new Message(global, textToTranslate), false, true);
            }
            boolean isTatoebaResult = false;  //will be true and remain true is one of the splits of the text is translated by tatoeba
            boolean isDictionaryResult = false;  //will be true and remain true only if all the splits of the text are translated by a dictionary
            String[] synonyms = null;

            if(mode != MOZILLA){
                int maxLength = 200;
                if(mode == NLLB || mode == NLLB_CACHE){
                    maxLength = 200;
                }else if(mode == MADLAD || mode == MADLAD_CACHE){
                    maxLength = 200;  //todo: research the best value for madlad
                }else if(mode == HY_MT){
                    maxLength = 5000;   //todo: research the best value for hy-mt
                }
                //we split the input text in sentences
                ArrayList<String> textSplit = new ArrayList<>();
                BreakIterator iterator = BreakIterator.getSentenceInstance(inputLanguage.getLocale());
                iterator.setText(textToTranslate);
                int start = iterator.first();
                for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
                    textSplit.add(textToTranslate.substring(start, end));
                }
                //we rejoin separated substrings whose union does not exceed the maximum input size
                boolean joined = true;
                while (joined) {
                    joined = false;
                    for (int i = 1; i < textSplit.size(); i++) {
                        int numTokens = tokenize(textSplit.get(i - 1), inputLanguage, outputLanguage, true).getInputIDs().length;
                        int numTokens2 = tokenize(textSplit.get(i), inputLanguage, outputLanguage, true).getInputIDs().length;
                        if ((numTokens + numTokens2 < maxLength) || (numTokens2 < 5)) {
                            textSplit.set(i - 1, textSplit.get(i - 1) + textSplit.get(i));
                            textSplit.remove(i);
                            i = i - 1;
                            joined = true;
                        }
                    }
                }
                android.util.Log.i("result", "Input text splitted in " + textSplit.size() + " subtexts:");
                for (String subtext : textSplit) {
                    android.util.Log.i("result", subtext);
                }
                android.util.Log.i("performance", "Text split done in: " + (System.currentTimeMillis() - initTime) + "ms");

                final String[] joinedStringOutput = {""};
                for (int i = 0; i < textSplit.size(); i++) {
                    String finalSplitResult = null;
                    if(global.isUseTranslationDictionaries()) {
                        String[] dictionaryResult = performDictionaryTranslation(textSplit.get(i), inputLanguage, outputLanguage);
                        if(dictionaryResult != null && dictionaryResult.length > 0) {
                            finalSplitResult = dictionaryResult[0];
                            if(textSplit.size() == 1 && dictionaryResult.length > 1){
                                synonyms = new String[dictionaryResult.length-1];
                                System.arraycopy(dictionaryResult, 1, synonyms, 0, dictionaryResult.length - 1);
                            }
                        }
                    }
                    if(finalSplitResult == null) {
                        isDictionaryResult = false;  //if this split is not translated by a dictionary we set isDictionaryResult to false
                        if (global.isUseTatoeba()) {
                            finalSplitResult = performTatoebaTranslation(textSplit.get(i), inputLanguage, outputLanguage);
                        }

                        if (finalSplitResult == null) {
                            //we execute translation of the current split using Neural Network
                            finalSplitResult = performNNTextTranslation(textSplit.get(i), joinedStringOutput, inputLanguage, outputLanguage, beamSize, saveResults, responseListener);
                        } else {
                            isTatoebaResult = true;
                        }
                    }else{
                        //we set isDictionary only if all the previous splits has been translated by the dictionary or if this is the first split
                        if(i==0 || isDictionaryResult){
                            isDictionaryResult = true;
                        }
                    }
                    // join the split result with the previous results
                    if (joinedStringOutput[0].equals("")) {
                        joinedStringOutput[0] = joinedStringOutput[0] + finalSplitResult;
                    } else {
                        joinedStringOutput[0] = joinedStringOutput[0] + " " + finalSplitResult;
                    }
                }
                long time = System.currentTimeMillis();
                //String finalResult = tokenizer.decode(completeOutputArray);
                finalResult = joinedStringOutput[0];
                android.util.Log.i("performance", "Detokenization done in: " + (System.currentTimeMillis() - time) + "ms");
            }else{
                //perform text translation using mozilla models
                if(global.isUseTranslationDictionaries()) {
                    String[] dictionaryResult = performDictionaryTranslation(textToTranslate, inputLanguage, outputLanguage);
                    if(dictionaryResult != null && dictionaryResult.length > 0) {
                        finalResult = dictionaryResult[0];
                        if(dictionaryResult.length > 1){
                            synonyms = new String[dictionaryResult.length-1];
                            System.arraycopy(dictionaryResult, 1, synonyms, 0, dictionaryResult.length - 1);
                        }
                    }
                }
                if(finalResult == null) {
                    if (global.isUseTatoeba()) {
                        finalResult = performTatoebaTranslation(textToTranslate, inputLanguage, outputLanguage);
                    }
                    synchronized (langResourcesLock) {
                        if (finalResult == null) {
                            finalResult = BergamotTranslator.translateMultiple(new String[]{textToTranslate}, inputLanguage, outputLanguage)[0];
                        } else {
                            isTatoebaResult = true;
                        }
                    }
                }else{
                    isDictionaryResult = true;
                }
            }
            android.util.Log.i("performance", "TRANSLATION DONE IN: " + (System.currentTimeMillis() - initTime) + "ms");
            if (saveResults) {
                lastOutputText = new GuiMessage(new Message(global, finalResult), currentResultID, false, true);
            }
            final long currentResultIDCopy = currentResultID;  //we do a copy because otherwise the currentResultID is incremented before notifying the message (due to the notification being executed in the mainThread)
            String finalResultConst = finalResult;
            TranslateListener.ResultType resultType;
            if(isDictionaryResult){
                resultType = TranslateListener.ResultType.DICTIONARY;
            } else if(isTatoebaResult) {
                resultType = TranslateListener.ResultType.TATOEBA;
            } else {
                resultType = TranslateListener.ResultType.NORMAL;
            }
            String[] finalSynonyms = synonyms;
            if (responseListener != null) {
                mainHandler.post(() -> responseListener.onTranslatedText(textToTranslate, finalResultConst, finalSynonyms, currentResultIDCopy, true, resultType, outputLanguage));
            } else {
                mainHandler.post(() -> notifyResult(textToTranslate, finalResultConst, finalSynonyms, currentResultIDCopy, true, resultType, outputLanguage));
            }
            currentResultID++;
        } catch (Exception e) {
            e.printStackTrace();
            if (responseListener != null) {
                mainHandler.post(() -> responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            } else {
                mainHandler.post(() -> notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            }
        }
    }

    @Nullable
    private String performTatoebaTranslation(String text, CustomLocale inputLanguage, CustomLocale outputLanguage){
        synchronized (langResourcesLock) {
            LinksData.DataMap linksContainer = languageResourcesManager.getTatoebaLinks().getOrDefault(inputLanguage.getISO3Language() + "-" + outputLanguage.getISO3Language(), null);
            String normalizedText = TextTools.normalizeText(text);
            String hash = Tools.shake256Hex(normalizedText, 8);
            if (linksContainer != null) {
                long initTime = System.currentTimeMillis();
                LinksData.PairList links = linksContainer.getDataMap().getOrDefault(hash, null);
                if (links != null) {
                    int[] srcIds = new int[links.getItemsCount()];
                    int[] tgtIds = new int[links.getItemsCount()];
                    int count = 0;
                    for (LinksData.Pair pair : links.getItemsList()) {
                        srcIds[count] = pair.getSrcSentence();
                        tgtIds[count] = pair.getTgtSentence();
                        count++;
                    }
                    String[] srcSentences = languageResourcesManager.getTatoebaDb().getSentences(srcIds);
                    for (int j = 0; j < srcSentences.length; j++) {
                        if (normalizedText.equalsIgnoreCase(TextTools.normalizeText(srcSentences[j]))) {
                            String textResult = languageResourcesManager.getTatoebaDb().getSentence(tgtIds[j]);
                            //mainHandler.post(() -> Toast.makeText(global, "Tatoeba sentence found: " + textResult, Toast.LENGTH_SHORT).show());
                            android.util.Log.i("status_tatoeba", "Tatoeba sentence found: " + textResult);
                            android.util.Log.i("performance_tatoeba", "TATOEBA SEARCH DONE IN: " + (System.currentTimeMillis() - initTime) + "ms");
                            return textResult;
                        }
                    }
                } else {
                    //mainHandler.post(() -> Toast.makeText(global, "Tatoeba sentence not found", Toast.LENGTH_SHORT).show());
                    android.util.Log.i("status_tatoeba", "Tatoeba sentence not found");
                }
                android.util.Log.i("performance_tatoeba", "TATOEBA SEARCH DONE IN: " + (System.currentTimeMillis() - initTime) + "ms");
            }
        }
        return null;
    }

    @Nullable
    private String[] performDictionaryTranslation(String text, CustomLocale inputLanguage, CustomLocale outputLanguage){
        synchronized (langResourcesLock){
            try {
                return DictionaryTranslator.translateWord(text, inputLanguage, outputLanguage);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private String performNNTextTranslation(final String textToTranslate, String[] joinedStringOutput, final CustomLocale inputLanguage, final CustomLocale outputLanguage, int beamSize, boolean saveResults, @Nullable final TranslateListener responseListener) throws Exception {
        //tokenization
        long time = System.currentTimeMillis();
        TokenizerResult input = null;
        String correctedSubText = correctText(textToTranslate, inputLanguage.getLocale());  //input text pre process
        input = tokenize(correctedSubText, inputLanguage, outputLanguage);
        android.util.Log.i("performance", "Tokenization done in: " + (System.currentTimeMillis() - time) + "ms");
        //encoder execution
        time = System.currentTimeMillis();
        OnnxTensor encoderResult = null;
        if(mode != HY_MT) {
            encoderResult = executeEncoder(input.getInputIDs(), input.getAttentionMask());
            android.util.Log.i("performance", "Encoder done in: " + (System.currentTimeMillis() - time) + "ms");
            if (encoderResult == null) {
                if (responseListener != null) {
                    mainHandler.post(() -> responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
                } else {
                    mainHandler.post(() -> notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
                }
                throw new Exception();
            }
        }
        //decoder execution
        TranslateListener translateListener = new TranslateListener() {
            @Override
            public void onTranslatedText(String textToTranslate, String text, String[] synonyms, long resultID, boolean isFinal, ResultType resultType, CustomLocale languageOfText) {
                //we return the partial results
                String outputText;
                if (joinedStringOutput[0].equals("")) {
                    outputText = joinedStringOutput[0] + text;
                } else {
                    outputText = joinedStringOutput[0] + " " + text;
                }
                if (saveResults) {
                    lastOutputText = new GuiMessage(new Message(global, outputText), currentResultID, false, false);
                }
                final long currentResultIDCopy = currentResultID;  //we do a copy because otherwise the currentResultID is incremented before notifying the message (due to the notification being executed in the mainThread)
                if (responseListener != null) {
                    mainHandler.post(() -> responseListener.onTranslatedText(textToTranslate, outputText, synonyms, currentResultIDCopy, false, resultType, outputLanguage));
                } else {
                    mainHandler.post(() -> notifyResult(textToTranslate, outputText, synonyms, currentResultIDCopy, false, resultType, outputLanguage));
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                //we do not return the partial results and notify an error
                if (responseListener != null) {
                    mainHandler.post(() -> responseListener.onFailure(reasons, value));
                } else {
                    mainHandler.post(() -> notifyError(reasons, value));
                }
            }
        };
        int[] completeOutputArray;
        if (beamSize > 1) {  //beam search
            completeOutputArray = executeCacheDecoder(textToTranslate, input, encoderResult, inputLanguage, outputLanguage, beamSize, translateListener);
        } else {  //greedy search (with kv cache)
            completeOutputArray = executeCacheDecoder(textToTranslate, input, encoderResult, inputLanguage, outputLanguage, 1, translateListener);
        }
        if(encoderResult != null) encoderResult.close();
        if(completeOutputArray != null) {
            return tokenizer.decode(completeOutputArray);
        }else{
            return "";
        }
    }

    @Nullable
    private OnnxTensor executeEncoder(int[] inputIDs, int[] attentionMask){
        try {
            OnnxTensor inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, inputIDs);
            OnnxTensor attentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, attentionMask);
            Map<String,OnnxTensor> input = new HashMap<String,OnnxTensor>();
            Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
            OrtSession.Result embedResult = null;
            if(mode == NLLB_CACHE) {
                //we do the embedding separately and then we pass the result to the encoder
                embedInput.put("input_ids", inputIDsTensor);
                embedInput.put("pre_logits", TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 1, 1024}));
                embedInput.put("use_lm_head", TensorUtils.convertBooleanToTensor(onnxEnv, false));
                ArraySet<String> requestedOutputs = new ArraySet<>();
                requestedOutputs.add("embed_matrix");
                embedResult = embedAndLmHeadSession.run(embedInput, requestedOutputs);

                input.put("input_ids",inputIDsTensor);
                input.put("attention_mask",attentionMaskTensor);
                input.put("embed_matrix", (OnnxTensor) embedResult.get(0));
            }else if(mode == MADLAD_CACHE) {
                //we do the embedding separately and then we pass the result to the encoder
                embedInput.put("input_ids", inputIDsTensor);
                ArraySet<String> requestedOutputs = new ArraySet<>();
                requestedOutputs.add("embed_matrix");
                embedResult = embedSession.run(embedInput, requestedOutputs);

                input.put("input_ids",inputIDsTensor);
                input.put("attention_mask",attentionMaskTensor);
                input.put("embed_matrix", (OnnxTensor) embedResult.get(0));
            }else{
                input.put("input_ids",inputIDsTensor);
                input.put("attention_mask",attentionMaskTensor);
            }
            OrtSession.Result result = encoderSession.run(input);
            if(embedResult != null){
                embedResult.close();
            }
            Optional<OnnxValue> output = result.get("last_hidden_state");

            //release memory
            inputIDsTensor.close();
            attentionMaskTensor.close();
            embedInput.forEach((s, onnxTensor) -> onnxTensor.close());
            input.forEach((s, onnxTensor) -> onnxTensor.close());

            return (OnnxTensor) output.get();
        } catch (OrtException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public int[] executeCacheDecoder(String textToTranslate, TokenizerResult input, @Nullable OnnxTensor encoderResult, final CustomLocale inputLanguage, final CustomLocale outputLanguage, int beamSize, @Nullable final TranslateListener responseListener) {
        int eos;
        if(mode == HY_MT){
            eos = tokenizer.PieceToID("<｜hy_place▁holder▁no▁2｜>");
        }else{
            eos = tokenizer.PieceToID("</s>");
        }
        int nLayers;
        int hiddenSize;
        int hiddenSizeAttention;
        int nHeads;
        if(mode == MADLAD_CACHE){
            nLayers = 32;
            hiddenSize = 1024;
            hiddenSizeAttention = 128;
            nHeads = 16;
        }else if(mode == NLLB_CACHE){
            nLayers = 12;
            hiddenSize = 1024;
            hiddenSizeAttention = 64;
            nHeads = 16;
        }else{  //if mode == HY_MT
            nLayers = 32;
            hiddenSize = 2048;
            hiddenSizeAttention = 128;
            nHeads = 4;  //nHeads in this case refers only to the number of heads used in kvCache, the real number of heads are 16, but this model uses group query attention, with a group size of 4
        }
        int initialPromptLength = tokenize(" ", inputLanguage, outputLanguage, false).getInputIDs().length;
        NNResult lastPartialResult = null;

        ArrayList<Integer>[] completeBeamOutput = new ArrayList[beamSize];  //contains the "beamSize" strings produced by the decoder
        for (int j = 0; j < beamSize; j++) {
            completeBeamOutput[j] = new ArrayList<Integer>();
        }
        double[] beamsOutputsProbabilities = new double[beamSize];  //contains for each of the "beamSize" strings produced by the decoder its overall probability
        int patienceLength = Math.round(PATIENCE*beamSize);
        ArrayList<int[]> finishedBeamSentences = new ArrayList<>(patienceLength);  //contains the "PATIENCE*beamSize" sentences finished by the decoder
        ArrayList<Double> finishedBeamSentencesProbabilities = new ArrayList<>(patienceLength);  //contains for each of the "PATIENCE*beamSize" sentences finished by the decoder its overall probability

        try {
            long initialTime;
            long time = System.currentTimeMillis();
            int[] input_ids = new int[beamSize];
            OnnxTensor inputIDsTensor;
            if(mode == MADLAD_CACHE){
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{0});  //for the first iteration we use input_ids = 0, with batch_size = 1
            }else if(mode == NLLB_CACHE){
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{2});  //for the first iteration we use input_ids = 2, with batch_size = 1
            }else{  // if mode == HY_MT
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, input.getInputIDs());  //for the first iteration we use the input_ids generated by the tokenizer (with the prompt), with batch_size = 1
            }
            int[] attentionMask = input.getAttentionMask();
            OnnxTensor attentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, attentionMask);
            CacheContainerNative cacheContainer = null;
            OnnxTensor decoderOutput = null;
            Map<String,OnnxTensor> decoderInput = new HashMap<String,OnnxTensor>();
            float [][][] logits = null;

            time = System.currentTimeMillis();
            //preparing cache initializer input
            OrtSession.Result initResult = null;
            OnnxTensor attentionMaskTensorBatched = null;
            OrtSession.Result initResultBatched = null;
            if(mode != HY_MT) {
                Map<String, OnnxTensor> initInput = new HashMap<String, OnnxTensor>();
                initInput.put("encoder_hidden_states", encoderResult);
                //execution of the cache initializer
                initResult = cacheInitSession.run(initInput);
                android.util.Log.i("performance", "Cache initialization done in: " + (System.currentTimeMillis() - time) + "ms");
                if (encoderResult != null)
                    encoderResult.close();  //we close it because from now on we only need initResult
            }
            //we convert the fixed decoder inputs to have batch_size==beamSize
            attentionMaskTensorBatched = beamSize > 1 ? batchEncoderAttentionMask(attentionMask, beamSize, true) : null;
            initResultBatched = initResult != null && beamSize > 1 ? batchEncoderKvCache(initResult, nLayers, beamSize, true) : null;  //this is not executed for HY_MT

            //we begin the iterative execution of the decoder
            String[] partialResults = new String[beamSize];  //used for log
            OrtSession.Result result = null;
            OrtSession.Result oldResult = null;
            int[] max = new int[beamSize];
            int[][] beamMax = new int[beamSize][beamSize];
            int j = 1;
            OnnxTensor emptyPreLogits = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 1, hiddenSize});
            OnnxTensor emptyPreLogitsBatch = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{beamSize, 1, hiddenSize});
            OnnxTensor emptyInputIds = TensorUtils.createInt64TensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 2});
            OnnxTensor emptyInputIdsBatch = TensorUtils.createInt64TensorWithSingleValue(onnxEnv, 0, new long[]{beamSize, 2});

            boolean stop = false;
            boolean earlyStop = false;

            while(!stop && !earlyStop){
                initialTime = System.currentTimeMillis();
                time = System.currentTimeMillis();

                //we prepare the decoder input
                decoderInput = new HashMap<String,OnnxTensor>();
                OrtSession.Result embedResult = null;
                if(mode == NLLB_CACHE){
                    //we do the embedding separately and then we pass the result to the decoder
                    Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
                    embedInput.put("input_ids", inputIDsTensor);
                    embedInput.put("pre_logits", j == 1 ? emptyPreLogits : emptyPreLogitsBatch);
                    embedInput.put("use_lm_head", TensorUtils.convertBooleanToTensor(onnxEnv, false));
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("embed_matrix");
                    embedResult = embedAndLmHeadSession.run(embedInput, requestedOutputs);

                    decoderInput.put("embed_matrix", (OnnxTensor) embedResult.get(0));
                }
                if(mode == MADLAD_CACHE) {
                    //we do the embedding separately and then we pass the result to the decoder
                    Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
                    embedInput.put("input_ids", inputIDsTensor);
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("embed_matrix");
                    embedResult = embedSession.run(embedInput, requestedOutputs);

                    decoderInput.put("embed_matrix", (OnnxTensor) embedResult.get(0));
                }
                decoderInput.put("input_ids", inputIDsTensor);
                OnnxTensor decoderPastTensor = null;
                if(j == 1){  //if it is the first iteration
                    //we run the decoder with a batch_size = 1
                    if(mode != HY_MT) {
                        decoderInput.put("encoder_attention_mask", attentionMaskTensor);
                    }else{
                        decoderInput.put("attention_mask", attentionMaskTensor);
                    }
                    long[] shape = {1, nHeads, 0, hiddenSizeAttention};
                    decoderPastTensor = TensorUtils.createFloatTensorWithSingleValue(onnxEnv,0, shape);
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + (mode != HY_MT ? ".decoder" : "") + ".key", decoderPastTensor);
                        decoderInput.put("past_key_values." + i + (mode != HY_MT ? ".decoder" : "") + ".value", decoderPastTensor);
                        if(mode != HY_MT) {
                            decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                            decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                        }
                    }
                }else {
                    if(j == 2 && beamSize > 1) {
                        attentionMaskTensor.close();   //we close it because from now on we only need attentionMaskTensorBatched
                        if(initResult != null) initResult.close();     //we close it because from now on we only need initResultBatched
                    }
                    //we run the decoder with batch_size = beamSize
                    if(mode != HY_MT) {
                        decoderInput.put("encoder_attention_mask", beamSize > 1 ? attentionMaskTensorBatched : attentionMaskTensor);
                    }else{
                        decoderInput.put("attention_mask", beamSize > 1 ? attentionMaskTensorBatched : attentionMaskTensor);
                    }
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + (mode != HY_MT ? ".decoder" : "") + ".key", (OnnxTensor) result.get("present." + i + (mode != HY_MT ? ".decoder" : "") + ".key").get());
                        decoderInput.put("past_key_values." + i + (mode != HY_MT ? ".decoder" : "") + ".value", (OnnxTensor) result.get("present." + i + (mode != HY_MT ? ".decoder" : "") + ".value").get());
                        if(mode != HY_MT) {
                            decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) (beamSize > 1 ? initResultBatched : initResult).get("present." + i + ".encoder.key").get());
                            decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) (beamSize > 1 ? initResultBatched : initResult).get("present." + i + ".encoder.value").get());
                        }
                    }
                }
                oldResult = result;
                android.util.Log.i("performance", "pre-execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();

                //decoder execution (with cache)
                result = decoderSession.run(decoderInput);

                if(decoderPastTensor != null) decoderPastTensor.close();
                android.util.Log.i("performance", "execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();

                if(oldResult != null) {
                    oldResult.close(); //serves to release the memory occupied by the result (otherwise it accumulates and increases a lot)
                }
                if(embedResult != null) {
                    embedResult.close();
                }
                android.util.Log.i("performance", "release RAM of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                //we take the logits
                OrtSession.Result lmHeadResult = null;
                if(mode == NLLB_CACHE) {
                    //we execute the lmHead separately to get the logits
                    Map<String, OnnxTensor> lmHeadInput = new HashMap<String, OnnxTensor>();
                    lmHeadInput.put("input_ids", j==1 ? emptyInputIds : emptyInputIdsBatch);
                    lmHeadInput.put("pre_logits", (OnnxTensor) result.get("pre_logits").get());
                    lmHeadInput.put("use_lm_head", TensorUtils.convertBooleanToTensor(onnxEnv, true));
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("logits");
                    lmHeadResult = embedAndLmHeadSession.run(lmHeadInput, requestedOutputs);
                    decoderOutput = (OnnxTensor) lmHeadResult.get(0);
                }else {
                    decoderOutput = (OnnxTensor) result.get("logits").get();
                }
                //we take the logits and the larger "beamSize" values
                logits = (float[][][]) decoderOutput.getValue();
                if(j == 1) {  //if we are at the first iteration
                    if(mode != NLLB && mode != NLLB_CACHE) {
                        if (beamSize > 1) {
                            //based on the logits, we initialize max, completeBeamOutput and beamsOutputsProbabilities
                            initBeamSearchData(logits, beamSize, max, completeBeamOutput, beamsOutputsProbabilities);
                        } else {
                            int seqLen = logits[0].length;
                            max[0] = Utils.getIndexOfLargest(logits[0][seqLen - 1]);
                            completeBeamOutput[0].add(max[0]);
                        }
                    }

                    //we prepare the inputs of the next iteration
                    if(mode == NLLB_CACHE){
                        for(int i=0; i<input_ids.length; i++){
                            input_ids[i] = tokenizer.getLanguageID(getNllbLanguageCode(outputLanguage.getCode()));
                        }
                    }else {
                        input_ids = max;
                    }
                    if(inputIDsTensor != null) inputIDsTensor.close();
                    inputIDsTensor = TensorUtils.createIntTensor(onnxEnv, input_ids, new long[]{beamSize,1});
                    //we convert the cache making it have a batch_size=beamSize ("beamSize" copies of the same cache)
                    result = batchDecoderKvCache(result, decoderOutput, nLayers, beamSize, true);

                }else{
                    if((mode == NLLB || mode == NLLB_CACHE) && j==2) {
                        if (beamSize > 1) {
                            //based on the logits, we initialize max, completeBeamOutput and beamsOutputsProbabilities
                            initBeamSearchData(logits, beamSize, max, completeBeamOutput, beamsOutputsProbabilities);
                        } else {
                            int seqLen = logits[0].length;
                            max[0] = Utils.getIndexOfLargest(logits[0][seqLen - 1]);
                            completeBeamOutput[0].add(max[0]);
                        }
                    }else {
                        if (beamSize > 1) {
                            //based on the logits we update beam search data
                            int[] maxProbabilities = new int[beamSize];
                            int sequenceLength = mode == HY_MT ? attentionMask.length : j;
                            cacheContainer = updateBeamSearchData(logits, beamSize, eos, result, sequenceLength, nLayers, nHeads, hiddenSizeAttention, cacheContainer, maxProbabilities, beamMax, max, completeBeamOutput, beamsOutputsProbabilities, finishedBeamSentences, finishedBeamSentencesProbabilities);
                        } else {
                            int seqLen = logits[0].length;
                            max[0] = Utils.getIndexOfLargest(logits[0][seqLen - 1]);
                            completeBeamOutput[0].add(max[0]);
                        }
                    }

                    //we prepare the inputs of the next iteration
                    input_ids = max;
                    if(inputIDsTensor != null) inputIDsTensor.close();
                    inputIDsTensor = TensorUtils.createIntTensor(onnxEnv, input_ids, new long[]{beamSize,1});
                }
                if(mode == HY_MT) {  //todo: make this part more optimized (measure and increase the speed and efficiency)
                    //we increment the attentionMask size of 1 for the next iteration
                    attentionMask = new int[attentionMask.length + 1];
                    Arrays.fill(attentionMask, 1);
                    if(beamSize > 1) {
                        if(attentionMaskTensorBatched != null) attentionMaskTensorBatched.close();
                        attentionMaskTensorBatched = batchEncoderAttentionMask(attentionMask, beamSize, true);
                    }else{
                        if(attentionMaskTensor != null) attentionMaskTensor.close();
                        attentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, attentionMask);
                    }
                }
                android.util.Log.i("performance", "post-execution of" + j + "th word done in: " + (System.currentTimeMillis() - time) + "ms");
                android.util.Log.i("performance", "Generation of" + j + "th word done in: " + (System.currentTimeMillis() - initialTime) + "ms");
                // we return the partial result with the highest probability
                int indexMaxActive = 0;
                int indexMaxFinished = 0;
                boolean maxSentenceFinished = false;
                double maxActiveNormalizedScore = 0;
                if(beamSize > 1) {
                    indexMaxActive = Utils.getIndexOfLargest(beamsOutputsProbabilities);
                    maxActiveNormalizedScore = normalizeScoreByLength(beamsOutputsProbabilities[indexMaxActive], completeBeamOutput[indexMaxActive].size());
                    if(!finishedBeamSentencesProbabilities.isEmpty()) {
                        indexMaxFinished = Utils.getIndexOfLargest(finishedBeamSentencesProbabilities);
                        if (finishedBeamSentencesProbabilities.get(indexMaxFinished) > maxActiveNormalizedScore) {  //todo: add an eventual margin
                            maxSentenceFinished = true;
                        }
                    }
                }

                int[] outputIDs = completeBeamOutput[indexMaxActive].stream().mapToInt(k -> k).toArray();
                String partialResult;
                double partialResultScore = -Double.MAX_VALUE;
                int[] partialResultIds;
                if(beamSize > 1) {
                    // if the beam search is active, to avoid too many back and forth changes in the partial results, we will prefer to show the continuation of the previous partial result (if present in the results)
                    // unless the new partial result has a much higher score (previous score + PARTIAL_RESULT_MARGIN)
                    boolean surpassedLastScoreMargin = false;
                    if(maxSentenceFinished){
                        surpassedLastScoreMargin = lastPartialResult == null || finishedBeamSentencesProbabilities.get(indexMaxFinished) > lastPartialResult.score + PARTIAL_RESULT_MARGIN;
                    }else{
                        surpassedLastScoreMargin = lastPartialResult == null || maxActiveNormalizedScore > lastPartialResult.score + PARTIAL_RESULT_MARGIN;
                    }

                    if (lastPartialResult != null && !surpassedLastScoreMargin) {
                        // we search if there still is a continuation of the lastPartialResult sentence in the active or finished results
                        int activeIndex = -1;
                        int finishedIndex = -1;
                        for(int i=0; i<completeBeamOutput.length; i++){
                            if(Tools.findArray(completeBeamOutput[i], lastPartialResult.ids) != -1){
                                activeIndex = i;
                            }
                        }
                        for(int i=0; i<finishedBeamSentences.size(); i++){
                            if(Tools.findArray(finishedBeamSentences.get(i), lastPartialResult.ids) != -1){
                                finishedIndex = i;
                            }
                        }
                        if(finishedIndex != -1 && activeIndex != -1){
                            //if the last partial result is in both finished and active sentence we return the one with the highest scores
                            if(finishedBeamSentencesProbabilities.get(finishedIndex) > normalizeScoreByLength(beamsOutputsProbabilities[activeIndex], completeBeamOutput[activeIndex].size())){
                                partialResultIds = finishedBeamSentences.get(finishedIndex);
                                partialResultScore = finishedBeamSentencesProbabilities.get(finishedIndex);
                            }else{
                                partialResultIds = completeBeamOutput[activeIndex].stream().mapToInt(k -> k).toArray();
                                partialResultScore = normalizeScoreByLength(beamsOutputsProbabilities[activeIndex], completeBeamOutput[activeIndex].size());
                            }
                        }else if(finishedIndex != -1) {
                            partialResultIds = finishedBeamSentences.get(finishedIndex);
                            partialResultScore = finishedBeamSentencesProbabilities.get(finishedIndex);
                        }else if(activeIndex != -1) {
                            partialResultIds = completeBeamOutput[activeIndex].stream().mapToInt(k -> k).toArray();
                            partialResultScore = normalizeScoreByLength(beamsOutputsProbabilities[activeIndex], completeBeamOutput[activeIndex].size());
                        }else{
                            //if the last partial result is not in finished or active sentence we return the partial result with top scores regardless of the last one
                            if(maxSentenceFinished) {
                                partialResultIds = finishedBeamSentences.get(indexMaxFinished);
                                partialResultScore = finishedBeamSentencesProbabilities.get(indexMaxFinished);
                            }else{
                                partialResultIds = outputIDs;
                                partialResultScore = maxActiveNormalizedScore;
                            }
                        }
                    } else {
                        if(maxSentenceFinished) {
                            partialResultIds = finishedBeamSentences.get(indexMaxFinished);
                            partialResultScore = finishedBeamSentencesProbabilities.get(indexMaxFinished);

                        }else{
                            partialResultIds = outputIDs;
                            partialResultScore = maxActiveNormalizedScore;
                        }
                    }
                    if(lastPartialResult == null) {
                        lastPartialResult = new NNResult();
                    }
                    lastPartialResult.ids = partialResultIds;
                    lastPartialResult.score = partialResultScore;
                    partialResult = tokenizer.decode(partialResultIds);
                    lastPartialResult.text = partialResult;
                }else{
                    // if beam search is not active we return the only partial result normally
                    partialResult = tokenizer.decode(outputIDs);
                }
                if(responseListener != null) {
                    responseListener.onTranslatedText(textToTranslate, partialResult, null, currentResultID, false, TranslateListener.ResultType.NORMAL, outputLanguage);
                }else {
                    notifyResult(textToTranslate, partialResult, null, currentResultID, false, TranslateListener.ResultType.NORMAL, outputLanguage);
                }
                j++;
                if(beamSize > 1){
                    android.util.Log.i("result ", "Finished sentences:");
                    for (int i=0; i<finishedBeamSentences.size(); i++){
                        android.util.Log.i("result "+i, tokenizer.decode(finishedBeamSentences.get(i))+"  Score: "+finishedBeamSentencesProbabilities.get(i));
                    }
                    android.util.Log.i("result ", "Active Batches:");
                }
                for(int i=0; i<beamSize; i++){
                    partialResults[i] = tokenizer.decode(completeBeamOutput[i].stream().mapToInt(k -> k).toArray());
                    android.util.Log.i("result "+i, partialResults[i]+"  Score: "+beamsOutputsProbabilities[i]);
                }

                if(lmHeadResult != null) lmHeadResult.close();

                //stopping conditions
                if(beamSize == 1){
                    stop = max[0] == eos;
                }else{
                    if(finishedBeamSentences.size() >= patienceLength){
                        stop = true;
                    }
                }

                //early stop if the decoder is generating in loop
                if(input.getInputIDs().length - initialPromptLength > 30){  //if the input is long
                    if(j > 3*input.getInputIDs().length) {
                        earlyStop = true;
                    }
                }else if(input.getInputIDs().length - initialPromptLength > 20){  //if the input is medium length
                    if(j > 4*input.getInputIDs().length){
                        earlyStop = true;
                    }
                }else if(input.getInputIDs().length - initialPromptLength > 10){  //if the input is short
                    if(j > 5*input.getInputIDs().length){
                        earlyStop = true;
                    }
                }else {  //if the input is very short (<= 10 tokens)
                    if(j > 8*input.getInputIDs().length){
                        earlyStop = true;
                    }
                }
            }

            if(result != null) result.close();
            if(initResult != null) initResult.close();
            if(cacheContainer != null) cacheContainer.close();
            if(attentionMaskTensorBatched != null) attentionMaskTensorBatched.close();
            if(initResultBatched != null) initResultBatched.close();
            if(emptyInputIds != null) emptyInputIds.close();
            if(emptyInputIdsBatch != null) emptyInputIdsBatch.close();
            if(emptyPreLogits != null) emptyPreLogits.close();
            if(emptyPreLogitsBatch != null) emptyPreLogitsBatch.close();

            //selection of the best final result
            if(beamSize == 1){
                return completeBeamOutput[0].stream().mapToInt(k -> k).toArray();
            } else {
                if(!finishedBeamSentencesProbabilities.isEmpty()) {
                    //length normalization
                    /*for (int i = 0; i < finishedBeamSentences.size(); i++) {
                        double probability = finishedBeamSentencesProbabilities.get(i);
                        int length = finishedBeamSentences.get(i).length;
                        finishedBeamSentencesProbabilities.set(i, normalizeScoreByLength(probability, length));
                    }*/
                    int indexMaxFinished = Utils.getIndexOfLargest(finishedBeamSentencesProbabilities);
                    if(indexMaxFinished != -1){
                        return finishedBeamSentences.get(indexMaxFinished);
                    }else {
                        return null;
                    }
                }else{
                    //in this case we had an early stopping and 0 finished sentences, so we return just the uncompleted active sentence with the best score
                    int indexMaxActive = Utils.getIndexOfLargest(beamsOutputsProbabilities);
                    if(indexMaxActive != -1){
                        return completeBeamOutput[indexMaxActive].stream().mapToInt(k -> k).toArray();
                    }else{
                        return null;
                    }
                }
            }

        } catch (OrtException | InvocationTargetException | NoSuchMethodException |
                 IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            if(responseListener != null) {
                mainHandler.post(() -> responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            }else{
                mainHandler.post(() -> notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            }
        }
        return null;
    }

    /**
     * Applies the Wu et al. (2016) length penalty dynamically without precomputation.
     *
     * @param logProb The raw, unnormalized log-probability of the sequence
     * @param length  The current length of the sequence
     * @return The normalized score
     */
    public static double normalizeScoreByLength(double logProb, int length) {
        if(LENGTH_ALPHA != 0) {
            // Math optimization: (5 + length)^alpha / (5 + 1)^alpha == ((5 + length) / 6.0)^alpha
            // This eliminates one expensive Math.pow() call completely.
            double base = (5.0 + length) / 6.0;
            // Calculate the actual length penalty
            double penalty = Math.pow(base, LENGTH_ALPHA);
            // Return the normalized log probability
            return logProb / penalty;
        }else{
            return logProb;
        }
    }

    public long incrementCurrentResultID(){
        currentResultID++;
        return currentResultID-1;
    }

    public long getCurrentResultID(){
        return currentResultID;
    }

    private String correctText(String text, Locale locale){
        String correctedText = text;
        String language = locale.getLanguage();
        //we add an eventual period if missing (or in general a terminator symbol)
        if(!language.equals("th")) {
            correctedText = correctedText.trim();   //we remove eventual white space from both ends of the text
            if(correctedText.length() >= 2) {
                if (Character.isLetterOrDigit(correctedText.charAt(correctedText.length() - 1))) {
                    correctedText = correctedText + getSentenceTerminator(locale);
                }
            }
        }
        //for Madlad only, we remove all the control characters (like \n), because those will make the model hallucinate
        if(mode == MADLAD || mode == MADLAD_CACHE){
            correctedText = text.replaceAll("\\R", " ")      // remove all newlines
                    .replaceAll("\\p{Cntrl}", "") // remove other control chars
                    .trim();
        }
        // collapse whitespace
        correctedText = correctedText.replaceAll("\\s+", " ");
        return correctedText;
    }


    private OnnxTensor batchEncoderAttentionMask(int[] attentionMask, int batchSize, boolean log) throws OrtException {
        long time = System.currentTimeMillis();
        int[] encoderMaskFlatBatched = TensorUtils.flattenIntArrayBatched(attentionMask, batchSize);
        OnnxTensor encoderAttentionMaskTensorBatched = TensorUtils.createIntTensor(onnxEnv, encoderMaskFlatBatched, new long[]{batchSize, attentionMask.length});
        encoderMaskFlatBatched = null;  //free the memory
        //System.gc();
        if(log) {
            android.util.Log.i("performance", "Mask batch initialization done in: " + (System.currentTimeMillis() - time) + "ms");
        }
        return encoderAttentionMaskTensorBatched;
    }

    @NonNull
    private OrtSession.Result batchEncoderKvCache(OrtSession.Result result, int nLayers, int batchSize, boolean log) throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException, OrtException {
        long time = System.currentTimeMillis();
        String[] names = new String[2*nLayers];
        OnnxValue[] values = new OnnxValue[2*nLayers];
        boolean[] ownedByResult = new boolean[2*nLayers];
        Arrays.fill(ownedByResult, true);
        String[] suffixes = {"key", "value"};
        long timeExtract = 0;
        long timeBatch = 0;
        long timeCreate = 0;
        int count = 0;
        for (int i = 0; i < nLayers; i++) {
            for (String suffix: suffixes) {
                //System.gc();
                names[count] = "present." + i + ".encoder."+suffix;
                long timeInner = System.currentTimeMillis();
                float[][][] keyValue = ((float[][][][]) TensorUtils.extractValue(result, "present." + i + ".encoder."+suffix))[0];
                timeExtract += System.currentTimeMillis() - timeInner;
                timeInner = System.currentTimeMillis();
                float[][][][] keyValueFlatBatched = TensorUtils.batchTensor(keyValue, batchSize);
                timeBatch += System.currentTimeMillis() - timeInner;
                timeInner = System.currentTimeMillis();
                values[count] = TensorUtils.createFloatTensorOptimized(onnxEnv, keyValueFlatBatched, new long[]{batchSize, keyValue.length, keyValue[0].length, keyValue[0][0].length});;
                timeCreate += System.currentTimeMillis() - timeInner;
                count++;
            }
        }
        //the Result constructor is private but this way we can use it anyway
        Constructor<OrtSession.Result> constructor = OrtSession.Result.class.getDeclaredConstructor(names.getClass(), values.getClass(), ownedByResult.getClass());
        constructor.setAccessible(true);
        OrtSession.Result initResultBatched = constructor.newInstance(names, values, ownedByResult);
        if(log) {
            android.util.Log.i("performance", "InitResult extract done in: " + timeExtract + "ms");
            android.util.Log.i("performance", "InitResult batch done in: " + timeBatch + "ms");
            android.util.Log.i("performance", "InitResult create done in: " + timeCreate + "ms");
            android.util.Log.i("performance", "InitResult batch initialization done in: " + (System.currentTimeMillis() - time) + "ms");
        }

        return initResultBatched;
    }

    private OrtSession.Result batchDecoderKvCache(OrtSession.Result result, OnnxTensor decoderOutput, int nLayers, int batchSize, boolean log) throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException, OrtException {
        long time = System.currentTimeMillis();
        String[] names = new String[2*nLayers+1];
        OnnxValue[] values = new OnnxValue[2*nLayers+1];
        boolean[] ownedByResult = new boolean[2*nLayers+1];
        Arrays.fill(ownedByResult, true);
        names[0] = "logits";
        values[0] = decoderOutput;  //result.get("logits").get();
        String[] suffixes = new String[]{"key", "value"};
        int count = 1;
        for (int i = 0; i < nLayers; i++) {
            for (String suffix: suffixes) {
                names[count] = "present." + i + (mode!=HY_MT ? ".decoder." : ".") + suffix;
                float[][][] keyValue = ((float[][][][]) TensorUtils.extractValue(result, "present." + i + (mode!=HY_MT ? ".decoder." : ".") + suffix))[0];
                float[] keyValueFlatBatched = TensorUtils.flattenFloatArrayBatched(keyValue, batchSize);
                values[count] = TensorUtils.createFloatTensor(onnxEnv, keyValueFlatBatched, new long[]{batchSize, keyValue.length, keyValue[0].length, keyValue[0][0].length});  //todo: evaluate the use of createFloatTensorOptimized
                count++;
            }
        }
        if(log) {
            android.util.Log.i("performance", "Decoder kvCache batch initialization done in: " + (System.currentTimeMillis() - time) + "ms");
        }
        result.close();
        //the Result constructor is private but this way we can use it anyway
        Constructor<OrtSession.Result> constructor = OrtSession.Result.class.getDeclaredConstructor(names.getClass(), values.getClass(), ownedByResult.getClass());
        constructor.setAccessible(true);
        return constructor.newInstance(names, values, ownedByResult);
    }

    private void initBeamSearchData(float [][][] logits, int beamSize, int[] max, ArrayList<Integer>[] completeBeamOutput, double[] beamsOutputsProbabilities){
        //the "beamSize" words with highest probability are inserted into max and added to completeBeamOutput
        int seqLen = logits[0].length;
        ArrayList<Integer> indexesToAvoid = new ArrayList<>();
        for (int i = 0; i < beamSize; i++) {
            max[i] = Utils.getIndexOfLargest(logits[0][seqLen-1], indexesToAvoid);
            indexesToAvoid.add(max[i]);
            completeBeamOutput[i].add(max[i]);
        }
        //we insert the initial probabilities of the "beamSize" output strings into beamsOutputsProbabilities
        for (int i = 0; i < beamSize; i++) {
            float maxLogit = logits[0][seqLen-1][max[i]];
            beamsOutputsProbabilities[i] = maxLogit - Utils.logSumExpFast(logits[0][seqLen-1]);
        }
    }

    private CacheContainerNative updateBeamSearchData(
            float [][][] logits, int beamSize, int eos, OrtSession.Result decoderResult, int sequenceLength, int nLayers, int nHeads, int hiddenSize,
            CacheContainerNative cacheContainer, int[] maxProbabilities, int[][] beamMax, int[] max, ArrayList<Integer>[] completeBeamOutput, double[] beamsOutputsProbabilities,
            ArrayList<int[]> finishedBeamSentences, ArrayList<Double> finishedBeamSentencesProbabilities
    ){
        //for each of the "beamSize" decoder outputs, the "beamSize" words with the highest probability are inserted into beamMax
        for(int k=0; k < beamSize; k++) {
            ArrayList<Integer> indexesToAvoid = new ArrayList<>();
            for (int i = 0; i < beamSize; i++) {
                int seqLen = logits[k].length;
                beamMax[k][i] = Utils.getIndexOfLargest(logits[k][seqLen-1], indexesToAvoid);
                indexesToAvoid.add(beamMax[k][i]);
            }
        }
        //Now beamMax will contain for each decoder output ("beamSize" outputs) the "beamSize" words with highest probability,
        // so for each output we calculate its overall probability for each of its "beamSize" words with highest probability
        long timeSoftmax = System.currentTimeMillis();
        double[] beamsOutputsProbabilitiesTemp = new double[beamSize*beamSize];
        for(int k=0; k < beamSize; k++) {
            //new version of probability calculation (logSumExp)
            int seqLen = logits[k].length;
            double logSumExp = Utils.logSumExpFast(logits[k][seqLen-1]);
            for (int i = 0; i < beamSize; i++) {
                float maxLogit = logits[k][seqLen-1][beamMax[k][i]];
                beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilities[k] + maxLogit - logSumExp;
                if(beamMax[k][i] == eos) {
                    beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilitiesTemp[(k*beamSize)+i] - EOS_PENALTY;
                }else{
                    beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilitiesTemp[(k*beamSize)+i] + WORD_REWARD;
                }
            }
        }
        android.util.Log.i("performance", "softmax done in: " + (System.currentTimeMillis()-timeSoftmax) + "ms");
        // Now we save in maxProbabilities the indices of the "beamSize" words generated by the decoder that have the
        // highest overall probability with their respective output sentences and then we will use them as the next inputs.
        // Plus, we also check if one of these sentences is finished (ends with eos), if so we don't insert its index in maxProbabilities,
        // but the sentence will be inserted in finishedBeamSentences and its probability in finishedBeamSentencesProbabilities
        ArrayList<Integer> indexesToAvoid = new ArrayList<>();
        for(int i=0; i<beamSize; i++){
            int largestIndex = Utils.getIndexOfLargest(beamsOutputsProbabilitiesTemp, indexesToAvoid);
            indexesToAvoid.add(largestIndex);
            // we apply sibling penalty to the remaining sentences
            int token = beamMax[largestIndex/beamSize][largestIndex%beamSize];
            for(int j=0; j<beamsOutputsProbabilitiesTemp.length; j++){
                if(!indexesToAvoid.contains(j)){
                    if(beamMax[j/beamSize][j%beamSize] == token){
                        beamsOutputsProbabilitiesTemp[j] = beamsOutputsProbabilitiesTemp[j] - SIBLING_PENALTY;
                    }
                }
            }
            // we eventually save a finished sentence, or we insert the index in maxProbabilities
            if(beamMax[largestIndex/beamSize][largestIndex%beamSize] == eos) {
                Integer[] sentence = new Integer[completeBeamOutput[largestIndex/beamSize].size()+1];
                completeBeamOutput[largestIndex/beamSize].toArray(sentence);
                sentence[sentence.length-1] = beamMax[largestIndex/beamSize][largestIndex%beamSize];
                finishedBeamSentences.add(Arrays.stream(sentence).mapToInt(Integer::intValue).toArray());
                double score = normalizeScoreByLength(beamsOutputsProbabilitiesTemp[largestIndex], sentence.length);
                finishedBeamSentencesProbabilities.add(score);
                i--; //this way we don't skip this cell of maxProbabilities and we will fill it with the next best unfinished sentence
            } else {
                maxProbabilities[i] = largestIndex;
            }
        }
        // we update the probabilities of the "beamSize" output strings in beamsOutputsProbabilities,
        // and add the "beamSize" words with higher probability (each to its own output string) to completeBeamOutput
        ArrayList<Integer>[] oldCompleteBeamOutput = completeBeamOutput.clone();
        for (int i = 0; i < beamSize; i++) {
            beamsOutputsProbabilities[i] = beamsOutputsProbabilitiesTemp[maxProbabilities[i]];
            completeBeamOutput[i] = (ArrayList<Integer>) oldCompleteBeamOutput[maxProbabilities[i]/beamSize].clone();
            completeBeamOutput[i].add(beamMax[maxProbabilities[i]/beamSize][maxProbabilities[i]%beamSize]);
        }
        // reorder of the kvCache to match the new selected inputs for the next iteration
        long timeCache = System.currentTimeMillis();
        CacheContainerNative oldCache = cacheContainer;
        cacheContainer = new CacheContainerNative(onnxEnv, decoderResult, nLayers, beamSize, nHeads, sequenceLength, hiddenSize, mode);
        if(oldCache != null) {
            oldCache.close();
        }
        android.util.Log.i("performance", "cache creation done in: " + (System.currentTimeMillis()-timeCache) + "ms");
        int[] indexes = new int[beamSize];
        for(int i=0; i<beamSize; i++){
            indexes[i] = maxProbabilities[i]/beamSize;
        }
        timeCache = System.currentTimeMillis();
        cacheContainer.reorder(indexes);
        android.util.Log.i("performance", "cache reorder done in: " + (System.currentTimeMillis()-timeCache) + "ms");
        //insert of the next tokens inputs in max
        for (int i = 0; i < beamSize; i++) {
            max[i] = beamMax[maxProbabilities[i]/beamSize][maxProbabilities[i]%beamSize];
        }
        return cacheContainer;
    }

    private static String getSentenceTerminator(Locale locale) {
        // Assuming most languages use a period (.)
        // Add custom cases for specific languages as needed
        String language = locale.getLanguage();
        switch (language) {
            case "zh": // Chinese
            case "ja": // Japanese
            case "ko": // Korean
                return "。"; // Ideographic full stop
            case "hi": // Hindi
                return "।";
            case "my": // Burmese
                return "။"; // Burmese full stop
            // Add other cases as needed for more languages
            default:
                return ".";
        }
    }

    private TokenizerResult tokenize(String text, final CustomLocale inputLanguage, final CustomLocale outputLanguage){
        if (mode == MADLAD_CACHE || mode == MADLAD) {
            return tokenizer.tokenize(text, inputLanguage.getCode(), outputLanguage.getCode());
        } else if(mode == NLLB_CACHE || mode == NLLB){
            return tokenizer.tokenize(text, getNllbLanguageCode(inputLanguage.getCode()), getNllbLanguageCode(outputLanguage.getCode()));
        }else{   //if mode == HY_MT
            return tokenizer.tokenize(text, inputLanguage.getCode(), outputLanguage.getCode(), getHyLanguageInfo(inputLanguage.getCode()), getHyLanguageInfo(outputLanguage.getCode()), false);
        }
    }

    private TokenizerResult tokenize(String text, final CustomLocale inputLanguage, final CustomLocale outputLanguage, boolean excludePrompt){
        if (mode == MADLAD_CACHE || mode == MADLAD) {
            return tokenizer.tokenize(text, inputLanguage.getCode(), outputLanguage.getCode());
        } else if(mode == NLLB_CACHE || mode == NLLB){
            return tokenizer.tokenize(text, getNllbLanguageCode(inputLanguage.getCode()), getNllbLanguageCode(outputLanguage.getCode()));
        }else{   //if mode == HY_MT
            return tokenizer.tokenize(text, inputLanguage.getCode(), outputLanguage.getCode(), getHyLanguageInfo(inputLanguage.getCode()), getHyLanguageInfo(outputLanguage.getCode()), excludePrompt);
        }
    }


    private void initializeNllbLanguagesCodes(Context context){
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(context.getResources().openRawResource(R.raw.nllb_supported_languages_all));
            NodeList listCode = document.getElementsByTagName("code");
            NodeList listCodeNllb = document.getElementsByTagName("code_NLLB");
            for (int i = 0; i < listCode.getLength(); i++) {
                nllbLanguagesCodes.put(listCode.item(i).getTextContent(), listCodeNllb.item(i).getTextContent());
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void initializeHyLanguagesInfo(Context context){
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(context.getResources().openRawResource(R.raw.hy_mt_supported_languages));
            NodeList listCode = document.getElementsByTagName("code");
            NodeList listEnNames = document.getElementsByTagName("en_name");
            NodeList listZhNames = document.getElementsByTagName("zh_name");
            for (int i = 0; i < listCode.getLength(); i++) {
                hyLanguagesInfo.put(listCode.item(i).getTextContent(), new HyLanguageInfo(listEnNames.item(i).getTextContent(), listZhNames.item(i).getTextContent()));
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private String getNllbLanguageCode(String languageCode){
        String nllbCode = nllbLanguagesCodes.get(languageCode);
        if (nllbCode == null) {
            Log.e("error", "Error Converting Language code " + languageCode + " to NLLB code");
            return languageCode;
        } else {
            return nllbCode;
        }
    }

    private HyLanguageInfo getHyLanguageInfo(String languageCode){
        HyLanguageInfo hyLanguageInfo = hyLanguagesInfo.get(languageCode);
        if (hyLanguageInfo == null) {
            Log.e("error", "Error Converting Language code " + languageCode + " to HY language info");
            return new HyLanguageInfo(languageCode, languageCode);
        } else {
            return hyLanguageInfo;
        }
    }


    public static ArrayList<CustomLocale> getSupportedLanguages(Context context, int mode) {
        ArrayList<CustomLocale> languages = new ArrayList<>();
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        boolean qualityLow = sharedPreferences.getBoolean("languagesNNQualityLow", false);
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            if(mode != MOZILLA) {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document document = null;
                if (mode == MADLAD || mode == MADLAD_CACHE) {
                    if (!qualityLow) {
                        document = documentBuilder.parse(context.getResources().openRawResource(R.raw.madlad_supported_launguages));
                    }else{
                        document = documentBuilder.parse(context.getResources().openRawResource(R.raw.madlad_supported_launguages_all));
                    }
                } else if (mode == NLLB || mode == NLLB_CACHE) {
                    if (!qualityLow) {
                        document = documentBuilder.parse(context.getResources().openRawResource(R.raw.nllb_supported_languages));
                    } else {
                        document = documentBuilder.parse(context.getResources().openRawResource(R.raw.nllb_supported_languages_all));
                    }
                }else if (mode == HY_MT) {
                    document = documentBuilder.parse(context.getResources().openRawResource(R.raw.hy_mt_supported_languages));
                }
                NodeList list = document.getElementsByTagName("code");
                for (int i = 0; i < list.getLength(); i++) {
                    languages.add(CustomLocale.getInstance(list.item(i).getTextContent()));
                }
            }else{
                for(String lang: mozillaLanguages){
                    languages.add(new CustomLocale(lang));
                }
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return languages;
    }

    public static class HyLanguageInfo {
        public String enName;
        public String zhName;

        public HyLanguageInfo(String enName, String zhName) {
            this.enName = enName;
            this.zhName = zhName;
        }
    }

    public static class TatoebaLinksContainer {
        @Nullable
        public LinksData.DataMap links;
        public ArrayList<Global.RTranslatorMode> modes;

        public TatoebaLinksContainer(@Nullable LinksData.DataMap links, ArrayList<Global.RTranslatorMode> modes){
            this.links = links;
            this.modes = modes;
        }
    }

    public static class NNResult {
        public String text = null;
        public int[] ids = null;
        public double score = -Double.MAX_VALUE;
        public int index = -1;
        public boolean isFinished = false;
    }

    private static abstract class TranslatorListener {
        public void onFailure(int[] reasons, long value){}
    }

    public static abstract class GeneralListener extends TranslatorListener {
        public abstract void onSuccess();
    }
}