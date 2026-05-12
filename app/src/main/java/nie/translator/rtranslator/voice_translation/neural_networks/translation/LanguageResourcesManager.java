package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import static nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator.MOZILLA;

import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.databases.tatoeba.LinksData;
import nie.translator.rtranslator.databases.tatoeba.TatoebaDbWrapper;
import nie.translator.rtranslator.tools.CustomLocale;

//todo: gestire la concorrenza (qui o in translator, dipende dall'esecuzione di mozilla e della ricerca in tatoeba)

public class LanguageResourcesManager {
    private Global global;
    private final LanguageResourcesIndicator languageResourcesIndicator = new LanguageResourcesIndicator();
    private final TatoebaDbWrapper tatoebaDb;
    private final Map<String, LinksData.DataMap> tatoebaLinks = new HashMap<>();
    private int modelMode;

    public LanguageResourcesManager(@NonNull Global global, int modelMode, CustomLocale firstTextLanguage, CustomLocale secondTextLanguage, CustomLocale firstLanguage, CustomLocale secondLanguage) throws Exception {
        this.global = global;
        this.modelMode = modelMode;
        String tatoebaDbPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/Tatoeba/tatoeba.db";
        String translationDictionariesDbPath = Environment.getExternalStorageDirectory().getPath() + "/models/Translation/TranslationDictionaries/translation_dict_ordered.db";
        tatoebaDb = new TatoebaDbWrapper(tatoebaDbPath);
        BergamotTranslator.initializeService();
        DictionaryTranslator.initializeService(translationDictionariesDbPath);
        loadLanguageResources(firstTextLanguage, secondTextLanguage, Global.RTranslatorMode.TEXT_TRANSLATION_MODE);
        loadLanguageResources(firstLanguage, secondLanguage, Global.RTranslatorMode.WALKIE_TALKIE_MODE);
    }

    public void setModelMode(int modelMode) {  //todo: eliminarlo se non servirà (in base a come gestiremo il cambio di mode)
        this.modelMode = modelMode;
    }

    public Map<String, LinksData.DataMap> getTatoebaLinks() {
        return tatoebaLinks;
    }

    public TatoebaDbWrapper getTatoebaDb() {
        return tatoebaDb;
    }

    public void loadLanguageResources(@NonNull CustomLocale srcLang, @NonNull CustomLocale tgtLang, Global.RTranslatorMode rtranslatorMode) throws Exception {
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
        currentModeResources[0] = !srcLang.getLanguage().equals("en") ? srcLang : null;
        currentModeResources[1] = !tgtLang.getLanguage().equals("en") ? tgtLang : null;
        if(modelMode == MOZILLA) {
            languageResourcesIndicator.setResourceTypeLoadStatus(rtranslatorMode, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(global.isUseTatoeba()){
            languageResourcesIndicator.setResourceTypeLoadStatus(rtranslatorMode, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    public boolean isPeerLoaded(@Nullable Peer peer){
        if(peer != null) {
            return languageResourcesIndicator.conversationSrcResources.containsKey(peer.getUniqueName());
        }
        return false;
    }

    public void loadSrcLangResourcesForPeer(CustomLocale srcLang, Peer peer) throws Exception {
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
        if(modelMode == MOZILLA) {
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(global.isUseTatoeba()){
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    public void loadTgtLangResourcesForConversation(CustomLocale tgtLang) throws Exception {
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
        if(modelMode == MOZILLA) {
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
        if(global.isUseTatoeba()){
            languageResourcesIndicator.setResourceTypeLoadStatus(Global.RTranslatorMode.CONVERSATION_MODE, LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    public void unloadSrcLangResourcesForPeer(Peer peer){
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

    public void unloadAllLangResourcesForConversation(){
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

    public void loadAllMozillaResources() throws Exception {
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.MOZILLA)) {
            for (CustomLocale resource : languageResourcesIndicator.getAllUniqueResources()) {
                BergamotTranslator.loadModelIntoCache(global, resource);
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.MOZILLA, true);
        }
    }

    public void loadAllTatoebaResources(){
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.TATOEBA)) {
            for (String langPairCode : languageResourcesIndicator.getAllUniqueResourcePairs()) {
                String[] langCodes = langPairCode.split("-");
                String srcLangCode = langCodes[0];
                String tgtLangCode = langCodes[1];
                LinksData.DataMap links = tatoebaDb.getLinkData(srcLangCode, tgtLangCode);
                tatoebaLinks.put(
                        langPairCode,
                        links
                );
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TATOEBA, true);
        }
    }

    public void loadAllTranslationDictionariesResources(){
        if(!languageResourcesIndicator.isResourceTypeLoaded(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY)) {
            for (CustomLocale resource : languageResourcesIndicator.getAllUniqueResources()) {
                DictionaryTranslator.loadDictionary(resource);
            }
            languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY, true);
        }
    }

    public void unloadAllMozillaResources(){
        /*for (CustomLocale resource : languageResourcesIndicator.getAllUniqueResources()) {
            BergamotTranslator.unloadModelFromCache(resource);
        }*/
        BergamotTranslator.cleanup();
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.MOZILLA, false);
    }

    public void unloadAllTatoebaResources(){
        for (String langPairCode : languageResourcesIndicator.getAllUniqueResourcePairs()) {
            tatoebaLinks.remove(langPairCode);
        }
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TATOEBA, false);
    }

    public void updatePeer(Peer oldPeer, Peer newPeer){
        languageResourcesIndicator.updatePeer(oldPeer, newPeer);
    }

    public void unloadAllTranslationDictionariesResources(){
        DictionaryTranslator.cleanup();
        languageResourcesIndicator.setResourceTypeLoadStatus(LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY, false);
    }

    private void performLoadLanguageResources(@NonNull CustomLocale srcLang, @NonNull CustomLocale tgtLang, Global.RTranslatorMode rtranslatorMode) throws Exception {
        Log.d("language_resources", "Language loaded: "+srcLang.getLanguage());
        if(modelMode == MOZILLA){
            ArrayList<CustomLocale> allUniqueResources = languageResourcesIndicator.getAllUniqueResources();
            boolean initialLoad = languageResourcesIndicator.isResourceTypeLoaded(rtranslatorMode, LanguageResourcesIndicator.ResourceType.MOZILLA);
            if (!srcLang.getLanguage().equals("en") && (initialLoad || !allUniqueResources.contains(srcLang))) {
                BergamotTranslator.loadModelIntoCache(global, srcLang);
            }
            if (!tgtLang.getLanguage().equals("en") && (initialLoad || !allUniqueResources.contains(tgtLang))) {
                BergamotTranslator.loadModelIntoCache(global, tgtLang);
            }
        }
        if(global.isUseTranslationDictionaries()){
            ArrayList<CustomLocale> allUniqueResources = languageResourcesIndicator.getAllUniqueResources();
            boolean initialLoad = languageResourcesIndicator.isResourceTypeLoaded(rtranslatorMode, LanguageResourcesIndicator.ResourceType.TRANSLATION_DICTIONARY);
            if (!srcLang.getISO3Language().equals("eng") && (initialLoad || !allUniqueResources.contains(srcLang))) {
                DictionaryTranslator.loadDictionary(srcLang);
            }
            if (!tgtLang.getISO3Language().equals("eng") && (initialLoad || !allUniqueResources.contains(tgtLang))) {
                DictionaryTranslator.loadDictionary(tgtLang);
            }
        }
        if(global.isUseTatoeba()){
            ArrayList<String> allUniqueResourcePais = languageResourcesIndicator.getAllUniqueResourcePairs();
            boolean initialLoad = languageResourcesIndicator.isResourceTypeLoaded(rtranslatorMode, LanguageResourcesIndicator.ResourceType.TATOEBA);
            String langPairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
            if(initialLoad || !allUniqueResourcePais.contains(langPairCode)){
                LinksData.DataMap links = tatoebaDb.getLinkData(srcLang.getISO3Language(), tgtLang.getISO3Language());
                tatoebaLinks.put(
                        langPairCode,
                        links
                );
            }
        }
    }
}
