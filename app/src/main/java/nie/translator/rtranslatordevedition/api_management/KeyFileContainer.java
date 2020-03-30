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

import android.content.DialogInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;
import java.io.File;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.GeneralActivity;
import nie.translator.rtranslatordevedition.Global;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.FileListAdapter;
import nie.translator.rtranslatordevedition.tools.gui.KeyFileSelectorButton;

public class KeyFileContainer {
    private GeneralActivity activity;
    private AppCompatImageButton deleteButton;
    private AlertDialog dialog;
    private Fragment fragment;
    private Global global;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private KeyFileSelectorButton selectFileButton;
    private TextView textView;

    public KeyFileContainer(final Global global, @NonNull final GeneralActivity activity, TextView textView, AppCompatImageButton deleteButton, KeyFileSelectorButton selectFileButton, Fragment fragment) {
        this.global = global;
        this.activity = activity;
        this.fragment = fragment;
        this.textView = textView;
        this.deleteButton = deleteButton;
        this.selectFileButton = selectFileButton;
        String apiKeyFileName = global.getApiKeyFileName();
        if (apiKeyFileName.length() > 0) {
            this.deleteButton.setVisibility(View.VISIBLE);
            this.textView.setText(apiKeyFileName);
        }
        this.deleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                activity.showConfirmDeleteDialog(new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        delete(new File(global.getFilesDir(), global.getApiKeyFileName()), new FileOperationListener() {
                            public void onSuccess() {
                                KeyFileContainer.this.textView.setText(global.getApiKeyFileName());
                                KeyFileContainer.this.deleteButton.setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                });
            }
        });
        this.selectFileButton.setOnClickListenerForActivated(new View.OnClickListener() {
            public void onClick(View v) {
                View editDialogLayout = activity.getLayoutInflater().inflate(R.layout.dialog_key_files, null);

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setCancelable(true);
                builder.setTitle((CharSequence) global.getResources().getString(R.string.dialog_select_file));

                dialog = builder.create();
                dialog.setView(editDialogLayout, 0, Tools.convertDpToPixels(activity, 16), 0, 0);
                dialog.show();

                final ListView listViewGui = (ListView) editDialogLayout.findViewById(R.id.list_view_dialog);
                final ProgressBar progressBar = (ProgressBar) editDialogLayout.findViewById(R.id.progressBar3);

                findJsonFiles(new File(Environment.getExternalStorageDirectory().getAbsolutePath()), (FilesListListener) new FilesListListener() {
                    public void onSuccess(ArrayList<File> filesList) {
                        progressBar.setVisibility(View.GONE);
                        listViewGui.setVisibility(View.VISIBLE);

                        final FileListAdapter adapter = new FileListAdapter(activity, filesList);
                        listViewGui.setAdapter(adapter);
                        listViewGui.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                                save((File) adapter.getItem(position), new FileOperationListener() {
                                    public void onSuccess() {
                                        KeyFileContainer.this.textView.setText(global.getApiKeyFileName());
                                        KeyFileContainer.this.deleteButton.setVisibility(View.VISIBLE);
                                        dialog.dismiss();
                                    }

                                    public void onFailure() {
                                        Toast.makeText(global, global.getResources().getString(R.string.error_picking_file), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private void findJsonFiles(final File dir, final FilesListListener filesListListener) {
        new Thread() {
            public void run() {
                super.run();
                final ArrayList<File> list = new ArrayList<>();
                findJsonFiles(dir, list);
                mainHandler.post(new Runnable() {
                    public void run() {
                        filesListListener.onSuccess(list);
                    }
                });
            }
        }.start();
    }

    private void findJsonFiles(File dir, ArrayList<File> matchingSAFFiles) {
        String safPattern = ".json";
        File[] listFile = dir.listFiles();
        if (listFile != null) {
            for (int i = 0; i < listFile.length; i++) {
                if (listFile[i].isDirectory()) {
                    findJsonFiles(listFile[i], matchingSAFFiles);
                } else if (listFile[i].getName().endsWith(safPattern)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(dir.toString());
                    sb.append(File.separator);
                    sb.append(listFile[i].getName());
                    matchingSAFFiles.add(new File(sb.toString()));
                }
            }
        }
    }

    private static abstract class FilesListListener {
        private FilesListListener() {
        }

        public void onSuccess(ArrayList<File> arrayList) {
        }
    }

    private void save(final File file, final FileOperationListener responseListener) {
        new Thread() {
            public void run() {
                super.run();
                if (Tools.copyFile(file, new File(activity.getFilesDir(), file.getName()))) {
                    global.setApiKeyFileName(file.getName());
                    global.resetApiToken();
                    mainHandler.post(new Runnable() {
                        public void run() {
                            responseListener.onSuccess();
                        }
                    });
                    return;
                }
                mainHandler.post(new Runnable() {
                    public void run() {
                        responseListener.onFailure();
                    }
                });
            }
        }.start();
    }

    private void delete(final File file, final FileOperationListener responseListener) {
        new Thread() {
            public void run() {
                super.run();
                Tools.deleteFile(file);
                global.setApiKeyFileName("");
                global.resetApiToken();
                mainHandler.post(new Runnable() {
                    public void run() {
                        responseListener.onSuccess();
                    }
                });
            }
        }.start();
    }

    public static abstract class FileOperationListener {
        public void onSuccess() {
        }

        public void onFailure() {
        }
    }
}
