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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode;

import android.animation.Animator;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.ButtonSearch;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;


public abstract class PairingToolbarFragment extends Fragment {
    private static final float LOADING_SIZE_DP = 24;
    protected boolean isLoadingVisible = false;
    private boolean appearSearchButton = false;
    protected boolean isLoadingAnimating;  // animation appearance or disappearance of the loading
    protected ButtonSearch buttonSearch;
    private ProgressBar loading;
    protected Global global;
    protected VoiceTranslationActivity activity;
    private ArrayList<CustomAnimator.EndListener> listeners = new ArrayList<>();
    private CustomAnimator animator = new CustomAnimator();


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        buttonSearch = view.findViewById(R.id.searchButton);
        loading = view.findViewById(R.id.progressBar2);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (VoiceTranslationActivity) requireActivity();
        global = (Global) activity.getApplication();
    }

    @Override
    public void onStart() {
        super.onStart();
        buttonSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activity.isSearching()) {
                    activity.stopSearch(false);
                    clearFoundPeers();
                } else {
                    startSearch();
                }
            }
        });
    }

    /**
     * In this method we not only make the loading appear but first we make the ButtonSearch disappear,
     * these two animations will be considered the loading appearance animation
     **/
    public void appearLoading(@Nullable CustomAnimator.EndListener responseListener) {
        if (responseListener != null) {
            listeners.add(responseListener);
        }
        isLoadingVisible = true;
        if (!isLoadingAnimating) {
            if (loading.getVisibility() != View.VISIBLE) {  // if the object has not already appeared graphically
                //animation execution
                isLoadingAnimating = true;
                buttonSearch.setVisible(false, new CustomAnimator.EndListener() {
                    @Override
                    public void onAnimationEnd() {
                        int loadingSizePx = Tools.convertDpToPixels(activity, LOADING_SIZE_DP);
                        Animator animation = animator.createAnimatorSize(loading, 1, 1, loadingSizePx, loadingSizePx, getResources().getInteger(R.integer.durationShort));
                        animation.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                loading.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                isLoadingAnimating = false;
                                if (!isLoadingVisible) {   // if isLoadingVisible has changed in the meantime
                                    disappearLoading(appearSearchButton, null);
                                } else {
                                    notifyLoadingAnimationEnd();
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {

                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {

                            }
                        });
                        animation.start();

                    }
                });
            } else {
                notifyLoadingAnimationEnd();
            }
        }
    }

    /**
     * In this method not only do we make the loading disappear but, if set by the user, after that we also make the ButtonSearch reappear,
     * these two animations will be considered the disappearance animation of the loading
     **/
    public void disappearLoading(final boolean appearSearchButton, @Nullable CustomAnimator.EndListener responseListener) {
        if (responseListener != null) {
            listeners.add(responseListener);
        }
        this.isLoadingVisible = false;
        this.appearSearchButton = appearSearchButton;
        if (!isLoadingAnimating) {
            if (loading.getVisibility() != View.GONE) {  // if the object has not already disappeared graphically
                // animation execution
                isLoadingAnimating = true;
                int loadingSizePx = Tools.convertDpToPixels(activity, LOADING_SIZE_DP);
                Animator animation = animator.createAnimatorSize(loading,loadingSizePx, loadingSizePx,1,1, getResources().getInteger(R.integer.durationShort));
                animation.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loading.setVisibility(View.GONE);
                        CustomAnimator.EndListener listener = new CustomAnimator.EndListener() {
                            @Override
                            public void onAnimationEnd() {
                                isLoadingAnimating = false;
                                if (isLoadingVisible) {   // if isLoadingVisible has changed in the meantime
                                    appearLoading(null);
                                } else {
                                    notifyLoadingAnimationEnd();
                                }
                            }
                        };
                        if (appearSearchButton) {
                            buttonSearch.setVisible(true, listener);
                        } else {
                            listener.onAnimationEnd();
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                animation.start();
            } else {
                notifyLoadingAnimationEnd();
            }
        }
    }

    private void notifyLoadingAnimationEnd() {
        // notify finished animation and elimination of listeners
        while (listeners.size() > 0) {
            listeners.remove(0).onAnimationEnd();
        }
    }

    public void clearFoundPeers() {
    }

    protected abstract void startSearch();
}
