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

package nie.translator.rtranslatordevedition.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gallery.imageselector.GalleryImageSelector;

import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;


public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String DEFAULT_NAME = "user";
    //self callbacks
    public static final int ON_INTERNET_LACK = 0;
    //internet Lack types
    public static final int DOWNLOAD_LANGUAGES = 0;
    public static final int ON_MISSING_GOOGLE_TTS = 2;
    //variables
    private int downloads = 0;
    private boolean isDownloading = false;
    //objects
    private GalleryImageSelector userImageContainer;
    private ProgressBar progressBar;
    private Global global;
    private SettingsActivity activity;
    private UserNamePreference userNamePreference;
    private SupportTtsQualityPreference supportTtsQualityPreference;
    private LanguagePreference languagePreference;

    private Handler selfHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            final Bundle data = message.getData();
            removeDownload();
            switch (data.getInt("type")) {
                case ON_INTERNET_LACK: {
                    //creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    builder.setCancelable(false);

                    switch (data.getInt("action")) {
                        case DOWNLOAD_LANGUAGES: {
                            builder.setMessage(R.string.error_internet_lack_loading_languages);
                            builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    supportTtsQualityPreference.downloadLanguages();
                                }
                            });
                            builder.setNegativeButton(android.R.string.cancel, null);
                            break;
                        }
                    }
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                    break;
                }
                case ON_MISSING_GOOGLE_TTS:
                    activity.showMissingGoogleTTSDialog();
                    break;

            }
            return true;
        }
    });


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        PreferenceGroupAdapter adapter = (PreferenceGroupAdapter) super.onCreateAdapter(super.getPreferenceScreen());
        RecyclerView recyclerView = view.findViewById(R.id.preference_recyclerview);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));


        // to scroll the scrollview up
        /*recyclerView.setFocusable(false);
        image.requestFocus();*/
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (SettingsActivity) requireActivity();
        global = (Global) activity.getApplication();
        progressBar = activity.findViewById(R.id.progressBarSettings);

        // user image initialization
        final UserImagePreference userImagePreference = (UserImagePreference) findPreference("userImage");
        userImagePreference.setLifecycleListener(new UserImagePreference.LifecycleListener() {
            @Override
            public void onBindViewHolder() {
                userImageContainer = new GalleryImageSelector(userImagePreference.getImage(), activity, SettingsFragment.this, R.drawable.user_icon, "com.gallery.RTranslator.provider");
            }
        });

        //inizializzazione user name
        userNamePreference = (UserNamePreference) findPreference("changeName");
        userNamePreference.setActivity(activity);
        /*userNamePreference.getEditTextHeight(new UserNamePreference.DateCallback() {
            @Override
            public void onViewHeightMeasured(int height) {
                creditPreference.setButtonHeight(activity,height);
            }
        });*/

        // change microphone sensibility initialization
        SeekBarPreference micSensibilityPreference = (SeekBarPreference) findPreference("micSensibilitySetting");
        micSensibilityPreference.initialize(activity, SeekBarPreference.MIC_SENSIBILITY_MODE);

        // language support option with low quality tts initialization
        supportTtsQualityPreference = (SupportTtsQualityPreference) findPreference("languagesQualityLow");
        supportTtsQualityPreference.setFragment(this);

        //languages list initialization
        languagePreference = (LanguagePreference) findPreference("language");
        languagePreference.setFragment(this);
        languagePreference.initializeLanguagesList();

        //link tts settings initialization
        Preference ttsPreference = findPreference("tts");
        ttsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent();
                intent.setAction("com.android.settings.TTS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return true;
            }
        });

        // change microphone sensibility initialization
        SeekBarPreference speechTimeoutPreference = (SeekBarPreference) findPreference("SpeechTimeoutSetting");
        speechTimeoutPreference.initialize(activity, SeekBarPreference.SPEECH_TIMEOUT_MODE);

        // change microphone sensibility initialization
        SeekBarPreference prevVoiceDurationPreference = (SeekBarPreference) findPreference("PrevVoiceDurationSetting");
        prevVoiceDurationPreference.initialize(activity, SeekBarPreference.PREV_VOICE_DURATION_MODE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //call onActivityResult
        userImageContainer.onActivityResult(requestCode, resultCode, data, true);
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public void addDownload() {
        if (downloads == 0) {
            isDownloading = true;
            progressBar.setVisibility(View.VISIBLE);
        }
        downloads++;
    }

    public void removeDownload() {
        if (downloads > 0) {
            if (downloads == 1) {
                progressBar.setVisibility(View.GONE);
                isDownloading = false;
            }
            downloads--;
        }
    }

    public LanguagePreference getLanguagePreference() {
        return languagePreference;
    }

    private void notifyInternetLack(int action, String data) {
        Messenger selfMessenger = new Messenger(selfHandler);
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putInt("type", ON_INTERNET_LACK);
        bundle.putInt("action", action);
        bundle.putString("newPassword", data);
        message.setData(bundle);
        try {
            selfMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void notifyMissingGoogleTTSDialog() {
        Messenger selfMessenger = new Messenger(selfHandler);
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putInt("command", ON_MISSING_GOOGLE_TTS);
        message.setData(bundle);
        try {
            selfMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public void onFailure(int[] reasons, long value, int action, @Nullable String data) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    notifyInternetLack(action, data);
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    notifyMissingGoogleTTSDialog();
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }
}
