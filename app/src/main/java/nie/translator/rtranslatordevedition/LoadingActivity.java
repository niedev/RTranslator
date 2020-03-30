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

package nie.translator.rtranslatordevedition;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.access.AccessActivity;
import nie.translator.rtranslatordevedition.tools.CustomLocale;
import nie.translator.rtranslatordevedition.tools.ErrorCodes;
import nie.translator.rtranslatordevedition.voice_translation.VoiceTranslationActivity;


public class LoadingActivity extends GeneralActivity {
    private Handler mainHandler;
    private boolean isVisible = false;
    private boolean startVoiceTranslationActivity = false;
    private Global global;

    public LoadingActivity() {
        // Required empty public constructor
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void onResume() {
        super.onResume();
        isVisible = true;
        global = (Global) getApplication();
        if (global.isFirstStart()) {
            Intent intent = new Intent(this, AccessActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        } else if (startVoiceTranslationActivity) {
            startVoiceTranslationActivity();
        } else {
            initializeApp();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false;
    }

    private void initializeApp() {
        global.getLanguages(false, new Global.GetLocalesListListener() {
            @Override
            public void onSuccess(ArrayList<CustomLocale> result) {
                if (isVisible) {
                    startVoiceTranslationActivity();
                } else {
                    startVoiceTranslationActivity = true;
                }
            }

            @Override
            public void onFailure(int[] reasons, long value) {
                Intent intent = new Intent(LoadingActivity.this, VoiceTranslationActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
    }

    private void startVoiceTranslationActivity() {
        Intent intent = new Intent(LoadingActivity.this, VoiceTranslationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void notifyGoogleTTSErrorDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                showGoogleTTSErrorDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        initializeApp();
                    }
                });
            }
        });
    }

    public void notifyInternetLack() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    // creation of the dialog.
                    AlertDialog.Builder builder = new AlertDialog.Builder(LoadingActivity.this);
                    //builder.setCancelable(true);
                    builder.setMessage(R.string.error_internet_lack_loading);
                    builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    builder.setPositiveButton(R.string.retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            initializeApp();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
            }
        });
    }

    private void notifyMissingGoogleTTSDialog() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible) {
                    showMissingGoogleTTSDialog();
                }
            }
        });
    }

    private void onFailure(int[] reasons, long value) {
        for (int aReason : reasons) {
            switch (aReason) {
                case ErrorCodes.SAFETY_NET_EXCEPTION:
                case ErrorCodes.MISSED_CONNECTION:
                    notifyInternetLack();
                    break;
                case ErrorCodes.MISSING_GOOGLE_TTS:
                    notifyMissingGoogleTTSDialog();
                    break;
                case ErrorCodes.GOOGLE_TTS_ERROR:
                    notifyGoogleTTSErrorDialog();
                    break;
                case ErrorCodes.MISSING_API_KEY:
                case ErrorCodes.WRONG_API_KEY:
                    break;
                default:
                    onError(aReason, value);
                    break;
            }
        }
    }
}
