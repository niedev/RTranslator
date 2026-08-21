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

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import ai.onnxruntime.OnnxJavaType;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;


public final class TensorUtils {
    public static OnnxTensor createIntTensor(OrtEnvironment env, int[] data, long[] shape) throws OrtException {
        long[] longData = Arrays.stream(data).mapToLong(i -> i).toArray();  //converts data into a long array
        OnnxTensor var10000 = null;
        var10000 = OnnxTensor.createTensor(env, LongBuffer.wrap(longData), shape);
        return var10000;
    }

    public static OnnxTensor createInt32Tensor(OrtEnvironment env, int[] data, long[] shape) throws OrtException {
        OnnxTensor var10000 = null;
        var10000 = OnnxTensor.createTensor(env, IntBuffer.wrap(data), shape);
        return var10000;
    }

    public static OnnxTensor createFloatTensor(OrtEnvironment env, float[] data, long[] shape) throws OrtException {
        OnnxTensor tensor = null;
        tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape);
        return tensor;
    }

    public static OnnxTensor createFloatTensor(OrtEnvironment env, float[][][][] data, long[] shape) throws OrtException {
        OnnxTensor tensor = null;
        float[] dataFlat = flattenFloatArray(data);
        FloatBuffer buffer = FloatBuffer.wrap(dataFlat);
        tensor = OnnxTensor.createTensor(env, buffer, shape);
        //tensor = OnnxTensor.createTensor(env, data);
        return tensor;
    }

    public static OnnxTensor createInt64TensorWithSingleValue(OrtEnvironment onnxEnv, long value, long[] shape){
        long flat_length = shape[0];
        for(int i=1; i<shape.length; i++){
            flat_length = flat_length * shape[i];
        }
        LongBuffer buffer;
        if(value != 0) {
            long[] array = new long[(int) flat_length];
            Arrays.fill(array, value);
            buffer = LongBuffer.wrap(array);
        }else{
            //This option reduces the execution time by about 2 orders of magnitude (e.g. from 500ms to 4ms) (because using a direct buffer, OnnxTensor.createTensor does not have to copy the buffer values)
            buffer = ByteBuffer.allocateDirect((int)(flat_length*8)).asLongBuffer();
        }
        try {
            return OnnxTensor.createTensor(onnxEnv,buffer,shape);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static OnnxTensor createFloatTensor(OrtEnvironment env, float[][][][] data, long[] shape, long[] timeResult) throws OrtException {
        OnnxTensor tensor = null;
        float[] dataFlat = flattenFloatArray(data);
        long time = System.currentTimeMillis();
        //This option reduces the execution time of createTensor and limits the RAM consumption in Java (because using a direct buffer, OnnxTensor.createTensor does not have to copy the buffer values)
        ByteBuffer buffer = ByteBuffer.allocateDirect((dataFlat.length*4));
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(dataFlat);
        buffer.position(0);

        tensor = OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT);
        timeResult[0] = System.currentTimeMillis()-time;
        //tensor = OnnxTensor.createTensor(env, data);
        return tensor;
    }

    public static OnnxTensor createFloatTensor(OrtEnvironment env, float[][][] data, long[] shape, long[] timeResult) throws OrtException {
        OnnxTensor tensor = null;
        float[] dataFlat = flattenFloatArray(data);
        long time = System.currentTimeMillis();
        //this option reduces the execution time of createTensor and limits the RAM consumption in Java (because using a direct buffer, OnnxTensor.createTensor does not have to copy the buffer values)
        ByteBuffer buffer = ByteBuffer.allocateDirect((dataFlat.length*4));
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(dataFlat);
        buffer.position(0);

        tensor = OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT);
        timeResult[0] = System.currentTimeMillis()-time;
        return tensor;
    }

    public static OnnxTensor createFloatTensorOptimized(OrtEnvironment env, float[][][][] data, long[] shape) throws OrtException {
        OnnxTensor tensor = null;
        tensor = OnnxTensor.createTensor(env, data);
        return tensor;
    }

    public static long[] tensorShape(long... dims) {
        return Arrays.copyOf(dims, dims.length);
    }

    public static float[] flattenFloatArray(float[][][] data){
        float[][] dataFlatTemp = new float[data.length][data[0].length * data[0][0].length];
        for(int j=0; j<data.length; j++){
            dataFlatTemp[j] = Floats.concat(data[j]);
        }
        return Floats.concat(dataFlatTemp);
    }

    public static float[] flattenFloatArray(float[][][][] data){
        float[][] dataFlatTemp = new float[data.length][data[0].length * data[0][0].length * data[0][0][0].length];
        for(int j=0; j<data.length; j++){
            dataFlatTemp[j] = flattenFloatArray(data[j]);
        }
        return Floats.concat(dataFlatTemp);
    }

    public static float[] flattenFloatArrayBatched(float[][] data, int batchSize){
        float[] dataFlat = Floats.concat(data);
        float[][] dataFlatBatchedInit = new float[batchSize][dataFlat.length];
        for (int i=0; i<batchSize; i++){
            dataFlatBatchedInit[i] = dataFlat;
        }
        return Floats.concat(dataFlatBatchedInit);
    }

    public static int[] flattenIntArrayBatched(int[] data, int batchSize){
        int[][] dataFlatBatchedInit = new int[batchSize][data.length];
        for (int i=0; i<batchSize; i++){
            dataFlatBatchedInit[i] = data;
        }
        return Ints.concat(dataFlatBatchedInit);
    }

    public static float[] flattenFloatArrayBatched(float[][][] data, int batchSize){
        float[] dataFlat = flattenFloatArray(data);
        float[][] keyValueFlatBatchedInit = new float[batchSize][dataFlat.length];
        for (int j=0; j<batchSize; j++){
            keyValueFlatBatchedInit[j] = dataFlat;
        }
        return Floats.concat(keyValueFlatBatchedInit);
    }

    public static Object extractValue(OrtSession.Result result, String name) throws OrtException {
        OnnxTensor tensor = (OnnxTensor) result.get(name).get();
        return tensor.getValue();
    }


    public static float[][][][] batchTensor(float[][][] data, int batchSize){
        float[][][][] dataBatched = new float[batchSize][][][];
        for(int j=0; j<batchSize; j++){
            dataBatched[j] = data;
        }
        return dataBatched;
    }

    public static float[][][] batchTensor(float[][] data, int batchSize){
        float[][][] dataBatched = new float[batchSize][][];
        for(int j=0; j<batchSize; j++){
            dataBatched[j] = data;
        }
        return dataBatched;
    }


    public static float[][][][] extractFloatMatrix(OrtSession.Result result, String name, int dim1, int dim2, int dim3, int dim4) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        int length = dim1*dim2*dim3*dim4;
        OnnxTensor tensor = (OnnxTensor) result.get(name).get();

        //We insert all the data into a flat array (using OnnxTensor's private getBuffer method, which returns the data reference without making a copy)
        Method method = tensor.getClass().getDeclaredMethod("getBuffer");
        method.setAccessible(true);
        FloatBuffer buffer = ((ByteBuffer) method.invoke(tensor)).asFloatBuffer();
        float[] outputValues = new float[length];
        buffer.get(outputValues, 0, length);

        //We convert the flat array with all the data into a shape matrix = shape
        float[][][][] outputMatrix = new float[dim1][dim2][dim3][dim4];
        for(int l=0; l<dim1; l++){
            for(int k=0; k<dim2; k++){
                for(int j=0; j<dim3; j++){
                    for(int i=0; i<dim4; i++){
                        outputMatrix[l][k][j][i] = outputValues[l*dim2*dim3*dim4 + k*dim3*dim4 + j*dim4 + i];
                    }
                }
            }
        }
        return outputMatrix;
    }

    public static float[][][][] extractFloatMatrix(OnnxTensor tensor, int dim1, int dim2, int dim3, int dim4, long[] timeResult) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        long time = System.currentTimeMillis();
        int length = dim1*dim2*dim3*dim4;

        //We insert all the data into a flat array (using OnnxTensor's private getBuffer method, which returns the data reference without making a copy)
        Method method = tensor.getClass().getDeclaredMethod("getBuffer");
        method.setAccessible(true);
        FloatBuffer buffer = ((ByteBuffer) method.invoke(tensor)).asFloatBuffer();
        float[] outputValues = new float[length];
        buffer.get(outputValues, 0, length);
        timeResult[0] = System.currentTimeMillis()-time;

        time = System.currentTimeMillis();
        //We convert the flat array with all the data into a shape matrix = shape
        float[][][][] outputMatrix = new float[dim1][dim2][dim3][dim4];
        for(int l=0; l<dim1; l++){
            for(int k=0; k<dim2; k++){
                for(int j=0; j<dim3; j++){
                    for(int i=0; i<dim4; i++){
                        outputMatrix[l][k][j][i] = outputValues[l*dim2*dim3*dim4 + k*dim3*dim4 + j*dim4 + i];
                    }
                }
            }
        }
        timeResult[1] = System.currentTimeMillis()-time;
        return outputMatrix;
    }

    public static float[][][][] extractFloatMatrixAlternative(OnnxTensor tensor, int dim1, int dim2, int dim3, int dim4) throws OrtException {
        int length = dim1*dim2*dim3*dim4;
        float[][][][] outputMatrix = (float[][][][]) tensor.getValue();
        return outputMatrix;
    }

    public static OnnxTensor convertIntArrayToTensor(OrtEnvironment env, int[] intArray) throws OrtException {
        //convert int Array to an array of longs so as to make the inputIDs compatible with the encoder (which uses 64bit ints, i.e. longs)
        long[] longArray = Arrays.stream(intArray).mapToLong(i -> i).toArray();
        //convert inputIDsLong and attentionMaskLong into tensors
        long[] shape = {1, intArray.length};
        LongBuffer longBuffer = LongBuffer.wrap(longArray);
        return OnnxTensor.createTensor(env,longBuffer,shape);
    }

    public static OnnxTensor convertIntArrayToTensor(OrtEnvironment env, int[] intArray, long[] shape) throws OrtException {
        //convert int Array to an array of longs so as to make the inputIDs compatible with the encoder (which uses 64bit ints, i.e. longs)
        long[] longArray = Arrays.stream(intArray).mapToLong(i -> i).toArray();
        //convert inputIDsLong and attentionMaskLong into tensors
        LongBuffer longBuffer = LongBuffer.wrap(longArray);
        return OnnxTensor.createTensor(env,longBuffer,shape);
    }

    public static OnnxTensor convertBooleanToTensor(OrtEnvironment env, boolean input) throws OrtException {
        long[] shape = {1};
        byte[] inputBytes = {(byte) 0};
        if(input){
            inputBytes[0] = (byte) 1;
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(inputBytes);
        return OnnxTensor.createTensor(env,byteBuffer,shape, OnnxJavaType.BOOL);
    }

    public static OnnxTensor createFloatTensorWithSingleValue(OrtEnvironment env, float value, long[] shape) throws OrtException {
        long flat_length = shape[0];
        for(int i=1; i<shape.length; i++){
            flat_length = flat_length * shape[i];
        }
        FloatBuffer buffer;
        if(value != 0) {
            float[] array = new float[(int) flat_length];
            Arrays.fill(array, value);
            buffer = FloatBuffer.wrap(array);
        }else{
            //This option reduces the execution time by about 2 orders of magnitude (e.g. from 500ms to 4ms) (because using a direct buffer, OnnxTensor.createTensor does not have to copy the buffer values)
            buffer = ByteBuffer.allocateDirect((int)(flat_length*4)).asFloatBuffer();
        }
        return OnnxTensor.createTensor(env,buffer,shape);
    }
}
