package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.tools.CustomLocale;

public class LanguageResourcesIndicator {
    public enum ResourceType {
        MOZILLA,
        TATOEBA,
        TRANSLATION_DICTIONARY
    }
    public CustomLocale[] textTranslationResources = new CustomLocale[2];
    public CustomLocale[] walkieTalkieResources = new CustomLocale[2];
    @Nullable
    public CustomLocale conversationTgtResource = null;  //the target model of conversation translation is always our language for every peer
    public HashMap<String, CustomLocale> conversationSrcResources = new HashMap<>();   // key: peer uniqueName, value: srcLanguage
    private ArrayList<ResourceType> textTranslationResourcesLoaded = new ArrayList<>(3);
    private ArrayList<ResourceType> walkieTalkieResourcesLoaded = new ArrayList<>(3);
    private ArrayList<ResourceType> conversationResourcesLoaded = new ArrayList<>(3);

    public ArrayList<CustomLocale> getAllUniqueResources(){
        ArrayList<CustomLocale> resources = new ArrayList<>();
        for (CustomLocale resource: textTranslationResources) {
            if(resource != null && !resources.contains(resource)){
                resources.add(resource);
            }
        }
        for (CustomLocale resource: walkieTalkieResources) {
            if(resource != null && !resources.contains(resource)){
                resources.add(resource);
            }
        }
        for (CustomLocale resource : conversationSrcResources.values()) {
            if (resource != null && !resources.contains(resource)) {
                resources.add(resource);
            }
        }
        if(conversationTgtResource != null && resources.contains(conversationTgtResource)){
            resources.add(conversationTgtResource);
        }
        return resources;
    }

    public ArrayList<String> getAllUniqueResourcePairs(){
        ArrayList<String> pairs = new ArrayList<>();
        if(textTranslationResources[0] != null && textTranslationResources[1] != null){
            String pairCode = textTranslationResources[0].getISO3Language()+"-"+textTranslationResources[1].getISO3Language();
            if(!pairs.contains(pairCode)){
                pairs.add(pairCode);
            }
        }
        if(walkieTalkieResources[0] != null && walkieTalkieResources[1] != null){
            String pairCode = walkieTalkieResources[0].getISO3Language()+"-"+walkieTalkieResources[1].getISO3Language();
            if(!pairs.contains(pairCode)){
                pairs.add(pairCode);
            }
        }
        if(conversationTgtResource != null) {
            for (CustomLocale srcResource : conversationSrcResources.values()) {
                if (srcResource != null) {
                    String pairCode = srcResource.getISO3Language()+"-"+conversationTgtResource.getISO3Language();
                    if(!pairs.contains(pairCode)){
                        pairs.add(pairCode);
                    }
                }
            }
        }
        return pairs;
    }

    public boolean isResourceContainedInOtherModes(CustomLocale lang, Global.RTranslatorMode currentMode){
        if(currentMode != Global.RTranslatorMode.TEXT_TRANSLATION_MODE && Arrays.asList(textTranslationResources).contains(lang)){
            return true;
        }
        if(currentMode != Global.RTranslatorMode.WALKIE_TALKIE_MODE && Arrays.asList(walkieTalkieResources).contains(lang)){
            return true;
        }
        if(currentMode != Global.RTranslatorMode.CONVERSATION_MODE){
            if(conversationSrcResources.containsValue(lang)){
                return true;
            }
            if(conversationTgtResource != null && conversationTgtResource.equals(lang)){
                return true;
            }
        }
        return false;
    }

    public boolean isSrcResourceContainedInOtherPeers(CustomLocale lang, Peer peer){
        for (Map.Entry<String, CustomLocale> entry : conversationSrcResources.entrySet()) {
            String key = entry.getKey();
            CustomLocale value = entry.getValue();

            if(!key.equals(peer.getUniqueName())){
                if(value.equals(lang)){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isResourcePairContainedInOtherModes(CustomLocale srcLang, CustomLocale tgtLang, Global.RTranslatorMode currentMode){
        if(currentMode != Global.RTranslatorMode.TEXT_TRANSLATION_MODE){
            if(textTranslationResources[0] != null && textTranslationResources[1] != null) {
                String pairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
                String pairCodeResource = textTranslationResources[0].getISO3Language() + "-" + textTranslationResources[1].getISO3Language();
                if(pairCode.equals(pairCodeResource)) {
                    return true;
                }
            }
        }
        if(currentMode != Global.RTranslatorMode.WALKIE_TALKIE_MODE){
            if(walkieTalkieResources[0] != null && walkieTalkieResources[1] != null) {
                String pairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
                String pairCodeResource = walkieTalkieResources[0].getISO3Language() + "-" + walkieTalkieResources[1].getISO3Language();
                if(pairCode.equals(pairCodeResource)) {
                    return true;
                }
            }
        }
        if(currentMode != Global.RTranslatorMode.CONVERSATION_MODE){
            for (CustomLocale srcResource : conversationSrcResources.values()) {
                if (srcResource != null && conversationTgtResource != null) {
                    String pairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
                    String pairCodeResource = srcResource.getISO3Language()+"-"+conversationTgtResource.getISO3Language();
                    if(pairCode.equals(pairCodeResource)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isResourcePairContainedInOtherPeers(CustomLocale srcLang, CustomLocale tgtLang, Peer peer){
        for (Map.Entry<String, CustomLocale> entry : conversationSrcResources.entrySet()) {
            String key = entry.getKey();
            CustomLocale value = entry.getValue();

            if(!key.equals(peer.getUniqueName()) && value != null && conversationTgtResource != null){
                String pairCode = srcLang.getISO3Language() + "-" + tgtLang.getISO3Language();
                String pairCodeResource = value.getISO3Language()+"-"+conversationTgtResource.getISO3Language();
                if(pairCode.equals(pairCodeResource)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isResourceTypeLoaded(Global.RTranslatorMode rtranslatorMode, ResourceType resourceType){
        switch (rtranslatorMode){
            case TEXT_TRANSLATION_MODE:
                if(textTranslationResourcesLoaded.contains(resourceType)){
                    return true;
                }
                break;
            case WALKIE_TALKIE_MODE:
                if(walkieTalkieResourcesLoaded.contains(resourceType)){
                    return true;
                }
                break;
            case CONVERSATION_MODE:
                if(conversationResourcesLoaded.contains(resourceType)){
                    return true;
                }
                break;
        }
        return false;
    }

    public boolean isResourceTypeLoaded(ResourceType resourceType){
        return textTranslationResourcesLoaded.contains(resourceType) || walkieTalkieResourcesLoaded.contains(resourceType) || conversationResourcesLoaded.contains(resourceType);
    }

    public void setResourceTypeLoadStatus(Global.RTranslatorMode rtranslatorMode, ResourceType resourceType, boolean loaded){
        switch (rtranslatorMode){
            case TEXT_TRANSLATION_MODE:
                if(textTranslationResourcesLoaded.contains(resourceType) && !loaded){
                    textTranslationResourcesLoaded.remove(resourceType);
                }else if(!textTranslationResourcesLoaded.contains(resourceType) && loaded){
                    textTranslationResourcesLoaded.add(resourceType);
                }
                break;
            case WALKIE_TALKIE_MODE:
                if(walkieTalkieResourcesLoaded.contains(resourceType) && !loaded){
                    walkieTalkieResourcesLoaded.remove(resourceType);
                }else if(!walkieTalkieResourcesLoaded.contains(resourceType) && loaded){
                    walkieTalkieResourcesLoaded.add(resourceType);
                }
                break;
            case CONVERSATION_MODE:
                if(conversationResourcesLoaded.contains(resourceType) && !loaded){
                    conversationResourcesLoaded.remove(resourceType);
                }else if(!conversationResourcesLoaded.contains(resourceType) && loaded){
                    conversationResourcesLoaded.add(resourceType);
                }
                break;
        }
    }

    public void setResourceTypeLoadStatus(ResourceType resourceType, boolean loaded){
        if(textTranslationResourcesLoaded.contains(resourceType) && !loaded){
            textTranslationResourcesLoaded.remove(resourceType);
        }else if(!textTranslationResourcesLoaded.contains(resourceType) && loaded){
            textTranslationResourcesLoaded.add(resourceType);
        }

        if(walkieTalkieResourcesLoaded.contains(resourceType) && !loaded){
            walkieTalkieResourcesLoaded.remove(resourceType);
        }else if(!walkieTalkieResourcesLoaded.contains(resourceType) && loaded){
            walkieTalkieResourcesLoaded.add(resourceType);
        }

        if(conversationResourcesLoaded.contains(resourceType) && !loaded){
            conversationResourcesLoaded.remove(resourceType);
        }else if(!conversationResourcesLoaded.contains(resourceType) && loaded){
            conversationResourcesLoaded.add(resourceType);
        }
    }

    public boolean areAllResourceTypeUnloaded(){
        return textTranslationResourcesLoaded.isEmpty() && walkieTalkieResourcesLoaded.isEmpty() && conversationResourcesLoaded.isEmpty();
    }

    public void updatePeer(Peer oldPeer, Peer newPeer){
        if(!oldPeer.getUniqueName().equals(newPeer.getUniqueName()) && conversationSrcResources.containsKey(oldPeer.getUniqueName())) {
            CustomLocale resource = conversationSrcResources.remove(oldPeer.getUniqueName());
            conversationSrcResources.put(newPeer.getUniqueName(), resource);
        }
    }

    public void reset(){
        textTranslationResources = new CustomLocale[2];
        walkieTalkieResources = new CustomLocale[2];
        conversationSrcResources = new HashMap<>();
        conversationTgtResource = null;
    }
}
