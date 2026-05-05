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

package nie.translator.rtranslator.tools.nn;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class CacheContainerNative {
    private int[] shape;
    private OnnxTensor[] cacheTensors;
    private long cacheContainerNativePointer;
    private int mode = Translator.NLLB;

    //Used to load CacheContainerNative.cpp on application startup
    static {
        System.loadLibrary("cache_container_native");
    }

    /**
     * This object will represents the data of the passed cache result, the reorder method will reorder the data of the cache result itself,
     * so if you pass a result to this object and then reorder, if you access to the result data those will be reordered.
     * @param env
     * @param cache
     * @param nLevels
     * @param batchSize
     * @param nHeads
     * @param sequenceLength
     * @param hiddenSize
     * @param mode
     */

    public CacheContainerNative(OrtEnvironment env, OrtSession.Result cache, int nLevels, int batchSize, int nHeads, int sequenceLength, int hiddenSize, int mode){
        try {
            cacheTensors = new OnnxTensor[nLevels*2];
            cacheContainerNativePointer = initialize(nLevels*2, batchSize, nHeads, sequenceLength, hiddenSize);
            int count=0;
            for (int i = 0; i < nLevels; i++) {
                    cacheTensors[count] = (OnnxTensor) cache.get("present." + i + (mode!= Translator.HY_MT ? ".decoder" : "") + ".key").get();
                    //we use OnnxTensor's private getBuffer method, which returns the data reference without making a copy of it and we pass this reference to the native cache container
                    Method method = cacheTensors[count].getClass().getDeclaredMethod("getBuffer");
                    method.setAccessible(true);
                    ByteBuffer buffer = (ByteBuffer) method.invoke(cacheTensors[count]);
                    insertValues(cacheContainerNativePointer, count, buffer);
                    count++;

                    cacheTensors[count] = (OnnxTensor) cache.get("present." + i + (mode!= Translator.HY_MT ? ".decoder" : "") + ".value").get();
                    //we use OnnxTensor's private getBuffer method, which returns the data reference without making a copy of it and we pass this reference to the native cache container
                    method = cacheTensors[count].getClass().getDeclaredMethod("getBuffer");
                    method.setAccessible(true);
                    buffer = (ByteBuffer) method.invoke(cacheTensors[count]);
                    insertValues(cacheContainerNativePointer, count, buffer);
                    count++;
            }
            shape = new int[]{nLevels*2, batchSize, nHeads, sequenceLength, hiddenSize};
            this.mode = mode;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void reorder(int[] indexes){
        reorder(cacheContainerNativePointer, indexes);
    }

    public void close(){
        close(cacheContainerNativePointer);
    }

    public OrtSession.Result getCacheResult(OrtEnvironment env){
        try {
            String[] names = new String[shape[0]];
            OnnxValue[] values = new OnnxValue[shape[0]];
            boolean[] ownedByResult = new boolean[shape[0]];
            Arrays.fill(ownedByResult, true);
            String[] suffixes = {"key", "value"};
            int count = 0;
            for (int i = 0; i < shape[0]/2; i++) {
                for (String suffix: suffixes) {
                    names[count] = "present." + i + (mode!= Translator.HY_MT ? ".decoder." : ".") + suffix;
                    ByteBuffer buffer = getBuffer(cacheContainerNativePointer, count);
                    values[count] = OnnxTensor.createTensor(env, buffer, new long[]{shape[1], shape[2], shape[3], shape[4]}, OnnxJavaType.FLOAT);
                    count++;
                }
            }
            //Result's constructor is private but this way we can use it anyway
            Constructor<OrtSession.Result> constructor = OrtSession.Result.class.getDeclaredConstructor(names.getClass(), values.getClass(), ownedByResult.getClass());
            constructor.setAccessible(true);
            return constructor.newInstance(names, values, ownedByResult);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException |
                 NoSuchMethodException | OrtException e) {
            e.printStackTrace();
            return null;
        }
    }

    private native long initialize(int dim1, int dim2, int dim3, int dim4, int dim5);
    private native void insertValues(long cacheContainer, int index, ByteBuffer data);
    private native void reorder(long cacheContainer, int[] indexes);
    private native ByteBuffer getBuffer(long cacheContainer, int index);
    private native void close(long cacheContainer);
}
