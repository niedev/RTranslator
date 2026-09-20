package nie.translator.rtranslator.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class ShowOriginalTranscriptionMsgPreference extends SwitchPreference {
    private SettingsFragment fragment;
    private Translator translator;
    private Global global;
    private SettingsActivity activity;

    public ShowOriginalTranscriptionMsgPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ShowOriginalTranscriptionMsgPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ShowOriginalTranscriptionMsgPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShowOriginalTranscriptionMsgPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        //translator = new Translator((Global) fragment.requireActivity().getApplication(), Translator.MADLAD_CACHE);
        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if(global != null) {
                    final SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("ShowOriginalTranscriptionMsgPreference", (Boolean) newValue);
                    editor.apply();
                }
                return true;
            }
        });
    }

    public void setFragment(@NonNull SettingsFragment fragment) {
        this.fragment = fragment;
        this.activity = (SettingsActivity) fragment.requireActivity();
        this.global = (Global) activity.getApplication();
    }
}
