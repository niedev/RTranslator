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
#include <string>
#include <sstream>
#include "libs/fastText/src/fasttext.h"

extern "C" JNIEXPORT jlong JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_LanguageDetector_initFastText(JNIEnv* env, jobject /* this */) {
    // Return a pointer to the native FastText object
    auto* ft = new fasttext::FastText();
    return reinterpret_cast<jlong>(ft);
}

extern "C" JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_LanguageDetector_loadModel(JNIEnv* env, jobject /* this */, jlong ptr, jstring path) {
    auto* ft = reinterpret_cast<fasttext::FastText*>(ptr);
    const char* pathStr = env->GetStringUTFChars(path, nullptr);

    // Load the .bin or .ftz model
    ft->loadModel(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_LanguageDetector_predictLanguage(JNIEnv* env, jobject /* this */, jlong ptr, jstring text, jdouble confidenceThreshold) {
    auto* ft = reinterpret_cast<fasttext::FastText*>(ptr);
    const char* textStr = env->GetStringUTFChars(text, nullptr);

    std::stringstream ioss(textStr);
    std::vector<std::pair<fasttext::real, std::string>> predictions;

    // Predict the single most likely language (k = 1)
    ft->predictLine(ioss, predictions, 1, confidenceThreshold);
    env->ReleaseStringUTFChars(text, textStr);

    if (!predictions.empty()) {
        std::string label = predictions[0].second;
        // fastText returns labels like "__label__en". We strip the prefix.
        std::string prefix = "__label__";
        if (label.find(prefix) == 0) {
            label = label.substr(prefix.length());
        }
        return env->NewStringUTF(label.c_str());
    }

    return env->NewStringUTF("und");
}

extern "C" JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_LanguageDetector_release(JNIEnv* env, jobject /* this */, jlong ptr) {
    auto* ft = reinterpret_cast<fasttext::FastText*>(ptr);
    delete ft; // Prevent memory leaks
}
