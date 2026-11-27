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

package nie.translator.rtranslator.voice_translation.neural_networks.translation;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

public class Tokenizer {
    public static final int NLLB = 0;
    public static final int NLLB_FIXED = 1;
    public static final int SEAMLESS = 2;
    public static final int MADLAD = 3;
    public static final int MADLAD_FIXED = 4;
    public static final int HY_MT = 5;
    private SentencePieceProcessorJava spProcessor;
    private HuggingFaceTokenizer hfTokenizer;
    private final int mode;
    //languagesNLLB and languagesSeamless contain the list of all supported languages, sorted by their ID order (their IDs are consecutive)
    private final String[] languagesNLLB = {"ace_Arab", "ace_Latn", "acm_Arab", "acq_Arab", "aeb_Arab", "afr_Latn", "ajp_Arab", "aka_Latn", "amh_Ethi", "apc_Arab", "arb_Arab", "ars_Arab", "ary_Arab", "arz_Arab", "asm_Beng", "ast_Latn", "awa_Deva", "ayr_Latn", "azb_Arab", "azj_Latn", "bak_Cyrl", "bam_Latn", "ban_Latn", "bel_Cyrl", "bem_Latn", "ben_Beng", "bho_Deva", "bjn_Arab", "bjn_Latn", "bod_Tibt", "bos_Latn", "bug_Latn", "bul_Cyrl", "cat_Latn", "ceb_Latn", "ces_Latn", "cjk_Latn", "ckb_Arab", "crh_Latn", "cym_Latn", "dan_Latn", "deu_Latn", "dik_Latn", "dyu_Latn", "dzo_Tibt", "ell_Grek", "eng_Latn", "epo_Latn", "est_Latn", "eus_Latn", "ewe_Latn", "fao_Latn", "pes_Arab", "fij_Latn", "fin_Latn", "fon_Latn", "fra_Latn", "fur_Latn", "fuv_Latn", "gla_Latn", "gle_Latn", "glg_Latn", "grn_Latn", "guj_Gujr", "hat_Latn", "hau_Latn", "heb_Hebr", "hin_Deva", "hne_Deva", "hrv_Latn", "hun_Latn", "hye_Armn", "ibo_Latn", "ilo_Latn", "ind_Latn", "isl_Latn", "ita_Latn", "jav_Latn", "jpn_Jpan", "kab_Latn", "kac_Latn", "kam_Latn", "kan_Knda", "kas_Arab", "kas_Deva", "kat_Geor", "knc_Arab", "knc_Latn", "kaz_Cyrl", "kbp_Latn", "kea_Latn", "khm_Khmr", "kik_Latn", "kin_Latn", "kir_Cyrl", "kmb_Latn", "kon_Latn", "kor_Hang", "kmr_Latn", "lao_Laoo", "lvs_Latn", "lij_Latn", "lim_Latn", "lin_Latn", "lit_Latn", "lmo_Latn", "ltg_Latn", "ltz_Latn", "lua_Latn", "lug_Latn", "luo_Latn", "lus_Latn", "mag_Deva", "mai_Deva", "mal_Mlym", "mar_Deva", "min_Latn", "mkd_Cyrl", "plt_Latn", "mlt_Latn", "mni_Beng", "khk_Cyrl", "mos_Latn", "mri_Latn", "zsm_Latn", "mya_Mymr", "nld_Latn", "nno_Latn", "nob_Latn", "npi_Deva", "nso_Latn", "nus_Latn", "nya_Latn", "oci_Latn", "gaz_Latn", "ory_Orya", "pag_Latn", "pan_Guru", "pap_Latn", "pol_Latn", "por_Latn", "prs_Arab", "pbt_Arab", "quy_Latn", "ron_Latn", "run_Latn", "rus_Cyrl", "sag_Latn", "san_Deva", "sat_Beng", "scn_Latn", "shn_Mymr", "sin_Sinh", "slk_Latn", "slv_Latn", "smo_Latn", "sna_Latn", "snd_Arab", "som_Latn", "sot_Latn", "spa_Latn", "als_Latn", "srd_Latn", "srp_Cyrl", "ssw_Latn", "sun_Latn", "swe_Latn", "swh_Latn", "szl_Latn", "tam_Taml", "tat_Cyrl", "tel_Telu", "tgk_Cyrl", "tgl_Latn", "tha_Thai", "tir_Ethi", "taq_Latn", "taq_Tfng", "tpi_Latn", "tsn_Latn", "tso_Latn", "tuk_Latn", "tum_Latn", "tur_Latn", "twi_Latn", "tzm_Tfng", "uig_Arab", "ukr_Cyrl", "umb_Latn", "urd_Arab", "uzn_Latn", "vec_Latn", "vie_Latn", "war_Latn", "wol_Latn", "xho_Latn", "ydd_Hebr", "yor_Latn", "yue_Hant", "zho_Hans", "zho_Hant", "zul_Latn"};
    private final String[] languagesSeamless = {"ace", "ace_Latn", "acm", "acq", "aeb", "afr", "ajp", "aka", "amh", "apc", "arb", "ars", "ary", "arz", "asm", "ast", "awa", "ayr", "azb", "azj", "bak", "bam", "ban", "bel", "bem", "ben", "bho", "bjn", "bjn_Latn", "bod", "bos", "bug", "bul", "cat", "ceb", "ces", "cjk", "ckb", "crh", "cym", "dan", "deu", "dik", "dyu", "dzo", "ell", "eng", "epo", "est", "eus", "ewe", "fao", "pes", "fij", "fin", "fon", "fra", "fur", "fuv", "gla", "gle", "glg", "grn", "guj", "hat", "hau", "heb", "hin", "hne", "hrv", "hun", "hye", "ibo", "ilo", "ind", "isl", "ita", "jav", "jpn", "kab", "kac", "kam", "kan", "kas", "kas_Deva", "kat", "knc", "knc_Latn", "kaz", "kbp", "kea", "khm", "kik", "kin", "kir", "kmb", "kon", "kor", "kmr", "lao", "lvs", "lij", "lim", "lin", "lit", "lmo", "ltg", "ltz", "lua", "lug", "luo", "lus", "mag", "mai", "mal", "mar", "min", "mkd", "plt", "mlt", "mni", "khk", "mos", "mri", "zsm", "mya", "nld", "nno", "nob", "npi", "nso", "nus", "nya", "oci", "gaz", "ory", "pag", "pan", "pap", "pol", "por", "prs", "pbt", "quy", "ron", "run", "rus", "sag", "san", "sat", "scn", "shn", "sin", "slk", "slv", "smo", "sna", "snd", "som", "sot", "spa", "als", "srd", "srp", "ssw", "sun", "swe", "swh", "szl", "tam", "tat", "tel", "tgk", "tgl", "tha", "tir", "taq", "taq_Tfng", "tpi", "tsn", "tso", "tuk", "tum", "tur", "twi", "tzm", "uig", "ukr", "umb", "urd", "uzn", "vec", "vie", "war", "wol", "xho", "ydd", "yor", "yue", "cmn", "cmn_Hant", "zul"};
    private final int DICTIONARY_LENGTH = 256000;

    public Tokenizer(String vocab_path, int mode) throws IOException {
        if(mode != HY_MT) {
            spProcessor = new SentencePieceProcessorJava();
            spProcessor.Load(vocab_path);
        }else{
            hfTokenizer = HuggingFaceTokenizer.newInstance(Paths.get(vocab_path));
        }
        this.mode = mode;
    }

    public TokenizerResult tokenize(String text, String srcLanguage, String tgtLanguage){
        return tokenize(text, srcLanguage, tgtLanguage, null, null, false);
    }

    public TokenizerResult tokenize(String text, String srcLanguage, String tgtLanguage, @Nullable Translator.HyLanguageInfo srcLangInfo, @Nullable Translator.HyLanguageInfo tgtLangInfo, boolean excludePrompt) {
        if(mode != HY_MT) {
            //for madlad we add <2tgtLanguage> at the beginning of the text (srcLanguage is not specified)
            if (mode == MADLAD || mode == MADLAD_FIXED) {
                text = "<2" + tgtLanguage + "> " + text;
            }
            //we translate text into ids via sentencepiece
            int[] ids = spProcessor.encode(text);

        /* The NLLBTokenizer's dictionary has a different mapping for tokens identified by the first 4 IDs values (from 0 to 3),
           also for the other IDs it has a value equal to those of the dictionary we passed to sentencepiece but with an addition of 1
           (idNLLB = idSentencePiece + 1), so now we make the necessary adjustments */
            /* For the SeamlessTokenizer's dictionary the same thing is true but the + 1 is also valid for the first 4 values of the IDs (the value 0, in addition, represents the padding) */
            if (mode != MADLAD && mode != MADLAD_FIXED) {   //MADLAD has a one-to-one match to the sentencepiece dictionary.
                for (int i = 0; i < ids.length; i++) {
                    //we add 1 to each element of ids
                    ids[i] = ids[i] + 1;
                    //we replace the values from 0 to 3 with the correct ones for NLBTokenizer (for Seamless the values are already all correct)
                    if (mode == NLLB || mode == NLLB_FIXED) {
                        switch (ids[i]) {
                            case 1: {
                                ids[i] = 3;
                                break;
                            }
                            case 2: {
                                ids[i] = 0;
                                break;
                            }
                            case 3: {
                                ids[i] = 2;
                                break;
                            }
                        }
                    }
                }
            }

            //add <eos> at the end and srcLanguage at the beginning (srcLanguage is not added for Madlad)
            int eos = PieceToID("</s>");
            int srcLanguageID = getLanguageID(srcLanguage);
            int[] idsExtended;
            if (mode != MADLAD && mode != MADLAD_FIXED) {
                idsExtended = new int[ids.length + 2];
                System.arraycopy(ids, 0, idsExtended, 1, ids.length);
                idsExtended[idsExtended.length - 1] = eos;
                idsExtended[0] = srcLanguageID;
            } else {
                idsExtended = new int[ids.length + 1];
                System.arraycopy(ids, 0, idsExtended, 0, ids.length);
                idsExtended[idsExtended.length - 1] = eos;
            }


            //we create the attention mask
            int[] attentionMask = new int[idsExtended.length];
            Arrays.fill(attentionMask, 1);

            if (mode == NLLB || mode == MADLAD) {
                return new TokenizerResult(idsExtended, attentionMask);
            } else if (mode == SEAMLESS) {
                //for seamless the ids must always be 512, we fill the empty ids with padding (0)
                int[] idsPadded = new int[512];
                Arrays.fill(idsPadded, 0);
                System.arraycopy(idsExtended, 0, idsPadded, 0, idsExtended.length);
                //for seamless also the attention mask must always have length 512, we fill the rest with 0
                int[] attentionMaskPadded = new int[512];
                Arrays.fill(attentionMaskPadded, 0);
                System.arraycopy(attentionMask, 0, attentionMaskPadded, 0, attentionMask.length);
                return new TokenizerResult(idsPadded, attentionMaskPadded);
            } else if (mode == NLLB_FIXED) {
                //for NLLB Fixed the ids must always be 256, we fill the empty ids with padding (0)
                int[] idsPadded = new int[256];
                Arrays.fill(idsPadded, 0);
                System.arraycopy(idsExtended, 0, idsPadded, 0, idsExtended.length);
                //also the attention mask must always have length 256, we fill the rest with 0
                int[] attentionMaskPadded = new int[256];
                Arrays.fill(attentionMaskPadded, 0);
                System.arraycopy(attentionMask, 0, attentionMaskPadded, 0, attentionMask.length);
                return new TokenizerResult(idsPadded, attentionMaskPadded);
            } else {
                //for Madlad Fixed the ids must always be 128, we fill the empty ids with padding (1)
                int[] idsPadded = new int[128];
                Arrays.fill(idsPadded, 1);
                System.arraycopy(idsExtended, 0, idsPadded, 0, idsExtended.length);
                //also the attention mask must always have length 128, we fill the rest with 0
                int[] attentionMaskPadded = new int[128];
                Arrays.fill(attentionMaskPadded, 0);
                System.arraycopy(attentionMask, 0, attentionMaskPadded, 0, attentionMask.length);
                return new TokenizerResult(idsPadded, attentionMaskPadded);
            }
        } else {
            //tokenization for hy-mt
            String tgtLangEnName = tgtLanguage;
            String tgtLangZhName = tgtLanguage;
            if(tgtLangInfo != null){
                tgtLangEnName = tgtLangInfo.enName;
                tgtLangZhName = tgtLangInfo.zhName;
            }
            String prompt;
            if(srcLanguage.equals("zho")) {
                prompt = "<｜hy_begin▁of▁sentence｜><｜hy_User｜>将以下文本翻译为"+tgtLangZhName+"，注意只需要输出翻译后的结果，不要额外解释：\n\n"+text+"<｜hy_Assistant｜>";
            } else {
                prompt = "<｜hy_begin▁of▁sentence｜><｜hy_User｜>Translate the following segment into "+tgtLangEnName+", without additional explanation.\n\n"+text+"<｜hy_Assistant｜>";
            }
            Encoding result = hfTokenizer.encode(new String[]{excludePrompt ? text : prompt}, false, false);

            Log.d("tokenizer_prompt", prompt);

            //we convert inputIds and attention mask from long[] to int[]
            long[] inputIdsLong = result.getIds();
            int[] inputIds = new int[inputIdsLong.length];
            for (int i = 0; i < inputIds.length; i++) {
                inputIds[i] = (int) inputIdsLong[i];
            }
            long[] attentionMaskLong = result.getAttentionMask();
            int[] attentionMask = new int[attentionMaskLong.length];
            for (int i = 0; i < attentionMask.length; i++) {
                attentionMask[i] = (int) attentionMaskLong[i];
            }

            return new TokenizerResult(inputIds, attentionMask);
        }
    }

    public int PieceToID(String token){
        if(mode == NLLB || mode == NLLB_FIXED || mode==MADLAD || mode==MADLAD_FIXED) {
            return spProcessor.PieceToID(token);
        }else if(mode == HY_MT) {
            return (int) hfTokenizer.encode(token).getIds()[0];
        }else{
            return spProcessor.PieceToID(token)+1;
        }
    }

    public int getLanguageID(String language){
        if(mode == NLLB || mode == NLLB_FIXED) {
            for (int i = 0; i < languagesNLLB.length; i++) {
                if (languagesNLLB[i].equals(language)) {
                    return DICTIONARY_LENGTH + i + 1;
                }
            }
        }else{
            for (int i = 0; i < languagesSeamless.length; i++) {
                if (languagesSeamless[i].equals(language)) {
                    return DICTIONARY_LENGTH + i + 1;
                }
            }
        }
        return -1;
    }

    public String decode(int[] ids) {
        String output = "";
        if(mode == NLLB || mode == NLLB_FIXED) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] < DICTIONARY_LENGTH && ids[i] > 3) {   //This check skips special tokens and those that sentencepiece does not have in the dictionary (such as languages)
                    output = output.concat(spProcessor.IDToPiece(ids[i] - 1));
                }
            }
        }else if(mode == HY_MT){
            long[] inputIdsLong = new long[ids.length];
            for (int i = 0; i < ids.length; i++) {
                inputIdsLong[i] = (long) ids[i];
            }
            output = hfTokenizer.decode(inputIdsLong);
            output = output.replace("<｜hy_place▁holder▁no▁2｜>", "");  //we remove the eos token
        }else{  //madlad and seamless
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] < DICTIONARY_LENGTH && ids[i] > 3) {   //This check skips special tokens and those that sentencepiece does not have in the dictionary (such as languages)
                    output = output.concat(spProcessor.IDToPiece(ids[i]));
                }
            }
        }
        //we remove all the new world characters (▁)
        if(!output.isEmpty() && output.charAt(0) == '▁'){
            output = output.substring(1);
        }
        return output.replace('▁', ' ');
    }
}
