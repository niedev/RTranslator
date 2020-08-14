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

package nie.translator.rtranslatordevedition.voice_translation._walkie_talkie_mode._walkie_talkie;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.AnimatedTextView;
import nie.translator.rtranslatordevedition.tools.gui.LanguageListAdapter;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicator;
import nie.translator.rtranslatordevedition.tools.services_communication.ServiceCommunicatorListener;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationFragment;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationService;


public class WalkieTalkieFragment extends VoiceTranslationFragment {
    public static final int INITIALIZE = 0;
    ConstraintLayout constraintLayout;
    private ImageButton exitButton;
    private LinearLayout firstLanguageSelector;
    private LinearLayout secondLanguageSelector;
    private TextView micInput;
    private ArrayList<String> languages = new ArrayList<>();
    //languageListDialog
    private LanguageListAdapter listView;
    private ListView listViewGui;
    private ProgressBar progressBar;
    private ImageButton reloadButton;
    private String selectedLanguageCode;
    private AlertDialog dialog;
    private Handler mHandler = new Handler();

    public WalkieTalkieFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.voiceTranslationServiceCommunicator = new WalkieTalkieService.WalkieTalkieServiceCommunicator(0);
        super.voiceTranslationServiceCallback = new VoiceTranslationServiceCallback();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_walkie_talkie, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout = view.findViewById(R.id.container);
        firstLanguageSelector = view.findViewById(R.id.firstLanguageSelector);
        secondLanguageSelector = view.findViewById(R.id.secondLanguageSelector);
        exitButton = view.findViewById(R.id.exitButton);
        micInput = view.findViewById(R.id.inputMicType);
        micInput.setVisibility(View.GONE);
        description.setText(R.string.description_walkie_talkie);
    }

    @Nullable
    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        /*Animation anim = AnimationUtils.loadAnimation(getActivity(), transit);
        anim.setDuration(3000);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {

            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });*/
        return super.onCreateAnimation(transit, enter, nextAnim);
    }

    /*@Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if(savedInstanceState!=null){
            //si riapre l' editText se era aperto al momento della distuzione dell' activity per motivi di sistema (es. rotazione o multi-windows)
            isEditTextOpen=savedInstanceState.getBoolean("isEditTextOpen");
            if(isEditTextOpen){
                keyboard.generateEditText(activity,microphone,editText,false);
            }
        }
    }*/

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Toolbar toolbar = activity.findViewById(R.id.toolbarWalkieTalkie);
        activity.setActionBar(toolbar);
        // we give the constraint layout the information on the system measures (status bar etc.), which has the fragmentContainer,
        // because they are not passed to it if started with a Transaction and therefore it overlaps the status bar because it fitsSystemWindows does not work
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(windowInsets.replaceSystemWindowInsets(windowInsets.getSystemWindowInsetLeft(),windowInsets.getSystemWindowInsetTop(),windowInsets.getSystemWindowInsetRight(),0));
        }

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.onBackPressed();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getArguments() != null) {
            if (getArguments().getBoolean("firstStart", false)) {
                getArguments().remove("firstStart");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connectToService();
                    }
                }, 300);
            } else {
                connectToService();
            }
        } else {
            connectToService();
        }
    }

    @Override
    protected void connectToService() {
        super.connectToService();
        activity.connectToWalkieTalkieService(voiceTranslationServiceCallback, new ServiceCommunicatorListener() {
            @Override
            public void onServiceCommunicator(ServiceCommunicator serviceCommunicator) {
                voiceTranslationServiceCommunicator = (VoiceTranslationService.VoiceTranslationServiceCommunicator) serviceCommunicator;
                restoreAttributesFromService();
                // listener setting for the two language selectors
                firstLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(1);
                    }
                });
                secondLanguageSelector.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showLanguageListDialog(2);
                    }
                });

                // setting of the selected languages
                ((WalkieTalkieService.WalkieTalkieServiceCommunicator) voiceTranslationServiceCommunicator).getFirstLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setFirstLanguage(language);
                    }
                });
                ((WalkieTalkieService.WalkieTalkieServiceCommunicator) voiceTranslationServiceCommunicator).getSecondLanguage(new WalkieTalkieService.LanguageListener() {
                    @Override
                    public void onLanguage(CustomLocale language) {
                        setSecondLanguage(language);
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                WalkieTalkieFragment.super.onFailureConnectingWithService(reasons, value);
            }
        });
    }

    private void showLanguageListDialog(final int languageNumber) {
        //when the dialog is shown at the beginning the loading is shown, then once the list of languages​is obtained (within the showList)
        //the loading is replaced with the list of languages
        String title = "";
        switch (languageNumber) {
            case 1: {
                title = global.getResources().getString(R.string.dialog_select_first_language);
                break;
            }
            case 2: {
                title = global.getResources().getString(R.string.dialog_select_second_language);
                break;
            }
        }

        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(title);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
        dialog.show();

        listViewGui = editDialogLayout.findViewById(R.id.list_view_dialog);
        progressBar = editDialogLayout.findViewById(R.id.progressBar3);
        reloadButton = editDialogLayout.findViewById(R.id.reloadButton);

        Global.GetLocaleListener listener = new Global.GetLocaleListener() {
            @Override
            public void onSuccess(final CustomLocale result) {
                reloadButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showList(languageNumber, result);
                    }
                });
                showList(languageNumber, result);
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        };

        switch (languageNumber) {
            case 1: {
                global.getFirstLanguage(false, listener);
                break;
            }
            case 2: {
                global.getSecondLanguage(false, listener);
                break;
            }
        }

    }

    private void showList(final int languageNumber, final CustomLocale selectedLanguage) {
        reloadButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        global.getLanguages(true, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(final ArrayList<CustomLocale> languages) {
                progressBar.setVisibility(View.GONE);
                listViewGui.setVisibility(View.VISIBLE);

                listView = new LanguageListAdapter(activity, languages, selectedLanguage);
                listViewGui.setAdapter(listView);
                listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        if (languages.contains((CustomLocale) listView.getItem(position))) {
                            switch (languageNumber) {
                                case 1: {
                                    setFirstLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                                case 2: {
                                    setSecondLanguage((CustomLocale) listView.getItem(position));
                                    break;
                                }
                            }
                        }
                        dialog.dismiss();
                    }
                });
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                onFailureShowingList(reasons, value);
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        firstLanguageSelector.setOnClickListener(null);
        secondLanguageSelector.setOnClickListener(null);
        activity.disconnectFromWalkieTalkieService((WalkieTalkieService.WalkieTalkieServiceCommunicator) voiceTranslationServiceCommunicator);
    }

    private void setFirstLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        ((WalkieTalkieService.WalkieTalkieServiceCommunicator) voiceTranslationServiceCommunicator).changeFirstLanguage(language);
        // save firstLanguage selected
        global.setFirstLanguage(language);
        // change language displayed
        ((AnimatedTextView) firstLanguageSelector.findViewById(R.id.firstLanguageName)).setText(language.getDisplayName(), true);
    }

    private void setSecondLanguage(CustomLocale language) {
        // new language setting in the WalkieTalkieService
        ((WalkieTalkieService.WalkieTalkieServiceCommunicator) voiceTranslationServiceCommunicator).changeSecondLanguage(language);
        // save secondLanguage selected
        global.setSecondLanguage(language);
        // change language displayed
        ((AnimatedTextView) secondLanguageSelector.findViewById(R.id.secondLanguageName)).setText(language.getDisplayName(), true);
    }

    private void onFailureShowingList(int[] reasons, long value) {
        progressBar.setVisibility(View.GONE);
        reloadButton.setVisibility(View.VISIBLE);
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.MISSED_ARGUMENT:
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    Toast.makeText(activity, getResources().getString(R.string.error_internet_lack_loading_languages), Toast.LENGTH_LONG).show();
                    break;
                default:
                    activity.onError(aReason, value);
                    break;
            }
        }
    }

    /*@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isEditTextOpen",isEditTextOpen);
    }*/
}