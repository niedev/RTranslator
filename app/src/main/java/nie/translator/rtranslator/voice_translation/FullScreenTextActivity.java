package nie.translator.rtranslator.voice_translation;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.gui.GuiTools;

public class FullScreenTextActivity extends AppCompatActivity {
    public static final String EXTRA_TEXT = "extra_fullscreen_text";
    public static final String EXTRA_LANGUAGE = "extra_fullscreen_language";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_text);

        // Retrieve the passed text and language
        String passedText = getIntent().getStringExtra(EXTRA_TEXT);
        CustomLocale passedLanguage = (CustomLocale) getIntent().getSerializableExtra(EXTRA_LANGUAGE);

        // Set the passed text
        TextView textView = findViewById(R.id.fullscreen_text_view);
        if (passedText != null) {
            textView.setText(passedText);
        }

        // Set text language for accessibility
        if(passedLanguage != null) {
            GuiTools.setTextLanguageInfoForAccessibility(textView, passedLanguage);
        }

        // Enable internal textView scrolling if the text hits 16sp and still overflows.
        textView.setMovementMethod(new ScrollingMovementMethod());

        // Setup the Exit Button
        ImageButton exitButton = findViewById(R.id.button_exit);
        exitButton.setOnClickListener(v -> finish()); // Closes the Activity

        // Enable Full-Screen Immersive Mode
        hideSystemUI();
    }

    private void hideSystemUI() {
        // Tells Android that the app will draw over the system window areas
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Hide both the status bar and the navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars());

        // Allow the bars to temporarily appear if the user swipes from the screen edge
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }
}
