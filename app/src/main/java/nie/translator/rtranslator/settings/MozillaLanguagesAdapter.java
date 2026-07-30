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

package nie.translator.rtranslator.settings;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.tools.SortedArrayList;
import nie.translator.rtranslator.tools.gui.ResourceManagerView;

public class MozillaLanguagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
    private enum ItemType {
        ITEM_MODEL,
        HEADER_INSTALLED,
        HEADER_AVAILABLE,
    }
    public enum ItemTypeExtended {
        ITEM_MODEL_INSTALLED,
        ITEM_MODEL_AVAILABLE,
        HEADER_INSTALLED,
        HEADER_AVAILABLE,
    }
    private final Comparator<ResourceManager> comparator = (o1, o2) -> o1.getTitle() != null && o2.getTitle() != null ? o1.getTitle().compareTo(o2.getTitle()) : 0;
    private final SortedArrayList<ResourceManager> modelsInstalled = new SortedArrayList<>(comparator);
    private final SortedArrayList<ResourceManager> modelsAvailable = new SortedArrayList<>(comparator);


    public MozillaLanguagesAdapter(ArrayList<ResourceManager> modelsInstalled, ArrayList<ResourceManager> modelsAvailable) {
        this.modelsInstalled.addAll(modelsInstalled);
        this.modelsAvailable.addAll(modelsAvailable);
        notifyItemRangeInserted(0, getItemCount());  // + eventual 2 headers
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if(viewType == ItemType.ITEM_MODEL.ordinal()) {
            // Ensure layout parameters are set correctly
            ResourceManagerView view = new ResourceManagerView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return new ModelHolder(view);
        } else if(viewType == ItemType.HEADER_INSTALLED.ordinal()) {
            return new HeaderInstalledHolder(LayoutInflater.from(parent.getContext()), parent);
        } else if(viewType == ItemType.HEADER_AVAILABLE.ordinal()) {
            return new HeaderAvailableHolder(LayoutInflater.from(parent.getContext()), parent);
        }
        return new ModelHolder(new ResourceManagerView(parent.getContext()));  // to not return null
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ResourceManagerItem item = getItem(position);
        if(holder instanceof ModelHolder && item instanceof ResourceManager){
            ModelHolder modelHolder = (ModelHolder) holder;
            ResourceManager resourceManager = (ResourceManager) item;
            modelHolder.setResourceManager(resourceManager);
            resourceManager.setView((modelHolder.getView()));
        }
    }

    @Nullable
    public ResourceManagerItem getItem(int position){
        ComposedIndex composedIndex = getComposedIndex(position);
        if(composedIndex != null) {
            if(composedIndex.itemType == ItemTypeExtended.HEADER_INSTALLED){
                return new ResourceManagerHeaderInstalled();
            }
            if(composedIndex.itemType == ItemTypeExtended.HEADER_AVAILABLE){
                return new ResourceManagerHeaderAvailable();
            }
            if(composedIndex.itemType == ItemTypeExtended.ITEM_MODEL_INSTALLED){
                return modelsInstalled.get(composedIndex.index);
            }
            if(composedIndex.itemType == ItemTypeExtended.ITEM_MODEL_AVAILABLE){
                return modelsAvailable.get(composedIndex.index);
            }
        }
        return null;
    }

    public int getCompleteIndex(ItemTypeExtended itemType, int index){
        int base = 0;
        if(itemType == ItemTypeExtended.HEADER_INSTALLED){
            if(!modelsInstalled.isEmpty()){
                return 0;
            }else{
                return -1;
            }
        }
        if(itemType == ItemTypeExtended.ITEM_MODEL_INSTALLED){
            if(!modelsInstalled.isEmpty()){
                return index + 1;
            }else{
                return -1;
            }
        }
        base = !modelsInstalled.isEmpty() ? modelsInstalled.size() : 0;
        if(itemType == ItemTypeExtended.HEADER_AVAILABLE){
            if(!modelsAvailable.isEmpty()){
                return base;
            }
        }
        base += 1;
        if(itemType == ItemTypeExtended.ITEM_MODEL_AVAILABLE){
            if(!modelsAvailable.isEmpty()){
                return base + index;
            }else{
                return -1;
            }
        }
        return -1;
    }

    public ComposedIndex getComposedIndex(int position){
        if(position >= 0 && position < getItemCount() && !(modelsInstalled.isEmpty() && modelsAvailable.isEmpty())) {
            if (position == 0) {
                if (!modelsInstalled.isEmpty()) {
                    return new ComposedIndex(ItemTypeExtended.HEADER_INSTALLED, 0);
                } else {
                    return new ComposedIndex(ItemTypeExtended.HEADER_AVAILABLE, 0);
                }
            }
            if (!modelsInstalled.isEmpty() && position < modelsInstalled.size() + 1) {
                return new ComposedIndex(ItemTypeExtended.ITEM_MODEL_INSTALLED, position-1);
            }
            if (!modelsInstalled.isEmpty() && position == modelsInstalled.size() + 1) {
                return new ComposedIndex(ItemTypeExtended.HEADER_AVAILABLE, 0);
            }
            int offset = !modelsInstalled.isEmpty() ? position - (modelsInstalled.size() + 1) : position - 1;
            if (offset < modelsAvailable.size()) {
                return new ComposedIndex(ItemTypeExtended.ITEM_MODEL_AVAILABLE, offset);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        ResourceManagerItem item = getItem(position);
        if (item instanceof ResourceManagerHeaderInstalled) {
            return ItemType.HEADER_INSTALLED.ordinal();
        } else if (item instanceof ResourceManagerHeaderAvailable) {
            return ItemType.HEADER_AVAILABLE.ordinal();
        } else  {   //instanceof ResourceManager
            return ItemType.ITEM_MODEL.ordinal();
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return (!modelsInstalled.isEmpty() ? modelsInstalled.size() : 0) + (!modelsAvailable.isEmpty() ? modelsAvailable.size() : 0);
    }

    public void setStatus(DownloadGroupInfo item, ResourceManagerView.State state, int progress) {
        setState(item, state, false);
        setProgress(item, progress);
    }

    public void setState(DownloadGroupInfo item, ResourceManagerView.State state, boolean animate){
        int indexInstalled = modelsInstalled.indexOf(item);
        if(indexInstalled != -1){
            modelsInstalled.get(indexInstalled).setState(state, animate);
            if(state != ResourceManagerView.State.DOWNLOADED){
                // in this case we move the row from modelsInstalled to modelsAvailable
                ResourceManager resourceManager = modelsInstalled.remove(indexInstalled);
                int newIndex = modelsAvailable.addOrdered(resourceManager);
                notifyItemMoved(getCompleteIndex(ItemTypeExtended.ITEM_MODEL_INSTALLED, indexInstalled), getCompleteIndex(ItemTypeExtended.ITEM_MODEL_AVAILABLE, newIndex));
            }
            return;
        }
        int indexAvailable = modelsAvailable.indexOf(item);
        if(indexAvailable != -1){
            modelsAvailable.get(indexAvailable).setState(state, animate);
            if(state == ResourceManagerView.State.DOWNLOADED){
                // in this case we move the row from modelsAvailable to modelsInstalled
                ResourceManager resourceManager = modelsAvailable.remove(indexAvailable);
                int newIndex = modelsInstalled.addOrdered(resourceManager);
                notifyItemMoved(getCompleteIndex(ItemTypeExtended.ITEM_MODEL_AVAILABLE, indexAvailable), getCompleteIndex(ItemTypeExtended.ITEM_MODEL_INSTALLED, newIndex));
            }
        }
    }

    public void setProgress(DownloadGroupInfo item, int progress){
        int indexInstalled = modelsInstalled.indexOf(item);
        if(indexInstalled != -1){
            modelsInstalled.get(indexInstalled).setProgress(progress);
            return;
        }
        int indexAvailable = modelsAvailable.indexOf(item);
        if(indexAvailable != -1){
            modelsAvailable.get(indexAvailable).setProgress(progress);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if(holder instanceof ModelHolder){
            ModelHolder modelHolder = (ModelHolder) holder;
            if(modelHolder.getResourceManager() != null) {
                modelHolder.getResourceManager().setView(null);
                modelHolder.setResourceManager(null);
            }
        }
    }

    @Override
    public boolean onFailedToRecycleView(@NonNull RecyclerView.ViewHolder holder) {
        return super.onFailedToRecycleView(holder);
    }

    /** The layout for each item in the RecycleView list*/
    private static class ModelHolder extends RecyclerView.ViewHolder {
        private final ResourceManagerView resourceManagerView;
        @Nullable
        private ResourceManager resourceManager;

        ModelHolder(ResourceManagerView resourceManagerView) {
            super(resourceManagerView);
            this.resourceManagerView = resourceManagerView;
        }

        public void setResourceManager(@Nullable ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }

        @Nullable
        public ResourceManager getResourceManager() {
            return resourceManager;
        }

        public ResourceManagerView getView() {
            return resourceManagerView;
        }
    }

    /** The layout for each item in the RecycleView list*/
    private static class HeaderInstalledHolder extends RecyclerView.ViewHolder {
        HeaderInstalledHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_row_models_installed, parent, false));
        }
    }

    /** The layout for each item in the RecycleView list*/
    private static class HeaderAvailableHolder extends RecyclerView.ViewHolder {
        HeaderAvailableHolder(LayoutInflater inflater, ViewGroup parent) {
            super(inflater.inflate(R.layout.component_row_models_available, parent, false));
        }
    }

    public interface ResourceManagerItem {

    }

    public static class ResourceManagerHeaderInstalled implements ResourceManagerItem {

    }

    public static class ResourceManagerHeaderAvailable implements ResourceManagerItem {

    }

    public static class ComposedIndex {
        public ItemTypeExtended itemType;
        public int index;

        public ComposedIndex(ItemTypeExtended itemType, int index) {
            this.itemType = itemType;
            this.index = index;
        }
    }
}
