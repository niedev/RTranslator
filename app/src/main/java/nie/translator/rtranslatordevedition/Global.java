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

package nie.translator.rtranslatordevedition;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import nie.translator.rtranslatordevedition.api_management.ConsumptionsDataManager;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.ConversationBluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.BluetoothCommunicator;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.Peer;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.connection.BluetoothConnection;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeersDataManager;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.translation.Translator;
import nie.translator.rtranslatordevedition.voice_translation.cloud_apis.voice.Recorder;


public class Global extends Application {
    public static final List<String> SCOPE = Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final int TOKEN_FETCH_MARGIN = 60 * 1000; // one minute
    private ArrayList<CustomLocale> languages = new ArrayList<>();
    private CustomLocale language;
    private CustomLocale firstLanguage;
    private CustomLocale secondLanguage;
    private RecentPeersDataManager recentPeersDataManager;
    private ConversationBluetoothCommunicator bluetoothCommunicator;
    private Translator translator;
    private String name = "";
    private String apiKeyFileName = "";
    private ConsumptionsDataManager databaseManager;
    private AccessToken apiToken;
    private int micSensitivity = -1;
    private int amplitudeThreshold = Recorder.DEFAULT_AMPLITUDE_THRESHOLD;
    private Handler mainHandler;
    private Thread getApiTokenThread;
    private ArrayDeque<ApiTokenListener> apiTokenListeners = new ArrayDeque<>();
    private static Handler mHandler = new Handler();
    private final Object lock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        recentPeersDataManager = new RecentPeersDataManager(this);
        bluetoothCommunicator = new ConversationBluetoothCommunicator(this, getName(), BluetoothConnection.STRATEGY_P2P_WITH_RECONNECTION);
        translator = new Translator(this);
        databaseManager = new ConsumptionsDataManager(this);
        getMicSensitivity();
    }


    public ConversationBluetoothCommunicator getBluetoothCommunicator() {
        return bluetoothCommunicator;
    }

    public void resetBluetoothCommunicator() {
        bluetoothCommunicator.destroy(new BluetoothCommunicator.DestroyCallback() {
            @Override
            public void onDestroyed() {
                bluetoothCommunicator = new ConversationBluetoothCommunicator(Global.this, getName(), BluetoothConnection.STRATEGY_P2P_WITH_RECONNECTION);
            }
        });
    }

    public void getLanguages(final boolean recycleResult, final GetLocalesListListener responseListener) {
        if (recycleResult && languages.size() > 0) {
            responseListener.onSuccess(languages);
        } else {
            translator.getSupportedLanguages(CustomLocale.getDefault(), new Translator.SupportedLanguagesListener() {
                @Override
                public void onLanguagesListAvailable(ArrayList<CustomLocale> languages) {
                    Global.this.languages = languages;
                    responseListener.onSuccess(languages);
                }

                @Override
                public void onFailure(int[] reasons, long value) {
                    responseListener.onFailure(reasons, value);
                }
            });
        }
    }

    public interface GetLocalesListListener {
        void onSuccess(ArrayList<CustomLocale> result);

        void onFailure(int[] reasons, long value);
    }

    public void getLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        getLanguages(true, new GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> languages) {
                CustomLocale predefinedLanguage = CustomLocale.getDefault();
                CustomLocale language = null;
                if (recycleResult && Global.this.language != null) {
                    language = Global.this.language;
                } else {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Global.this);
                    String code = sharedPreferences.getString("language", predefinedLanguage.getCode());
                    if (code != null) {
                        language = CustomLocale.getInstance(code);
                    }
                }

                int index = CustomLocale.search(languages, language);
                if (index != -1) {
                    language = languages.get(index);
                } else {
                    int index2 = CustomLocale.search(languages, predefinedLanguage);
                    if (index2 != -1) {
                        language = predefinedLanguage;
                    } else {
                        language = new CustomLocale("en", "US");
                    }
                }

                Global.this.language = language;
                responseListener.onSuccess(language);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public void getFirstLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        getLanguages(true, new GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                getLanguage(true, new GetLocaleListener() {
                    @Override
                    public void onSuccess(CustomLocale predefinedLanguage) {
                        CustomLocale language = null;
                        if (recycleResult && Global.this.firstLanguage != null) {
                            language = Global.this.firstLanguage;
                        } else {
                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Global.this);
                            String code = sharedPreferences.getString("firstLanguage", predefinedLanguage.getCode());
                            if (code != null) {
                                language = CustomLocale.getInstance(code);
                            }
                        }

                        int index = CustomLocale.search(languages, language);
                        if (index != -1) {
                            language = languages.get(index);
                        } else {
                            int index2 = CustomLocale.search(languages, predefinedLanguage);
                            if (index2 != -1) {
                                language = predefinedLanguage;
                            } else {
                                language = new CustomLocale("en", "US");
                            }
                        }

                        Global.this.firstLanguage = language;
                        responseListener.onSuccess(language);
                    }

                    @Override
                    public void onFailure(int[] reasons, long value) {
                        responseListener.onFailure(reasons, value);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });

    }

    public void getSecondLanguage(final boolean recycleResult, final GetLocaleListener responseListener) {
        getLanguages(true, new GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> languages) {
                CustomLocale predefinedLanguage = CustomLocale.getDefault();
                CustomLocale language = null;
                if (recycleResult && Global.this.secondLanguage != null) {
                    language = Global.this.secondLanguage;
                } else {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(Global.this);
                    String code = sharedPreferences.getString("secondLanguage", null);
                    if (code != null) {
                        language = CustomLocale.getInstance(code);
                    }
                }

                int index = CustomLocale.search(languages, language);
                if (index != -1) {
                    language = languages.get(index);
                } else {
                    language = new CustomLocale("en", "US");
                }

                Global.this.secondLanguage = language;
                responseListener.onSuccess(language);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                responseListener.onFailure(reasons, value);
            }
        });
    }

    public interface GetLocaleListener {
        void onSuccess(CustomLocale result);

        void onFailure(int[] reasons, long value);
    }

    public void setLanguage(CustomLocale language) {
        this.language = language;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("language", language.getCode());
        editor.apply();
    }

    public void setFirstLanguage(CustomLocale language) {
        this.firstLanguage = language;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstLanguage", language.getCode());
        editor.apply();
    }

    public void setSecondLanguage(CustomLocale language) {
        this.secondLanguage = language;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("secondLanguage", language.getCode());
        editor.apply();
    }


    public int getAmplitudeThreshold() {
        return amplitudeThreshold;
    }


    public int getMicSensitivity() {
        if (micSensitivity == -1) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            micSensitivity = sharedPreferences.getInt("micSensibility", 50);
            setAmplitudeThreshold(micSensitivity);
        }
        return micSensitivity;
    }

    public void setMicSensitivity(int value) {
        micSensitivity = value;
        setAmplitudeThreshold(micSensitivity);
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("micSensibility", value);
        editor.apply();
    }

    private void setAmplitudeThreshold(int micSensitivity) {
        float amplitudePercentage = 1f - (micSensitivity / 100f);
        if (amplitudePercentage < 0.5f) {
            amplitudeThreshold = Math.round(Recorder.MIN_AMPLITUDE_THRESHOLD + ((Recorder.DEFAULT_AMPLITUDE_THRESHOLD - Recorder.MIN_AMPLITUDE_THRESHOLD) * (amplitudePercentage * 2)));
        } else {
            amplitudeThreshold = Math.round(Recorder.DEFAULT_AMPLITUDE_THRESHOLD + ((Recorder.MAX_AMPLITUDE_THRESHOLD - Recorder.DEFAULT_AMPLITUDE_THRESHOLD) * ((amplitudePercentage - 0.5F) * 2)));
        }
    }

    public String getName() {
        if (name.length() == 0) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            name = sharedPreferences.getString("name", "user");
        }
        return name;
    }

    public void setName(String savedName) {
        name = savedName;
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("name", savedName);
        editor.apply();
        getBluetoothCommunicator().setName(savedName);  //si aggiorna il nome anche per il comunicator
    }

    public Peer getMyPeer() {
        return new Peer(null, getName(), false);
    }

    public abstract static class MyPeerListener {
        public abstract void onSuccess(Peer myPeer);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public void getMyID(final MyIDListener responseListener) {
        responseListener.onSuccess(Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID));
    }

    public abstract static class MyIDListener {
        public abstract void onSuccess(String id);

        public void onFailure(int[] reasons, long value) {
        }
    }

    public RecentPeersDataManager getRecentPeersDataManager() {
        return recentPeersDataManager;
    }

    public abstract static class ResponseListener {
        public void onSuccess() {

        }

        public void onFailure(int[] reasons, long value) {
        }
    }

    public void addUsage(float creditToSub) {
        // add consumption to the database
        databaseManager.addUsage(creditToSub);
    }

    public boolean isFirstStart() {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean("firstStart", true);
    }

    public void setFirstStart(boolean firstStart) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("firstStart", firstStart);
        editor.apply();
    }

    public String getApiKeyFileName() {
        if (apiKeyFileName.length() == 0) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            apiKeyFileName = sharedPreferences.getString("apiKeyFileName", "");
        }
        return apiKeyFileName;
    }

    public void setApiKeyFileName(String apiKeyFileName) {
        this.apiKeyFileName = apiKeyFileName;
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("apiKeyFileName", apiKeyFileName);
        editor.apply();
    }


    //api token

    public synchronized void resetApiToken() {
        apiToken = null;
    }

    public void getApiToken(final boolean recycleResult, @Nullable final ApiTokenListener responseListener) {
        synchronized (lock) {
            if (responseListener != null) {
                apiTokenListeners.addLast(responseListener);
            }
            if (recycleResult && apiToken != null && apiToken.getExpirationTime().getTime() > System.currentTimeMillis()) {
                //notifica del successo a tutti i listeners
                notifyGetApiTokenSuccess();
            } else {
                if (getApiTokenThread == null) {
                    getApiTokenThread = new Thread(new GetApiTokenRunnable(new ApiTokenListener() {
                        @Override
                        public void onSuccess(final AccessToken apiToken) {
                            //notifica del successo a tutti i listeners
                            notifyGetApiTokenSuccess();
                        }

                        @Override
                        public void onFailure(final int[] reasons, final long value) {
                            //notifica del fallimento a tutti i listeners
                            notifyGetApiTokenFailure(reasons, value);
                        }
                    }), "getAppToken");
                    getApiTokenThread.start();
                }
            }
        }
    }

    private void notifyGetApiTokenSuccess() {
        synchronized (lock) {
            while (apiTokenListeners.peekFirst() != null) {
                apiTokenListeners.pollFirst().onSuccess(apiToken);
            }
            getApiTokenThread = null;
        }
    }

    private void notifyGetApiTokenFailure(final int[] reasons, final long value) {
        synchronized (lock) {
            while (apiTokenListeners.peekFirst() != null) {
                apiTokenListeners.pollFirst().onFailure(reasons, value);
            }
            getApiTokenThread = null;
        }
    }

    private class GetApiTokenRunnable implements Runnable {
        @Nullable
        private ApiTokenListener responseListener;

        private GetApiTokenRunnable(@Nullable ApiTokenListener responseListener) {
            this.responseListener = responseListener;
        }

        @Override
        public void run() {
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    Log.d("token", "token fetched");
                    final InputStream stream;
                    try {
                        stream = new FileInputStream(new File(getFilesDir(), getApiKeyFileName()));
                        try {
                            final GoogleCredentials credentials = GoogleCredentials.fromStream(stream).createScoped(SCOPE);
                            apiToken = credentials.refreshAccessToken();
                            if (responseListener != null) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (apiToken != null) {
                                            responseListener.onSuccess(apiToken);
                                        } else {
                                            responseListener.onFailure(new int[]{ErrorCodes.WRONG_API_KEY}, -1);
                                        }
                                    }
                                });
                            }

                            // Schedule access token refresh before it expires
                            if (mHandler != null) {
                                // elimination of all runnables in handlers to ensure that only one getAppToken is scheduled at a time
                                mHandler.removeCallbacksAndMessages(null);
                                long refreshTime = Math.max(apiToken.getExpirationTime().getTime() - System.currentTimeMillis() - TOKEN_FETCH_MARGIN, TOKEN_FETCH_MARGIN);
                                mHandler.postDelayed(new GetApiTokenRunnable(null), refreshTime);
                            }
                        } catch (final IOException e) {
                            Log.e("token", "Failed to obtain access token.", e);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (responseListener != null) {
                                        if (e.getCause() instanceof UnknownHostException) {
                                            responseListener.onFailure(new int[]{ErrorCodes.MISSED_CONNECTION}, -1);
                                        } else {
                                            responseListener.onFailure(new int[]{ErrorCodes.WRONG_API_KEY}, -1);
                                        }
                                    }
                                }
                            });
                        }
                    } catch (FileNotFoundException e) {
                        Log.e("token", "Failed to obtain access token.", e);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (responseListener != null) {
                                    responseListener.onFailure(new int[]{ErrorCodes.MISSING_API_KEY}, -1);
                                }
                            }
                        });
                    }
                }
            }.start();
        }
    }

    public interface ApiTokenListener {
        void onSuccess(AccessToken apiToken);

        void onFailure(int[] reasons, long value);
    }
}