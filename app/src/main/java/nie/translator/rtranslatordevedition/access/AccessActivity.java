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

package nie.translator.rtranslatordevedition.access;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import nie.translator.rtranslatordevedition.GeneralActivity;
import nie.translator.rtranslatordevedition.R;


public class AccessActivity extends GeneralActivity {
    public static final int USER_DATA_FRAGMENT = 0;
    public static final int NOTICE_FRAGMENT = 1;
    private Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);
        if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment_inizialization");
        } else {
            startFragment(NOTICE_FRAGMENT, null);
        }
    }

    public void startFragment(int action, Bundle bundle) {
        switch (action) {
            case USER_DATA_FRAGMENT: {
                UserDataFragment userDataFragment = new UserDataFragment();
                if (bundle != null) {
                    userDataFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_initialization_container, userDataFragment);
                transaction.commit();
                fragment = userDataFragment;
                break;
            }
            case NOTICE_FRAGMENT: {
                NoticeFragment noticeFragment = new NoticeFragment();
                if (bundle != null) {
                    noticeFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_initialization_container, noticeFragment);
                transaction.commit();
                fragment = noticeFragment;
                break;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //Save the fragment's instance
        getSupportFragmentManager().putFragment(outState, "fragment_inizialization", fragment);
    }


    @Override
    public void onBackPressed() {
        if(fragment instanceof UserDataFragment){
            startFragment(NOTICE_FRAGMENT,null);
            return;
        }
        super.onBackPressed();
    }
}


