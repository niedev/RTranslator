package nie.translator.rtranslator.tools;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import nie.translator.rtranslator.R;
import nie.translator.rtranslator.bluetooth.Message;
import nie.translator.rtranslator.bluetooth.Peer;
import nie.translator.rtranslator.tools.gui.messages.GuiMessage;
import nie.translator.rtranslator.tools.gui.messages.MessagesAdapter;
import nie.translator.rtranslator.tools.gui.ThemeManager;

//This activity is used only to create the images of the conversation mode in the readme

public class ImageActivity extends Activity {
    private int theme;
    private String themeMode;
    protected MessagesAdapter mAdapter;
    protected RecyclerView mRecyclerView;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        theme = ThemeManager.getTheme(this);
        themeMode = ThemeManager.getThemeMode(this);
        setTheme(theme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_conversation);

        ThemeManager.updateSystemBars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(theme != ThemeManager.getTheme(this) || !themeMode.equals(ThemeManager.getThemeMode(this))) {
            recreate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mRecyclerView = findViewById(R.id.recycler_view);

        ArrayList<GuiMessage> messages = new ArrayList<>();
        /*messages.add(new GuiMessage(new Message(this, "m", "Hello, how are you?"), true, true));
        messages.add(new GuiMessage(new Message(this, new Peer(null, "Carlos11", true), "m", "I'm fine"), false, true));
        messages.add(new GuiMessage(new Message(this, new Peer(null, "Denise12", true), "m", "Me too"), false, true));*/

        messages.add(new GuiMessage(new Message(this, new Peer(null, "Alice10", true), "m", "¿Hola, cómo estás?"), false, true));
        messages.add(new GuiMessage(new Message(this, "m", "Estoy bien"), true, true));
        messages.add(new GuiMessage(new Message(this, new Peer(null, "Denise12", true), "m", "Yo también"), false, true));

        /*messages.add(new GuiMessage(new Message(this, new Peer(null, "Alice10", true), "m", "Bonjour comment allez-vous?"), false, true));
        messages.add(new GuiMessage(new Message(this, new Peer(null, "Carlos11", true), "m", "Je vais bien"), false, true));
        messages.add(new GuiMessage(new Message(this, "m", "Moi aussi"), true, true));*/

        mAdapter = new MessagesAdapter(messages, this.getApplication(),  -1, new MessagesAdapter.Callback() {
            @Override
            public void onFirstItemAdded() {
                mRecyclerView.setVisibility(View.VISIBLE);
            }
            @Override
            public void onTTSButtonClick(GuiMessage message, boolean play) {
                // Do nothing for ImageActivity
            }
        });
        mRecyclerView.setAdapter(mAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(layoutManager);
    }
}
