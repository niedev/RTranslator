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

package nie.translator.rtranslator.access;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import nie.translator.rtranslator.GeneralActivity;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.downloader2.DownloadGroupInfo;
import nie.translator.rtranslator.downloader2.DownloadManager;
import nie.translator.rtranslator.settings.ModelManagerFragment;
import nie.translator.rtranslator.settings.MozillaManagerFragment;
import nie.translator.rtranslator.tools.Tools;


public class AccessActivity extends GeneralActivity {
    public static final int USER_DATA_FRAGMENT = 0;
    public static final int NOTICE_FRAGMENT = 1;
    public static final int DOWNLOAD_FRAGMENT = 2;
    public static final int MODEL_MANAGER = 3;
    public static final int MOZILLA_MANAGER = 4;
    private Fragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        Global global = (Global) getApplication();
        if (savedInstanceState != null) {
            //Restore the fragment's instance
            fragment = getSupportFragmentManager().getFragment(savedInstanceState, "fragment_inizialization");
        } else {
            final SharedPreferences sharedPreferences = this.getSharedPreferences("default", Context.MODE_PRIVATE);
            String savedUserName = sharedPreferences.getString("name", "");
            if(savedUserName.length()>0){  //means that the user has already inserted the info in UserDataFragment, so we can start directly the DownloadFragment or the ModelManagerFragment
                DownloadManager downloadManager = new DownloadManager(this);
                ArrayList<DownloadGroupInfo> savedDownloadStatus = downloadManager.getSavedDownloadStatus();
                int index = savedDownloadStatus.indexOf(global.getInitialDownloadInfo());
                if(index != -1 && savedDownloadStatus.get(index).isAllDownloadCompleted()){  //means that the initial download is already completed, so we can start directly the ModelManagerFragment
                    startFragment(MODEL_MANAGER, null);
                }else{   //means that the initial download is not completed, so we must start the DownloadFragment
                    startFragment(DOWNLOAD_FRAGMENT, null);
                }
            }else{
                startFragment(NOTICE_FRAGMENT, null);
            }
        }
    }

    @Override
    protected void onStart() {
        Global global = (Global) getApplication();
        if(global != null){
            global.setAccessActivity(this);
        }
        super.onStart();  //called here because otherwise the onStart of the DownloadFragment is called before this onStart, and this could cause problems.
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(Global.USE_EXTERNAL_MEMORY_FOR_RESOURCES) {
            checkAndRequireAllFilesPermission();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Global global = (Global) getApplication();
        if(global != null){
            global.setAccessActivity(null);
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
                if (Global.REQUIRED_PERMISSIONS_NOTIFICATIONS.length > 0 && !Tools.hasPermissions(this, Global.REQUIRED_PERMISSIONS_NOTIFICATIONS)) {
                    showPermissionDialog();
                }
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
            case DOWNLOAD_FRAGMENT: {
                DownloadFragment2 downloadFragment = new DownloadFragment2();
                if (bundle != null) {
                    downloadFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_initialization_container, downloadFragment);
                transaction.commit();
                fragment = downloadFragment;
                break;
            }
            case MODEL_MANAGER: {
                ModelManagerFragment modelManagerFragment = new ModelManagerFragment();
                if (bundle != null) {
                    modelManagerFragment.setArguments(bundle);
                }
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_initialization_container, modelManagerFragment);
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
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_initialization_container, mozillaManagerFragment);
                transaction.commit();
                fragment = mozillaManagerFragment;
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
        if(fragment instanceof DownloadFragment2){
            startFragment(USER_DATA_FRAGMENT,null);
            return;
        }
        if(fragment instanceof MozillaManagerFragment){
            startFragment(MODEL_MANAGER, null);
            return;
        }
        finishAndRemoveTask(); //todo: test if this closes the app when we are in the model manager fragment
        //super.onBackPressed();
    }

    private void checkAndRequireAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesPermission();
            }
        }
    }

    private void requestAllFilesPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 100);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivityForResult(intent, 100);
        }
    }

    public void showPermissionDialog(){
        final View editDialogLayout = this.getLayoutInflater().inflate(R.layout.dialog_permission, null);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog);
        builder.setCancelable(true);

        AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(this, 16), 0, 0);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {

            }
        });

        ImageView icon = editDialogLayout.findViewById(R.id.dialogIcon);
        TextView text = editDialogLayout.findViewById(R.id.textView);
        CardView continueButton = editDialogLayout.findViewById(R.id.okButtonCard);
        CardView cancelButton = editDialogLayout.findViewById(R.id.cancelButtonCard);

        //set icon
        icon.setImageDrawable(getResources().getDrawable(R.drawable.notification_icon));

        //set text
        text.setText(getString(R.string.description_permission_notification));

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                requestPermissions(Global.REQUIRED_PERMISSIONS_NOTIFICATIONS, Global.REQUEST_CODE_PERMISSIONS_NOTIFICATIONS);
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.cancel();
            }
        });
    }
}


