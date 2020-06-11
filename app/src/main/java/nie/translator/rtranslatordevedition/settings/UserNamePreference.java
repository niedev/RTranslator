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

package nie.translator.rtranslatordevedition.settings;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.GuiTools;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.tools.BluetoothTools;


public class UserNamePreference extends Preference {
    private TextView username;
    private Activity activity;
    private Global global;
    private Context context;

    public UserNamePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    public UserNamePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    public UserNamePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public UserNamePreference(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        username = (TextView) holder.findViewById(R.id.username);
        ImageButton editUsernameButton = (ImageButton) holder.findViewById(R.id.editNameButton);

        //user name initialization
        username.setText(global.getName());
        editUsernameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (activity != null) {
                    final GuiTools.EditTextDialog editTextDialog = GuiTools.createEditTextDialog(activity, username.getText().toString(), global.getResources().getString(R.string.dialog_insert_name), R.layout.dialog_edit_name);
                    final AlertDialog dialog = editTextDialog.getDialog();
                    final EditText editText = editTextDialog.getEditText();
                    final TextInputLayout editTextLayout = editTextDialog.getEditTextLayout();
                    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialogInterface) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    String savedName = editText.getText().toString();
                                    if (savedName.length() > 0) {
                                        //compatibility check with supported characters
                                        ArrayList<Character> supportedCharacters = BluetoothTools.getSupportedUTFCharacters(context);
                                        boolean equals = true;
                                        for (int i = 0; i < savedName.length() && equals; i++) {
                                            if (!supportedCharacters.contains(Character.valueOf(savedName.charAt(i)))) {
                                                equals = false;
                                            }
                                        }

                                        if (equals) {
                                            global.setName(savedName);
                                            username.setText(savedName);
                                            dialog.dismiss();
                                        } else {
                                            editTextLayout.setError(global.getResources().getString(R.string.error_wrong_username) + BluetoothTools.getSupportedNameCharactersString(global));
                                        }
                                    } else {
                                        editTextLayout.setError(global.getResources().getString(R.string.error_missing_username));
                                    }
                                }
                            });
                        }
                    });
                    dialog.show();
                }
            }
        });
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
        this.global = (Global) activity.getApplication();
    }
}
