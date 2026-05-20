package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import androidx.annotation.Nullable;

import java.util.Objects;

import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.TextTools;

//todo: aggiungere una vera gestione degli errori (basata sulle eccezioni)
public class DictionaryTranslator {
    //Used to load BergamotTranslator.cpp with bergamot library on application startup
    static {
        System.loadLibrary("dictionary_translator_interface");
    }

    public static void initializeService(String dbPath){
        initializeServiceNative(dbPath);
    }

    public static void loadDictionary(CustomLocale lang){
        String langCode = convertToMacroLanguage(lang).getISO3Language();
        if(!Objects.equals(langCode, "eng")) {
            loadDictionaryNative(langCode);
        }
    }

    public static void unloadDictionary(CustomLocale lang){
        unloadDictionaryNative(convertToMacroLanguage(lang).getISO3Language());
    }

    @Nullable
    public static String[] translateWord(String word, CustomLocale srcLang, CustomLocale tgtLang){
        long time = System.currentTimeMillis();
        String normalizedWord = TextTools.normalizeText(word);
        srcLang = convertToMacroLanguage(srcLang);
        tgtLang = convertToMacroLanguage(tgtLang);
        String[] result = translateWordNative(normalizedWord, srcLang.getISO3Language(), tgtLang.getISO3Language());
        if(result != null) {
            for (int i = 0; i < result.length; i++) {
                result[i] = TextTools.capitalizeFirstLetter(result[i], tgtLang);
            }
        }
        android.util.Log.i("dict_performance", "dictionary translation done in: " + (System.currentTimeMillis()-time) + "ms");
        return result;
    }

    public static boolean containsWord(String word, CustomLocale lang){
        long time = System.currentTimeMillis();
        lang = convertToMacroLanguage(lang);
        boolean result = containsWordNative(word, lang.getISO3Language());
        android.util.Log.i("dict_performance", "dictionary search done in: " + (System.currentTimeMillis()-time) + "ms");
        return result;
    }

    private static CustomLocale convertToMacroLanguage(CustomLocale lang) {
        if(lang == null) return null;
        String langCode = lang.getISO3Language();

        // Convert to lowercase to ensure the check is case-insensitive
        switch (langCode.toLowerCase()) {

            // Chinese (zho)
            case "cmn": case "cjy": case "czh": case "gan":
            case "hsn": case "wuu": case "hak":
                return new CustomLocale("zho");

            // Persian (fas)
            case "pes": case "prs":
                return new CustomLocale("fas");

            // Malay (msa)
            case "zsm": case "meo": case "vkt": case "mfa":
                return new CustomLocale("msa");

            // Arabic (ara)
            case "arb": case "arz": case "apc": case "acm":
            case "afb": case "ary":
                return new CustomLocale("ara");

            // Norwegian (nor)
            case "nob":
                return new CustomLocale("nor");

            // Serbo-Croatian (hbs)
            case "srp": case "hrv": case "bos":
                return new CustomLocale("hbs");

            // Kurdish (kur)
            case "kmr":
                return new CustomLocale("kur");

            // Default: return the original code if no match is found
            default:
                return lang;
        }
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
