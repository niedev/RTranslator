//
// Created by Local on 02/12/2025.
//

#include <jni.h>
#include <string>
#include <android/log.h>
#include "libs/bergamot/src/translator/byte_array_util.h"
#include "libs/bergamot/src/translator/parser.h"
#include "libs/bergamot/src/translator/response.h"
#include "libs/bergamot/src/translator/response_options.h"
#include "libs/bergamot/src/translator/service.h"
#include "libs/bergamot/src/translator/utils.h"
#include <string>
using namespace marian::bergamot;
#include <unordered_map>
#include <mutex>

struct ModelContainer{
    public:
        std::shared_ptr<TranslationModel> toEnglishModel;
        std::shared_ptr<TranslationModel> fromEnglishModel;

        ModelContainer(std::shared_ptr<TranslationModel> toEnglishModel, std::shared_ptr<TranslationModel> fromEnglishModel){
            this->toEnglishModel = toEnglishModel;
            this->fromEnglishModel = fromEnglishModel;
        }
};

static std::unordered_map<std::string, std::shared_ptr<ModelContainer>> model_cache;
static std::unique_ptr<BlockingService> global_service = nullptr;
static std::mutex service_mutex;
static std::mutex translation_mutex;

void initializeService() {
    std::lock_guard<std::mutex> lock(service_mutex);

    if (global_service == nullptr) {
        BlockingService::Config blockingConfig;
        blockingConfig.cacheSize = 0;  //todo: change it back to 256 in release
        blockingConfig.logger.level = "off";
        global_service = std::make_unique<BlockingService>(blockingConfig);
    }
}

void loadModelIntoCache(const std::string& toEngCfg, const std::string& fromEngConfig, const std::string& lang) {
    std::lock_guard<std::mutex> lock(service_mutex);

    auto validate = true;
    auto pathsDir = "";

    if (model_cache.find(lang) == model_cache.end()) {
        auto toEngOptions = parseOptionsFromString(toEngCfg, validate, pathsDir);
        auto fromEngOptions = parseOptionsFromString(fromEngConfig, validate, pathsDir);
        model_cache[lang] = std::make_shared<ModelContainer>(std::make_shared<TranslationModel>(toEngOptions),
                std::make_shared<TranslationModel>(fromEngOptions));
    }
}

void unloadModelFromCache(const std::string& lang) {
    std::lock_guard<std::mutex> lock(service_mutex);

    model_cache.erase(lang);
}

std::vector<std::string> translateMultiple(std::vector<std::string> &&inputs, const std::string& srcLang, const std::string& trgLang) {
    initializeService();

    std::shared_ptr<TranslationModel> firstModel = nullptr;
    std::shared_ptr<TranslationModel> secondModel = nullptr;

    if(srcLang == trgLang) return inputs;

    // Assume models are already loaded in cache
    if (srcLang != "en") {
        if(model_cache.find(srcLang) == model_cache.end()) throw std::runtime_error("Missing loaded src model: "+srcLang);
        firstModel = model_cache[srcLang]->toEnglishModel;
    }
    if (trgLang != "en") {
        if(model_cache.find(trgLang) == model_cache.end()) throw std::runtime_error("Missing loaded trg model: "+trgLang);
        secondModel = model_cache[trgLang]->fromEnglishModel;
    }

    std::vector<ResponseOptions> responseOptions;
    responseOptions.reserve(inputs.size());
    for (size_t i = 0; i < inputs.size(); ++i) {
        ResponseOptions opts;
        opts.HTML = false;
        opts.qualityScores = false;
        opts.alignment = false;
        opts.sentenceMappings = false;
        responseOptions.emplace_back(opts);
    }

    std::lock_guard<std::mutex> translation_lock(translation_mutex);
    std::vector<Response> responses;
    if (firstModel != nullptr && secondModel != nullptr) {
        responses = global_service->pivotMultiple(firstModel, secondModel, std::move(inputs),
                                                  responseOptions);
    } else if (firstModel != nullptr) {
        responses = global_service->translateMultiple(firstModel, std::move(inputs),
                                                      responseOptions);
    } else if (secondModel != nullptr) {
        responses = global_service->translateMultiple(secondModel, std::move(inputs),
                                                      responseOptions);
    } else {
        throw std::runtime_error("Missing loaded models");
    }

    std::vector<std::string> results;
    results.reserve(responses.size());
    for (const auto &response: responses) {
        results.push_back(response.target.text);
    }

    return results;
}

void cleanup(){
    std::lock_guard<std::mutex> lock(service_mutex);
    global_service.reset();
    model_cache.clear();
}


extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_BergamotTranslator_initializeServiceNative(
        JNIEnv* env,
        jclass /* this */) {
    try {
        initializeService();
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_BergamotTranslator_loadModelIntoCacheNative(
        JNIEnv* env,
        jclass /* this */,
        jstring toEngCfg,
        jstring fromEngCfg,
        jstring lang) {

    const char* c_toEngCfg = env->GetStringUTFChars(toEngCfg, nullptr);
    const char* c_fromEngCfg = env->GetStringUTFChars(fromEngCfg, nullptr);
    const char* c_lang = env->GetStringUTFChars(lang, nullptr);

    try {
        std::string toEngCfg_str(c_toEngCfg);
        std::string fromEngCfg_str(c_fromEngCfg);
        std::string key_str(c_lang);
        loadModelIntoCache(toEngCfg_str, fromEngCfg_str, key_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(toEngCfg, c_toEngCfg);
    env->ReleaseStringUTFChars(fromEngCfg, c_fromEngCfg);
    env->ReleaseStringUTFChars(lang, c_lang);
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_BergamotTranslator_unloadModelFromCacheNative(
        JNIEnv* env,
        jclass /* this */,
        jstring lang) {

    const char* c_lang = env->GetStringUTFChars(lang, nullptr);

    try {
        std::string lang_str(c_lang);
        unloadModelFromCache(lang_str);
    } catch(const std::exception &e) {
        jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
        env->ThrowNew(exceptionClass, e.what());
    }

    env->ReleaseStringUTFChars(lang, c_lang);
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT jobjectArray JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_BergamotTranslator_translateMultipleNative(
        JNIEnv *env,
        jclass /* this */,
        jobjectArray inputs,
        jstring srcLang,
        jstring trgLang) {

    const char *c_srcLang = env->GetStringUTFChars(srcLang, nullptr);
    const char *c_trgLang = env->GetStringUTFChars(trgLang, nullptr);

    jsize inputCount = env->GetArrayLength(inputs);
    std::vector<std::string> cpp_inputs;
    cpp_inputs.reserve(inputCount);

    for (jsize i = 0; i < inputCount; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(inputs, i);
        const char *c_str = env->GetStringUTFChars(jstr, nullptr);
        cpp_inputs.emplace_back(c_str);
        env->ReleaseStringUTFChars(jstr, c_str);
        env->DeleteLocalRef(jstr);
    }

    jobjectArray result = nullptr;
    try {
        std::string srcLang_str(c_srcLang);
        std::string trgLang_str(c_trgLang);
        std::vector<std::string> translations = translateMultiple(std::move(cpp_inputs), srcLang_str, trgLang_str);

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

    return result;
}

extern "C" __attribute__((visibility("default"))) JNIEXPORT void JNICALL
Java_nie_translator_rtranslator_voice_1translation_neural_1networks_translation_BergamotTranslator_cleanupNative(JNIEnv* env, jclass /* this */) {
    cleanup();
}