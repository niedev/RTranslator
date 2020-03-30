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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.CustomFragmentPagerAdapter;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.PairingToolbarFragment;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation.connection_info.PeersInfoFragment;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode._conversation.main.ConversationMainFragment;


public class ConversationFragment extends PairingToolbarFragment {
    private ConstraintLayout constraintLayout;
    private ImageButton exitButton;
    private TabLayout tabLayout;
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
        tabLayout = view.findViewById(R.id.tabsLayout);
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
            constraintLayout.dispatchApplyWindowInsets(windowInsets);
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
                if (position == 0 && positionOffset == 0 && pagerPosition == 1) {
                    pagerPosition = 0;
                    global.getBluetoothCommunicator().removeCallback(communicatorCallback);
                    ((PeersInfoFragment) pagerAdapter.getFragment(1)).onDeselected();
                    buttonSearch.setVisible(false,null);
                } else if (position == 1 && positionOffset == 0 && pagerPosition == 0) {
                    pagerPosition = 1;
                    global.getBluetoothCommunicator().addCallback(communicatorCallback);
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

        tabLayout.setupWithViewPager(pager);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.onBackPressed();
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
        if (pagerPosition == 1) {
            global.getBluetoothCommunicator().addCallback(communicatorCallback);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (pagerPosition == 1) {
            global.getBluetoothCommunicator().removeCallback(communicatorCallback);
        }
    }

    @Override
    protected void startSearch() {
        ((PeersInfoFragment) pagerAdapter.getFragment(1)).startSearch();
    }
}
