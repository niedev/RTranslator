package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.Objects;

import javax.annotation.Nullable;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.tools.CustomLocale;

//default config:
/*
                    "models:\n" +
                    "  - "+model.getPath()+"\n" +
                    "vocabs:\n" +
                    "  - "+srcVocab.getAbsolutePath()+"\n" +
                    "  - "+trgVocab.getAbsolutePath()+"\n" +
                    "beam-size: 1\n" +
                    "normalize: 1.0\n" +
                    "word-penalty: 0\n" +
                    "max-length-break: 128\n" +
                    "mini-batch-words: 1024\n" +
                    "max-length-factor: 2.0\n" +
                    "skip-cost: true\n" +
                    "cpu-threads: 1\n" +
                    "quiet: true\n" +
                    "quiet-translation: true\n" +
                    (model.getName().contains("intgemm8.bin") ? "gemm-precision: int8shiftAll\n" : "") +
                    (model.getName().contains("intgemm.alphas.bin") ? "gemm-precision: int8shiftAlphaAll\n" : "") +
                    "alignment: soft\n";
 */

public class BergamotTranslator {

    enum ModelType {
        TO_ENGLISH,
        FROM_ENGLISH
    }

    //Used to load BergamotTranslator.cpp with bergamot library on application startup
    static {
        System.loadLibrary("bergamot_translator_interface");
    }

    public static void initializeService(){
        initializeServiceNative();
    }

    public static void loadModelIntoCache(Context context, CustomLocale lang) throws Exception{
        String langCode = lang.getLanguage();
        if(!Objects.equals(langCode, "en")) {
            String toEngCfg = modelConfigGeneration(context, langCode, ModelType.TO_ENGLISH);
            String fromEngCfg = modelConfigGeneration(context, langCode, ModelType.FROM_ENGLISH);
            loadModelIntoCacheNative(toEngCfg, fromEngCfg, langCode);
        }
    }

    public static void unloadModelFromCache(CustomLocale lang){
        unloadModelFromCacheNative(lang.getLanguage());
    }

    public static String[] translateMultiple(String[] inputs, CustomLocale srcLang, CustomLocale trgLang) {
        return translateMultipleNative(inputs, srcLang.getLanguage(), trgLang.getLanguage());
    }

    public static void cleanup(){
        cleanupNative();
    }

    //todo: aggiungere una vera gestione degli errori (basata sulle eccezioni)
    private static String modelConfigGeneration(Context context, String lang, ModelType modelType) throws Exception{
        String basePath;
        if(Global.USE_EXTERNAL_MEMORY_FOR_RESOURCES){
            basePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/models/" + "Translation/Mozilla/";
        }else{
            basePath = context.getFilesDir().getAbsolutePath() + "/Translation/Mozilla/";
        }
        File modelFolder;
        if(modelType == ModelType.TO_ENGLISH){
            modelFolder = new File( basePath + lang +"/"+lang+"en");
        }else{
            modelFolder = new File(basePath + lang +"/en"+lang);
        }
        File[] modelFiles = modelFolder.listFiles();
        File model = null;
        File srcVocab = null;
        File trgVocab = null;
        File lexShort = null;
        if(modelFiles == null) throw new Exception("No models files found");
        for (File file : modelFiles) {
            if(file.getName().contains("model")){
                model = file;
                continue;
            }
            if(file.getName().contains("srcvocab")){
                srcVocab = file;
                continue;
            }
            if(file.getName().contains("trgvocab")){
                trgVocab = file;
                continue;
            }
            if(file.getName().contains("vocab")){
                srcVocab = file;
                trgVocab = file;
                continue;
            }
            if(file.getName().contains("lex.")){
                lexShort = file;
                continue;
            }
        }
        if (model != null && lexShort != null && (srcVocab != null && trgVocab != null)) {
            return "models:\n" +
                    "  - "+model.getPath()+"\n" +
                    "vocabs:\n" +
                    "  - "+srcVocab.getPath()+"\n" +
                    "  - "+trgVocab.getPath()+"\n" +
                    "shortlist: \n" +
                    "  - "+lexShort.getPath()+"\n" +
                    "  - false\n" +
                    "beam-size: 1\n" +
                    "normalize: 1.0\n" +
                    "word-penalty: 0\n" +
                    "max-length-break: 256\n" +
                    "max-length-factor: 3.0\n" +
                    "mini-batch-words: 2048\n" +
                    "maxi-batch: 100\n" +
                    "maxi-batch-sort: src\n" +
                    "skip-cost: false\n" +
                    "workspace: 256\n" +
                    "cpu-threads: 1\n" +
                    //"allow-unk: true\n" +
                    "quiet: true\n" +
                    "quiet-translation: true\n" +
                    (model.getName().contains("intgemm8.bin") ? "gemm-precision: int8shiftAll\n" : "") +
                    (model.getName().contains("intgemm.alphas.bin") ? "gemm-precision: int8shiftAlphaAll\n" : "") +
                    "alignment: soft\n";
        }else{
            throw new Exception("Missing files");
        }
    }


    private static native void initializeServiceNative();

    private static native void loadModelIntoCacheNative(String toEngCfg, String fromEngCfg, String lang);

    private static native void unloadModelFromCacheNative(String lang);

    private static native String[] translateMultipleNative(String[] inputs, String srcLang, String trgLang);

    private static native void cleanupNative();
}
