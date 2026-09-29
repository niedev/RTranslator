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

package nie.translator.rtranslator.tools.gui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.LocaleSpan;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Objects;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.CustomLocale;
import nie.translator.rtranslator.tools.Tools;

public class GuiTools {
    public static EditTextDialog createEditTextDialog(Activity activity, String text, String title, int layout) {
        final View editDialogLayout = activity.getLayoutInflater().inflate(layout, null);
        final EditText editText = editDialogLayout.findViewById(R.id.editText);
        final TextInputLayout editTextLayout = editDialogLayout.findViewById(R.id.editTextLayout);
        if (text != null) {
            editText.setText(text);
            editText.setSelection(text.length());
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle(title);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setView(editDialogLayout);
        Objects.requireNonNull(dialog.getWindow()).setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return new EditTextDialog(dialog, editText, editTextLayout);
    }

    public static class EditTextDialog {
        private AlertDialog dialog;
        private EditText editText;
        private TextInputLayout editTextLayout;

        public EditTextDialog(AlertDialog dialog, EditText editText, TextInputLayout editTextLayout) {
            this.dialog = dialog;
            this.editText = editText;
            this.editTextLayout = editTextLayout;
        }

        public AlertDialog getDialog() {
            return dialog;
        }

        public EditText getEditText() {
            return editText;
        }

        public TextInputLayout getEditTextLayout() {
            return editTextLayout;
        }
    }

    public static int getColor(Context context, int colorCode) {
        return context.getResources().getColor(colorCode, null);
    }

    public static ColorStateList getColorStateList(Context context, int colorCode) {
        return context.getResources().getColorStateList(colorCode, null);
    }


    public static void showLanguageListDialog(Activity activity, String title, ArrayList<CustomLocale> languages, CustomLocale selectedLanguage, boolean showTTSInfo, OnLanguageClickListener clickListener) {
        LanguageListAdapter listViewAdapter;
        ListView listView;
        AlertDialog dialog;

        //initialize and show dialog
        final View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_languages, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialogWithTitle);
        builder.setCancelable(true);

        dialog = builder.create();
        dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 22), 0, 0);
        dialog.show();

        //initialize dialog gui
        TextView titleView = editDialogLayout.findViewById(R.id.title);
        listView = editDialogLayout.findViewById(R.id.list_view_dialog);
        SearchView searchView = editDialogLayout.findViewById(R.id.search);
        TextView emptyTextView = editDialogLayout.findViewById(R.id.empty_text_view);

        titleView.setText(title);

        //initialize and show dialog list
        listViewAdapter = new LanguageListAdapter(activity, showTTSInfo, languages, selectedLanguage);
        listView.setEmptyView(emptyTextView);
        listView.setAdapter(listViewAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                CustomLocale language = null;
                Object item = listViewAdapter.getItem(position);
                if(item instanceof CustomLocale){
                    language = (CustomLocale) item;
                }
                clickListener.onItemClick(parent, view, position, id, language);
                dialog.dismiss();
            }
        });

        //initialize search bar
        searchView.setOnClickListener(v -> searchView.setIconified(false));   //make the whole search bar clickable
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // We don't need to do anything on submit, because we filter as the user types
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Call the built-in filter method on the adapter
                listViewAdapter.getFilter().filter(newText);
                return true;
            }
        });

        Log.i("languages_list", "List Showed");
    }

    public  interface OnLanguageClickListener {
        void onItemClick(AdapterView<?> parent, View view, final int position, long id, @Nullable CustomLocale item);
    }

    /**
     * This method intercepts TalkBack interaction with the descriptionView and instead of reading the
     * long description directly it reads a placeholder.
     * It also defines a click listener only for TalkBack that if clicked, makes TalkBack read the full description.
     * This way the user will not listen the long description during TalkBack navigation directly, but only with a click.
     * @param descriptionView a TextView that contains a long description
     */
    public static void setDescriptionOptimizationForAccessibility(TextView descriptionView){
        ViewCompat.setAccessibilityDelegate(descriptionView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                // Override what TalkBack reads when it lands on the view
                info.setContentDescription("Mode description");  //todo: convert the text to resource and translate it

                // Add a custom action hint ("Double-tap to read full description")
                AccessibilityNodeInfoCompat.AccessibilityActionCompat readAction =
                        new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                AccessibilityNodeInfoCompat.ACTION_CLICK,
                                "read full description"  //todo: convert the text to resource and translate it
                        );
                info.addAction(readAction);
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, Bundle args) {
                // 3. Handle the double-tap to read the text
                if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                    CharSequence textToRead = ((TextView) host).getText();
                    host.announceForAccessibility(textToRead);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    /**
     * Intercepts TalkBack interaction with the passed view and adds the language info to the text read by it.
     * This way, if the system TTS supports the language, TalkBack with read it with that language (otherwise it will use the default system language).
     * @param view
     * @param language
     */
    public static void setTextLanguageInfoForAccessibility(View view, CustomLocale language){
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                // Grab whatever text is currently inside the view
                CharSequence currentText = info.getText();

                if (currentText != null && currentText.length() > 0) {
                    SpannableString accessibleText = new SpannableString(currentText);

                    // Tell TalkBack to read this specific node in the source language
                    accessibleText.setSpan(
                            new LocaleSpan(language.getLocale()),
                            0,
                            accessibleText.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    info.setText(accessibleText);
                }
            }
        });
    }

    public static boolean isTalkBackActive(Context context){
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean isAccessibilityEnabled = am.isEnabled();
        boolean isExploreByTouchEnabled = am.isTouchExplorationEnabled();
        return isAccessibilityEnabled && isExploreByTouchEnabled;
    }
}
