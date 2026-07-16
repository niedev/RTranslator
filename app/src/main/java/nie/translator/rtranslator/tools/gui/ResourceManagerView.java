package nie.translator.rtranslator.tools.gui;

import android.animation.Animator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.gui.animations.CustomAnimator;

public class ResourceManagerView extends ConstraintLayout {
    public enum State {
        EMPTY,
        PAUSED,
        DOWNLOADING,
        DOWNLOADED
    };

    public static int BUTTON_DELETE_LEFT_MARGIN = 16;
    public static int BUTTON_DELETE_RIGHT_MARGIN = 16;
    public static int BUTTON_DELETE_SIZE = 30;

    private State state;
    private TextView titleResource;
    private TextView descriptionResource;
    private TextView textResource;
    private ProgressBar progressBarDownload;
    private ImageView buttonDelete;
    private ImageView buttonDownload;
    private ImageView playIcon;
    private ImageView pauseIcon;
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
        textResource = findViewById(R.id.textDownload);
        progressBarDownload = findViewById(R.id.progressBarDownload);
        buttonDownload = findViewById(R.id.buttonDownload);
        buttonDelete = findViewById(R.id.buttonDelete);
        playIcon = findViewById(R.id.playImage);
        pauseIcon = findViewById(R.id.pauseImage);

        // Apply custom XML attributes if provided
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ResourceManagerView);

            if (a.hasValue(R.styleable.ResourceManagerView_modelTitle)) {
                titleResource.setText(a.getString(R.styleable.ResourceManagerView_modelTitle));
            }
            if (a.hasValue(R.styleable.ResourceManagerView_modelDescription)) {
                descriptionResource.setText(a.getString(R.styleable.ResourceManagerView_modelDescription));
            }
            if (a.hasValue(R.styleable.ResourceManagerView_modelSize)) {
                textResource.setText(a.getString(R.styleable.ResourceManagerView_modelSize));
            }

            a.recycle();
        }

        // Set up internal component logic
        buttonDownload.setOnClickListener(v -> {
            // Trigger download logic natively inside the component
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

    // --- Public APIs to manipulate the component from the Activity/Fragment ---


    public void setState(State state, boolean animate) {
        State oldState = this.state;
        this.state = state;
        if(animation != null) {
            animation.cancel();
            animation = null;
        }
        if(animate) {
            switch (state) {
                case DOWNLOADING:
                    if(oldState == State.EMPTY) {
                        animation = animator.animateResourceDownloadStart(getContext(), buttonDownload, progressBarDownload, pauseIcon, buttonDelete, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                animation = null;
                            }
                        });
                    }else if(oldState == State.PAUSED){
                        pauseIcon.setVisibility(View.INVISIBLE);
                        playIcon.setVisibility(View.VISIBLE);
                    }
                    break;
                case PAUSED:
                    if(oldState == State.DOWNLOADING){
                        pauseIcon.setVisibility(View.VISIBLE);
                        playIcon.setVisibility(View.INVISIBLE);
                    }
                    break;
                case EMPTY:
                    if(oldState == State.DOWNLOADING || oldState == State.DOWNLOADED){
                        ImageView pauseOrResumeIcon;
                        if(pauseIcon.getVisibility() == View.VISIBLE){
                            pauseOrResumeIcon = pauseIcon;
                        }else{
                            pauseOrResumeIcon = playIcon;
                        }
                        animation = animator.animateResourceDeletion(getContext(), buttonDownload, progressBarDownload, pauseOrResumeIcon, buttonDelete, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                animation = null;
                            }
                        });
                    }
                    break;
                case DOWNLOADED:
                    if(oldState == State.DOWNLOADING){
                        animation = animator.animateResourceDownloadCompleted(getContext(), progressBarDownload, pauseIcon, buttonDelete, new CustomAnimator.Listener() {
                            @Override
                            public void onAnimationEnd() {
                                animation = null;
                            }
                        });
                    }
                    break;
            }
        }else{
            switch (state) {
                case DOWNLOADING:
                    buttonDownload.setVisibility(INVISIBLE);
                    progressBarDownload.setVisibility(VISIBLE);
                    pauseIcon.setVisibility(VISIBLE);
                    playIcon.setVisibility(INVISIBLE);
                    buttonDelete.setVisibility(VISIBLE);
                    break;
                case PAUSED:
                    buttonDownload.setVisibility(INVISIBLE);
                    progressBarDownload.setVisibility(VISIBLE);
                    pauseIcon.setVisibility(INVISIBLE);
                    playIcon.setVisibility(VISIBLE);
                    buttonDelete.setVisibility(VISIBLE);
                    break;
                case EMPTY:
                    buttonDownload.setVisibility(VISIBLE);
                    progressBarDownload.setVisibility(INVISIBLE);
                    pauseIcon.setVisibility(INVISIBLE);
                    playIcon.setVisibility(INVISIBLE);
                    buttonDelete.setVisibility(INVISIBLE);
                    break;
                case DOWNLOADED:
                    buttonDownload.setVisibility(INVISIBLE);
                    progressBarDownload.setVisibility(INVISIBLE);
                    pauseIcon.setVisibility(INVISIBLE);
                    playIcon.setVisibility(INVISIBLE);
                    buttonDelete.setVisibility(VISIBLE);
                    break;
            }
        }
    }

    public void setDownloadProgress(int progress) {
        progressBarDownload.setProgress(progress);
        if (progress >= 100) {
            progressBarDownload.setVisibility(View.INVISIBLE);
            buttonDownload.setVisibility(View.VISIBLE);
            // Maybe change icon to a checkmark here
        }
    }

    // --- Callback Interface (Similar to passing a function prop in React) ---

    public interface Listener {
        void onDownloadClicked();
        void onPauseClicked();
        void onResumeClicked();
        void onDeletePressed();
    }

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }
}
