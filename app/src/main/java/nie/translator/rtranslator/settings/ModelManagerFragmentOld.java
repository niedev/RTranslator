package nie.translator.rtranslator.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import nie.translator.rtranslator.Global;
import nie.translator.rtranslator.R;
import nie.translator.rtranslator.voice_translation.neural_networks.translation.Translator;

public class ModelManagerFragmentOld extends Fragment {
    private SettingsActivity activity;
    private Global global;
    private RadioGroup radioGroup;
    private boolean restartTranslator = false;
    private Button applyButton;
    private ProgressBar loading;
    private boolean applyingModel = false;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_model_manager_old, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        radioGroup = view.findViewById(R.id.model_radios);
        applyButton = view.findViewById(R.id.apply_button);
        loading = view.findViewById(R.id.loading_models);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        activity = (SettingsActivity) requireActivity();
        global = (Global) activity.getApplication();

        //initialize check with the current mode
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        int mode = sharedPreferences.getInt("selectedTranslationModel", Translator.MOZILLA);
        switch (mode) {
            case Translator.MOZILLA:
                radioGroup.check(R.id.mozilla_radio);
                break;
            case Translator.NLLB:
            case Translator.NLLB_CACHE:
                radioGroup.check(R.id.nllb_radio);
                break;
            case Translator.MADLAD:
            case Translator.MADLAD_CACHE:
                radioGroup.check(R.id.madlad_radio);
                break;
            case Translator.HY_MT:
                radioGroup.check(R.id.hy_radio);
                break;
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId){
                case R.id.mozilla_radio:
                    changeModel(Translator.MOZILLA);
                    break;
                case R.id.nllb_radio:
                    changeModel(Translator.NLLB_CACHE);
                    break;
                case R.id.madlad_radio:
                    changeModel(Translator.MADLAD_CACHE);
                    break;
                case R.id.hy_radio:
                    changeModel(Translator.HY_MT);
                    break;
            }
        });
        applyButton.setOnClickListener((v) -> {
            radioGroup.setActivated(false);
            applyButton.setActivated(false);
            loading.setVisibility(View.VISIBLE);
            applyingModel = true;
            /*global.restartTranslator(new Translator.GeneralListener() {
                @Override
                public void onSuccess() {
                    radioGroup.setActivated(true);
                    applyButton.setActivated(true);
                    applyButton.setVisibility(View.INVISIBLE);
                    loading.setVisibility(View.INVISIBLE);
                    applyingModel = false;
                }

                @Override
                public void onFailure(int[] reasons, long value) {

                }
            });*/
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    private void changeModel(int newModel){
        SharedPreferences sharedPreferences = global.getSharedPreferences("default", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putInt("selectedTranslationModel", newModel);
        edit.apply();
        restartTranslator = global.getTranslator().getMode() != newModel;
        if(restartTranslator){
            applyButton.setVisibility(View.VISIBLE);
        }else{
            applyButton.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}
