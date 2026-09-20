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

package nie.translator.rtranslator.voice_translation._conversation_mode._conversation;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.settings.SettingsActivity;
import nie.translator.rtranslator.tools.gui.CustomFragmentPagerAdapter;
import nie.translator.rtranslator.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslator.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslator.voice_translation._conversation_mode.PairingToolbarFragment;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.connection_info.PeersInfoFragment;
import nie.translator.rtranslator.voice_translation._conversation_mode._conversation.main.ConversationMainFragment;


public class ConversationFragment extends PairingToolbarFragment {
    private ConstraintLayout constraintLayout;
    private AppCompatImageButton exitButton;
    private AppCompatImageButton settingsButton;
    private MaterialCardView tabConversationContainer;
    private TextView tabConversationTitle;
    private MaterialCardView tabConnectionContainer;
    private TextView tabConnectionTitle;
    private ViewPager pager;
    private CustomFragmentPagerAdapter pagerAdapter;
    private VoiceTranslationActivity.Callback communicatorCallback;
    private CustomAnimator animator = new CustomAnimator();
    private int pagerPosition = 0;

    public ConversationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        communicatorCallback = new VoiceTranslationActivity.Callback() {
            @Override
            public void onSearchStarted() {
                if (!isLoadingVisible && !isLoadingAnimating) {
                    buttonSearch.setSearching(true, animator);
                }
            }

            @Override
            public void onSearchStopped() {
                if (!isLoadingVisible && !isLoadingAnimating) {
                    buttonSearch.setSearching(false, animator);
                }
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        constraintLayout = view.findViewById(R.id.container);
        exitButton = view.findViewById(R.id.exitButton);
        settingsButton = view.findViewById(R.id.settingsButton);
        tabConversationContainer = view.findViewById(R.id.tabConversationContainer);
        tabConversationTitle = view.findViewById(R.id.tabConversationName);
        tabConnectionContainer = view.findViewById(R.id.tabConnectionContainer);
        tabConnectionTitle = view.findViewById(R.id.tabConnectionName);
        pager = view.findViewById(R.id.tabs);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Toolbar toolbar = activity.findViewById(R.id.toolbarConversation);
        activity.setActionBar(toolbar);
        // we give the constraint layout the information on the system measures (status bar etc.), which the fragmentContainer has, because they are not passed
        // to it if started with a Transaction and therefore it overlaps the status bar because fitsSystemWindows does not work
        WindowInsets windowInsets = activity.getFragmentContainer().getRootWindowInsets();
        if (windowInsets != null) {
            constraintLayout.dispatchApplyWindowInsets(windowInsets.replaceSystemWindowInsets(windowInsets.getSystemWindowInsetLeft(),windowInsets.getSystemWindowInsetTop(),windowInsets.getSystemWindowInsetRight(),0));
        }

        // insertion of the list of titles
        List<String> titles = new ArrayList<>();
        titles.add(getResources().getString(R.string.conversation));
        titles.add(getResources().getString(R.string.connection));

        pagerAdapter = new CustomFragmentPagerAdapter(activity, getChildFragmentManager(), titles, new Fragment[]{new ConversationMainFragment(), new PeersInfoFragment()});
        pager.setAdapter(pagerAdapter);
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                //using positionComplete instead of position to identify the selected page we will start the animations when a view surpass the middle of the screen during scrolling
                float positionComplete = position + positionOffset;
                if (positionComplete <= 0.5 && pagerPosition == 1) {
                    pagerPosition = 0;
                    animator.animateTabSelection(activity, tabConversationContainer, tabConversationTitle, tabConnectionContainer, tabConnectionTitle, 0);
                    if(global.getBluetoothCommunicator() != null) {
                        global.getBluetoothCommunicator().removeCallback(communicatorCallback);
                    }
                    ((PeersInfoFragment) pagerAdapter.getFragment(1)).onDeselected();
                    buttonSearch.setVisible(false,null);
                } else if (positionComplete >= 0.5 && pagerPosition == 0) {
                    pagerPosition = 1;
                    animator.animateTabSelection(activity, tabConversationContainer, tabConversationTitle, tabConnectionContainer, tabConnectionTitle, 1);
                    if(global.getBluetoothCommunicator() != null) {
                        global.getBluetoothCommunicator().addCallback(communicatorCallback);
                    }
                    ((PeersInfoFragment) pagerAdapter.getFragment(1)).onSelected();
                    if(!isLoadingVisible) {
                        buttonSearch.setVisible(true, null);
                    }
                }
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.onBackPressed();
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        tabConversationContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pager.getCurrentItem() != 0){
                    pager.setCurrentItem(0, true);
                }
            }
        });
        tabConnectionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pager.getCurrentItem() != 1){
                    pager.setCurrentItem(1, true);
                }
            }
        });
    }

    public Fragment getCurrentFragment() {
        int position = pager.getCurrentItem();
        return pagerAdapter.getFragment(position);
    }

    @Override
    public void clearFoundPeers() {
        PeersInfoFragment peersInfoFragment = (PeersInfoFragment) pagerAdapter.getFragment(1);
        if (peersInfoFragment != null) {
            peersInfoFragment.clearFoundPeers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pagerPosition == 1 && global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().addCallback(communicatorCallback);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (pagerPosition == 1 && global.getBluetoothCommunicator() != null) {
            global.getBluetoothCommunicator().removeCallback(communicatorCallback);
        }
    }

    @Override
    protected void startSearch() {
        ((PeersInfoFragment) pagerAdapter.getFragment(1)).startSearch();
    }
}
