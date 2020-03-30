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

package nie.translator.rtranslatordevedition.tools.gui;

import androidx.annotation.Nullable;
import nie.translator.rtranslatordevedition.tools.gui.peers.Listable;

public class WeightElement implements Comparable {
    private Listable element;
    private int weight=0;

    public WeightElement(Listable element, int weight) {
        this.element = element;
        this.weight = weight;
    }

    public WeightElement(Listable element) {
        this.element = element;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public Listable getElement() {
        return element;
    }

    public void setElement(Listable element) {
        this.element = element;
    }


    @Override
    public int compareTo(Object o) {
        if (o instanceof WeightElement) {
            WeightElement weightElement = (WeightElement) o;
            return weight - weightElement.getWeight();
        }
        return 0;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof WeightElement){
            WeightElement weightElement= (WeightElement) obj;
            return element.equals(weightElement.getElement());
        }
        return false;
    }
}
