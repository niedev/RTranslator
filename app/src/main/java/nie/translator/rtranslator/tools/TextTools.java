package nie.translator.rtranslator.tools;

import android.icu.text.BreakIterator;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class TextTools {
    private static final Pattern TRANSCRIPTION_TAGS_PATTERN = Pattern.compile("\\[.*?\\]|\\(.*?\\)");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("\\p{P}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\p{N}");
    private static final Pattern MULTI_WHITESPACE_PATTERN = Pattern.compile("\\s+");

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

    public static String normalizeTextForLid(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Unicode NFC normalization for diacritic consistency
        String text = Normalizer.normalize(input, Normalizer.Form.NFC);

        // Lowercase for classifier consistency
        text = text.toLowerCase(Locale.ROOT);

        // Wipe ambient audio tags (e.g., "[music]") left behind by Whisper
        text = TRANSCRIPTION_TAGS_PATTERN.matcher(text).replaceAll(" ");

        // Convert all punctuation and numbers to spaces
        text = PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");
        text = NUMBER_PATTERN.matcher(text).replaceAll(" ");

        // Collapse whitespace down cleanly
        text = MULTI_WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        return text.trim();
    }

    public static String capitalizeFirstLetter(String s, CustomLocale locale) {
        if (s == null || s.isEmpty()) return s;

        int first = s.codePointAt(0);
        int firstLen = Character.charCount(first);

        String firstChar = new String(Character.toChars(first));
        String upper = firstChar.toUpperCase(locale.getLocale());

        return upper + s.substring(firstLen);
    }

    public static int countWords(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);

        int count = 0;
        int start = boundary.first();
        int end = boundary.next();

        while (end != BreakIterator.DONE) {
            // BreakIterator counts spaces and commas as boundaries.
            // We only increment if the token starts with a letter or number.
            if (Character.isLetterOrDigit(text.codePointAt(start))) {
                count++;
            }
            start = end;
            end = boundary.next();
        }

        return count;
    }
}
