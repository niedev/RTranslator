package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.pemistahl.lingua.api.IsoCode639_3;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.databases.tatoeba.LinksData;
import nie.translator.rtranslator.databases.tatoeba.TatoebaDbWrapper;
import nie.translator.rtranslator.tools.CustomLocale;

//todo: gestire la concorrenza (qui o in translator, dipende dall'esecuzione di mozilla e della ricerca in tatoeba)

/**
 * This class will manage the resources for the languages used in each rtranslatorMode (TEXT_TRANSLATION_MODE || WALKIE_TALKIE_MODE || CONVERSATION_MODE),
 * normally, each mode has a srcLang and tgtLang associated with it (except CONVERSATION_MODE, which has a different structure), the language resources of each mode are:
 *  - the Mozilla model of srcLang  (if Mozilla is used for this rtranslatorMode)
 *  - the Mozilla model of tgtLang  (if Mozilla is used for this rtranslatorMode)
 *  - the translation dictionary of srcLang  (if useTranslationDictionaries is enabled)
 *  - the translation dictionary of tgtLang  (if useTranslationDictionaries is enabled)
 *  - the tatoeba data structure of the srcLang and tgtLang combo  (if useTatoeba is enabled)
 * <p>
 * To set the language resources for the language combo and rtranslatorMode, the method setLanguageResources shoul be called,
 * this method will unload the previous language resources of the previous language combo assigned to this rtranslatorMode
 * and load the new language resources of the new language combo passed, that will also be assigned to this rtranslatorMode.
 * <p>
 * In the case of CONVERSATION_MODE, this manager will store the tgtLang (via setTgtLangResourcesForConversation),
 * which is the language used in this device for conversation mode.
 * And it will also store for each connected peer the data of the peer, associated with its language, called srcLang (via setSrcLangResourcesForPeer).
 * The language combos associated to the CONVERSATION_MODE will be, for each srcLang of each peer, the couple composed of the srcLang and the only tgtLang (the same for all the srcLangs).
 * <p>
 * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
 */
public class LanguageResourcesManager {
    private Global global;
    private final LanguageResourcesIndicator languageResourcesIndicator = new LanguageResourcesIndicator();
    private final TatoebaDbWrapper tatoebaDb;
    private final Map<String, LinksData.DataMap> tatoebaLinks = new HashMap<>();
    @Nullable
    private LanguageDetector linguaLanguageDetector;
    private boolean useMozillaForTranslation = false;
    private boolean useMozillaForVoiceTranslation = false;
    private boolean useTatoeba = false;
    private boolean useTranslationDictionaries = false;

    public LanguageResourcesManager(
            @NonNull Global global,
            boolean useMozillaForTranslation,
            boolean useMozillaForVoiceTranslation,
            boolean useTatoeba,
            boolean useTranslationDictionaries,
            CustomLocale firstTextLanguage,
            CustomLocale secondTextLanguage,
            CustomLocale firstLanguage,
            CustomLocale secondLanguage
    ) throws Exception {

        this.global = global;
        String basePath;
        if(Global.USE_EXTERNAL_MEMORY_FOR_RESOURCES){
            basePath = Environment.getExternalStorageDirectory().getPath() + "/models";
        }else{
            basePath = global.getFilesDir().getPath();
        }
        String tatoebaDbPath = basePath + "/Translation/Tatoeba/tatoeba.db";
        String translationDictionariesDbPath = basePath + "/Translation/TranslationDictionaries/translation_dict_ordered.db";
        tatoebaDb = new TatoebaDbWrapper(tatoebaDbPath);
        BergamotTranslator.initializeService();
        DictionaryTranslator.initializeService(translationDictionariesDbPath);
        //we load the deu translation dictionary (to and from English) because it will be used for the LID for the English language (I chose to use the deu dict because it is by far the biggest)
        DictionaryTranslator.loadDictionary(new CustomLocale("deu"));
        setStatus(useMozillaForTranslation, useMozillaForVoiceTranslation, useTatoeba, useTranslationDictionaries,
                firstTextLanguage, secondTextLanguage, firstLanguage, secondLanguage, null);
    }

    /**
     * This method is called when one of the settings regarding the language resources is changed (or to initialize the LanguageResourcesManager).
     * Since, when we change the translation model for some rtranslator mode (so if we change translator model or useMozillaForVoiceTranslation),
     * the selected language can change (in the case a previously selected lang is not supported by the new mode), we also receive the selected languages for all modes.
     * When called, this method will update all the language resources of all rtranslator modes (TEXT_TRANSLATION_MODE || WALKIE_TALKIE_MODE || CONVERSATION_MODE)
     * based on the new settings and selected languages.
     * @param useMozillaForTranslation
     * @param useMozillaForVoiceTranslation
     * @param useTatoeba
     * @param useTranslationDictionaries
     * @param firstTextLanguage
     * @param secondTextLanguage
     * @param firstLanguage
     * @param secondLanguage
     * @param language
     * @throws Exception
     */
    public void setStatus(
            boolean useMozillaForTranslation,
            boolean useMozillaForVoiceTranslation,
            boolean useTatoeba,
            boolean useTranslationDictionaries,
            CustomLocale firstTextLanguage,
            CustomLocale secondTextLanguage,
            CustomLocale firstLanguage,
            CustomLocale secondLanguage,
            @Nullable CustomLocale language
    ) throws Exception {

        this.useMozillaForTranslation = useMozillaForTranslation;
        this.useMozillaForVoiceTranslation = useMozillaForVoiceTranslation;
        this.useTatoeba = useTatoeba;
        this.useTranslationDictionaries = useTranslationDictionaries;

        //eventual unloads
        if(!useMozillaForTranslation && !useMozillaForVoiceTranslation){
            unloadAllMozillaResources();
        }
        if(!useTatoeba){
            unloadAllTatoebaResources();
        }
        if(!useTranslationDictionaries){
            unloadAllTranslationDictionariesResources();
        }

        // initial load (if called by the constructor), or change of resources / load due to the new languages and parameters
        setLanguageResources(firstTextLanguage, secondTextLanguage, Global.RTranslatorMode.TEXT_TRANSLATION_MODE);
        setLanguageResources(firstLanguage, secondLanguage, Global.RTranslatorMode.WALKIE_TALKIE_MODE);
        if(isTgtLangResourcesForConversationLoaded() && language != null){
            // if language was already loaded for conversation mode we update it, if not, we do not load it (it will be loaded later from the external)
            setTgtLangResourcesForConversation(language);
        }
        // After the initial unload of the unused resource types, we need to load the resource types that where previously not loaded.
        // The resources of TEXT_TRANSLATION_MODE and WALKIE_TALKIE_MODE are loaded / updated via the setLanguageResources
        // the same thing goes for the tgtLang resources of the CONVERSATION_MODE via the setTgtLangResourcesForConversation above.
        // Now the only resources that needs the loading of previously missing resources are (if present) the srcLangs of CONVERSATION_MODE,
        // we do that via the methods below.
        // Note that we don't need to update the resources based on the new languages like in the cases above because the external peers
        // will not change languages when we change status, so the only parameter that can change for the connected peers during a seStatus are the
        // settings, not the languages.
        if(isMozillaEnabled(Global.RTranslatorMode.CONVERSATION_MODE)) loadMozillaResourcesForPeers();
        if(useTatoeba) loadTatoebaResourcesForPeers();
        if(useTranslationDictionaries) loadTranslationDictionariesResourcesForPeers();
    }

    /**
     * This method will set the language resources for the language combo composed of srcLang and tgtLang
     * and the rtranslatorMode passed (TEXT_TRANSLATION_MODE || WALKIE_TALKIE_MODE || CONVERSATION_MODE (excluded with this method)),
     * (usually its is called when the app starts, when we change the settings, or when a different language of TEXT_TRANSLATION_MODE or WALKIE_TALKIE_MODE is selected by the user)
     * the language resources are:
     *  - the Mozilla model of srcLang  (if Mozilla is used for this rtranslatorMode)
     *  - the Mozilla model of tgtLang  (if Mozilla is used for this rtranslatorMode)
     *  - the translation dictionary of srcLang  (if useTranslationDictionaries is enabled)
     *  - the translation dictionary of tgtLang  (if useTranslationDictionaries is enabled)
     *  - the tatoeba data structure of the srcLang and tgtLang combo  (if useTatoeba is enabled)
     * <p>
     * To set the language resources for the language combo and rtranslatorMode passed, this method will
     * unload the previous language resources of the previous language combo assigned to this rtranslatorMode
     * and load the new language resources of the new language combo passed, that will also be assigned to this rtranslatorMode.
     * <p>
     * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
     *
     * @param srcLang
     * @param tgtLang
     * @param rtranslatorMode
     * @throws Exception
     */
    public void setLanguageResources(@NonNull CustomLocale srcLang, @NonNull CustomLocale tgtLang, Global.RTranslatorMode rtranslatorMode) throws Exception {
        Log.i("language_resource", "setLanguageResources for "+srcLang.getDisplayLanguage()+", "+tgtLang.getDisplayLanguage()+", "+rtranslatorMode.name());
        CustomLocale[] currentModeResources = null;
        switch (rtranslatorMode) {
            case TEXT_TRANSLATION_MODE:
                currentModeResources = languageResourcesIndicator.textTranslationResources;
                break;
            case WALKIE_TALKIE_MODE:
                currentModeResources = languageResourcesIndicator.walkieTalkieResources;
                break;
            case CONVERSATION_MODE:
                // cannot use this method for conversation mode
                return;
        }
        //we unload the resources of these languages of this mode that will no longer be used by this mode or the others
        for (CustomLocale resource : currentModeResources) {
            if (resource != null && !resource.equals(srcLang) && !resource.equals(tgtLang) && !languageResourcesIndicator.isResourceContainedInOtherModes(resource, rtranslatorMode)) {
                BergamotTranslator.unloadModelFromCache(resource);
                DictionaryTranslator.unloadDictionary(resource);
            }
        }
        if (currentModeResources[0] != null &&
                currentModeResources[1] != null &&
                !(currentModeResources[0].equals(srcLang) && currentModeResources[1].equals(tgtLang)) &&
                !languageResourcesIndicator.isResourcePairContainedInOtherModes(currentModeResources[0], currentModeResources[1], rtranslatorMode)) {
            tatoebaLinks.remove(currentModeResources[0].getISO3Language() + "-" + currentModeResources[1].getISO3Language());
        }
        //we load all the resources of the new languages that are not already loaded
        performLoadLanguageResources(srcLang, tgtLang, rtranslatorMode);
        //we update the indicator to reflect the new resources status
        currentModeResources[0] = srcLang;
        currentModeResources[1] = tgtLang;
        if(isMozillaEnabled(rtranslatorMode)) {
            languageResourcesIndicator.setResourceTypeLoadStatus(rtranslatorMode, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(useTatoeba){
            languageResourcesIndicator.setResourceTypeLoadStatus(rtranslatorMode, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
        if(useTranslationDictionaries){
            languageResourcesIndicator.setResourceTypeLoadStatus(rtranslatorMode, LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY, true);
        }
    }

    /**
     * This method will set the tgtLang of the rtranslatorMode CONVERSATION_MODE (usually when the Conversation mode is started), and will update / load all
     * the language resources of the language combos composed of each srcLang (of each Peer associated with this mode) with this tgtLang,
     * the language resources are:
     *  - the Mozilla model of srcLang  (if Mozilla is used for this rtranslatorMode)
     *  - the Mozilla model of tgtLang  (if Mozilla is used for this rtranslatorMode)
     *  - the translation dictionary of srcLang  (if useTranslationDictionaries is enabled)
     *  - the translation dictionary of tgtLang  (if useTranslationDictionaries is enabled)
     *  - the tatoeba data structure of the srcLang and tgtLang combo  (if useTatoeba is enabled)
     *  So the only updated resources will be the Mozilla models and translation dicts of tgtLang and all the srcLang-tgtLang combos of Tatoeba.
     * <p>
     * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
     * @param tgtLang
     * @throws Exception
     */
    public void setTgtLangResourcesForConversation(CustomLocale tgtLang) throws Exception {
        Log.i("language_resource", "setTgtLangResourcesForConversation for "+tgtLang.getDisplayLanguage());
        //we unload the resources of this mode that will no longer be used by this mode or the others
        CustomLocale resource = languageResourcesIndicator.conversationTgtResource;
        if(resource != null && !resource.equals(tgtLang)){
            if(!languageResourcesIndicator.isResourceContainedInOtherModes(resource, Global.RTranslatorMode.CONVERSATION_MODE)){
                BergamotTranslator.unloadModelFromCache(resource);
                DictionaryTranslator.unloadDictionary(resource);
            }
        }
        for(CustomLocale srcResource: languageResourcesIndicator.conversationSrcResources.values()) {
            if (resource != null &&
                    !(resource.equals(tgtLang)) &&
                    !languageResourcesIndicator.isResourcePairContainedInOtherModes(srcResource, resource, Global.RTranslatorMode.CONVERSATION_MODE)) {
                tatoebaLinks.remove(srcResource.getISO3Language() + "-" + resource.getISO3Language());
            }
        }
        //we load the new resources if are is not already loaded
        for(CustomLocale srcResource: languageResourcesIndicator.conversationSrcResources.values()){
            if(srcResource != null) {
                performLoadLanguageResources(srcResource, tgtLang, Global.RTranslatorMode.CONVERSATION_MODE);
            }
        }
        //we update the indicator to reflect the new resources status
        languageResourcesIndicator.conversationTgtResource = tgtLang;
        if(isMozillaEnabled(Global.RTranslatorMode.CONVERSATION_MODE)) {
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(useTatoeba){
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    /**
     * This method will set / add (if not present) the srcLang and peer combo for the rtranslatorMode CONVERSATION_MODE (usually when a peer is connected or changes language),
     * and will update / load all the language resources of the language combo composed of this srcLang and tgtLang,
     * the language resources are:
     *  - the Mozilla model of srcLang  (if Mozilla is used for this rtranslatorMode)
     *  - the Mozilla model of tgtLang  (if Mozilla is used for this rtranslatorMode)
     *  - the translation dictionary of srcLang  (if useTranslationDictionaries is enabled)
     *  - the translation dictionary of tgtLang  (if useTranslationDictionaries is enabled)
     *  - the tatoeba data structure of the srcLang and tgtLang combo  (if useTatoeba is enabled)
     *  So the only updated resources will be the Mozilla models and translation dicts of this srcLang and this srcLang-tgtLang combo of Tatoeba.
     * <p>
     * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
     * @param srcLang
     * @param peer
     * @throws Exception
     */
    public void setSrcLangResourcesForPeer(CustomLocale srcLang, Peer peer) throws Exception {
        Log.i("language_resource", "setSrcLangResourcesForPeer for "+srcLang.getDisplayLanguage()+", "+peer.getName());
        CustomLocale tgtLang = languageResourcesIndicator.conversationTgtResource != null ? languageResourcesIndicator.conversationTgtResource : global.getLanguage(true);
        HashMap<String, CustomLocale> conversationSrcModels = languageResourcesIndicator.conversationSrcResources;
        //we unload the resources of this peer and mode that will no longer be used by this peer and mode or the others
        CustomLocale resource = conversationSrcModels.get(peer.getUniqueName());
        if(resource != null && !resource.equals(srcLang)){
            if(!languageResourcesIndicator.isSrcResourceContainedInOtherPeers(resource, peer) && !languageResourcesIndicator.isResourceContainedInOtherModes(resource, Global.RTranslatorMode.CONVERSATION_MODE)){
                BergamotTranslator.unloadModelFromCache(resource);
                DictionaryTranslator.unloadDictionary(resource);
            }
        }
        if (resource != null &&
                !(resource.equals(srcLang)) &&
                !languageResourcesIndicator.isResourcePairContainedInOtherPeers(srcLang, tgtLang, peer) &&
                !languageResourcesIndicator.isResourcePairContainedInOtherModes(resource, tgtLang, Global.RTranslatorMode.CONVERSATION_MODE
                )) {
            tatoebaLinks.remove(resource.getISO3Language() + "-" + tgtLang.getISO3Language());
        }
        //we load all the resources of the new languages that are not already loaded
        performLoadLanguageResources(srcLang, tgtLang, Global.RTranslatorMode.CONVERSATION_MODE);
        //we update the indicator to reflect the new models status
        languageResourcesIndicator.conversationSrcResources.put(peer.getUniqueName(), srcLang);
        if(isMozillaEnabled(Global.RTranslatorMode.CONVERSATION_MODE)) {
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(useTatoeba){
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    /**
     * This method will remove the srcLang and peer combo for the rtranslatorMode CONVERSATION_MODE (usually when a Peer is disconnected),
     * and will update / unload all the language resources of the language combo composed of this srcLang and tgtLang,
     * the language resources are:
     *  - the Mozilla model of srcLang  (if Mozilla is used for this rtranslatorMode)
     *  - the Mozilla model of tgtLang  (if Mozilla is used for this rtranslatorMode)
     *  - the translation dictionary of srcLang  (if useTranslationDictionaries is enabled)
     *  - the translation dictionary of tgtLang  (if useTranslationDictionaries is enabled)
     *  - the tatoeba data structure of the srcLang and tgtLang combo  (if useTatoeba is enabled)
     *  So the only updated resources will be the Mozilla models and translation dicts of this srcLang and this srcLang-tgtLang combo of Tatoeba.
     * <p>
     * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
     * @param peer
     */
    public void unloadSrcLangResourcesForPeer(Peer peer){
        Log.i("language_resource", "unloadSrcLangResourcesForPeer for "+peer.getName());
        CustomLocale tgtLang = languageResourcesIndicator.conversationTgtResource != null ? languageResourcesIndicator.conversationTgtResource : global.getLanguage(true);
        HashMap<String, CustomLocale> conversationSrcResources = languageResourcesIndicator.conversationSrcResources;
        //we unload from bergamot the model of this peer and mode that will no longer be used by this peer and mode or the others
        CustomLocale resource = conversationSrcResources.get(peer.getUniqueName());
        if(resource != null){
            if(!languageResourcesIndicator.isSrcResourceContainedInOtherPeers(resource, peer) && !languageResourcesIndicator.isResourceContainedInOtherModes(resource, Global.RTranslatorMode.CONVERSATION_MODE)){
                BergamotTranslator.unloadModelFromCache(resource);
                DictionaryTranslator.unloadDictionary(resource);
            }
        }
        if (resource != null &&
                !(resource.equals(tgtLang)) &&
                !languageResourcesIndicator.isResourcePairContainedInOtherModes(resource, tgtLang, Global.RTranslatorMode.CONVERSATION_MODE)) {
            tatoebaLinks.remove(resource.getISO3Language() + "-" + tgtLang.getISO3Language());
        }
        //we update the indicator to reflect the new models status
        conversationSrcResources.remove(peer.getUniqueName());
    }

    /**
     * This method will remove the tgtLang, all the srcLang and peer combo of the rtranslatorMode CONVERSATION_MODE (usually when we exit the Conversation mode),
     * and will update / unload all the language resources of the language combo associated to the langs of CONVERSATION_MODE.
     * <p>
     * Also note that the language resources will be transparently recycled between all the different languages, language combos and modes of this manager.
     */
    public void unloadAllLangResourcesForConversation(){
        Log.i("language_resource", "unloadAllLangResourcesForConversation");
        // unload of all the resources of the conversationSrcResources
        CustomLocale tgtLang = languageResourcesIndicator.conversationTgtResource != null ? languageResourcesIndicator.conversationTgtResource : global.getLanguage(true);
        HashMap<String, CustomLocale> conversationSrcResources = languageResourcesIndicator.conversationSrcResources;
        for(CustomLocale srcResource : conversationSrcResources.values()){
            if(srcResource != null && !languageResourcesIndicator.isResourceContainedInOtherModes(srcResource, Global.RTranslatorMode.CONVERSATION_MODE)){
                BergamotTranslator.unloadModelFromCache(srcResource);
                DictionaryTranslator.unloadDictionary(srcResource);
            }
        }
        for(CustomLocale srcResource: languageResourcesIndicator.conversationSrcResources.values()) {
            if (srcResource != null &&
                    !languageResourcesIndicator.isResourcePairContainedInOtherModes(srcResource, tgtLang, Global.RTranslatorMode.CONVERSATION_MODE)) {
                tatoebaLinks.remove(srcResource.getISO3Language() + "-" + tgtLang.getISO3Language());
            }
        }
        // unload of the mozilla models of conversationTgtResource (all tatoeba resources have already been removed in the loop above)
        CustomLocale resource = languageResourcesIndicator.conversationTgtResource;
        if(resource != null && !languageResourcesIndicator.isResourceContainedInOtherModes(resource, Global.RTranslatorMode.CONVERSATION_MODE)){
            BergamotTranslator.unloadModelFromCache(resource);
            DictionaryTranslator.unloadDictionary(resource);
        }
        //we update the indicator to reflect the new models status
        languageResourcesIndicator.conversationTgtResource = null;
        languageResourcesIndicator.conversationSrcResources = new HashMap<>();
    }

    /**
     * This method is called to update a Peer associated with CONVERSATION_MODE
     * @param oldPeer
     * @param newPeer
     */
    public void updatePeer(Peer oldPeer, Peer newPeer){
        languageResourcesIndicator.updatePeer(oldPeer, newPeer);
    }

    public boolean isPeerLoaded(@Nullable Peer peer){
        if(peer != null) {
            return languageResourcesIndicator.conversationSrcResources.containsKey(peer.getUniqueName());
        }
        return false;
    }


    public Map<String, LinksData.DataMap> getTatoebaLinks() {
        return tatoebaLinks;
    }

    public TatoebaDbWrapper getTatoebaDb() {
        return tatoebaDb;
    }

    public String detectLanguageWithLingua(String text){
        if (linguaLanguageDetector == null || text == null || text.trim().isEmpty()) {
            return "und";
        }

        try {
            long time = System.currentTimeMillis();
            Language detectedLanguage = linguaLanguageDetector.detectLanguageOf(text);
            android.util.Log.i("lid_performance", "Lingua language identification done in: " + (System.currentTimeMillis()-time) + "ms");

            // Intercept cases where Lingua cannot confidently identify the language
            if (detectedLanguage == Language.UNKNOWN || detectedLanguage.getIsoCode639_3() == IsoCode639_3.NONE) {
                return "und";
            }

            // getIsoCode639_3() yields an enum constant (e.g. ENG, FRA)
            // We read its string name and drop it to lowercase to match the "und" standard format
            return detectedLanguage.getIsoCode639_3().name().toLowerCase(Locale.ROOT);

        } catch (Exception e) {
            // Fallback safety barrier against unexpected platform/string parsing hiccups
            return "und";
        }
    }


    private boolean isTgtLangResourcesForConversationLoaded(){
        return languageResourcesIndicator.conversationTgtResource != null;
    }

    private void loadMozillaResourcesForPeers() throws Exception {
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.MOZILLA)) {
            for (CustomLocale resource : languageResourcesIndicator.getUniqueSrcConversationResources()) {
                BergamotTranslator.loadModelIntoCache(global, resource);
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
    }

    private void loadTatoebaResourcesForPeers(){
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.TATOEBA)) {
            for (String langPairCode : languageResourcesIndicator.getUniqueSrcConversationResourcePairs()) {
                String[] langCodes = langPairCode.split("-");
                String srcLangCode = langCodes[0];
                String tgtLangCode = langCodes[1];
                if(!tatoebaLinks.containsKey(langPairCode)) {
                    LinksData.DataMap links = tatoebaDb.getLinkData(srcLangCode, tgtLangCode);
                    tatoebaLinks.put(
                            langPairCode,
                            links
                    );
                }
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    private void loadTranslationDictionariesResourcesForPeers(){
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY)) {
            for (CustomLocale resource : languageResourcesIndicator.getUniqueSrcConversationResources()) {
                DictionaryTranslator.loadDictionary(resource);
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY, true);
        }
    }

    private void unloadAllMozillaResources(){
        /*for (CustomLocale resource : languageResourcesIndicator.getAllUniqueResources()) {
            BergamotTranslator.unloadModelFromCache(resource);
        }*/
        BergamotTranslator.cleanup();
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.MOZILLA, false);
    }

    private void unloadAllTatoebaResources() {
        for (String langPairCode : languageResourcesIndicator.getAllUniqueResourcePairs()) {
            tatoebaLinks.remove(langPairCode);
        }
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TATOEBA, false);
    }

    private void unloadAllTranslationDictionariesResources(){
        //we unload all the dicts resources, except for the ones used by WalkieTalkie mode, where the dicts must be kept in memory for the LID
        for (CustomLocale resource : languageResourcesIndicator.getAllUniqueResources()) {
            boolean found = false;
            for(CustomLocale walkieTalkieRes : languageResourcesIndicator.walkieTalkieResources){
                if(walkieTalkieRes.equalsLanguage(resource)){
                    found = true;
                    break;
                }
            }
            if(!found){
                DictionaryTranslator.unloadDictionary(resource);
            }
        }
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY, false);
    }

    private void performLoadLanguageResources(@NonNull CustomLocale srcLang, @NonNull CustomLocale tgtLang, Global.RTranslatorMode rtranslatorMode) throws Exception {
        Log.d("language_resources", "Language loaded: "+srcLang.getLanguage());
        long time = System.currentTimeMillis();
        if(isMozillaEnabled(rtranslatorMode)){
            if (!srcLang.getLanguage().equals("en")) {
                BergamotTranslator.loadModelIntoCache(global, srcLang);
            }
            if (!tgtLang.getLanguage().equals("en")) {
                BergamotTranslator.loadModelIntoCache(global, tgtLang);
            }
        }
        if(useTranslationDictionaries || rtranslatorMode == Global.RTranslatorMode.WALKIE_TALKIE_MODE){  // WalkieTalkie mode must always have dicts loaded for LID
            if (!srcLang.getISO3Language().equals("eng")) {
                DictionaryTranslator.loadDictionary(srcLang);
            }
            if (!tgtLang.getISO3Language().equals("eng")) {
                DictionaryTranslator.loadDictionary(tgtLang);
            }
        }
        if(useTatoeba){
            String langPairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
            if(!tatoebaLinks.containsKey(langPairCode)){
                LinksData.DataMap links = tatoebaDb.getLinkData(srcLang.getISO3Language(), tgtLang.getISO3Language());
                tatoebaLinks.put(
                        langPairCode,
                        links
                );
            }
        }
        //load of lingua resources for language detection in walkieTalkie mode
        if(rtranslatorMode == Global.RTranslatorMode.WALKIE_TALKIE_MODE) {
            if(linguaLanguageDetector != null) linguaLanguageDetector.unloadLanguageModels();
            linguaLanguageDetector = buildDetectorFromLocales(new CustomLocale[]{srcLang, tgtLang});
        }
        Log.d("performance_resources", "Resources loaded in: "+(System.currentTimeMillis()-time));
    }

    private static LanguageDetector buildDetectorFromLocales(CustomLocale[] locales) {
        List<IsoCode639_3> linguaIsoCodes = new ArrayList<>();

        // Map and filter valid 3-letter enum mappings
        for (CustomLocale locale : locales) {
            try {
                // Explicitly fetching the 3-letter ISO code string
                String upperCaseLanguage = locale.getISO3Language().toUpperCase(Locale.ROOT);
                IsoCode639_3 code = IsoCode639_3.valueOf(upperCaseLanguage);

                // Prevent duplicate entries in our config list
                if (!linguaIsoCodes.contains(code)) {
                    linguaIsoCodes.add(code);
                }
            } catch (MissingResourceException | IllegalArgumentException e) {
                // MissingResourceException: thrown if Java/Android lacks a 3-letter map for the locale
                // IllegalArgumentException: thrown if the code isn't supported by Lingua
                // Both are skipped safely to prevent runtime crashes
            }
        }

        // Validate constraint (Lingua requires at least 2 languages to build)
        if (linguaIsoCodes.size() >= 2) {
            IsoCode639_3[] codesArray = linguaIsoCodes.toArray(new IsoCode639_3[0]);
            return LanguageDetectorBuilder
                    .fromIsoCodes639_3(codesArray)
                    .build();
        }

        return null; // Fallback if an adequate language pool couldn't be formed
    }

    private boolean isMozillaEnabled(Global.RTranslatorMode rtranslatorMode){
        return useMozillaForTranslation || ((rtranslatorMode == Global.RTranslatorMode.WALKIE_TALKIE_MODE || rtranslatorMode == Global.RTranslatorMode.CONVERSATION_MODE) && useMozillaForVoiceTranslation);
    }
}
