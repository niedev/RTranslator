package nie.translator.rtranslator.tools.gui;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.material.button.MaterialButton;

/**
 * This is a custom MaterialButton variant that uses the system marquee engine to auto scroll
 * the text when it doesn't fit its container.
 * The marquee method auto scroll could be used with XML attributes in the normal MaterialButton,
 * but (for some stupid reason) they only work if the view is selected programmatically with setSelected.
 * But this will not work well with TalkBack (it will announce the view as selected).
 * With this custom view, TalkBack will see the view as not selected (overriding onInitializeAccessibilityNodeInfo).
 */
public class AutoScrollMaterialButton extends MaterialButton {
    public AutoScrollMaterialButton(Context context) {
        super(context);
        init();
    }

    public AutoScrollMaterialButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoScrollMaterialButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Force the necessary native marquee attributes programmatically
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.MARQUEE);
        setMarqueeRepeatLimit(-1); // -1 means marquee_forever

        // Triggers the native marquee engine
        setSelected(true);
    }

    // ACCESSIBILITY FIX: Prevents TalkBack from announcing "Selected"
    // before reading the text, ensuring visually impaired users aren't confused.
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setSelected(false);
    }
}
