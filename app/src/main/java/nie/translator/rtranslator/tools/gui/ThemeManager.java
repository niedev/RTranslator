package nie.translator.rtranslator.tools.gui;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {
    public enum ThemeOption {
        SYSTEM,
        LIGHT,
        DARK
    }

    private ThemeManager() {}


    public static void restore(Context context) {
        int choice = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                .getInt("theme", ThemeOption.SYSTEM.ordinal());

        apply(choice);
    }

    public static ThemeOption getSavedChoice(Context context){
        int choice = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                .getInt("theme", ThemeOption.SYSTEM.ordinal());
        return convertThemeOption(choice);
    }

    public static void saveAndApply(Context context, ThemeOption choice) {
        context.getSharedPreferences("default", Context.MODE_PRIVATE)
                .edit()
                .putInt("theme", choice.ordinal())
                .apply();

        apply(choice);
        updateSystemNightMode(context, choice);
    }


    private static void apply(int choice){
        apply(convertThemeOption(choice));
    }

    private static void apply(ThemeOption choice) {
        int mode;
        switch (choice) {
            case DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static void updateSystemNightMode(Context context, ThemeOption choice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        UiModeManager manager = context.getApplicationContext().getSystemService(UiModeManager.class);

        if (manager == null) {
            return;
        }

        int mode;
        switch (choice) {
            case DARK:
                mode = UiModeManager.MODE_NIGHT_YES;
                break;

            case LIGHT:
                mode = UiModeManager.MODE_NIGHT_NO;
                break;

            case SYSTEM:
            default:
                mode = UiModeManager.MODE_NIGHT_AUTO;
                break;
        }

        manager.setApplicationNightMode(mode);
    }

    private static ThemeOption convertThemeOption(int choice){
        switch (choice) {
            case 1:
                return ThemeOption.LIGHT;
            case 2:
                return ThemeOption.DARK;
            default:
                return ThemeOption.SYSTEM;
        }
    }
}