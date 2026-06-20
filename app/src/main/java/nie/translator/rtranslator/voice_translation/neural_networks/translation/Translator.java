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
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.ErrorCodes;
import nie.translator.rtranslator.tools.FileTools;
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
    private final int mode;
    private Tokenizer tokenizer;
    private OrtEnvironment onnxEnv;
    private OrtSession encoderSession;
    private OrtSession decoderSession;
    private OrtSession cacheInitSession;
    private OrtSession embedAndLmHeadSession;
    private OrtSession embedSession;
    private Map<String, String> nllbLanguagesCodes = new HashMap<String, String>();
    private static final double EOS_PENALTY = 0.9;
    @Nullable
    private GuiMessage lastInputText;
    @Nullable
    private GuiMessage lastOutputText;
    private long currentResultID = 0;
    private ArrayList<TranslateListener> callbacks = new ArrayList<>();
    private android.os.Handler mainHandler;   // handler that can be used to post to the main thread
    private ArrayDeque<DataContainer> dataToTranslate = new ArrayDeque<>();
    private final Object lock = new Object();
    private final int EMPTY_BATCH_SIZE = 1;
    private boolean translatingMessages = false;
    private boolean translating = false;


    public Translator(@NonNull Global global, int mode, InitListener initListener) {
        this.global = global;
        this.mode = mode;
        mainHandler = new android.os.Handler(Looper.getMainLooper());
        initializeNllbLanguagesCodes(global);

        String encoderPath = global.getFilesDir().getPath() + "/NLLB_encoder.onnx";
        String decoderPath = global.getFilesDir().getPath() + "/NLLB_decoder.onnx";
        String vocabPath = global.getFilesDir().getPath() + "/sentencepiece_bpe.model";
        String embedAndLmHeadPath = global.getFilesDir().getPath() + "/NLLB_embed_and_lm_head.onnx";
        String cacheInitializerPath = global.getFilesDir().getPath() + "/NLLB_cache_initializer.onnx";

        final Thread t = new Thread("textTranslation") {
            public void run() {
                onnxEnv = OrtEnvironment.getEnvironment();
                //we transfer the vocab file from the assets to the internal memory (because the tokenizer can open vocab only via a path to internal or external memory)
                File outFile = new File(global.getFilesDir(), "sentencepiece_bpe.model");
                if(!outFile.exists()) {
                    FileTools.copyAssetToInternalMemory(global, "sentencepiece_bpe.model");
                }

                try {
                    OrtSession.SessionOptions decoderOptions = new OrtSession.SessionOptions();
                    decoderOptions.setMemoryPatternOptimization(false);
                    decoderOptions.setCPUArenaAllocator(false);
                    decoderOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                    decoderSession = onnxEnv.createSession(decoderPath, decoderOptions);

                    OrtSession.SessionOptions encoderOptions = new OrtSession.SessionOptions();
                    encoderOptions.setMemoryPatternOptimization(false);
                    encoderOptions.setCPUArenaAllocator(false);
                    encoderOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                    encoderSession = onnxEnv.createSession(encoderPath, encoderOptions);

                    OrtSession.SessionOptions cacheInitOptions = new OrtSession.SessionOptions();
                    cacheInitOptions.setMemoryPatternOptimization(false);
                    cacheInitOptions.setCPUArenaAllocator(false);
                    cacheInitOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                    cacheInitSession = onnxEnv.createSession(cacheInitializerPath, cacheInitOptions);

                    OrtSession.SessionOptions embedAndLmHeadOptions = new OrtSession.SessionOptions();
                    embedAndLmHeadOptions.setMemoryPatternOptimization(false);
                    embedAndLmHeadOptions.setCPUArenaAllocator(false);
                    embedAndLmHeadOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                    if(mode == MADLAD_CACHE) {
                        embedSession = onnxEnv.createSession(embedAndLmHeadPath, embedAndLmHeadOptions);
                    }else {
                        embedAndLmHeadSession = onnxEnv.createSession(embedAndLmHeadPath, embedAndLmHeadOptions);
                    }

                    decoderOptions.close();
                    encoderOptions.close();
                    cacheInitOptions.close();

                    mainHandler.post(() -> initListener.onInitializationFinished());

                } catch (OrtException e) {
                    e.printStackTrace();
                    mainHandler.post(() -> initListener.onError(new int[]{ErrorCodes.ERROR_LOADING_MODEL},0));
                }
                if(mode == MADLAD_CACHE) {
                    tokenizer = new Tokenizer(vocabPath, Tokenizer.MADLAD);
                }else{
                    tokenizer = new Tokenizer(vocabPath, Tokenizer.NLLB);
                }
            }
        };
        t.start();
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

    public interface TranslateListener extends TranslatorListener {
        void onTranslatedText(String textToTranslate, String TranslatedText, long resultID, boolean isFinal, CustomLocale languageOfText);
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
                performTextTranslation(text, languageInput, data.languageOutput, data.beamSize, false, new TranslateListener() {
                    @Override
                    public void onTranslatedText(String textToTranslate, String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
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

    public interface TranslateMessageListener extends TranslatorListener {
        void onTranslatedMessage(ConversationMessage conversationMessage, long messageID, boolean isFinal);
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

    public interface DetectLanguageListener extends TranslatorListener {
        void onDetectedText(NeuralNetworkApiResult result);
    }

    public interface DetectMultiLanguageListener extends TranslatorListener {
        void onDetectedText(NeuralNetworkApiResult firstResult, NeuralNetworkApiResult secondResult, int message);
    }

    public void addCallback(final TranslateListener callback) {
        callbacks.add(callback);
    }

    public void removeCallback(TranslateListener callback) {
        callbacks.remove(callback);
    }

    private void notifyResult(String textToTranslate, String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onTranslatedText(textToTranslate, text, resultID, isFinal, languageOfText);
        }
    }

    private void notifyError(int[] reasons, long value) {
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onFailure(reasons, value);
        }
    }

    private void performTextTranslation(final String textToTranslate, final CustomLocale inputLanguage, final CustomLocale outputLanguage, int beamSize, boolean saveResults, @Nullable final TranslateListener responseListener) {
        long initTime = System.currentTimeMillis();
        android.util.Log.i("result", "Translation input: " + textToTranslate);
        if(saveResults) {
            lastInputText = new GuiMessage(new Message(global, textToTranslate), false, true);
        }

        //we split the input text in sentences
        ArrayList<String> textSplit = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(inputLanguage.getLocale());
        iterator.setText(textToTranslate);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            textSplit.add(textToTranslate.substring(start,end));
        }
        //we rejoin separated substrings whose union does not exceed the maximum input size
        boolean joined = true;
        while (joined) {
            joined = false;
            for (int i = 1; i < textSplit.size(); i++) {
                int numTokens = tokenizer.tokenize(getNllbLanguageCode(inputLanguage.getCode()), getNllbLanguageCode(outputLanguage.getCode()), textSplit.get(i-1)).getInputIDs().length;
                int numTokens2 = tokenizer.tokenize(getNllbLanguageCode(inputLanguage.getCode()), getNllbLanguageCode(outputLanguage.getCode()), textSplit.get(i)).getInputIDs().length;
                if ((numTokens + numTokens2 < 200) || (numTokens2 < 5)) {
                    textSplit.set(i-1, textSplit.get(i-1) + textSplit.get(i));
                    textSplit.remove(i);
                    i = i - 1;
                    joined = true;
                }
            }
        }
        android.util.Log.i("result", "Input text splitted in "+textSplit.size()+" subtexts:");
        for (String subtext : textSplit) {
            android.util.Log.i("result", subtext);
        }
        android.util.Log.i("performance", "Text split done in: " + (System.currentTimeMillis() - initTime) + "ms");

        final String[] joinedStringOutput = {""};
        for(int i=0; i<textSplit.size(); i++) {
            ArrayList<Integer>[] completeBeamOutput = new ArrayList[beamSize];  //contains the "beamSize" strings produced by the decoder
            for (int j = 0; j < beamSize; j++) {
                completeBeamOutput[j] = new ArrayList<Integer>();
            }
            double[] beamsOutputsProbabilities = new double[beamSize];  //contains for each of the "beamSize" strings produced by the decoder its overall probability
            //tokenization
            long time = System.currentTimeMillis();
            TokenizerResult input = null;
            String correctedSubText = correctText(textSplit.get(i), inputLanguage.getLocale());
            if (mode == MADLAD_CACHE) {
                input = tokenizer.tokenize(inputLanguage.getCode(), outputLanguage.getCode(), correctedSubText);
            } else {  //if mode == NLLB_CACHE
                input = tokenizer.tokenize(getNllbLanguageCode(inputLanguage.getCode()), getNllbLanguageCode(outputLanguage.getCode()), correctedSubText);
            }
            android.util.Log.i("performance", "Tokenization done in: " + (System.currentTimeMillis() - time) + "ms");
            //encoder execution
            time = System.currentTimeMillis();
            OnnxTensor encoderResult = executeEncoder(input.getInputIDs(), input.getAttentionMask());
            android.util.Log.i("performance", "Encoder done in: " + (System.currentTimeMillis() - time) + "ms");
            if(encoderResult == null){
                if (responseListener != null) {
                    mainHandler.post(() -> responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
                } else {
                    mainHandler.post(() -> notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
                }
                return;
            }
            //decoder execution
            final int eos = tokenizer.PieceToID("</s>");
            ArrayList<Integer> completeOutput = new ArrayList<Integer>();
            completeOutput.add(0);   //tokenizer.PieceToID("<s>")
            TranslateListener translateListener = new TranslateListener() {
                @Override
                public void onTranslatedText(String textToTranslate, String text, long resultID, boolean isFinal, CustomLocale languageOfText) {
                    //we return the partial results
                    String outputText;
                    if(joinedStringOutput[0].equals("")){
                        outputText = joinedStringOutput[0] + text;
                    } else {
                        outputText = joinedStringOutput[0] + " " + text;
                    }
                    if (saveResults) {
                        lastOutputText = new GuiMessage(new Message(global, outputText), currentResultID, false, false);
                    }
                    final long currentResultIDCopy = currentResultID;  //we do a copy because otherwise the currentResultID is incremented before notifying the message (due to the notification being executed in the mainThread)
                    if (responseListener != null) {
                        mainHandler.post(() -> responseListener.onTranslatedText(textToTranslate, outputText, currentResultIDCopy, false, outputLanguage));
                    } else {
                        mainHandler.post(() -> notifyResult(textToTranslate, outputText, currentResultIDCopy, false, outputLanguage));
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
            if (beamSize > 1) {  //beam search
                executeCacheDecoderBeam(textToTranslate, input, encoderResult, completeBeamOutput, beamsOutputsProbabilities, outputLanguage, beamSize, translateListener);
            } else if (beamSize == 1) {  //greedy search (with kv cache)
                executeCacheDecoderGreedy(textToTranslate, input, encoderResult, completeOutput, outputLanguage, translateListener);
            }
            //we convert the ids of completeOutputs into a string and return it
            encoderResult.close();
            int[] completeOutputArray;
            if (mode == MADLAD_CACHE || mode == NLLB_CACHE && beamSize > 1) {
                int indexMax = 0;
                for (int j = 0; j < beamSize; j++) {
                    indexMax = Utils.getIndexOfLargest(beamsOutputsProbabilities);
                }
                completeOutputArray = completeBeamOutput[indexMax].stream().mapToInt(k -> k).toArray();
            } else {
                completeOutputArray = completeOutput.stream().mapToInt(k -> k).toArray();  //converte completeOutput in un array di int
            }
            String finalSplitResult = tokenizer.decode(completeOutputArray);
            if(joinedStringOutput[0].equals("")){
                joinedStringOutput[0] = joinedStringOutput[0] + finalSplitResult;
            }else {
                joinedStringOutput[0] = joinedStringOutput[0] + " " + finalSplitResult;
            }
        }
        long time = System.currentTimeMillis();
        //String finalResult = tokenizer.decode(completeOutputArray);
        String finalResult = joinedStringOutput[0];
        android.util.Log.i("performance", "Detokenization done in: " + (System.currentTimeMillis() - time) + "ms");
        android.util.Log.i("performance", "TRANSLATION DONE IN: " + (System.currentTimeMillis() - initTime) + "ms");
        if (saveResults) {
            lastOutputText = new GuiMessage(new Message(global, finalResult), currentResultID, false, true);
        }
        final long currentResultIDCopy = currentResultID;  //we do a copy because otherwise the currentResultID is incremented before notifying the message (due to the notification being executed in the mainThread)
        if (responseListener != null) {
            mainHandler.post(() -> responseListener.onTranslatedText(textToTranslate, finalResult, currentResultIDCopy, true, outputLanguage));
        } else {
            mainHandler.post(() -> notifyResult(textToTranslate, finalResult, currentResultIDCopy, true, outputLanguage));
        }
        currentResultID++;
    }

    @Nullable
    private OnnxTensor executeEncoder(int[] inputIDs, int[] attentionMask){
        try {
            OnnxTensor inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, inputIDs);
            OnnxTensor attentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, attentionMask);
            Map<String,OnnxTensor> input = new HashMap<String,OnnxTensor>();
            OrtSession.Result embedResult = null;
            if(mode == NLLB_CACHE) {
                //we do the embedding separately and then we pass the result to the encoder
                Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
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
                Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
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
            //Object value = output.get().getValue();   //utile solo per il debug
            return (OnnxTensor) output.get();
        } catch (OrtException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void executeCacheDecoderGreedy(String textToTranslate, TokenizerResult input, OnnxTensor encoderResult, ArrayList<Integer> completeOutput, final CustomLocale outputLanguage, @Nullable final TranslateListener responseListener){
        try {
            long time = System.currentTimeMillis();
            long initialTime;
            final int eos = tokenizer.PieceToID("</s>");
            int nLayers;
            int hiddenSize;
            if(mode == MADLAD_CACHE){
                nLayers = 32;
                hiddenSize = 128;
            }else{   //if mode == NLLB_CACHE
                nLayers = 12;
                hiddenSize = 64;
            }

            int[] input_ids;
            if(mode == MADLAD_CACHE) {
                input_ids = new int[]{0};
            }else{
                input_ids = new int[]{2};
            }
            OnnxTensor inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, input_ids);
            OnnxTensor encoderAttentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, input.getAttentionMask());
            OnnxTensor decoderOutput = null;
            Map<String,OnnxTensor> decoderInput = new HashMap<String,OnnxTensor>();
            float[][][] value = null;
            float [] outputValues = null;
            int[] outputIDs = null;
            //we prepare the input of the cache initializer
            Map<String,OnnxTensor> initInput = new HashMap<String,OnnxTensor>();
            initInput.put("encoder_hidden_states", encoderResult);
            //cache initializer execution
            OrtSession.Result initResult = null;
                initResult = cacheInitSession.run(initInput);
            android.util.Log.i("performance", "Cache initialization done in: " + (System.currentTimeMillis()-time) + "ms");

            //we begin the iterative execution of the decoder
            OrtSession.Result result = null;
            OrtSession.Result oldResult = null;
            OnnxTensor emptyPreLogits = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 1, 1024});
            OnnxTensor emptyInputIds = TensorUtils.createInt64TensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 2});
            int max = -1;
            int j = 1;
            while(max != eos){
                initialTime = System.currentTimeMillis();
                time = System.currentTimeMillis();
                //we prepare the decoder input
                decoderInput = new HashMap<String,OnnxTensor>();
                decoderInput.put("input_ids", inputIDsTensor);
                decoderInput.put("encoder_attention_mask", encoderAttentionMaskTensor);
                OrtSession.Result embedResult = null;
                if(mode == NLLB_CACHE){
                    //we do the embedding separately and then we pass the result to the encoder
                    Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
                    embedInput.put("input_ids", inputIDsTensor);
                    embedInput.put("pre_logits", emptyPreLogits);
                    embedInput.put("use_lm_head", TensorUtils.convertBooleanToTensor(onnxEnv, false));
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("embed_matrix");
                    embedResult = embedAndLmHeadSession.run(embedInput, requestedOutputs);

                    decoderInput.put("embed_matrix", (OnnxTensor) embedResult.get(0));
                }
                if(mode == MADLAD_CACHE) {
                    Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
                    embedInput.put("input_ids", inputIDsTensor);
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("embed_matrix");
                    embedResult = embedSession.run(embedInput, requestedOutputs);

                    decoderInput.put("embed_matrix", (OnnxTensor) embedResult.get(0));
                    decoderInput.put("encoder_hidden_states", encoderResult);
                }
                if(j == 1){
                    long[] shape = {1, 16, 0, hiddenSize};
                    OnnxTensor decoderPastTensor = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, shape);
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + ".decoder.key", decoderPastTensor);
                        decoderInput.put("past_key_values." + i + ".decoder.value", decoderPastTensor);
                        decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                        decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                    }
                }else {
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + ".decoder.key", (OnnxTensor) result.get("present." + i + ".decoder.key").get());
                        decoderInput.put("past_key_values." + i + ".decoder.value", (OnnxTensor) result.get("present." + i + ".decoder.value").get());
                        decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                        decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                    }
                }
                oldResult = result;
                android.util.Log.i("performance", "pre-execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();
                //decoder execution (with cache)
                result = decoderSession.run(decoderInput);

                android.util.Log.i("performance", "execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();

                if(oldResult != null) {
                    oldResult.close(); //serves to release the memory occupied by the result (otherwise it accumulates and increases a lot)
                    android.util.Log.i("performance", "release RAM of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                }
                if(embedResult != null) {
                    embedResult.close();
                }
                //we take the logits and the max value
                OrtSession.Result lmHeadResult = null;
                if(mode == NLLB_CACHE) {
                    //we execute the lmHead separately to get the logits
                    Map<String, OnnxTensor> lmHeadInput = new HashMap<String, OnnxTensor>();
                    lmHeadInput.put("input_ids", emptyInputIds);
                    lmHeadInput.put("pre_logits", (OnnxTensor) result.get("pre_logits").get());
                    lmHeadInput.put("use_lm_head", TensorUtils.convertBooleanToTensor(onnxEnv, true));
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("logits");
                    lmHeadResult = embedAndLmHeadSession.run(lmHeadInput, requestedOutputs);
                    decoderOutput = (OnnxTensor) lmHeadResult.get(0);
                }else {
                    decoderOutput = (OnnxTensor) result.get("logits").get();
                }
                value = (float[][][]) decoderOutput.getValue();
                outputValues = value[0][0];
                max = Utils.getIndexOfLargest(outputValues);
                completeOutput.add(max);
                if(lmHeadResult != null){
                    lmHeadResult.close();
                }
                //we prepare the inputs of the next iteration
                if(j == 1 && mode == NLLB_CACHE) {
                    input_ids[0] = tokenizer.getLanguageID(getNllbLanguageCode(outputLanguage.getCode()));
                }else{
                    input_ids[0] = max;
                }
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, input_ids);
                android.util.Log.i("performance", "post-execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                android.util.Log.i("performance", "Generation of"+j+"th word done in: " + (System.currentTimeMillis() - initialTime) + "ms");
                //we return the partial result
                outputIDs = completeOutput.stream().mapToInt(i -> i).toArray();
                String partialResult = tokenizer.decode(outputIDs);
                if(responseListener != null) {
                    responseListener.onTranslatedText(textToTranslate, partialResult, currentResultID, false, outputLanguage);
                }else{
                    notifyResult(textToTranslate, partialResult, currentResultID, false, outputLanguage);
                }
                android.util.Log.i("result", partialResult);
                j++;
                //early stop if the decoder is generating in loop
                if(input.getInputIDs().length > 30){  //if the input is long
                    if(j > 3*input.getInputIDs().length) {
                        break;
                    }
                }else if(input.getInputIDs().length > 20){  //if the input is medium length
                    if(j > 4*input.getInputIDs().length){
                        break;
                    }
                }else if(input.getInputIDs().length > 10){  //if the input is short
                    if(j > 5*input.getInputIDs().length){
                        break;
                    }
                }else if(input.getInputIDs().length > 5){  //if the input is very short
                    if(j > 8*input.getInputIDs().length){
                        break;
                    }
                }
            }
            if(result != null) {
                result.close();
            }
            initResult.close();

        } catch (OrtException e) {
            if(responseListener != null) {
                responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0);
            }else{
                notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0);
            }
        }
    }

    // for now beam search is not included (and not updated, so it won't work with the final models) because with this implementation we have random crashes
    public void executeCacheDecoderBeam(String textToTranslate, TokenizerResult input, OnnxTensor encoderResult, ArrayList<Integer>[] completeBeamOutput, double[] beamsOutputsProbabilities, final CustomLocale outputLanguage, int beamSize, @Nullable final TranslateListener responseListener) {
        final int eos = tokenizer.PieceToID("</s>");
        int nLayers;
        int hiddenSize;
        if(mode == MADLAD_CACHE){
            nLayers = 32;
            hiddenSize = 128;
        }else{   //if mode == NLLB_CACHE
            nLayers = 12;
            hiddenSize = 64;
        }

        try {
            long initialTime;
            long time = System.currentTimeMillis();
            int[] input_ids = new int[beamSize];
            OnnxTensor inputIDsTensor;
            if(mode == MADLAD_CACHE){
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{0});  //for the first iteration we use input_ids = 0, with batch_size = 1
            }else{   //if mode == NLLB_CACHE
                inputIDsTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, new int[]{2});  //for the first iteration we use input_ids = 2, with batch_size = 1
            }
            OnnxTensor encoderAttentionMaskTensor = TensorUtils.convertIntArrayToTensor(onnxEnv, input.getAttentionMask());
            int encoderInputIdsLength = input.getInputIDs().length;
            CacheContainerNative cacheContainer = null;
            OnnxTensor decoderOutput = null;
            Map<String,OnnxTensor> decoderInput = new HashMap<String,OnnxTensor>();
            float [][][] outputValues = null;

            time = System.currentTimeMillis();
            //preparing cache initializer input
            Map<String,OnnxTensor> initInput = new HashMap<String,OnnxTensor>();
            initInput.put("encoder_hidden_states", encoderResult);
            //execution of the cache initializer
            OrtSession.Result initResult = cacheInitSession.run(initInput);
            android.util.Log.i("performance", "Cache initialization done in: " + (System.currentTimeMillis()-time) + "ms");

            time = System.currentTimeMillis();
            //we convert the fixed decoder inputs to have batch_size==beamSize
            OnnxTensor encoderResultBatched = null;
            if(mode == MADLAD_CACHE) {
                float[][] encoderValue = ((float[][][]) encoderResult.getValue())[0];
                float[] encoderValueFlatBatched = TensorUtils.flattenFloatArrayBatched(encoderValue, beamSize);
                encoderResultBatched = TensorUtils.createFloatTensor(onnxEnv, encoderValueFlatBatched, new long[]{beamSize, encoderValue.length, encoderValue[0].length});
                encoderValue = null;  //free the memory
                encoderValueFlatBatched = null;  //free the memory
                //System.gc();
                android.util.Log.i("performance", "Encoder batch initialization done in: " + (System.currentTimeMillis()-time) + "ms");
            }
            time = System.currentTimeMillis();
            OnnxTensor encoderAttentionMaskTensorBatched;
            int[] encoderMaskFlatBatched = TensorUtils.flattenIntArrayBatched(input.getAttentionMask(), beamSize);
            encoderAttentionMaskTensorBatched = TensorUtils.createIntTensor(onnxEnv, encoderMaskFlatBatched, new long[]{beamSize, input.getAttentionMask().length});
            encoderMaskFlatBatched = null;  //free the memory
            //System.gc();
            android.util.Log.i("performance", "Mask batch initialization done in: " + (System.currentTimeMillis()-time) + "ms");
            time = System.currentTimeMillis();
            OrtSession.Result initResultBatched;
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
                    float[][][] keyValue = ((float[][][][]) TensorUtils.extractValue(initResult, "present." + i + ".encoder."+suffix))[0];
                    timeExtract += System.currentTimeMillis() - timeInner;
                    timeInner = System.currentTimeMillis();
                    float[][][][] keyValueFlatBatched = TensorUtils.batchTensor(keyValue, beamSize);
                    timeBatch += System.currentTimeMillis() - timeInner;
                    timeInner = System.currentTimeMillis();
                    values[count] = TensorUtils.createFloatTensorOptimized(onnxEnv, keyValueFlatBatched, new long[]{beamSize, keyValue.length, keyValue[0].length, keyValue[0][0].length});;
                    timeCreate += System.currentTimeMillis() - timeInner;
                    count++;
                }
            }
            //the Result constructor is private but this way we can use it anyway
            Constructor<OrtSession.Result> constructor = OrtSession.Result.class.getDeclaredConstructor(names.getClass(), values.getClass(), ownedByResult.getClass());
            constructor.setAccessible(true);
            initResultBatched = constructor.newInstance(names, values, ownedByResult);
            android.util.Log.i("performance", "InitResult extract done in: " + timeExtract + "ms");
            android.util.Log.i("performance", "InitResult batch done in: " + timeBatch + "ms");
            android.util.Log.i("performance", "InitResult create done in: " + timeCreate + "ms");
            android.util.Log.i("performance", "InitResult batch initialization done in: " + (System.currentTimeMillis()-time) + "ms");

            //we begin the iterative execution of the decoder
            String[] partialResults = new String[beamSize];  //used for log
            OrtSession.Result result = null;
            OrtSession.Result oldResult = null;
            int[] max = new int[beamSize];
            int[][] beamMax = new int[beamSize][beamSize];
            int j = 1;
            OnnxTensor emptyPreLogits = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 1, 1024});
            OnnxTensor emptyPreLogitsBatch = TensorUtils.createFloatTensorWithSingleValue(onnxEnv, 0, new long[]{beamSize, 1, 1024});
            OnnxTensor emptyInputIds = TensorUtils.createInt64TensorWithSingleValue(onnxEnv, 0, new long[]{EMPTY_BATCH_SIZE, 2});
            OnnxTensor emptyInputIdsBatch = TensorUtils.createInt64TensorWithSingleValue(onnxEnv, 0, new long[]{beamSize, 2});

            while(input_ids[0] != eos){   //input_ids[0] should always contain the ultimate value generated from the text with highest probability (to be verified)
                initialTime = System.currentTimeMillis();
                time = System.currentTimeMillis();
                //we prepare the decoder input
                decoderInput = new HashMap<String,OnnxTensor>();
                OrtSession.Result embedResult = null;
                if(mode == NLLB_CACHE){
                    //we do the embedding separately and then we pass the result to the encoder
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
                    Map<String,OnnxTensor> embedInput = new HashMap<String,OnnxTensor>();
                    embedInput.put("input_ids", inputIDsTensor);
                    ArraySet<String> requestedOutputs = new ArraySet<>();
                    requestedOutputs.add("embed_matrix");
                    embedResult = embedSession.run(embedInput, requestedOutputs);

                    decoderInput.put("embed_matrix", (OnnxTensor) embedResult.get(0));
                    decoderInput.put("encoder_hidden_states", encoderResult);
                }
                decoderInput.put("input_ids", inputIDsTensor);
                if(j == 1){  //se è la prima iterazione
                    //we run the decoder with a batch_size = 1
                    decoderInput.put("encoder_attention_mask", encoderAttentionMaskTensor);
                    if(mode == MADLAD_CACHE) {
                        decoderInput.put("encoder_hidden_states", encoderResult);
                    }
                    long[] shape = {1, 16, 0, hiddenSize};
                    OnnxTensor decoderPastTensor = TensorUtils.createFloatTensorWithSingleValue(onnxEnv,0, shape);
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + ".decoder.key", decoderPastTensor);
                        decoderInput.put("past_key_values." + i + ".decoder.value", decoderPastTensor);
                        decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResult.get("present." + i + ".encoder.key").get());
                        decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResult.get("present." + i + ".encoder.value").get());
                    }
                }else {
                    if(j == 2) {
                        encoderAttentionMaskTensor.close();   //we close it because from now on we only need encoderAttentionMaskTensorBatched
                        encoderResult.close();  //we close it because from now on we only need encoderResultBatched
                        initResult.close();     //we close it because from now on we only need initResultBatched
                    }
                    //we run the decoder with batch_size = beamSize
                    decoderInput.put("encoder_attention_mask", encoderAttentionMaskTensorBatched);
                    if(mode == MADLAD_CACHE) {
                        decoderInput.put("encoder_hidden_states", encoderResultBatched);
                    }
                    for (int i = 0; i < nLayers; i++) {
                        decoderInput.put("past_key_values." + i + ".decoder.key", (OnnxTensor) result.get("present." + i + ".decoder.key").get());
                        decoderInput.put("past_key_values." + i + ".decoder.value", (OnnxTensor) result.get("present." + i + ".decoder.value").get());
                        decoderInput.put("past_key_values." + i + ".encoder.key", (OnnxTensor) initResultBatched.get("present." + i + ".encoder.key").get());
                        decoderInput.put("past_key_values." + i + ".encoder.value", (OnnxTensor) initResultBatched.get("present." + i + ".encoder.value").get());
                    }
                }
                oldResult = result;
                android.util.Log.i("performance", "pre-execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();
                //decoder execution (with cache)
                result = decoderSession.run(decoderInput);
                android.util.Log.i("performance", "execution of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                time = System.currentTimeMillis();

                if(oldResult != null) {
                    oldResult.close(); //serves to release the memory occupied by the result (otherwise it accumulates and increases a lot)
                }
                if(embedResult != null) {
                    embedResult.close();
                }
                android.util.Log.i("performance", "release RAM of"+j+"th word done in: " + (System.currentTimeMillis()-time) + "ms");
                //we take the logits and the max value
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
                if(j == 1) {  //if we are at the first iteration
                    //decoderOutput = (OnnxTensor) result.get("logits").get();
                    outputValues = (float[][][]) decoderOutput.getValue();
                    //the "beamSize" words with highest probability are inserted into max and added to completeBeamOutput
                    ArrayList<Integer> indexesToAvoid = new ArrayList<>();
                    for (int i = 0; i < beamSize; i++) {
                        max[i] = Utils.getIndexOfLargest(outputValues[0][0], indexesToAvoid);
                        indexesToAvoid.add(max[i]);
                        completeBeamOutput[i].add(max[i]);
                    }
                    //we insert the initial probabilities of the "beamSize" output strings into beamsOutputsProbabilities
                    for (int i = 0; i < beamSize; i++) {
                        float maxLogit = outputValues[0][0][max[i]];
                        //old version of probability calculation (softmax)
                        //beamsOutputsProbabilities[i] = Math.log(Utils.softmax(maxLogit, outputValues[0][0]));
                        //new version of probability calculation (logSumExp)
                        beamsOutputsProbabilities[i] = maxLogit - Utils.logSumExpFast(outputValues[0][0]);
                    }
                    //we prepare the inputs of the next iteration
                    if(mode == NLLB_CACHE){
                        for(int i=0; i<input_ids.length; i++){
                            input_ids[i] = tokenizer.getLanguageID(getNllbLanguageCode(outputLanguage.getCode()));
                        }
                    }else {
                        input_ids = max;
                    }
                    inputIDsTensor = TensorUtils.createIntTensor(onnxEnv, input_ids, new long[]{beamSize,1});
                    //we convert the cache making it have a batch_size=beamSize ("beamSize" copies of the same cache)
                    names = new String[2*nLayers+1];
                    values = new OnnxValue[2*nLayers+1];
                    ownedByResult = new boolean[2*nLayers+1];
                    Arrays.fill(ownedByResult, true);
                    names[0] = "logits";
                    values[0] = decoderOutput;  //result.get("logits").get();
                    suffixes = new String[]{"key", "value"};
                    count = 1;
                    for (int i = 0; i < nLayers; i++) {
                        for (String suffix: suffixes) {
                            names[count] = "present." + i + ".decoder."+suffix;
                            float[][][] keyValue = ((float[][][][]) TensorUtils.extractValue(result, "present." + i + ".decoder."+suffix))[0];
                            float[] keyValueFlatBatched = TensorUtils.flattenFloatArrayBatched(keyValue, beamSize);
                            values[count] = TensorUtils.createFloatTensor(onnxEnv, keyValueFlatBatched, new long[]{beamSize, keyValue.length, keyValue[0].length, keyValue[0][0].length});;
                            count++;
                        }
                    }
                    result.close();
                    //the Result constructor is private but this way we can use it anyway
                    constructor = OrtSession.Result.class.getDeclaredConstructor(names.getClass(), values.getClass(), ownedByResult.getClass());
                    constructor.setAccessible(true);
                    result = constructor.newInstance(names, values, ownedByResult);

                }else{
                    //decoderOutput = (OnnxTensor) result.get("logits").get();
                    outputValues = (float[][][]) decoderOutput.getValue();
                    //for each of the "beamSize" decoder outputs, the "beamSize" words with the highest probability are inserted into beamMax
                    for(int k=0; k < beamSize; k++) {
                        ArrayList<Integer> indexesToAvoid = new ArrayList<>();
                        for (int i = 0; i < beamSize; i++) {
                            beamMax[k][i] = Utils.getIndexOfLargest(outputValues[k][0], indexesToAvoid);
                            indexesToAvoid.add(beamMax[k][i]);
                        }
                    }
                    //Now beamMax will contain for each decoder output ("beamSize" outputs) the "beamSize" words with highest probability,
                    // so for each output we calculate its overall probability for each of its "beamSize" words with highest probability
                    long timeSoftmax = System.currentTimeMillis();
                    double[] beamsOutputsProbabilitiesTemp = new double[beamSize*beamSize];
                    for(int k=0; k < beamSize; k++) {
                        //old version of probability calculation (softmax)
                        /*for (int i = 0; i < beamSize; i++) {
                            beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilities[k] + Math.log(Utils.softmax(outputValues[k][0][beamMax[k][i]], outputValues[k][0]));
                            if(beamMax[k][i] == eos){
                                beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilitiesTemp[(k*beamSize)+i]/EOS_PENALTY;
                            }
                        }*/
                        //new version of probability calculation (logSumExp)
                        double logSumExp = Utils.logSumExpFast(outputValues[k][0]);
                        for (int i = 0; i < beamSize; i++) {
                            float maxLogit = outputValues[k][0][beamMax[k][i]];
                            beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilities[k] + maxLogit - logSumExp;
                            if(beamMax[k][i] == eos){
                                beamsOutputsProbabilitiesTemp[(k*beamSize)+i] = beamsOutputsProbabilitiesTemp[(k*beamSize)+i]/EOS_PENALTY;
                            }
                        }
                    }
                    android.util.Log.i("performance", "softmax done in: " + (System.currentTimeMillis()-timeSoftmax) + "ms");
                    // Now we save in maxProbabilities the indices of the "beamSize" words generated by the decoder that have the
                    // highest overall probability with their respective output sentences and then we will use them as the next inputs
                    ArrayList<Integer> indexesToAvoid = new ArrayList<>();
                    int[] maxProbabilities = new int[beamSize];
                    for(int i=0; i<beamSize; i++){
                        maxProbabilities[i] = Utils.getIndexOfLargest(beamsOutputsProbabilitiesTemp, indexesToAvoid);
                        indexesToAvoid.add(maxProbabilities[i]);
                    }
                    // we update the probabilities of the "beamSize" output strings in beamsOutputsProbabilities,
                    // and add the "beamSize" words with higher probability (each to its own output string) to completeBeamOutput
                    ArrayList<Integer>[]  oldCompleteBeamOutput = completeBeamOutput.clone();
                    for (int i = 0; i < beamSize; i++) {
                        beamsOutputsProbabilities[i] = beamsOutputsProbabilitiesTemp[maxProbabilities[i]];
                        completeBeamOutput[i] = (ArrayList<Integer>) oldCompleteBeamOutput[maxProbabilities[i]/beamSize].clone();
                        completeBeamOutput[i].add(beamMax[maxProbabilities[i]/beamSize][maxProbabilities[i]%beamSize]);
                    }
                    //we prepare the inputs of the next iteration
                    for (int i = 0; i < beamSize; i++) {
                        input_ids[i] = beamMax[maxProbabilities[i]/beamSize][maxProbabilities[i]%beamSize];
                    }
                    inputIDsTensor = TensorUtils.createIntTensor(onnxEnv, input_ids, new long[]{beamSize,1});
                    long timeCache = System.currentTimeMillis();
                    CacheContainerNative oldCache = cacheContainer;
                    cacheContainer = new CacheContainerNative(onnxEnv, result, nLayers, beamSize, 16, j, hiddenSize);
                    if(oldCache != null){
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
                }
                android.util.Log.i("performance", "post-execution of" + j + "th word done in: " + (System.currentTimeMillis() - time) + "ms");
                android.util.Log.i("performance", "Generation of" + j + "th word done in: " + (System.currentTimeMillis() - initialTime) + "ms");
                // we return the partial result with the highest probability
                int indexMax = 0;
                for(int i=0; i<beamSize; i++){
                    indexMax = Utils.getIndexOfLargest(beamsOutputsProbabilities);
                }
                int [] outputIDs = completeBeamOutput[indexMax].stream().mapToInt(k -> k).toArray();
                String partialResult = tokenizer.decode(outputIDs);
                if(responseListener != null) {
                    responseListener.onTranslatedText(textToTranslate, partialResult, currentResultID, false, outputLanguage);
                }else {
                    notifyResult(textToTranslate, partialResult, currentResultID, false, outputLanguage);
                }
                j++;
                for(int i=0; i<beamSize; i++){
                    partialResults[i] = tokenizer.decode(completeBeamOutput[i].stream().mapToInt(k -> k).toArray());
                    android.util.Log.i("result "+i, partialResults[i]);
                }
            }

            if(result != null) {
                result.close();
            }
            initResult.close();
            if(cacheContainer != null) {
                cacheContainer.close();
            }
            if (encoderAttentionMaskTensorBatched != null) {
                encoderAttentionMaskTensorBatched.close();
            }
            if(encoderResultBatched != null) {
                encoderResultBatched.close();
            }
            initResultBatched.close();

        } catch (OrtException | InvocationTargetException | NoSuchMethodException |
                 IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            if(responseListener != null) {
                mainHandler.post(() -> responseListener.onFailure(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            }else{
                mainHandler.post(() -> notifyError(new int[]{ErrorCodes.ERROR_EXECUTING_MODEL}, 0));
            }
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
                if (!Character.isLetterOrDigit(correctedText.charAt(correctedText.length() - 1))) {
                    return correctedText;
                }
                return correctedText + getSentenceTerminator(locale);
            }
        }
        return text;
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

    private String getNllbLanguageCode(String languageCode){
        if(nllbLanguagesCodes != null) {
            String nllbCode = nllbLanguagesCodes.get(languageCode);
            if (nllbCode == null) {
                Log.e("error", "Error Converting Language code " + languageCode + " to NLLB code");
                return languageCode;
            } else {
                return nllbCode;
            }
        }else{
            Log.e("error", "Error Converting Language code " + languageCode + " to NLLB code, the NllbLanguagesCodes are not initialized");
            return languageCode;
        }
    }


    public static ArrayList<CustomLocale> getSupportedLanguages(Context context, int mode) {
        ArrayList<CustomLocale> languages = new ArrayList<>();
        SharedPreferences sharedPreferences = context.getSharedPreferences("default", Context.MODE_PRIVATE);
        boolean qualityLow = sharedPreferences.getBoolean("languagesNNQualityLow", false);
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = null;
            if(mode == MADLAD){
                document = documentBuilder.parse(context.getResources().openRawResource(R.raw.madlad_supported_launguages));
            }else{  //if mode == NLLB
                if(!qualityLow) {
                    document = documentBuilder.parse(context.getResources().openRawResource(R.raw.nllb_supported_languages));
                }else{
                    document = documentBuilder.parse(context.getResources().openRawResource(R.raw.nllb_supported_languages_all));
                }
            }
            NodeList list = document.getElementsByTagName("code");
            for (int i = 0; i < list.getLength(); i++) {
                languages.add(CustomLocale.getInstance(list.item(i).getTextContent()));
            }
        } catch (IOException | SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
        return languages;
    }


    private interface TranslatorListener {
        void onFailure(int[] reasons, long value);
    }
}