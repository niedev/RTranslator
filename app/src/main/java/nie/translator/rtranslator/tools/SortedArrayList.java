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

package nie.translator.rtranslator.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class SortedArrayList<T> extends ArrayList<T> {
    private final Comparator<T> comparator;

    public SortedArrayList(Comparator<T> comparator) {
        super();
        this.comparator = comparator;
    }

    @Override
    public boolean add(T element) {
        addOrdered(element);
        return true;
    }

    public int addOrdered(T element) {
        int index = Collections.binarySearch(this, element, comparator);
        if (index < 0) {
            index = ~index;
        }
        super.add(index, element);
        return index;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        // If adding a large collection, doing an O(N) shift for every single item is very slow.
        // It is mathematically much more efficient to append them all and do one final sort.
        boolean modified = super.addAll(c);
        if (modified) {
            this.sort(comparator);
        }
        return modified;
    }

    @Override
    public T set(int index, T element) {
        T oldElement = super.get(index);

        // Check if the new element naturally fits in the exact same spot.
        // It must be >= the previous element AND <= the next element.
        boolean fitsAfterPrev = (index == 0) || (comparator.compare(super.get(index - 1), element) <= 0);
        boolean fitsBeforeNext = (index == size() - 1) || (comparator.compare(element, super.get(index + 1)) <= 0);

        if (fitsAfterPrev && fitsBeforeNext) {
            // The order is maintained. We can just do a standard O(1) swap.
            return super.set(index, element);
        }

        // If it breaks the order, remove the old element and insert the new one correctly
        super.remove(index);
        this.add(element); // Re-uses our O(log N) binary search add

        return oldElement;
    }
}
