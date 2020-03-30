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

package nie.translator.rtranslatordevedition.tools.gui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.animations.CustomAnimator;

public class ButtonSearch extends AppCompatImageButton {
    private boolean searching = true;
    private boolean visible = true;
    private boolean animating = false;
    private ArrayList<CustomAnimator.EndListener> listeners = new ArrayList<>();
    private OnClickListener clickListener;
    private int drawableId = R.drawable.cancel_icon;


    public ButtonSearch(Context context) {
        super(context);
    }

    public ButtonSearch(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonSearch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * When this method is called it update searching and if the SearchButton is not visible or has an animation in progress
     * the graphic update is applied at the end of the animation of the appearance
     */
    public void setSearching(boolean searching, @Nullable CustomAnimator animator) {
        this.searching = searching;
        if (!animating && visible) {
            if (animator != null) {
                if (this.searching) {
                    if (drawableId != R.drawable.cancel_icon) {  // if graphically the object is not already searching
                        // animation execution
                        animating = true;
                        animator.animateIconChange(this, getDrawable(R.drawable.cancel_icon), new CustomAnimator.EndListener() {
                            @Override
                            public void onAnimationEnd() {
                                animating = false;
                                drawableId = R.drawable.cancel_icon;
                                // restore visible
                                setVisible(visible, null);
                            }
                        });
                    } else {
                        // restore visible
                        setVisible(visible, null);
                    }
                } else {
                    if (drawableId != R.drawable.search_icon) {  // if the object is not already graphically !searching
                        // animation execution
                        animating = true;
                        animator.animateIconChange(this, getDrawable(R.drawable.search_icon), new CustomAnimator.EndListener() {
                            @Override
                            public void onAnimationEnd() {
                                animating = false;
                                drawableId = R.drawable.search_icon;
                                // restore visible
                                setVisible(visible, null);
                            }
                        });
                    } else {
                        // restore visible
                        setVisible(visible, null);
                    }
                }
            } else {
                if (this.isSearching()) {
                    setImageDrawable(getDrawable(R.drawable.cancel_icon));
                    drawableId = R.drawable.cancel_icon;
                } else {
                    setImageDrawable(getDrawable(R.drawable.search_icon));
                    drawableId = R.drawable.search_icon;
                }
            }
        }
    }

    /**
     */
    public void setVisible(boolean visible, @Nullable final CustomAnimator.EndListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        this.visible = visible;
        if (!animating) {
            if (isVisible()) {
                // appearance
                if (getVisibility() != VISIBLE) {  // if graphically the object is not already visible
                    // animation execution
                    this.animating = true;
                    final Animation enlargeAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.enlarge_icon);
                    enlargeAnimation.setDuration(getResources().getInteger(R.integer.durationShort));
                    startAnimation(enlargeAnimation);
                    getAnimation().setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            setVisibility(VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            animating = false;
                            if (!isVisible()) {   // if visible has changed in the meantime
                                setVisible(isVisible(), null);
                            } else {
                                notifySetVisibleSuccess();
                                // restore icon
                                setSearching(searching, null);
                            }
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                } else {
                    notifySetVisibleSuccess();
                }
            } else {
                // disappearance
                if (getVisibility() != GONE) {  // if graphically the object is not already !visible
                    // animation execution
                    this.animating = true;
                    final Animation dwindleAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.dwindle_icon);
                    dwindleAnimation.setDuration(getResources().getInteger(R.integer.durationShort));
                    startAnimation(dwindleAnimation);
                    getAnimation().setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            setVisibility(GONE);
                            animating = false;
                            if (isVisible()) {   // if visible has changed in the meantime
                                setVisible(isVisible(), null);
                            } else {
                                notifySetVisibleSuccess();
                            }
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                } else {
                    notifySetVisibleSuccess();
                }
            }
        }
    }

    private void notifySetVisibleSuccess() {
        // finished animation notification and elimination of listeners
        while (listeners.size() > 0) {
            listeners.remove(0).onAnimationEnd();
        }
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        clickListener = l;
        super.setOnClickListener(clickListener);
    }

    public boolean isSearching() {
        return searching;
    }

    public boolean isVisible() {
        return visible;
    }

    public Drawable getDrawable(int id) {
        Drawable drawable = getResources().getDrawable(id, null);
        drawable.setTintList(GuiTools.getColorStateList(getContext(), R.color.primary));
        return drawable;
    }
}
