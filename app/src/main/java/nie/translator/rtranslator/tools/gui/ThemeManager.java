package nie.translator.rtranslator.tools.gui;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.View;

import androidx.preference.PreferenceManager;

import nie.translator.rtranslator.R;

public final class ThemeManager {
	public static final String THEME_MODE = "themeMode";
	public static final String DARK_THEME = "darkTheme";
	public static final String SYSTEM = "system";
	public static final String LIGHT = "light";
	public static final String DARK = "dark";
	public static final String GRAPHITE = "graphite";
	public static final String MIDNIGHT = "midnight";
	public static final String AMOLED = "amoled";

	private ThemeManager() {
	}

	public static String getThemeMode(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getString(THEME_MODE, SYSTEM);
	}

	public static boolean isDark(Context context) {
		String mode = getThemeMode(context);
		// An activity with a forced configuration must still be able to return to the system mode.
		return DARK.equals(mode) || (!LIGHT.equals(mode) &&
				(Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
	}

	public static int getDarkTheme(Context context) {
		String theme = PreferenceManager.getDefaultSharedPreferences(context).getString(DARK_THEME, GRAPHITE);
		if(MIDNIGHT.equals(theme)){
			return R.style.Theme_Speech_Midnight;
		}else if(AMOLED.equals(theme)){
			return R.style.Theme_Speech_Amoled;
		}
		return R.style.Theme_Speech_Graphite;
	}

	public static int getTheme(Context context) {
		return isDark(context) ? getDarkTheme(context) : R.style.Theme_Speech;
	}

	public static Context wrapContext(Context context) {
		String mode = getThemeMode(context);
		if(!LIGHT.equals(mode) && !DARK.equals(mode)){
			return context;
		}
		// Override only appearance so orientation, locale, and font changes can still propagate.
		Configuration configuration = new Configuration();
		configuration.uiMode = (context.getResources().getConfiguration().uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
				(isDark(context) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
		return context.createConfigurationContext(configuration);
	}

	// Android 12 also uses this preference for the system's launch screen.
	public static void updateNightMode(Context context) {
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
			String mode = getThemeMode(context);
			int nightMode = LIGHT.equals(mode) ? UiModeManager.MODE_NIGHT_NO :
					DARK.equals(mode) ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_AUTO;
			UiModeManager manager = context.getSystemService(UiModeManager.class);
			if(manager != null){
				manager.setApplicationNightMode(nightMode);
			}
		}
	}

	public static void updateSystemBars(Activity activity) {
		int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
		if(!isDark(activity)){
			flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
		}
		activity.getWindow().getDecorView().setSystemUiVisibility(flags);
	}
}
