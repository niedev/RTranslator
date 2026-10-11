package nie.translator.rtranslator.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceViewHolder;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.tools.Tools;
import nie.translator.rtranslator.tools.gui.GuiTools;
import nie.translator.rtranslator.tools.gui.ThemeManager;

public class ThemePreference extends ListPreference {
	public ThemePreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public CharSequence getSummary() {
		int index = findIndexOfValue(getValue());
		if(index < 0){
			index = 0;
		}
		return getContext().getString(R.string.preference_summary_dark_theme, getEntries()[index],
				getContext().getResources().getStringArray(R.array.dark_theme_descriptions)[index]);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		Context context = new ContextThemeWrapper(getContext(), ThemeManager.getDarkTheme(getContext()));
		setSwatch(holder.findViewById(R.id.themeBackgroundSwatch), context, R.color.primary_very_lite);
		setSwatch(holder.findViewById(R.id.themeSurfaceSwatch), context, R.color.accent_white);
		setSwatch(holder.findViewById(R.id.themeAccentSwatch), context, R.color.primary);
	}

	private void setSwatch(View view, Context context, int color) {
		GradientDrawable drawable = new GradientDrawable();
		drawable.setShape(GradientDrawable.OVAL);
		drawable.setColor(GuiTools.getColor(context, color));
		drawable.setStroke(Tools.convertDpToPixels(context, 1), GuiTools.getColor(context, R.color.very_light_gray));
		view.setBackground(drawable);
	}
}
