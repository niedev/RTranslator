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

package nie.translator.rtranslatordevedition.tools;

public class Chronometer {
    public static final int SECONDS=0;
    public static final int MILLI_SECONDS=1;

    private long startTime;

    public void start(){
        startTime=System.currentTimeMillis();
    }

    public float stop(int outputType){
        int divider=0;
        switch (outputType){
            case SECONDS:{
                divider=1000;
                break;
            }
            case MILLI_SECONDS:{
                divider=1;
                break;
            }
        }
        return (System.currentTimeMillis()-startTime)/divider;
    }
}
