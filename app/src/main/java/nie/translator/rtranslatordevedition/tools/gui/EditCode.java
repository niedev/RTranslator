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

package nie.translator.rtranslatordevedition.tools.gui;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

public class EditCode extends AppCompatEditText {
    private boolean muteTextWatcher=false;
    private boolean delete = false;
    private int codeLength=4;

    public EditCode(Context context) {
        super(context);
        initEditText();
    }

    public EditCode(Context context, AttributeSet attrs) {
        super(context, attrs);
        initEditText();
    }

    public EditCode(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initEditText();
    }

    public void setCodeLenght(int codeLength){
        this.codeLength=codeLength;
    }

    private void initEditText() {
        setLetterSpacing(0.5f);
        setSingleLine(true);
        setText("------");
        SpannableString lastSpannableString = new SpannableString("------");
        ForegroundColorSpan fcs = new ForegroundColorSpan(Color.LTGRAY);
        lastSpannableString.setSpan(fcs, 0, lastSpannableString.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        final SpannableStringBuilder text = new SpannableStringBuilder(lastSpannableString);

        setText(text);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if(!muteTextWatcher) {
                    if (count > after) {
                        delete = true;
                    }
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(!muteTextWatcher) {
                    int replacePosition = getSelectionEnd();

                    if (start < codeLength) {
                        if (!delete) {
                            SpannableString lastSpannableChar = new SpannableString(s.subSequence(start, start + count));
                            // Span to set char color
                            ForegroundColorSpan fcs = new ForegroundColorSpan(Color.BLACK);
                            // Set the text color for the last character
                            lastSpannableChar.setSpan(fcs, 0, 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            text.delete(start, start + 1);
                            text.insert(start, lastSpannableChar);
                        } else {
                            SpannableString lastSpannableChar = new SpannableString("-");
                            // Span to set char color
                            ForegroundColorSpan fcs = new ForegroundColorSpan(Color.LTGRAY);
                            // Set the text color for the last character
                            lastSpannableChar.setSpan(fcs, 0, 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                            text.delete(start, start + 1);
                            text.insert(start, lastSpannableChar);
                        }
                        muteTextWatcher = true;
                        setText(text);
                        setSelection(replacePosition);
                    } else {
                        muteTextWatcher = true;
                        setText(text);
                        setSelection(replacePosition - 1);
                    }

                    delete = false;
                }else {
                    muteTextWatcher=false;
                }

            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
