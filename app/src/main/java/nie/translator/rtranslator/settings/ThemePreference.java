package nie.translator.rtranslator.settings;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.LanguageListAdapter;
import nie.translator.rtranslator.tools.gui.ThemeManager;

public class ThemePreference extends Preference {
    private Activity activity;

    public ThemePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ThemePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ThemePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThemePreference(Context context) {
        super(context);
    }


    public void initialize(Activity activity){
        this.activity = activity;
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (activity != null) {
                    showOptionsDialog();
                }
                return false;
            }
        });
        updateSummary();
    }

    private void updateSummary() {
        ThemeManager.ThemeOption choice = ThemeManager.getSavedChoice(activity);
        switch (choice) {
            case SYSTEM:
                setSummary(activity.getResources().getString(R.string.system_default));
                break;
            case LIGHT:
                setSummary(activity.getResources().getString(R.string.light));
                break;
            case DARK:
                setSummary(activity.getResources().getString(R.string.dark));
                break;
        }
    }

    private void showOptionsDialog(){
        AlertDialog dialog;

        //initialize and show dialog
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_theme_opions, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 22), 0, 0);
        dialog.show();

        //initialize dialog gui and listeners
        RadioGroup radioGroup = editDialogLayout.findViewById(R.id.radioTheme);
        MaterialButton applyButton = editDialogLayout.findViewById(R.id.apply_button);
        MaterialButton cancelButton = editDialogLayout.findViewById(R.id.cancel_button);

        ThemeManager.ThemeOption choice = ThemeManager.getSavedChoice(activity);
        switch (choice) {
            case SYSTEM:
                radioGroup.check(R.id.radioSystem);
                break;
            case LIGHT:
                radioGroup.check(R.id.radioLight);
                break;
            case DARK:
                radioGroup.check(R.id.radioDark);
                break;
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            /*if(checkedId == R.id.radioSystem){
                ThemeManager.apply(ThemeManager.ThemeOption.SYSTEM);
            }else if(checkedId == R.id.radioLight){
                ThemeManager.apply(ThemeManager.ThemeOption.LIGHT);
            }else if(checkedId == R.id.radioDark){
                ThemeManager.apply(ThemeManager.ThemeOption.DARK);
            }*/
        });
        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int checkedId = radioGroup.getCheckedRadioButtonId();
                if(checkedId == R.id.radioSystem){
                    ThemeManager.saveAndApply(activity, ThemeManager.ThemeOption.SYSTEM);
                    updateSummary();
                }else if(checkedId == R.id.radioLight){
                    ThemeManager.saveAndApply(activity, ThemeManager.ThemeOption.LIGHT);
                    updateSummary();
                }else if(checkedId == R.id.radioDark){
                    ThemeManager.saveAndApply(activity, ThemeManager.ThemeOption.DARK);
                    updateSummary();
                }
                dialog.dismiss();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //ThemeManager.restore(activity);
                dialog.dismiss();
            }
        });
    }
}
