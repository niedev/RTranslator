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

package nie.translator.rtranslator.settings;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import nie.translator.rtranslator.GeneralActivity;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.Tools;


public class SettingsActivity extends GeneralActivity {
    public static final String SETTINGS_FRAGMENT = "startSettings";
    public static final String MODEL_MANAGER = "startModelManager";
    public static final String MOZILLA_MANAGER = "startMozillaManager";
    private Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        Drawable icon = toolbar.getNavigationIcon();

        if (icon != null) {
            TypedValue color = new TypedValue();
            toolbar.getContext().getTheme().resolveAttribute(
                    R.attr.colorPrimaryStrong, color, true);

            icon = DrawableCompat.wrap(icon).mutate();
            DrawableCompat.setTint(icon, color.data);
            toolbar.setNavigationIcon(icon);
        }

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                decorView.getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        Intent intent = getIntent();
        String action = null;
        if(intent != null && intent.hasExtra("start_mode")) {
            action = intent.getStringExtra("start_mode");
        }
        if(action != null){
            startFragment(action, null);
        }else if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment_inizialization");
        }else {
            startFragment(SETTINGS_FRAGMENT, null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Update the original intent with the new one.
        // This is used when this activity is launched when it is already running.
        // If we don't set the newIntent, the new one passed in the launch is ignored.
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This is in case the intent with the command to start a fragment has been called
        // when this activity was already started, in this case we start it here and not in onCreate
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra("start_mode")) {
            String action = intent.getStringExtra("start_mode");
            if(action != null){
                /* We remove the extra from the activity intent.
                 * This is because otherwise, the next time we launch this Activity without an intent, this intent will be used again and again.
                 * For this reason, the intent request must be executed only one time for each time it is passed from outside.
                 */
                intent.removeExtra("start_mode");
                // we start the requested fragment (if it has not already started in onCreate)
                startFragment(action, null);
            }
        }
    }

    public void startFragment(String action, Bundle bundle) {
        switch (action) {
            case SETTINGS_FRAGMENT: {
                if(!(fragment instanceof SettingsFragment)) {
                    SettingsFragment settingsFragment = new SettingsFragment();
                    if (bundle != null) {
                        settingsFragment.setArguments(bundle);
                    }
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    transaction.replace(R.id.fragment_settings_container, settingsFragment);
                    transaction.commit();
                    fragment = settingsFragment;
                }
                break;
            }
            case MODEL_MANAGER: {
                if(!(fragment instanceof ModelManagerFragment)) {
                    ModelManagerFragment modelManagerFragment = new ModelManagerFragment();
                    if (bundle != null) {
                        modelManagerFragment.setArguments(bundle);
                    }
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    transaction.replace(R.id.fragment_settings_container, modelManagerFragment);
                    transaction.commit();
                    fragment = modelManagerFragment;
                }
                break;
            }
            case MOZILLA_MANAGER: {
                if(!(fragment instanceof MozillaManagerFragment)) {
                    MozillaManagerFragment mozillaManagerFragment = new MozillaManagerFragment();
                    if (bundle != null) {
                        mozillaManagerFragment.setArguments(bundle);
                    }
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                    transaction.replace(R.id.fragment_settings_container, mozillaManagerFragment);
                    transaction.commit();
                    fragment = mozillaManagerFragment;
                }
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //Save the fragment's instance
        getSupportFragmentManager().putFragment(outState, "fragment_inizialization", fragment);
    }

    @Override
    public void onBackPressed() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_settings_container);
        if (fragment != null) {
            if (fragment instanceof SettingsFragment) {
                SettingsFragment settingsFragment = (SettingsFragment) fragment;
                if (settingsFragment.isDownloading()) {
                    showDownloadDialog();
                }else {
                    super.onBackPressed();
                }
            }else if (fragment instanceof ModelManagerFragment) {
                if(((ModelManagerFragment) fragment).checkSettingsChanged()){
                    showConfirmApplyDialog();
                }else {
                    ((ModelManagerFragment) fragment).applySettings();
                }
            }else if (fragment instanceof MozillaManagerFragment) {
                startFragment(MODEL_MANAGER, null);
            }else{
                super.onBackPressed();
            }
        }else{
            super.onBackPressed();
        }
    }

    private void showDownloadDialog() {
        //creation of the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.error_wait_download_end);
        builder.setPositiveButton(android.R.string.ok, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showConfirmApplyDialog(){
        final View editDialogLayout = getLayoutInflater().inflate(R.layout.dialog_apply, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(this, 16), 0, 0);
        dialog.show();

        MaterialButton applyButton = editDialogLayout.findViewById(R.id.apply_button);
        MaterialButton cancelButton = editDialogLayout.findViewById(R.id.exit_button);

        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                ((ModelManagerFragment) fragment).applySettings();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                ((ModelManagerFragment) fragment).restoreGuiPreferenceState();
                ((ModelManagerFragment) fragment).applySettings();
            }
        });
    }
}
