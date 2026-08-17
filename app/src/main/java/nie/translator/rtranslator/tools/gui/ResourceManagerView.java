package nie.translator.rtranslator.tools.gui;

import android.animation.Animator;
import android.content.Context;
import android.content.res.TypedArray;
import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Locale;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.animations.CustomAnimator;

public class ResourceManagerView extends ConstraintLayout {
    public enum State {
        EMPTY,
        PAUSED,
        DOWNLOADING,
        DOWNLOADED
    };

    public static final int BUTTON_DELETE_LEFT_MARGIN = 8;
    public static final int BUTTON_DELETE_RIGHT_MARGIN = 16;
    public static final int BUTTON_DELETE_SIZE = 24;
    public static int BUTTON_DELETE_LEFT_MARGIN_REDUCED_PX;
    public static int BUTTON_DELETE_RIGHT_MARGIN_REDUCED_PX;
    public static int BUTTON_DELETE_SIZE_REDUCED_PX;
    public static int PROGRESS_BAR_SIZE_PX;

    private State state = State.EMPTY;
    private TextView titleResource;
    private TextView descriptionResource;
    private int modelSizeMb = 0;
    private TextView textDownload;
    private CircularProgressIndicator progressBarDownload;
    private ImageView buttonDelete;
    private ImageView buttonDownload;
    private ImageView playIcon;
    private ImageView pauseIcon;
    private ImageView errorIcon;
    private CustomAnimator animator = new CustomAnimator();
    private Animator animation;

    // Constructors required by Android
    public ResourceManagerView(Context context) {
        super(context);
        init(context, null);
    }

    public ResourceManagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ResourceManagerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Inflate the merge layout into THIS ConstraintLayout
        LayoutInflater.from(context).inflate(R.layout.component_resource_manager, this, true);

        // Bind views
        titleResource = findViewById(R.id.titleResource);
        descriptionResource = findViewById(R.id.descriptionResource);
        textDownload = findViewById(R.id.textDownload);
        progressBarDownload = findViewById(R.id.progressBarDownload);
        buttonDownload = findViewById(R.id.buttonDownload);
        buttonDelete = findViewById(R.id.buttonDelete);
        playIcon = findViewById(R.id.playImage);
        pauseIcon = findViewById(R.id.pauseImage);
        errorIcon = findViewById(R.id.errorIcon);

        //initialize buttonDelete reduced measures
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) buttonDelete.getLayoutParams();
        BUTTON_DELETE_SIZE_REDUCED_PX = layoutParams.width;
        BUTTON_DELETE_LEFT_MARGIN_REDUCED_PX = layoutParams.leftMargin;
        BUTTON_DELETE_RIGHT_MARGIN_REDUCED_PX = layoutParams.rightMargin;
        PROGRESS_BAR_SIZE_PX = progressBarDownload.getIndicatorSize();

        // Apply custom XML attributes if provided
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ResourceManagerView);

            if (a.hasValue(R.styleable.ResourceManagerView_modelTitle)) {
                titleResource.setText(a.getString(R.styleable.ResourceManagerView_modelTitle));
            }
            if (a.hasValue(R.styleable.ResourceManagerView_modelDescription)) {
                String descriptionText = a.getString(R.styleable.ResourceManagerView_modelDescription);
                if(descriptionText != null && !descriptionText.isEmpty()){
                    descriptionResource.setText(descriptionText);
                }else{
                    descriptionResource.setVisibility(GONE);
                }
            }
            if (a.hasValue(R.styleable.ResourceManagerView_modelSize)) {
                float sizeInMB = a.getInt(R.styleable.ResourceManagerView_modelSize, 0);
                setModelSize(sizeInMB);
            }
            if (a.hasValue(R.styleable.ResourceManagerView_state)) {
                int stateIndex = a.getInt(R.styleable.ResourceManagerView_state, 0);
                if(stateIndex >= 0 && stateIndex < State.values().length) {
                    setState(State.values()[stateIndex], false);
                }
            }

            a.recycle();
        }

        // Set up callbacks
        buttonDownload.setOnClickListener(v -> {
            if (listener != null && state == State.EMPTY) {
                listener.onDownloadClicked();
            }
        });
        progressBarDownload.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener != null) {
                    if(state == State.PAUSED) {
                        listener.onResumeClicked();
                    }else if(state == State.DOWNLOADING) {
                        listener.onPauseClicked();
                    }
                }
            }
        });
        buttonDelete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener != null) {
                    listener.onDeletePressed();
                }
            }
        });
    }

    // Public APIs to manipulate the component from the Activity/Fragment ---


    public void setState(State state, boolean animate) {
        State oldState = this.state;
        this.state = state;
        if(animation != null && this.state != oldState && animate) {
            animation.cancel();
            animation = null;
        }
        if(state != State.DOWNLOADING && state != State.PAUSED){
            //we delete the eventual text progress and restore the normal text that show the size of the model
            setModelSize(modelSizeMb);
        }
        if(animate) {
            if(this.state != oldState) {
                switch (state) {
                    case DOWNLOADING:
                        if (oldState == State.EMPTY) {
                            animation = animator.animateResourceDownloadStart(getContext(), buttonDownload, progressBarDownload, pauseIcon, buttonDelete, new CustomAnimator.Listener() {
                                @Override
                                public void onAnimationEnd() {
                                    animation = null;
                                }
                            });
                        } else if (oldState == State.PAUSED) {
                            applyStateDownloading();
                        }
                        if (errorIcon.getVisibility() != INVISIBLE) {   //we eventually remove the error
                            errorIcon.setVisibility(INVISIBLE);
                        }
                        break;
                    case PAUSED:
                        if (oldState == State.DOWNLOADING) {
                            applyStatePaused();
                        }
                        break;
                    case EMPTY:
                        if (oldState == State.DOWNLOADING || oldState == State.DOWNLOADED) {
                            ImageView pauseOrResumeIcon;
                            if (pauseIcon.getVisibility() == View.VISIBLE) {
                                pauseOrResumeIcon = pauseIcon;
                            } else {
                                pauseOrResumeIcon = playIcon;
                            }
                            animation = animator.animateResourceDeletion(getContext(), buttonDownload, progressBarDownload, pauseOrResumeIcon, buttonDelete, new CustomAnimator.Listener() {
                                @Override
                                public void onAnimationEnd() {
                                    animation = null;
                                }
                            });
                        }
                        if (errorIcon.getVisibility() != INVISIBLE) {   //we eventually remove the error
                            errorIcon.setVisibility(INVISIBLE);
                        }
                        break;
                    case DOWNLOADED:
                        if (oldState == State.DOWNLOADING) {
                            animation = animator.animateResourceDownloadCompleted(getContext(), progressBarDownload, pauseIcon, buttonDelete, new CustomAnimator.Listener() {
                                @Override
                                public void onAnimationEnd() {
                                    animation = null;
                                }
                            });
                        }
                        break;
                }
            }
        }else{
            // Force reset all alphas so cancelled animations don't leave views transparent
            buttonDownload.setAlpha(1f);
            progressBarDownload.setAlpha(1f);
            pauseIcon.setAlpha(1f);
            playIcon.setAlpha(1f);
            buttonDelete.setAlpha(1f);

            switch (state) {
                case DOWNLOADING:
                    applyStateDownloading();
                    if (errorIcon.getVisibility() != INVISIBLE) {   //we eventually remove the error
                        errorIcon.setVisibility(INVISIBLE);
                    }
                    break;
                case PAUSED:
                    applyStatePaused();
                    break;
                case EMPTY:
                    applyStateEmpty();
                    if (errorIcon.getVisibility() != INVISIBLE) {   //we eventually remove the error
                        errorIcon.setVisibility(INVISIBLE);
                    }
                    break;
                case DOWNLOADED:
                    applyStateDownloaded();
                    break;
            }
        }
    }

    public String getTitle() {
        return titleResource.getText().toString();
    }

    public void setTitle(@Nullable String title) {
        if(title != null) this.titleResource.setText(title);
    }

    public String getDescription() {
        return descriptionResource.getText().toString();
    }

    public void setDescription(@Nullable String description) {
        if(description != null && !description.isEmpty()) {
            this.descriptionResource.setText(description);
        }else{
            this.descriptionResource.setVisibility(GONE);
        }
    }

    public int getModelSizeMb() {
        return modelSizeMb;
    }

    public void setModelSize(float modelSizeMb) {
        if(modelSizeMb > 0) {
            this.modelSizeMb = (int) modelSizeMb;
            if(modelSizeMb >= 1000){
                DecimalFormat df = new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.US));
                textDownload.setText(df.format(modelSizeMb / 1000) + " GB");
            }else{
                DecimalFormat df = new DecimalFormat("0");
                textDownload.setText(df.format(modelSizeMb) + " MB");
            }
        }
    }

    private void applyStateDownloading(){
        buttonDownload.setVisibility(INVISIBLE);
        progressBarDownload.setVisibility(VISIBLE);
        pauseIcon.setVisibility(VISIBLE);
        playIcon.setVisibility(INVISIBLE);
        buttonDelete.setVisibility(VISIBLE);
        setButtonDeleteSize(false, false);
        progressBarDownload.setIndicatorSize(PROGRESS_BAR_SIZE_PX);
        buttonDownload.setClickable(false);
        pauseIcon.setClickable(true);
        playIcon.setClickable(false);
        buttonDelete.setClickable(true);
    }

    private void applyStatePaused(){
        buttonDownload.setVisibility(INVISIBLE);
        progressBarDownload.setVisibility(VISIBLE);
        pauseIcon.setVisibility(INVISIBLE);
        playIcon.setVisibility(VISIBLE);
        buttonDelete.setVisibility(VISIBLE);
        setButtonDeleteSize(false, false);
        progressBarDownload.setIndicatorSize(PROGRESS_BAR_SIZE_PX);
        buttonDownload.setClickable(false);
        pauseIcon.setClickable(false);
        playIcon.setClickable(true);
        buttonDelete.setClickable(true);
    }

    private void applyStateEmpty(){
        buttonDownload.setVisibility(VISIBLE);
        progressBarDownload.setVisibility(INVISIBLE);
        pauseIcon.setVisibility(INVISIBLE);
        playIcon.setVisibility(INVISIBLE);
        buttonDelete.setVisibility(INVISIBLE);
        setButtonDeleteSize(true, true);
        progressBarDownload.setIndicatorSize(PROGRESS_BAR_SIZE_PX);
        buttonDownload.setClickable(true);
        pauseIcon.setClickable(false);
        playIcon.setClickable(false);
        buttonDelete.setClickable(false);
    }

    private void applyStateDownloaded(){
        buttonDownload.setVisibility(INVISIBLE);
        progressBarDownload.setVisibility(INVISIBLE);
        pauseIcon.setVisibility(INVISIBLE);
        playIcon.setVisibility(INVISIBLE);
        buttonDelete.setVisibility(VISIBLE);
        setButtonDeleteSize(false, true);
        progressBarDownload.setIndicatorSize(1);
        buttonDownload.setClickable(false);
        pauseIcon.setClickable(false);
        playIcon.setClickable(false);
        buttonDelete.setClickable(true);
    }

    private void setButtonDeleteSize(boolean reduced, boolean marginsReduced){
        int buttonDeleteLeftMargin = marginsReduced ? ResourceManagerView.BUTTON_DELETE_LEFT_MARGIN_REDUCED_PX : Tools.convertDpToPixels(getContext(), ResourceManagerView.BUTTON_DELETE_LEFT_MARGIN);
        if(reduced) {
            int buttonDeleteRightMargin = ResourceManagerView.BUTTON_DELETE_RIGHT_MARGIN_REDUCED_PX;
            int buttonDeleteSize = ResourceManagerView.BUTTON_DELETE_SIZE_REDUCED_PX;
            ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) buttonDelete.getLayoutParams();
            layoutParams.width = buttonDeleteSize;
            //layoutParams.height = buttonDeleteSize;
            layoutParams.rightMargin = buttonDeleteRightMargin;
            buttonDelete.setLayoutParams(layoutParams);
            ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) progressBarDownload.getLayoutParams();
            layoutParams2.rightMargin = buttonDeleteLeftMargin;
            progressBarDownload.setLayoutParams(layoutParams2);
        }else{
            int buttonDeleteRightMargin = Tools.convertDpToPixels(getContext(), ResourceManagerView.BUTTON_DELETE_RIGHT_MARGIN);
            int buttonDeleteSize = Tools.convertDpToPixels(getContext(), ResourceManagerView.BUTTON_DELETE_SIZE);
            ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) buttonDelete.getLayoutParams();
            layoutParams.width = buttonDeleteSize;
            //layoutParams.height = buttonDeleteSize;
            layoutParams.rightMargin = buttonDeleteRightMargin;
            buttonDelete.setLayoutParams(layoutParams);
            ConstraintLayout.LayoutParams layoutParams2 = (ConstraintLayout.LayoutParams) progressBarDownload.getLayoutParams();
            layoutParams2.rightMargin = buttonDeleteLeftMargin;
            progressBarDownload.setLayoutParams(layoutParams2);
        }
    }

    public void setDownloadProgress(int progress) {
        setDownloadProgress(progress, false, false);
    }

    public void setDownloadProgress(int progress, boolean unzipping, boolean testing) {
        if(progress > 0 && !unzipping && !testing) {
            // show the progress in the progress bar
            progressBarDownload.setIndeterminate(false);
            progressBarDownload.setProgress(progress);
            // show the numerical progress
            if (modelSizeMb > 0 && (state == State.DOWNLOADING || state == State.PAUSED)) {
                if (modelSizeMb >= 1000) {
                    DecimalFormat df = new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.US));
                    float modelSizeGb = (float) modelSizeMb / 1000F;
                    // even when the file downloaded is compressed (the size of the model is larger than the size downloaded),
                    // we show the progress considering the percentage on the final uncompressed size, to make the compression transparent.
                    float modelDownloadedGb = (progress * modelSizeGb) / 100;
                    textDownload.setText(df.format(modelDownloadedGb) + "/" + df.format(modelSizeGb) + " GB");
                } else {
                    DecimalFormat df = new DecimalFormat("0");
                    // even when the file downloaded is compressed (the size of the model is larger than the size downloaded),
                    // we show the progress considering the percentage on the final uncompressed size, to make the compression transparent.
                    float modelDownloadedMb = (float) (progress * modelSizeMb) / 100;
                    textDownload.setText(df.format(modelDownloadedMb) + "/" + df.format(modelSizeMb) + " MB");
                }
            }
            return;
        }
        if(unzipping){
            progressBarDownload.setIndeterminate(true);
            if (state == State.DOWNLOADING || state == State.PAUSED) {
                textDownload.setText("Unzipping...");
            }
            return;
        }
        if(testing){
            progressBarDownload.setIndeterminate(true);
            if (state == State.DOWNLOADING || state == State.PAUSED) {
                textDownload.setText("Testing...");
            }
            return;
        }
    }

    public void setError(int reason){
        if(reason != -1){
            errorIcon.setVisibility(VISIBLE);
            errorIcon.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(listener != null){
                        listener.onErrorPressed();
                    }
                }
            });
        }else{
            errorIcon.setVisibility(INVISIBLE);
            errorIcon.setOnClickListener(null);
        }
    }

    // --- Callback Interface (Similar to passing a function prop in React) ---

    public interface Listener {
        void onDownloadClicked();
        void onPauseClicked();
        void onResumeClicked();
        void onDeletePressed();
        void onErrorPressed();
    }

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
