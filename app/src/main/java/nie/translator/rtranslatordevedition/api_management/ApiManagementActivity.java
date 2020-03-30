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

package nie.translator.rtranslatordevedition.api_management;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import nie.translator.rtranslatordevedition.GeneralActivity;
import nie.translator.rtranslatordevedition.R;


public class ApiManagementActivity extends GeneralActivity {
    public static final String CREDIT_FRAGMENT = "startCredit";
    private Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credit);

        Toolbar toolbar = findViewById(R.id.toolbarCredit);
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowHomeEnabled(true);

        if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment_inizialization");
        } else {
            startFragment(CREDIT_FRAGMENT, null);
        }
    }

    public void startFragment(String action, Bundle bundle) {
        switch (action) {
            case CREDIT_FRAGMENT: {
                ApiManagementFragment apiManagementFragment = new ApiManagementFragment();
                if (bundle != null) {
                    apiManagementFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
                transaction.replace(R.id.fragment_credit_container, apiManagementFragment);
                transaction.commit();
                fragment = apiManagementFragment;
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
}
