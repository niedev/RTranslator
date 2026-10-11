package nie.translator.rtranslator.tools.gui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.GeneralActivity;
import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.settings.ThemePreference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36}, application = Application.class)
public class ThemeTest {
	private Context context;
	private SharedPreferences preferences;
	private final String[] palettes = {ThemeManager.GRAPHITE, ThemeManager.MIDNIGHT, ThemeManager.AMOLED};

	@Before
	public void setUp() {
		context = RuntimeEnvironment.getApplication();
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		preferences.edit().clear().commit();
	}

	@Test
	public void systemModeFollowsSystemAndExplicitModesOverrideIt() {
		RuntimeEnvironment.setQualifiers("notnight");
		assertEquals(R.style.Theme_Speech, ThemeManager.getTheme(context));
		RuntimeEnvironment.setQualifiers("night");
		assertEquals(R.style.Theme_Speech_Graphite, ThemeManager.getTheme(context));
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.LIGHT).commit();
		assertEquals(R.style.Theme_Speech, ThemeManager.getTheme(context));
		assertEquals(Configuration.UI_MODE_NIGHT_NO, ThemeManager.wrapContext(context).getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
		RuntimeEnvironment.setQualifiers("notnight");
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.DARK).commit();
		assertEquals(R.style.Theme_Speech_Graphite, ThemeManager.getTheme(context));
		assertEquals(Configuration.UI_MODE_NIGHT_YES, ThemeManager.wrapContext(context).getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
	}

	@Test
	public void paletteIsRememberedWhileLightModeIsSelected() {
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.LIGHT).putString(ThemeManager.DARK_THEME, ThemeManager.AMOLED).commit();
		assertEquals(R.style.Theme_Speech, ThemeManager.getTheme(context));
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.DARK).commit();
		assertEquals(R.style.Theme_Speech_Amoled, ThemeManager.getTheme(context));
		preferences.edit().putString(ThemeManager.DARK_THEME, "unknown").commit();
		assertEquals(R.style.Theme_Speech_Graphite, ThemeManager.getTheme(context));
	}

	@Test
	public void forcedAppearanceStillFollowsOrientationChanges() {
		RuntimeEnvironment.setQualifiers("port-notnight");
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.DARK).commit();
		Context wrappedContext = ThemeManager.wrapContext(context);
		assertEquals(Configuration.ORIENTATION_PORTRAIT, wrappedContext.getResources().getConfiguration().orientation);
		RuntimeEnvironment.setQualifiers("land-notnight");
		assertEquals(Configuration.ORIENTATION_LANDSCAPE, wrappedContext.getResources().getConfiguration().orientation);
		assertEquals(Configuration.UI_MODE_NIGHT_YES, wrappedContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
	}

	@Test
	public void modelMemoryBarResolvesThemedArrayColors() {
		for(String palette : palettes) {
			Context themedContext = selectPalette(palette);
			View view = LayoutInflater.from(themedContext).inflate(R.layout.fragment_models_manager, null);
			SegmentProgressBar bar = view.findViewById(R.id.barRam);
			assertEquals(4, bar.getSegments().size());
			assertEquals(GuiTools.getColor(themedContext, R.color.light_gray), bar.getSegments().get(0).color);
			assertEquals(GuiTools.getColor(themedContext, R.color.blue), bar.getSegments().get(1).color);
		}
	}

	@Test
	public void appearancePreferencesPersistSelectionAndDescribeIt() {
		Context themedContext = selectPalette(ThemeManager.GRAPHITE);
		PreferenceManager manager = new PreferenceManager(themedContext);
		PreferenceScreen screen = manager.inflateFromResource(themedContext, R.xml.preferences, null);
		ThemePreference preference = (ThemePreference) screen.findPreference(ThemeManager.DARK_THEME);
		preference.setValue(ThemeManager.AMOLED);
		assertEquals(ThemeManager.AMOLED, preferences.getString(ThemeManager.DARK_THEME, null));
		assertTrue(preference.getSummary().toString().contains("AMOLED"));
		assertTrue(preference.getSummary().toString().contains("Pure black"));
	}

	@Test
	@Config(application = ThemeApplication.class)
	public void returningActivityRecreatesWithTheNewPaletteAndSystemBarIcons() {
		RuntimeEnvironment.setQualifiers("night");
		selectPalette(ThemeManager.GRAPHITE);
		ActivityController<ThemeActivity> controller = Robolectric.buildActivity(ThemeActivity.class).setup();
		int recreations = ThemeActivity.recreations;
		controller.pause().stop();
		// The effective palette stays the same, but the old forced configuration must be cleared.
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.SYSTEM).commit();
		controller.start().resume();
		Shadows.shadowOf(Looper.getMainLooper()).idle();
		assertTrue(ThemeActivity.recreations > recreations);
		recreations = ThemeActivity.recreations;
		controller.pause().stop();
		preferences.edit().putString(ThemeManager.DARK_THEME, ThemeManager.AMOLED).commit();
		controller.start().resume();
		Shadows.shadowOf(Looper.getMainLooper()).idle();
		assertTrue(ThemeActivity.recreations > recreations);
		assertEquals(Color.BLACK, GuiTools.getColor(controller.get(), R.color.primary_very_lite));
		assertEquals(0, controller.get().getWindow().getDecorView().getSystemUiVisibility() &
				(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
		controller.pause().stop();
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.LIGHT).commit();
		controller.start().resume();
		Shadows.shadowOf(Looper.getMainLooper()).idle();
		assertEquals(Color.WHITE, GuiTools.getColor(controller.get(), R.color.accent_white));
		assertTrue((controller.get().getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0);
		controller.pause().stop().destroy();
	}

	@Test
	public void darkTextAndFilledControlsMeetContrastRequirements() {
		for(String palette : palettes) {
			Context themedContext = selectPalette(palette);
			for(int background : new int[]{R.color.primary_very_lite, R.color.accent_white, R.color.very_very_light_gray, R.color.sent_message}) {
				assertContrast(themedContext, R.color.black, background, 4.5);
				assertContrast(themedContext, R.color.very_dark_gray, background, 4.5);
				assertContrast(themedContext, R.color.light_gray, background, 4.5);
			}
			for(int background : new int[]{R.color.primary, R.color.primary_very_dark, R.color.accent}) {
				assertContrast(themedContext, R.color.on_primary, background, 4.5);
			}
			assertContrast(themedContext, R.color.red, R.color.accent_white, 4.5);
			assertContrast(themedContext, R.color.secondary, R.color.sent_message, 4.5);
		}
	}

	@Test
	public void amoledUsesPureBlackForBackgroundAndMainSurfaces() {
		Context themedContext = selectPalette(ThemeManager.AMOLED);
		assertEquals(Color.BLACK, GuiTools.getColor(themedContext, R.color.primary_very_lite));
		assertEquals(Color.BLACK, GuiTools.getColor(themedContext, R.color.accent_white));
		assertNotEquals(Color.BLACK, GuiTools.getColor(themedContext, R.color.very_very_light_gray));
		Context dialogContext = new MaterialAlertDialogBuilder(themedContext, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog).getContext();
		assertEquals(Color.BLACK, GuiTools.getColor(dialogContext, R.color.primary_very_lite));
	}

	@Test
	public void layoutsInflateWithoutLosingTheSelectedPalette() {
		for(String palette : palettes) {
			Context themedContext = selectPalette(palette);
			for(int layout : new int[]{R.layout.activity_settings, R.layout.fragment_translation, R.layout.fragment_walkie_talkie,
					R.layout.activity_access, R.layout.activity_main, R.layout.activity_loading, R.layout.fragment_conversation,
					R.layout.fragment_conversation_main, R.layout.fragment_pairing, R.layout.fragment_models_manager,
					R.layout.fragment_mozilla_manager, R.layout.fragment_download, R.layout.fragment_notice, R.layout.fragment_user_data,
					R.layout.component_message_send, R.layout.component_message_received, R.layout.dialog_permission,
					R.layout.dialog_error, R.layout.dialog_apply, R.layout.dialog_delete, R.layout.dialog_loading,
					R.layout.dialog_edit_name, R.layout.dialog_languages, R.layout.preference_seekbar, R.layout.preference_user_name}) {
				View view = LayoutInflater.from(themedContext).inflate(layout, null);
				if(layout == R.layout.fragment_translation || layout == R.layout.fragment_walkie_talkie) {
					CardView controls = view.findViewById(R.id.cardView8);
					assertEquals(GuiTools.getColor(themedContext, R.color.accent_white), controls.getCardBackgroundColor().getDefaultColor());
				}
			}
			View message = LayoutInflater.from(themedContext).inflate(R.layout.component_message_send, null);
			TextView text = message.findViewById(R.id.text);
			assertEquals(GuiTools.getColor(themedContext, R.color.black), text.getCurrentTextColor());
		}
	}

	private Context selectPalette(String palette) {
		preferences.edit().putString(ThemeManager.THEME_MODE, ThemeManager.DARK).putString(ThemeManager.DARK_THEME, palette).commit();
		return new ContextThemeWrapper(ThemeManager.wrapContext(context), ThemeManager.getTheme(context));
	}

	private void assertContrast(Context context, int foreground, int background, double minimum) {
		assertTrue(ColorUtils.calculateContrast(GuiTools.getColor(context, foreground), GuiTools.getColor(context, background)) >= minimum);
	}

	public static class ThemeApplication extends Global {
		@Override
		public void onCreate() {
			// Theme changes do not need to initialize translation models or Bluetooth.
			setModelsLoaded(true);
		}
	}

	public static class ThemeActivity extends GeneralActivity {
		static int recreations;

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_settings);
		}

		@Override
		public void recreate() {
			recreations++;
			super.recreate();
		}
	}
}
