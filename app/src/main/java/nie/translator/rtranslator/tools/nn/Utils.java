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

package nie.translator.rtranslator.tools.nn;

import java.util.ArrayList;

public class Utils {
    public static double softmax(float input, float[] neuronValues) {
        double total = 0;
        for (float neuronValue : neuronValues) {
            total += Math.exp(neuronValue);
        }
        return Math.exp(input) / total;
    }

    public static double logSumExp(float[] neuronValues) {
        double total = 0;
        for (float neuronValue : neuronValues) {
            total += Math.exp(neuronValue);
        }
        return Math.log(total);
    }

    public static double logSumExpFast(float[] neuronValues){
        double max = Double.NEGATIVE_INFINITY;
        for (double v : neuronValues) {
            if (v > max) {
                max = v;
            }
        }
        double threshold = 20.0; // Skip correction if other values are much lower
        double sum = 0.0;
        for (double v : neuronValues) {
            double diff = v - max;
            if (diff > -threshold) {
                sum += Math.exp(diff);
            }
        }
        return max + Math.log(sum);
    }

    public static double logSumExpFaster(float[] neuronValues){
        double max = Double.NEGATIVE_INFINITY;
        for (double v : neuronValues) {
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    public static int getIndexOfLargest(float[] array){
        //long time = System.currentTimeMillis();
        if (array == null || array.length == 0){
            return -1;
        } // null or empty
        int largestIndex = 0;
        float largest = -Float.MAX_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest) {
                largestIndex = i;
                largest = array[largestIndex];
            }
        }
        //android.util.Log.i("performance", "index of largest time: " + (System.currentTimeMillis()-time) + "ms");
        return largestIndex; // position of the largest found
    }

    public static int getIndexOfLargest(double[] array){
        //long time = System.currentTimeMillis();
        if (array == null || array.length == 0){
            return -1;
        } // null or empty
        int largestIndex = 0;
        double largest = -Double.MAX_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest) {
                largestIndex = i;
                largest = array[largestIndex];
            }
        }
        //android.util.Log.i("performance", "index of largest time: " + (System.currentTimeMillis()-time) + "ms");
        return largestIndex; // position of the largest found
    }

    public static int getIndexOfLargest(ArrayList<Double> array){
        //long time = System.currentTimeMillis();
        if (array == null || array.isEmpty()){
            return -1;
        } // null or empty
        int largestIndex = 0;
        double largest = -Double.MAX_VALUE;
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) > largest) {
                largestIndex = i;
                largest = array.get(largestIndex);
            }
        }
        //android.util.Log.i("performance", "index of largest time: " + (System.currentTimeMillis()-time) + "ms");
        return largestIndex; // position of the largest found
    }

    public static int getIndexOfLargest(float[] array, ArrayList<Integer> indexesToAvoid){
        //long time = System.currentTimeMillis();
        if (array == null || array.length == 0){
            return -1;
        } // null or empty
        int largestIndex = 0;
        float largest = -Float.MAX_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest && !indexesToAvoid.contains(i)) {
                largestIndex = i;
                largest = array[largestIndex];
            }
        }
        //android.util.Log.i("performance", "index of largest time: " + (System.currentTimeMillis()-time) + "ms");
        return largestIndex; // position of the largest found
    }

    public static int getIndexOfLargest(double[] array, ArrayList<Integer> indexesToAvoid){
        //long time = System.currentTimeMillis();
        if (array == null || array.length == 0){
            return -1;
        } // null or empty
        int largestIndex = 0;
        double largest = -Double.MAX_VALUE;
        for (int i = 0; i < array.length; i++) {
            if (array[i] > largest && !indexesToAvoid.contains(i)) {
                largestIndex = i;
                largest = array[largestIndex];
            }
        }
        //android.util.Log.i("performance", "index of largest time: " + (System.currentTimeMillis()-time) + "ms");
        return largestIndex; // position of the largest found
    }
}
