package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.communicator.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

public class BluetoothTools {
    public static final int FIX_NUMBER = 0;
    public static final int FIX_TEXT = 1;

    /**
     * return all characters of UTF encoding (this is because bluetooth only support a certain amount of bytes
     * to send to nearby devices)
     * @param context
     * @return
     */
    public static ArrayList<Character> getSupportedUTFCharacters(Context context) {
        ArrayList<Character> characters = new ArrayList<>();
        characters.add(' ');
        characters.add('!');
        characters.add('"');
        characters.add('#');
        characters.add('$');
        characters.add('%');
        characters.add('&');
        characters.add('\'');
        characters.add('(');
        characters.add(')');
        characters.add('*');
        characters.add('+');
        characters.add(',');
        characters.add('-');
        characters.add('.');
        characters.add('/');
        characters.add('0');
        characters.add('1');
        characters.add('2');
        characters.add('3');
        characters.add('4');
        characters.add('5');
        characters.add('6');
        characters.add('7');
        characters.add('8');
        characters.add('9');
        characters.add(':');
        characters.add(';');
        characters.add('<');
        characters.add('=');
        characters.add('>');
        characters.add('?');
        characters.add('@');
        characters.add('A');
        characters.add('B');
        characters.add('C');
        characters.add('D');
        characters.add('E');
        characters.add('F');
        characters.add('G');
        characters.add('H');
        characters.add('I');
        characters.add('J');
        characters.add('K');
        characters.add('L');
        characters.add('M');
        characters.add('N');
        characters.add('O');
        characters.add('P');
        characters.add('Q');
        characters.add('R');
        characters.add('S');
        characters.add('T');
        characters.add('U');
        characters.add('V');
        characters.add('W');
        characters.add('X');
        characters.add('Y');
        characters.add('Z');
        characters.add('[');
        characters.add('\\');
        characters.add(']');
        characters.add('^');
        characters.add('_');
        characters.add('`');
        characters.add('a');
        characters.add('b');
        characters.add('c');
        characters.add('d');
        characters.add('e');
        characters.add('f');
        characters.add('g');
        characters.add('h');
        characters.add('i');
        characters.add('j');
        characters.add('k');
        characters.add('l');
        characters.add('m');
        characters.add('n');
        characters.add('o');
        characters.add('p');
        characters.add('q');
        characters.add('r');
        characters.add('s');
        characters.add('t');
        characters.add('u');
        characters.add('v');
        characters.add('w');
        characters.add('x');
        characters.add('y');
        characters.add('z');
        characters.add('{');
        characters.add('|');
        characters.add('}');
        characters.add('~');
        Collections.sort(characters);  // alphabetical order
        return characters;
    }

    public static String getSupportedNameCharactersString(Context context) {
        String string = "";
        ArrayList<Character> characters = getSupportedUTFCharacters(context);
        for (Character character : characters) {
            string = string.concat(" " + character.toString());
        }
        int lentgh = string.getBytes(StandardCharsets.UTF_8).length;
        Log.e("lenght", lentgh + "");
        return string;
    }

    public static String fixLength(Context context, String string, int length, int typeOfFix) {
        int fillingLength = length - string.length();
        if (fillingLength > 0) {
            // filling
            Character fillChar = getSupportedUTFCharacters(context).get(0);
            StringBuilder outputBuffer = new StringBuilder(fillingLength);
            for (int i = 0; i < fillingLength; i++) {
                outputBuffer.append(fillChar);
            }
            if (typeOfFix == FIX_NUMBER) {
                return outputBuffer.toString().concat(string);
            } else {
                return string.concat(outputBuffer.toString());
            }
        } else {
            // cut
            if (typeOfFix == FIX_NUMBER) {
                return string.substring(fillingLength * -1);
            } else {
                return string.substring(0, length);
            }
        }
    }

    public static String generateRandomUTFString(Context context, int length) {
        return new RandomString(length).nextString(context);
    }

    public static String generateBluetoothNameId(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String id = sharedPreferences.getString("bluetoothNameId", "");
        if (id != null && id.length() == 2) {
            return id;
        } else {
            //generazione dell'id e salvataggio
            SharedPreferences.Editor edit = sharedPreferences.edit();
            id = generateRandomUTFString(context, 2);
            edit.putString("bluetoothNameId", id);
            edit.apply();
            return id;
        }
    }

    /**
     * index cell goes in second array
     */
    public static ArrayDeque<byte[]> splitBytes(byte[] array, int subArraysLength) {

        ArrayDeque<byte[]> resultMatrixList = new ArrayDeque<>();
        for (int j = 0; j < array.length; j += subArraysLength) {
            byte[] subArray;
            if (j + subArraysLength < array.length) {
                subArray = new byte[subArraysLength];
            } else {
                subArray = new byte[array.length - j];
            }
            System.arraycopy(array, j, subArray, 0, subArray.length);
            resultMatrixList.addLast(subArray);
        }

        return resultMatrixList;
    }

    public static byte[] concatBytes(byte[]... arrays) {
        int resultArrayLength = 0;
        for (byte[] array : arrays) {
            resultArrayLength += array.length;
        }
        byte[] resultArray = new byte[resultArrayLength];
        int destIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, resultArray, destIndex, array.length);
            destIndex += array.length;
        }
        return resultArray;
    }

    public static byte[] subBytes(byte[] array, int begin, int end) {
        if (end <= begin || begin < 0 || end > array.length) {
            return null;
        }
        int length = end - begin;
        byte[] subArray = new byte[length];
        System.arraycopy(array, begin, subArray, 0, length);
        return subArray;
    }

    private static class RandomString {
        private final Random random;
        private final char[] buf;

        /**
         * Create an alphanumeric string generator.
         */
        private RandomString(int length, Random random) {
            if (length < 1) throw new IllegalArgumentException();
            this.random = Objects.requireNonNull(random);
            this.buf = new char[length];
        }

        /**
         * Create an alphanumeric strings from a secure generator.
         */
        private RandomString(int length) {
            this(length, new SecureRandom());
        }

        /**
         * Generate a random string.
         */
        private String nextString(Context context) {
            for (int idx = 0; idx < buf.length; ++idx) {
                buf[idx] = (getSupportedUTFCharacters(context).get(random.nextInt(95)));  //si genera un carattere casuale composto da tutti i valori possibili del codice ascii normale (non esteso) per poter essere espressi da un solo byte in utf-8
            }
            return new String(buf);
        }
    }
}
