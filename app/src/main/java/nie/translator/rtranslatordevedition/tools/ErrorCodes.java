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

public class ErrorCodes {
    //errors
    //generals
    public static final int MISSED_CONNECTION = 5;
    public static final int ERROR=13;
    //locals
    public static final int MISSED_ARGUMENT = 0;
    public static final int MISSED_CREDENTIALS = 1;
    public static final int MAX_CREDIT_OFFET_REACHED = 11;
    //safetyNet
    public static final int SAFETY_NET_EXCEPTION = 8;
    public static final int MISSING_PLAY_SERVICES = 9;
    //tts
    public static final int GOOGLE_TTS_ERROR =100;
    public static final int MISSING_GOOGLE_TTS =101;
    //key
    public static final int MISSING_API_KEY =102;
    public static final int WRONG_API_KEY =103    ;
}
