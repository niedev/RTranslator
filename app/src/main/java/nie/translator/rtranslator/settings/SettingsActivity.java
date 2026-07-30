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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import nie.translator.rtranslator.GeneralActivity;
import nie.translator.rtranslator.R;


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
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowHomeEnabled(true);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment_inizialization");
        } else {
            startFragment(SETTINGS_FRAGMENT, null);
        }
    }

    public void startFragment(String action, Bundle bundle) {
        switch (action) {
            case SETTINGS_FRAGMENT: {
                SettingsFragment accessFragment = new SettingsFragment();
                if (bundle != null) {
                    accessFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                transaction.replace(R.id.fragment_settings_container, accessFragment);
                transaction.commit();
                fragment = accessFragment;
                break;
            }
            case MODEL_MANAGER: {
                //ModelManagerFragmentOld modelManagerFragment = new ModelManagerFragmentOld();
                ModelManagerFragment modelManagerFragment = new ModelManagerFragment();
                if (bundle != null) {
                    modelManagerFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                transaction.replace(R.id.fragment_settings_container, modelManagerFragment);
                transaction.commit();
                fragment = modelManagerFragment;
                break;
            }
            case MOZILLA_MANAGER: {
                MozillaManagerFragment mozillaManagerFragment = new MozillaManagerFragment();
                if (bundle != null) {
                    mozillaManagerFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                transaction.replace(R.id.fragment_settings_container, mozillaManagerFragment);
                transaction.commit();
                fragment = mozillaManagerFragment;
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
                startFragment(SETTINGS_FRAGMENT, null);
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
}
