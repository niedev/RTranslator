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
#include <android/log.h>
#include <string>
#include <unordered_map>
#include "protobuf/dictionary_data.pb.h"
#include <memory>
#include "libs/sqlite4java/include/sqlite3.h"


struct DictionaryContainer{
public:
    std::shared_ptr<mydata::DataMap> toEnglishDict;
    std::shared_ptr<mydata::DataMap> fromEnglishDict;

    DictionaryContainer(std::shared_ptr<mydata::DataMap> toEnglishDict, std::shared_ptr<mydata::DataMap> fromEnglishDict){
        this->toEnglishDict = toEnglishDict;
        this->fromEnglishDict = fromEnglishDict;
    }
};

static std::unordered_map<std::string, std::shared_ptr<DictionaryContainer>> dict_cache;
static std::mutex service_mutex;
static std::string databasePath;

void initializeService(const std::string& dbPath) {
    std::lock_guard <std::mutex> lock(service_mutex);
    databasePath = dbPath;
}

std::shared_ptr<mydata::DataMap> getDictData(const std::string& lang, const bool toEnglish) {
    sqlite3* db;
    sqlite3_stmt* stmt;
    std::shared_ptr<mydata::DataMap> result = nullptr;

    if (sqlite3_open_v2(databasePath.c_str(), &db, SQLITE_OPEN_READONLY, NULL) != SQLITE_OK) {
        return {};
    }

    const char* sql = "SELECT data FROM dictionaries WHERE srcLang = ? AND toEnglish = ? LIMIT 1";

    if (sqlite3_prepare_v2(db, sql, -1, &stmt, NULL) == SQLITE_OK) {
        // Bind arguments
        sqlite3_bind_text(stmt, 1, lang.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int(stmt, 2, toEnglish ? 1 : 0);

        if (sqlite3_step(stmt) == SQLITE_ROW) {
            // Get pointer to the BLOB memory
            const void* blobPtr = sqlite3_column_blob(stmt, 0);

            // Get the size of the BLOB in bytes
            int blobSize = sqlite3_column_bytes(stmt, 0);

            if (blobPtr != nullptr && blobSize > 0) {
                // Parsing with Protobuf
                auto dataMap = std::make_shared<mydata::DataMap>();
                if (dataMap->ParseFromArray(blobPtr, blobSize)) {
                    result = std::move(dataMap);
                }
            }
        }
    }

    sqlite3_finalize(stmt);
    sqlite3_close(db);
    return result;
}

void loadTranslationDictionary(const std::string& lang) {
    std::lock_guard <std::mutex> lock(service_mutex);

    if (dict_cache.find(lang) == dict_cache.end()) {
        auto toEngDict = getDictData(lang, true);
        auto fromEngDict = getDictData(lang, false);
        if(toEngDict != nullptr && fromEngDict != nullptr) {
            dict_cache[lang] = std::make_shared<DictionaryContainer>(toEngDict, fromEngDict);
        }
    }
}

void unloadTranslationDictionary(const std::string& lang) {
    std::lock_guard<std::mutex> lock(service_mutex);

    dict_cache.erase(lang);
}

std::vector<std::string> translateWord(const std::string& word, const std::string& srcLang, const std::string& tgtLang){
    std::lock_guard <std::mutex> lock(service_mutex);

    std::shared_ptr<mydata::DataMap> firstDict = nullptr;
    std::shared_ptr<mydata::DataMap> secondDict = nullptr;

    if(srcLang == tgtLang) return {word};

    // Assume models are already loaded in cache
    if (srcLang != "eng") {
        if(dict_cache.find(srcLang) == dict_cache.end()) throw std::runtime_error("Missing loaded dict: "+srcLang);
        firstDict = dict_cache[srcLang]->toEnglishDict;
    }
    if (tgtLang != "eng") {
        if(dict_cache.find(tgtLang) == dict_cache.end()) throw std::runtime_error("Missing loaded dict: "+tgtLang);
        secondDict = dict_cache[tgtLang]->fromEnglishDict;
    }

    if (firstDict != nullptr && secondDict != nullptr) {
        auto it = firstDict->data().find(word);
        if (it != firstDict->data().end()) {
            const mydata::Values& valuesObj = it->second;
            // Now we search each enWord translation in the secondDict, until we find a match, then we return all its translation in the tgtLang
            for (const std::string& val : valuesObj.value()) {
                auto it2 = secondDict->data().find(val.c_str());
                if (it2 != secondDict->data().end()) {
                    const mydata::Values& valuesObj2 = it2->second;
                    const auto& translations = valuesObj2.value();
                    return std::vector<std::string>(translations.begin(), translations.end());
                }
            }
        }
    } else if (firstDict != nullptr) {
        auto it = firstDict->data().find(word);
        if (it != firstDict->data().end()) {
            const mydata::Values& valuesObj = it->second;
            const auto& translations = valuesObj.value();
            return std::vector<std::string>(translations.begin(), translations.end());
        }
    } else if (secondDict != nullptr) {
        auto it = secondDict->data().find(word);
        if (it != secondDict->data().end()) {
            const mydata::Values& valuesObj = it->second;
            const auto& translations = valuesObj.value();
            return std::vector<std::string>(translations.begin(), translations.end());
        }
    } else {
        throw std::runtime_error("Missing loaded dictionaries");
    }

    return {};
}

void cleanup(){
    std::lock_guard<std::mutex> lock(service_mutex);
    dict_cache.clear();
}



extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_DictionaryTranslator_initializeServiceNative(
        JNIEnv* env,
        jclass /* this */,
        jstring dbPath) {
    try {
        const char* c_dbPath = env->GetStringUTFChars(dbPath, nullptr);
        std::string dbPath_str(c_dbPath);
        initializeService(dbPath_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_DictionaryTranslator_loadDictionaryNative(
        JNIEnv* env,
        jclass /* this */,
        jstring lang) {

    const char* c_lang = env->GetStringUTFChars(lang, nullptr);

    try {
        std::string lang_str(c_lang);
        loadTranslationDictionary(lang_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(lang, c_lang);
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_DictionaryTranslator_unloadDictionaryNative(
        JNIEnv* env,
        jclass /* this */,
        jstring lang) {

    const char* c_lang = env->GetStringUTFChars(lang, nullptr);

    try {
        std::string lang_str(c_lang);
        unloadTranslationDictionary(lang_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(lang, c_lang);
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jobjectArray JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_DictionaryTranslator_translateWordNative(
        JNIEnv *env,
        jclass /* this */,
        jstring word,
        jstring srcLang,
        jstring trgLang) {

    const char *c_srcLang = env->GetStringUTFChars(srcLang, nullptr);
    const char *c_trgLang = env->GetStringUTFChars(trgLang, nullptr);
    const char *c_word = env->GetStringUTFChars(word, nullptr);

    jobjectArray result = nullptr;
    try {
        std::string srcLang_str(c_srcLang);
        std::string trgLang_str(c_trgLang);
        std::string word_str(c_word);
        std::vector<std::string> translations = translateWord(word_str, srcLang_str, trgLang_str);

        jclass stringClass = env->FindClass("java/lang/String");
        result = env->NewObjectArray((jsize) translations.size(), stringClass, nullptr);

        for (size_t i = 0; i < translations.size(); ++i) {
            jstring jstr = env->NewStringUTF(translations[i].c_str());
            env->SetObjectArrayElement(result, (jsize) i, jstr);
            env->DeleteLocalRef(jstr);
        }
    } catch (const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(srcLang, c_srcLang);
    env->ReleaseStringUTFChars(trgLang, c_trgLang);
    env->ReleaseStringUTFChars(word, c_word);

    return result;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_DictionaryTranslator_cleanupNative(JNIEnv* env, jclass /* this */) {
    cleanup();
}