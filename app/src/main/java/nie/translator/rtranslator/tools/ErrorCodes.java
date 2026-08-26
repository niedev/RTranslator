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

    //neural networks
    public static final int ERROR_LOADING_MODEL = 34;
    public static final int ERROR_EXECUTING_MODEL = 35;
    public static final int ERROR_LANGUAGE_NOT_SUPPORTED = 36;

    //language identification
    public static final int LANGUAGE_UNKNOWN = 15;
    public static final int FIRST_RESULT_FAIL = 16;
    public static final int SECOND_RESULT_FAIL = 17;
    public static final int BOTH_RESULTS_FAIL = 18;
    public static final int BOTH_RESULTS_SUCCESS = 19;

    //permissions
    public static final int NO_PERMISSIONS = -10;

    // model manager
    public static final int NO_DOWNLOADED_RESOURCE = 110;
}
