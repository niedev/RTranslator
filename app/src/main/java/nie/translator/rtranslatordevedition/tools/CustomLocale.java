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

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

public class CustomLocale implements Comparable<CustomLocale>, Serializable {
    @NonNull
    private Locale locale;

    public CustomLocale(String language, String country, String variant) {
        locale = new Locale(language, country, variant);
    }

    public CustomLocale(String languageCode, String countryCode) {
        locale = new Locale(languageCode, countryCode);
    }

    public CustomLocale(@NonNull Locale locale) {
        this.locale = locale;
    }

    public static CustomLocale getInstance(String code) {
        CustomLocale locale = null;
        String[] languageCode = code.split("-");
        if (languageCode.length == 1) {
            locale = new CustomLocale(new Locale(languageCode[0]));
        } else if (languageCode.length == 2) {
            locale = new CustomLocale(new Locale(languageCode[0], languageCode[1]));
        } else if (languageCode.length >= 3) {
            locale = new CustomLocale(new Locale(languageCode[0], languageCode[1], languageCode[2]));
        }
        return locale;
    }


    public String getLanguage() {
        return locale.getLanguage();
    }

    public String getScript() {
        return locale.getScript();
    }

    public String getCountry() {
        return locale.getCountry();
    }

    public String getVariant() {
        return locale.getVariant();
    }

    public String getExtension(char key) {
        return locale.getExtension(key);
    }

    public Set<Character> getExtensionKeys() {
        return locale.getExtensionKeys();
    }

    public Set<String> getUnicodeLocaleAttributes() {
        return locale.getUnicodeLocaleAttributes();
    }

    public String getUnicodeLocaleType(String key) {
        return locale.getUnicodeLocaleType(key);
    }

    public Set<String> getUnicodeLocaleKeys() {
        return locale.getUnicodeLocaleKeys();
    }

    public String toString() {
        return locale.toString();
    }

    public String toLanguageTag() {
        return locale.toLanguageTag();
    }

    public String getISO3Language() throws MissingResourceException {
        return locale.getISO3Language();
    }

    public String getISO3Country() throws MissingResourceException {
        return locale.getISO3Country();
    }

    public String getCode() {
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    public String getDisplayLanguage() {
        return locale.getDisplayLanguage();
    }

    public String getDisplayLanguage(Locale locale) {
        return locale.getDisplayLanguage(locale);
    }

    public String getDisplayScript() {
        return locale.getDisplayScript();
    }

    public String getDisplayScript(Locale inLocale) {
        return locale.getDisplayScript(inLocale);
    }

    public String getDisplayCountry() {
        return locale.getDisplayCountry();
    }

    public String getDisplayCountry(Locale locale) {
        return locale.getDisplayCountry(locale);
    }

    public String getDisplayVariant() {
        return locale.getDisplayVariant();
    }

    public String getDisplayVariant(Locale inLocale) {
        return locale.getDisplayVariant(inLocale);
    }

    public String getDisplayName() {
        return locale.getDisplayName();
    }

    public String getDisplayName(Locale locale) {
        return locale.getDisplayName(locale);
        /*String displayLanguage;
        if (locale.getDisplayCountry().isEmpty()) {
            displayLanguage = locale.getDisplayLanguage();
        } else {
            displayLanguage = locale.getDisplayLanguage() + " (" + locale.getDisplayCountry() + ")";
        }
        return displayLanguage;*/
    }

    @Override
    public Object clone() {
        return locale.clone();
    }

    @Override
    public int compareTo(CustomLocale o) {
        return getDisplayName().compareTo(((CustomLocale) o).getDisplayName());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CustomLocale) {
            CustomLocale locale = (CustomLocale) obj;
            if (equalsLanguage(locale) && getCountry() != null && locale.getCountry() != null) {  // if the language matches and the country is present in both
                return getCountry().equals(locale.getCountry());
            }
        }
        return false;
    }

    public boolean equalsLanguage(CustomLocale locale) {
        if (getLanguage() != null && locale != null && locale.getLanguage() != null) {
            return getLanguage().equals(locale.getLanguage());
        } else {
            return false;
        }
    }

    public static boolean containsLanguage(ArrayList<CustomLocale> array, CustomLocale locale) {
        boolean found = false;
        if (array != null && locale != null) {
            for (int i = 0; i < array.size() && !found; i++) {
                found = locale.equalsLanguage(array.get(i));
            }
        }
        return found;
    }

    public static int search(@Nullable ArrayList<CustomLocale> array, @Nullable CustomLocale locale) {
        int index = -1;
        if (array != null && locale != null) {
            // look for a locale that matches perfectly
            for (int i = 0; i < array.size() && index == -1; i++) {
                if (locale.equals(array.get(i))) {
                    index = i;
                }
            }
            // if we don't find anything, we look for a locale for which at least the language matches
            for (int i = 0; i < array.size() && index == -1; i++) {
                if (locale.equalsLanguage(array.get(i))) {
                    index = i;
                }
            }
        }
        return index;
    }

    public Locale getLocale() {
        return locale;
    }

    public static CustomLocale getDefault() {
        return new CustomLocale(Locale.getDefault());
    }
}
