package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Objects;

import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.TextTools;

//todo: aggiungere una vera gestione degli errori (basata sulle eccezioni)
public class DictionaryTranslator {
    //Used to load BergamotTranslator.cpp with bergamot library on application startup
    static {
        System.loadLibrary("dictionary_translator_interface");
    }

    private static final String[] LANGUAGES = {
            "afr", "ara", "aze", "bel", "ben", "bul", "cat", "ces", "cym", "dan",
            "deu", "ell", "epo", "est", "eus", "fas", "fin", "fra", "gla", "gle",
            "glg", "hbs", "heb", "hin", "hun", "hye", "ind", "isl", "ita", "jpn",
            "kat", "kaz", "khm", "kir", "kor", "kur", "lao", "lat", "lav", "lit",
            "mal", "mar", "mkd", "mon", "mri", "msa", "mya", "nld", "nor", "pol",
            "por", "ron", "rus", "slk", "slv", "spa", "sqi", "swa", "swe", "tam",
            "tat", "tel", "tgk", "tgl", "tha", "tur", "ukr", "urd", "uzb", "vie",
            "yid", "zho"
    };

    public static void initializeService(String dbPath){
        initializeServiceNative(dbPath);
    }

    public static void loadDictionary(@Nullable Context context, CustomLocale lang){
        String langCode = convertToMacroLanguage(context, lang).getCode();
        if(!Objects.equals(langCode, "eng")) {
            loadDictionaryNative(langCode);
        }
    }

    public static void unloadDictionary(@Nullable Context context, CustomLocale lang){
        unloadDictionaryNative(convertToMacroLanguage(context, lang).getCode());
    }

    @Nullable
    public static String[] translateWord(@Nullable Context context, String word, CustomLocale srcLang, CustomLocale tgtLang){
        long time = System.currentTimeMillis();
        String normalizedWord = TextTools.normalizeText(word);
        srcLang = convertToMacroLanguage(context, srcLang);
        tgtLang = convertToMacroLanguage(context, tgtLang);
        String[] result = translateWordNative(normalizedWord, srcLang.getCode(), tgtLang.getCode());
        if(result != null) {
            for (int i = 0; i < result.length; i++) {
                result[i] = TextTools.capitalizeFirstLetter(result[i], tgtLang);
            }
        }
        android.util.Log.i("dict_performance", "dictionary translation done in: " + (System.currentTimeMillis()-time) + "ms");
        return result;
    }

    public static boolean containsWord(@Nullable Context context, String word, CustomLocale lang){
        long time = System.currentTimeMillis();
        lang = convertToMacroLanguage(context, lang);
        boolean result = containsWordNative(word, lang.getCode());
        android.util.Log.i("dict_performance", "dictionary search done in: " + (System.currentTimeMillis()-time) + "ms");
        return result;
    }

    private static CustomLocale convertToMacroLanguage(@Nullable Context context, CustomLocale lang) {
        if(lang == null) return null;
        String langCode = lang.getCode();

        // Convert to lowercase to ensure the check is case-insensitive
        switch (langCode.toLowerCase()) {

            // Chinese (zho)
            case "cmn": case "cjy": case "czh": case "gan":
            case "hsn": case "wuu": case "hak":
                return new CustomLocale(context, "zho");

            // Persian (fas)
            case "pes": case "prs":
                return new CustomLocale(context, "fas");

            // Malay (msa)
            case "zsm": case "meo": case "vkt": case "mfa":
                return new CustomLocale(context, "msa");

            // Arabic (ara)
            case "arb": case "arz": case "apc": case "acm":
            case "afb": case "ary":
                return new CustomLocale(context, "ara");

            // Norwegian (nor)
            case "nob":
                return new CustomLocale(context, "nor");

            // Serbo-Croatian (hbs)
            case "srp": case "hrv": case "bos":
                return new CustomLocale(context, "hbs");

            // Kurdish (kur)
            case "kmr":
                return new CustomLocale(context, "kur");

            // Default: return the original code if no match is found
            default:
                return lang;
        }
    }

    public static ArrayList<CustomLocale> getSupportedLanguages(Context context){
        ArrayList<CustomLocale> languages = new ArrayList<>(LANGUAGES.length);
        for(String langCode: LANGUAGES){
            languages.add(new CustomLocale(context, langCode));
        }
        return languages;
    }



    public static void cleanup(){
        cleanupNative();
    }

    private static native void initializeServiceNative(String dbPath);

    private static native void loadDictionaryNative(String lang);

    private static native void unloadDictionaryNative(String lang);

    @Nullable
    private static native String[] translateWordNative(String word, String srcLang, String tgtLang);

    private static native boolean containsWordNative(String word, String lang);

    private static native void cleanupNative();
}
