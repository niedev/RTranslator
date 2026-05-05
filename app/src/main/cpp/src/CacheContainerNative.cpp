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

#include <jni.h>
#include <stdio.h>
#include <vector>
#include <android/log.h>
#include <string>
#include <cstring> // Required for memcpy
//#include "onnxruntime/core/session/onnxruntime_c_api.h"
//#include "onnxruntime/core/session/onnxruntime_c_api.h"

std::vector<int> jintArrayTointVector2(JNIEnv* env, jintArray jarray);


class CacheContainer{
private:
    float ** cacheContainer;
    float * cacheValueTempContainer;
    int dim1;  //32*2
    int dim2;  //batch_size
    int dim3;  //16
    int dim4;  //sequence_length
    int dim5;  //128

public:
    CacheContainer(int dim1, int dim2, int dim3, int dim4, int dim5){
        this->dim1 = dim1;  //32*2
        this->dim2 = dim2;  //batch_size
        this->dim3 = dim3;  //16
        this->dim4 = dim4;  //sequence_length
        this->dim5 = dim5;  //128
        cacheContainer = new float*[dim1];
        cacheValueTempContainer = new float[dim2*dim1*dim3*dim4*dim5];
    }

    void insertData(JNIEnv *env, int index, jobject dataBuffer){
        //we fill the empty vector with the values taken from the buffer
        float * bufferArr = (float *)(*env).GetDirectBufferAddress(dataBuffer);
        long size = (*env).GetDirectBufferCapacity(dataBuffer);
        //we convert bufferArr to a flat array of floats
        int length = dim2*dim3*dim4*dim5;
        if(length*4 != size){
            __android_log_print(ANDROID_LOG_ERROR, "BUFFER ERROR", "%s", "size of buffer different from expected");
        }
        //we save buffer pointer in cacheContainer
        cacheContainer[index] = bufferArr;
    }

    void reorder(std::vector<int> * indexes){
        // if there is no reorder to be done we return
        bool change = false;
        for (int i = 0; i < indexes->size() && !change; ++i) {
            if((*indexes)[i] != i) {
                change = true;
            }
        }
        if(!change){
            return;
        }

        for (int i = 0; i < indexes->size(); ++i) {  //the cacheValue vectors are copied and will be reinserted into the cacheValue
            if((*indexes)[i] != i) {
                getCacheBatchValueFast((*indexes)[i], &cacheValueTempContainer[getFlatIndexB(i,0,0,0,0)]);
            }
        }
        //reorder is performed
        for (int i = 0; i < indexes->size(); ++i) {
            if((*indexes)[i] != i) {
                setCacheBatchValueFast(i, &cacheValueTempContainer[getFlatIndexB(i,0,0,0,0)]);
            }
        }
    }

    jobject getBuffer(JNIEnv *env, int index){
        float * address = cacheContainer[index];
        long capacity = dim2*dim3*dim4*dim5*sizeof(float);
        jobject buffer = env->NewDirectByteBuffer(address, capacity);
        return buffer;
    }

    int getFlatIndex(int i2, int i3, int i4, int i5){
        return i2*dim3*dim4*dim5 + i3*dim4*dim5 + i4*dim5 + i5;
    }

    int getFlatIndexB(int i1, int i2, int i3, int i4, int i5){
        return i1*dim1*dim3*dim4*dim5 + i2*dim3*dim4*dim5 + i3*dim4*dim5 + i4*dim5 + i5;
    }

    ~CacheContainer(){
        delete[] cacheContainer;
        delete[] cacheValueTempContainer;
    }

private:
    void getCacheBatchValue(int batchIndex, float * cacheBatchValue){
        for(int l=0; l<dim1; l++) {
            for (int k = 0; k < dim3; k++) {
                for (int j = 0; j < dim4; j++) {
                    for (int i = 0; i < dim5; i++) {
                        cacheBatchValue[getFlatIndexB(0,l, k, j, i)] = cacheContainer[l][getFlatIndex(batchIndex, k, j, i)];
                    }
                }
            }
        }
    }

    void setCacheBatchValue(int batchIndex, float * cacheBatchValue){
        for(int l=0; l<dim1; l++) {
            for (int k = 0; k < dim3; k++) {
                for (int j = 0; j < dim4; j++) {
                    for (int i = 0; i < dim5; i++) {
                        cacheContainer[l][getFlatIndex(batchIndex, k, j, i)] = cacheBatchValue[getFlatIndexB(0,l, k, j, i)];
                    }
                }
            }
        }
    }

    void getCacheBatchValueFast(int batchIndex, float * cacheBatchValue){
        size_t blockSize = (size_t)dim3 * dim4 * dim5;
        size_t blockBytes = blockSize * sizeof(float);

        for(int l = 0; l < dim1; l++) {
            // Memory address in the main cache for this specific layer and batch
            float* src = cacheContainer[l] + (batchIndex * blockSize);
            // Memory address in the temp container
            float* dst = cacheBatchValue + (l * blockSize);

            // Instantly copy the entire chunk of dim3*dim4*dim5 floats
            std::memcpy(dst, src, blockBytes);
        }
    }

    void setCacheBatchValueFast(int batchIndex, float * cacheBatchValue){
        size_t blockSize = (size_t)dim3 * dim4 * dim5;
        size_t blockBytes = blockSize * sizeof(float);

        for(int l = 0; l < dim1; l++) {
            // Memory address in the main cache for this specific layer and batch
            float* dst = cacheContainer[l] + (batchIndex * blockSize);
            // Memory address in the temp container
            float* src = cacheBatchValue + (l * blockSize);

            // Instantly copy the entire chunk of dim3*dim4*dim5 floats
            std::memcpy(dst, src, blockBytes);
        }
    }
};



extern "C"
JNIEXPORT jlong JNICALL
Java_nie_translator_rtranslator_tools_nn_CacheContainerNative_initialize(JNIEnv *env,
                                     jobject thiz,
                                     jint dim1, jint dim2,
                                     jint dim3, jint dim4, jint dim5) {
    __android_log_print(ANDROID_LOG_ERROR, "CACHE CONTAINER NATIVE", "%s", "initialization of cache container");
    return (long) new CacheContainer(dim1, dim2, dim3, dim4, dim5);
}

extern "C"
JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_tools_nn_CacheContainerNative_insertValues(JNIEnv *env,
                                     jobject thiz,
                                     jlong cacheContainerPointer, jint index, jobject data) {
    //__android_log_print(ANDROID_LOG_ERROR, "CACHE CONTAINER NATIVE", "%s", "inserting data");
    CacheContainer *cacheContainer = (CacheContainer *) cacheContainerPointer;
    cacheContainer->insertData(env, index, data);
}

extern "C"
JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_tools_nn_CacheContainerNative_reorder(JNIEnv *env,
                                       jobject thiz,
                                       jlong cacheContainerPointer, jintArray indexes) {
    __android_log_print(ANDROID_LOG_ERROR, "CACHE CONTAINER NATIVE", "%s", "reordering cache");
    CacheContainer *cacheContainer = (CacheContainer *) cacheContainerPointer;
    std::vector<int> indexesConverted = jintArrayTointVector2(env, indexes);
    cacheContainer->reorder(&indexesConverted);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_nie_translator_rtranslator_tools_nn_CacheContainerNative_getBuffer(JNIEnv *env,
                                      jobject thiz,
                                      jlong cacheContainerPointer, jint index) {
    __android_log_print(ANDROID_LOG_ERROR, "CACHE CONTAINER NATIVE", "%s", "get cache buffer");
    CacheContainer *cacheContainer = (CacheContainer *) cacheContainerPointer;
    return cacheContainer->getBuffer(env, index);
}

extern "C"
JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_tools_nn_CacheContainerNative_close(JNIEnv *env,
                                        jobject thiz,
                                        jlong cacheContainerPointer) {
    __android_log_print(ANDROID_LOG_ERROR, "CACHE CONTAINER NATIVE", "%s", "closing cache container");
    CacheContainer *cacheContainer = (CacheContainer *) cacheContainerPointer;
    delete cacheContainer;
}


std::vector<int> jintArrayTointVector2(JNIEnv* env, jintArray jarray) {
    jsize size = env->GetArrayLength(jarray);
    std::vector<int> vector(size);
    env->GetIntArrayRegion(jarray, jsize{0}, size, &vector[0]);
    env->DeleteLocalRef(jarray);
    return vector;
}