package nie.translator.rtranslator.tools;

import java.text.Normalizer;
import java.util.Locale;

public class TextTools {
    //used to normalize text before comparison
    public static String normalizeText(String input) {
        if (input == null) return null;

        // Unicode NFC normalization
        String text = Normalizer.normalize(input, Normalizer.Form.NFC);

        // Standardize punctuation
        text = text
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("‘", "'")
                .replace("’", "'")
                .replace("–", "-")
                .replace("—", "-");

        // Trim
        text = text.trim();

        // Collapse whitespace
        text = text.replaceAll("\\s+", " ");

        // Case normalization
        text = text.toLowerCase(Locale.ROOT);

        return text;
    }

    public static String capitalizeFirstLetter(String s, CustomLocale locale) {
        if (s == null || s.isEmpty()) return s;

        int first = s.codePointAt(0);
        int firstLen = Character.charCount(first);

        String firstChar = new String(Character.toChars(first));
        String upper = firstChar.toUpperCase(locale.getLocale());

        return upper + s.substring(firstLen);
    }
}
