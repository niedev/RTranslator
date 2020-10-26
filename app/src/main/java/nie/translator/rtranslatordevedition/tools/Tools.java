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

package nie.translator.rtranslatordevedition.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.gui.peers.GuiPeer;
import com.bluetooth.communicator.Peer;

public class Tools {
    public static final int CONNECTION_SERVICE = 0;
    public static final int CONVERSATION_SERVICE = 1;
    public static final int WALKIE_TALKIE_SERVICE = 2;

    public static synchronized String convertBitmapToString(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    public static synchronized Bitmap convertStringToBitmap(String imageString) {
        try {
            byte[] decodedString = Base64.decode(imageString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static synchronized Bitmap convertBytesToBitmap(byte[] imageBytes) {
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public static Bitmap convertDrawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static synchronized byte[] convertBitmapToBytes(Bitmap image, int quality) {
        byte[] ret;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        ret = baos.toByteArray();
        image.recycle();
        return ret;
    }

    public static synchronized byte[] convertBitmapToBytes(Bitmap image) {
        return convertBitmapToBytes(image, 100);
    }

    public static synchronized void saveBitmapToFile(File file, Bitmap image) {
        OutputStream outStream = null;
        try {
            outStream = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized Bitmap getBitmapFromFile(File file) {
        if (file.exists()) {
            return BitmapFactory.decodeFile(file.getPath());
        } else {
            return null;
        }
    }

    public static Bitmap getResizedBitmap(Context context, Bitmap image, int maxSizeinPixels, int quality) {
        int height;
        int width;
        float bitmapRatio = ((float) image.getWidth()) / ((float) image.getHeight());
        if (bitmapRatio > 1.0f) {
            width = maxSizeinPixels;
            height = (int) (((float) width) / bitmapRatio);
        } else {
            height = maxSizeinPixels;
            width = (int) (((float) height) * bitmapRatio);
        }
        Bitmap imageScaled = Bitmap.createScaledBitmap(image, width, height, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        imageScaled.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public static Bitmap getResizedBitmap(Context context, Bitmap image, int maxSizeinPixels) {
        return getResizedBitmap(context, image, maxSizeinPixels, 100);
    }

    public static int convertDpToPixels(Context context, float dp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics);
    }

    public static float convertPixelsToDp(Context context, int px) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (px / (metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static int convertSpToPixels(Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    public static boolean copyFile(File src, File dst) {
        boolean ret = true;
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            } catch (IOException e) {
                ret = false;
            }
        } catch (IOException e) {
            ret = false;
        }
        return ret;
    }

    public static boolean deleteFile(File file) {
        return file.delete();
    }

    public static ArrayList<String> getRunningServices(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ArrayList<String> runningServicesNames = new ArrayList<>();

        if (am != null) {
            List<ActivityManager.RunningServiceInfo> l;
            l = am.getRunningServices(50);  //il problema è qui, non è afffidabile perchè fa vedere un numero finito di services
            for (ActivityManager.RunningServiceInfo runningServiceInfo : l) {
                runningServicesNames.add(runningServiceInfo.service.getClassName());
                /*if (runningServiceInfo.service.getClassName().equals(WalkieTalkieService.class.getName())) {
                    service= WALKIE_TALKIE_SERVICE;
                }else if(runningServiceInfo.service.getClassName().equals(ConversationService.class.getName())){
                    service= CONVERSATION_SERVICE;
                }*/
            }
        }
        // only one service returns because we cannot have both multi and single device services active at the same time
        return runningServicesNames;
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, Drawable drawable) {
        Bitmap bitmap;
        Bitmap bitmap1;

        bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bitmap1 = bitmap.copy(Bitmap.Config.HARDWARE, false);
        } else {
            bitmap1 = bitmap.copy(Bitmap.Config.RGB_565, false);
        }

        return bitmap1;
    }

    public static CipherData encript(String plainText, SecretKey encryptionKey) {  //encription key of 128 bit, iv of 128 bit
        byte[] text = Base64.decode(plainText, Base64.NO_WRAP);

        return encript(text, encryptionKey);
    }

    public static CipherData encript(byte[] plaintext, SecretKey encryptionKey) {
        byte[] encryptedData = null;
        byte[] iv = null;

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
            iv = cipher.getIV();
            encryptedData = cipher.doFinal(plaintext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
            e.printStackTrace();
        }
        if (encryptedData != null && iv != null) {
            return new CipherData(encryptedData, iv);
        } else {
            return null;
        }
    }

    public static String decriptToString(CipherData cipherText, SecretKey encryptionKey) {
        byte[] text = decript(cipherText, encryptionKey);
        if (text != null) {
            return Base64.encodeToString(text, Base64.NO_WRAP);
        }
        return null;
    }

    public static byte[] decript(CipherData cipherData, SecretKey encryptionKey) {
        byte[] data = null;

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new IvParameterSpec(cipherData.getIv()));
            data = cipher.doFinal(cipherData.getEncriptedData());

        } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        return data;
    }

    public static byte[] merge(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] c = new byte[length];

        int count = 0;
        for (byte[] array : arrays) {
            for (byte anArray : array) {
                c[count] = anArray;
            }
        }
        return c;
    }

    public static byte[] objToByte(Parcelable object) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objStream = new ObjectOutputStream(byteStream);
            objStream.writeObject(object);

            return byteStream.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    public static Object byteToObj(byte[] bytes) {
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
            ObjectInputStream objStream = new ObjectInputStream(byteStream);

            return objStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns true if the app was granted all the permissions. Otherwise, returns false.
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static SecretKey generateAuthenticationKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
            keyGenerator.init(128);
            return keyGenerator.generateKey();

        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static SecretKey generateSymmetricKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128);
            return keyGenerator.generateKey();

        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static ArrayList<GuiPeer> cloneList(ArrayList<GuiPeer> list) {
        ArrayList<GuiPeer> clone = new ArrayList<GuiPeer>(list.size());
        for (Peer item : list) clone.add((GuiPeer) item.clone());
        return clone;
    }

    public abstract static class ResponseListener {
        public abstract void onSuccess();

        public void onFailure(int[] reasons, long value) {
        }
    }

    public static class CipherData implements Serializable {
        private byte[] encriptedData;
        private byte[] iv;


        public CipherData(byte[] encriptedData, byte[] iv) {
            this.encriptedData = encriptedData;
            this.iv = iv;
        }


        public byte[] getEncriptedData() {
            return encriptedData;
        }

        public byte[] getIv() {
            return iv;
        }
    }
}
