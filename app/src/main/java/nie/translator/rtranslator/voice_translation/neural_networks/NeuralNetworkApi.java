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

package nie.translator.rtranslator.voice_translation.neural_networks;

import androidx.annotation.NonNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.extensions.OrtxPackage;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.tools.ErrorCodes;

public class NeuralNetworkApi {
    protected Global global;
    private ArrayList<Thread> pendingThreads= new ArrayList<>();
    public static boolean isVerifying = false;

    protected void addPendingThread(Thread thread){
        pendingThreads.add(thread);
    }

    protected Thread takePendingThread(){
        if(pendingThreads.size()>0) {
            return pendingThreads.remove(0);
        }else{
            return null;
        }
    }

    public static void testModelIntegrity(@NonNull String testModelPath, InitListener initListener){
        //we try to load the model in testModelPath, if we don't have an exception the model file is perfect, else we have an integrity problem
        try {
            isVerifying = true;
            File modelFile = new File(testModelPath);
            if(modelFile.exists() && FilenameUtils.getExtension(testModelPath).equals("onnx")){
                OrtEnvironment onnxEnv = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions testOptions = new OrtSession.SessionOptions();
                testOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                testOptions.setMemoryPatternOptimization(false);
                testOptions.setCPUArenaAllocator(false);
                if(!testModelPath.contains("detokenizer.onnx")) {   //for Whisper_detokenizer.onnx we test with OnnxRuntime optimization because we do it that way in the Recognizer
                    testOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                }
                OrtSession testSession = onnxEnv.createSession(testModelPath, testOptions);
                testSession.close();
            }
            isVerifying = false;
            initListener.onInitializationFinished();
        } catch (OrtException e) {
            e.printStackTrace();
            isVerifying = false;
            initListener.onError(new int[]{ErrorCodes.ERROR_LOADING_MODEL},0);
        }
    }

    public static void testFolderIntegrity(@NonNull String testFolderPath, InitListener initListener){
        //we try to load the model in testModelPath, if we don't have an exception the model file is perfect, else we have an integrity problem
        try {
            isVerifying = true;
            File dir = new File(testFolderPath);
            Collection<File> files = FileUtils.listFiles(dir, null, true);
            for(File modelFile : files) {
                if (modelFile.exists() && FilenameUtils.getExtension(modelFile.getPath()).equals("onnx")) {
                    OrtEnvironment onnxEnv = OrtEnvironment.getEnvironment();
                    OrtSession.SessionOptions testOptions = new OrtSession.SessionOptions();
                    testOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath());
                    testOptions.setMemoryPatternOptimization(false);
                    testOptions.setCPUArenaAllocator(false);
                    if (!modelFile.getPath().contains("detokenizer.onnx")) {   //for Whisper_detokenizer.onnx we test with OnnxRuntime optimization because we do it that way in the Recognizer
                        testOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT);
                    }
                    OrtSession testSession = onnxEnv.createSession(modelFile.getPath(), testOptions);
                    testSession.close();
                }
            }
            isVerifying = false;
            initListener.onInitializationFinished();
        } catch (OrtException e) {
            e.printStackTrace();
            isVerifying = false;
            initListener.onError(new int[]{ErrorCodes.ERROR_LOADING_MODEL},0);
        }
    }

    public interface InitListener{
        void onInitializationFinished();
        void onError(int[] reasons, long value);
    }
}
