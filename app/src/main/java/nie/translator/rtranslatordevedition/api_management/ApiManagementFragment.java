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


import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.CustomTime;
import nie.translator.rtranslatordevedition.tools.gui.CustomDayGraphsPagerAdapter;
import nie.translator.rtranslatordevedition.tools.gui.DeactivableButton;
import nie.translator.rtranslatordevedition.tools.gui.GuiTools;
import nie.translator.rtranslatordevedition.tools.gui.KeyFileSelectorButton;


public class ApiManagementFragment extends Fragment {
    private final int APPEAR_GRAPHS = 0;
    private final int APPEAR_NO_DATA_MESSAGE = 1;
    private ImageButton buttonNext;
    private ImageButton buttonPrev;
    private TextView graphDate;
    private TextView graphDescription;
    private TextView noDataMessage;
    private ViewPager pager;
    private TextView keyFileName;
    private TextView costsDescription;
    private TextView keyFileDescription;
    private KeyFileSelectorButton selectFileButton;
    private AppCompatImageButton deleteButton;
    private KeyFileContainer keyFileContainer;
    private int index;
    private ConsumptionsDataManager databaseManager;
    private Handler selfHandler;
    private Global global;
    private ApiManagementActivity activity;
    //permissions
    public static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 5;
    public static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public ApiManagementFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selfHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                int command = message.getData().getInt("command");
                switch (command) {
                    case APPEAR_GRAPHS:
                        CustomTime olderDate = message.getData().getParcelable("olderDate");
                        if (olderDate != null) {
                            olderDate = new CustomTime(olderDate.getTime());
                            pager.setAdapter(new CustomDayGraphsPagerAdapter(activity, olderDate, databaseManager));
                            index = pager.getAdapter().getCount() - 1;
                            buttonNext.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    int itemCount = pager.getAdapter().getCount();
                                    if (index < itemCount - 1) {
                                        index++;
                                    }
                                    pager.setCurrentItem(index, true);
                                }
                            });
                            buttonPrev.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (index > 0) {
                                        index--;
                                    }
                                    pager.setCurrentItem(index, true);
                                }
                            });
                            pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                                @Override
                                public void onPageSelected(int index) {
                                    ApiManagementFragment.this.onPageSelected(index);
                                }
                            });

                            if (index == pager.getCurrentItem()) {
                                onPageSelected(index);
                            } else {
                                pager.setCurrentItem(index);
                            }
                        }
                        break;
                    case APPEAR_NO_DATA_MESSAGE:
                        buttonNext.setVisibility(View.GONE);
                        buttonPrev.setVisibility(View.GONE);
                        graphDate.setVisibility(View.GONE);
                        graphDescription.setVisibility(View.GONE);
                        pager.setVisibility(View.GONE);
                        noDataMessage.setVisibility(View.VISIBLE);
                        break;
                }
                return true;
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_credit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        buttonNext = view.findViewById(R.id.buttonNext);
        buttonPrev = view.findViewById(R.id.buttonPrev);
        graphDate = view.findViewById(R.id.graphDate);
        graphDescription = view.findViewById(R.id.graphDescription);
        noDataMessage = view.findViewById(R.id.noDataMessage);
        pager = view.findViewById(R.id.viewPager);
        keyFileName = view.findViewById(R.id.keyText);
        deleteButton = view.findViewById(R.id.deleteKeyButton);
        selectFileButton = view.findViewById(R.id.editKeyButton);
        costsDescription = view.findViewById(R.id.costsDescription);
        costsDescription.setMovementMethod(LinkMovementMethod.getInstance());
        keyFileDescription = view.findViewById(R.id.keyFileDescription);
        keyFileDescription.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (ApiManagementActivity) requireActivity();
        global = (Global) activity.getApplication();

        databaseManager = new ConsumptionsDataManager(activity);
        new Thread("appearGraphs") {
            @Override
            public void run() {
                if (databaseManager.getHoursCount() > 0) {
                    appearGraphs();
                } else {
                    appearNoDataMessage();
                }
            }
        }.start();
        //keyfile container initialization
        keyFileContainer = new KeyFileContainer(global, activity, keyFileName, deleteButton, selectFileButton, this);

        selectFileButton.setOnClickListenerForDeactivatedForMissingMicPermission(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
    }

    private void appearNoDataMessage() {
        Messenger selfMessenger = new Messenger(selfHandler);
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putInt("command", APPEAR_NO_DATA_MESSAGE);
        message.setData(bundle);
        try {
            selfMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void appearGraphs() {
        Messenger selfMessenger = new Messenger(selfHandler);
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putInt("command", APPEAR_GRAPHS);
        bundle.putParcelable("olderDate", databaseManager.getOlderDate());
        message.setData(bundle);
        try {
            selfMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void onPageSelected(int index) {
        CustomDayGraphsPagerAdapter adapter = (CustomDayGraphsPagerAdapter) pager.getAdapter();
        int itemCount = adapter.getCount();
        CustomTime date = adapter.getDate(index);
        int day = date.getDay();
        String monthName = date.getMonthName();
        int year = date.getYear();
        graphDate.setText(day + " " + monthName + " " + year);

        //checking if need to deactivate or activate the buttons
        if (index == itemCount - 1) {
            if (buttonNext.isEnabled()) {
                buttonNext.setEnabled(false);
                buttonNext.setImageTintList(GuiTools.getColorStateList(activity, R.color.light_gray));
            }
        } else {
            if (!buttonNext.isEnabled()) {
                buttonNext.setEnabled(true);
                buttonNext.setImageTintList(GuiTools.getColorStateList(activity, R.color.very_dark_gray));
            }
        }
        if (index == 0) {
            if (buttonPrev.isEnabled()) {
                buttonPrev.setEnabled(false);
                buttonPrev.setImageTintList(GuiTools.getColorStateList(activity, R.color.light_gray));
            }
        } else {
            if (!buttonPrev.isEnabled()) {
                buttonPrev.setEnabled(true);
                buttonPrev.setImageTintList(GuiTools.getColorStateList(activity, R.color.very_dark_gray));
            }
        }
    }

    /**
     * Handles user acceptance (or denial) of our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(activity, R.string.error_missing_mic_permissions, Toast.LENGTH_LONG).show();
                selectFileButton.deactivate(DeactivableButton.DEACTIVATED_FOR_MISSING_MIC_PERMISSION);
                return;
            }
        }

        //possible activation of the selectFileButton
        if(selectFileButton.getActivationStatus()!=DeactivableButton.ACTIVATED) {
            selectFileButton.activate(false);
        }
    }
}
