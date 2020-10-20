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
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Tools;

public class UserImageContainer {
    private final String DEFAULT_IMAGE = "default";
    private final String CUSTOM_IMAGE = "custom";
    private static final String TEMP_PHOTO_DIRECTORY = "temporary_images";
    private static final String TEMP_PICKED_PHOTO_FILE = "temporary_picked_holder.jpg";
    private static final String TEMP_CROPPED_PHOTO_FILE = "temporary_cropped_holder.jpg";
    private static int PICK_IMAGE = 1;
    private static int CROP_IMAGE = 2;
    private ImageView imageView;
    private Bitmap image;
    private Activity activity;
    private Fragment fragment;


    public UserImageContainer(ImageView image, @NonNull final Activity activity, final Fragment fragment) {
        this.imageView = image;
        this.activity = activity;
        this.fragment = fragment;

        //user image initialization
        Bitmap imageBitmap = Tools.getBitmapFromFile(new File(activity.getFilesDir(), "user_image"));
        if (imageBitmap != null) {
            // get the user image and set it as the image
            RoundedBitmapDrawable circlularImage = RoundedBitmapDrawableFactory.create(activity.getResources(), imageBitmap);
            circlularImage.setCircular(true);
            imageView.setImageDrawable(circlularImage);
            imageView.setTag(CUSTOM_IMAGE);
            this.image = circlularImage.getBitmap();
        } else {
            imageView.setImageResource(R.drawable.user_icon);
            imageView.setTag(DEFAULT_IMAGE);
        }
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] items;
                if (imageView.getTag() == CUSTOM_IMAGE) {
                    items = new String[2];
                    items[0] = activity.getResources().getString(R.string.menu_image_select_from_gallery);
                    items[1] = activity.getResources().getString(R.string.menu_image_remove);
                } else {
                    items = new String[1];
                    items[0] = activity.getResources().getString(R.string.menu_image_select_from_gallery);
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setCancelable(true);
                builder.setTitle("user image");
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            Intent pickIntent = new Intent("android.intent.action.PICK");
                            pickIntent.setType("image/*");

                            if (fragment != null) {
                                fragment.startActivityForResult(pickIntent, PICK_IMAGE);
                            } else {
                                activity.startActivityForResult(pickIntent, PICK_IMAGE);
                            }
                        } else {
                            // insert the default image in the imageView
                            imageView.setImageResource(R.drawable.user_icon);
                            imageView.setTag(DEFAULT_IMAGE);
                            UserImageContainer.this.image = null;
                            // delete the previous saved image
                            File file = new File(activity.getFilesDir(), "user_image");
                            file.delete();
                        }
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data, boolean saveImage) {
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            //copyFile the result into cache
            copyImageUriIntoFile(data.getData(), getTempPickedFile());
            //start crop
            Intent intent = new Intent("com.android.camera.action.CROP");
            intent.setDataAndTypeAndNormalize(getTempPickedUri(), "image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", imageView.getWidth());
            intent.putExtra("outputY", imageView.getHeight());
            intent.putExtra("scaleUpIfNeeded", true);
            intent.putExtra("noFaceDetection", true);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra("output", getTempCroppedUri());
            intent.setClipData(ClipData.newRawUri("output", getTempCroppedUri()));
            if (fragment != null) {
                fragment.startActivityForResult(intent, CROP_IMAGE);
            } else {
                activity.startActivityForResult(intent, CROP_IMAGE);
            }

        } else if (requestCode == CROP_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Bitmap image = null;
            File tempFile = getTempCroppedFile();

            if (tempFile != null && tempFile.exists()) {
                String path = tempFile.getAbsolutePath();

                /*String filePath= Environment.getExternalStorageDirectory()+"/"+TEMP_PHOTO_FILE;
                System.out.println("path "+filePath);*/

                image = BitmapFactory.decodeFile(path);
                tempFile.delete();
            }

            if (image == null && data.getData() != null) {  //nel caso non sia stata salvata nel file
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = activity.getContentResolver().query(data.getData(), filePathColumn, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String picturePath = cursor.getString(columnIndex);
                    cursor.close();
                    // withdrawal of the selected image
                    image = BitmapFactory.decodeFile(picturePath);
                    // to prevent rotation bug
                    try {
                        image = modifyOrientation(image, picturePath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (image != null) {
                // insertion of the selected image in the imageView
                RoundedBitmapDrawable circlularImage = RoundedBitmapDrawableFactory.create(activity.getResources(), image);
                circlularImage.setCircular(true);
                imageView.setImageDrawable(circlularImage);
                imageView.setTag(CUSTOM_IMAGE);
                this.image = image;
                if (saveImage) {
                    // saving the selected image
                    Tools.saveBitmapToFile(new File(activity.getFilesDir(), "user_image"), image);
                }
            } else {
                Toast.makeText(activity, activity.getResources().getString(R.string.error_selecting_image), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void copyImageUriIntoFile(Uri sourceUri, File destinationFile) {
        InputStream inputStream = null;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            inputStream = activity.getContentResolver().openInputStream(sourceUri);
            if (inputStream != null) {
                bis = new BufferedInputStream(inputStream);
                bos = new BufferedOutputStream(new FileOutputStream(destinationFile, false));
                byte[] buf = new byte[1024];
                while (bis.read(buf) != -1) {
                    bos.write(buf);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null) bis.close();
                if (bos != null) bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void saveContent() {
        if (image != null) {
            Tools.saveBitmapToFile(new File(activity.getFilesDir(), "user_image"), image);
        }
    }

    private static Bitmap modifyOrientation(Bitmap bitmap, String image_absolute_path) throws IOException {
        ExifInterface ei = new ExifInterface(image_absolute_path);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotate(bitmap, 90);

            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotate(bitmap, 180);

            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotate(bitmap, 270);

            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return flip(bitmap, true, false);

            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return flip(bitmap, false, true);

            default:
                return bitmap;
        }
    }

    private static Bitmap rotate(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private static Bitmap flip(Bitmap bitmap, boolean horizontal, boolean vertical) {
        Matrix matrix = new Matrix();
        matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Uri getTempPickedUri() {
        return FileProvider.getUriForFile(activity, "nie.translator.rtranslatordevedition.fileprovider", getTempPickedFile());
    }

    private Uri getTempCroppedUri() {
        return FileProvider.getUriForFile(activity, "nie.translator.rtranslatordevedition.fileprovider", getTempCroppedFile());
    }

    private File getTempPickedFile() {
        /*if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(Environment.getExternalStorageDirectory(),TEMP_PHOTO_FILE);
            try {
                file.createNewFile();
            } catch (IOException e) {}
            return file;
        } else {
            return null;
        }*/
        activity.getCacheDir().mkdirs();
        File directory = new File(activity.getCacheDir(), TEMP_PHOTO_DIRECTORY);
        directory.mkdirs();
        File file = new File(directory, TEMP_PICKED_PHOTO_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
            }
        }
        return file.getAbsoluteFile();
    }

    private File getTempCroppedFile() {
        /*if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File file = new File(Environment.getExternalStorageDirectory(),TEMP_PHOTO_FILE);
            try {
                file.createNewFile();
            } catch (IOException e) {}
            return file;
        } else {
            return null;
        }*/
        activity.getCacheDir().mkdirs();
        File directory = new File(activity.getCacheDir(), TEMP_PHOTO_DIRECTORY);
        directory.mkdirs();
        File file = new File(directory, TEMP_CROPPED_PHOTO_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
            }
        }
        return file.getAbsoluteFile();
    }
}
