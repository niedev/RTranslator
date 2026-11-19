package nie.translator.rtranslator.databases.tatoeba;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.CodedInputStream;
import com.niedev.sqlite4java.SQLiteConnection;
import com.niedev.sqlite4java.SQLiteException;
import com.niedev.sqlite4java.SQLiteStatement;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import nie.translator.rtranslator.tools.CustomLocale;

public class TatoebaDbWrapper {
    private static final String[] LANGUAGES = {
            "abk", "acm", "ady", "afb", "afh", "afr", "aii", "ain", "ajp", "akl",
            "aln", "alt", "amh", "ang", "aoz", "apc", "ara", "arg", "arn", "arq",
            "ary", "arz", "asm", "ast", "ava", "avk", "awa", "aym", "aze", "bak",
            "bal", "bam", "ban", "bar", "bcl", "bel", "ben", "ber", "bho", "bis",
            "bjn", "bod", "bom", "bos", "bre", "brx", "bua", "bul", "bvy", "bzt",
            "cat", "cay", "cbk", "ceb", "ces", "cha", "che", "chg", "cho", "chr",
            "chv", "cjy", "ckb", "ckt", "cmo", "cor", "cos", "cpi", "crh", "crk",
            "crs", "csb", "cym", "dan", "deu", "diq", "div", "dng", "drt",
            "dsb", "dtp", "dws", "egl", "ell", "emx", "eng", "enm", "epo", "est",
            "eus", "evn", "ewe", "ext", "fao", "fij", "fin", "fkv", "fra", "frm",
            "fro", "frr", "fry", "fuc", "fur", "fuv", "gaa", "gag", "gan", "gaz",
            "gbm", "gcf", "gil", "gla", "gle", "glg", "glv", "gom", "gos", "got",
            "grc", "grn", "gsw", "guc", "guj", "guw", "hak", "hat", "hau", "haw",
            "hax", "hbo", "heb", "hif", "hil", "hin", "hoc", "hrv", "hrx", "hsb",
            "hsn", "hun", "hye", "hyw", "iba", "ibo", "ido", "ike", "ile", "ilo",
            "ina", "ind", "isl", "isv", "ita", "izh", "jam", "jav", "jbo", "jdt",
            "jpa", "jpn", "kaa", "kab", "kal", "kan", "kas", "kat", "kaz", "kek",
            "kha", "khm", "kin", "kir", "kiu", "kjh", "klj", "kmr", "knc", "koi",
            "kor", "kpv", "krc", "krl", "ksh", "kum", "kzj", "laa", "lad", "lao",
            "lat", "ldn", "lfn", "lij", "lim", "lin", "lit", "liv", "lkt", "lld",
            "lmo", "lou", "ltg", "ltz", "lug", "lut", "lvs", "lzh", "lzz", "mad",
            "mah", "mal", "mar", "max", "mdf", "mdr", "mfa", "mfe", "mgm", "mhr",
            "mic", "mik", "min", "mkd", "mlg", "mlt", "mnc", "mni", "mnr", "moh",
            "mon", "mri", "mrj", "mus", "mvv", "mwl", "mww", "mya", "myv", "nah",
            "nan", "nap", "nav", "nch", "nds", "ngt", "ngu", "niu", "njo", "nld",
            "nnb", "nno", "nob", "nog", "non", "nov", "npi", "nst", "nus", "nya",
            "oar", "oci", "ofs", "oji", "ood", "ori", "orv", "osp", "oss", "osx",
            "ota", "otk", "pag", "pal", "pam", "pan", "pap", "pcd", "pdc", "pes",
            "pfl", "phn", "pli", "pms", "pnb", "pol", "por", "ppl", "prg", "pus",
            "quc", "que", "qxq", "qya", "rap", "rhg", "rif", "roh", "rom", "ron",
            "rue", "run", "rus", "ryu", "sah", "san", "sat", "scn", "sco", "sdh",
            "sgs", "shi", "shs", "shy", "sin", "sjn", "skr", "slk", "slv", "sma",
            "sme", "smo", "sna", "snd", "som", "sot", "spa", "sqi", "srd", "srn",
            "srp", "ssw", "stq", "sun", "sux", "swc", "swe", "swg", "swh", "syc",
            "syl", "szl", "tah", "tam", "tat", "tel", "tet", "tgk", "tgl", "tha",
            "thv", "tig", "tir", "tkl", "tlh", "tly", "tmr", "tok", "ton", "tpi",
            "tpn", "tsn", "tso", "tts", "tuk", "tum", "tur", "tvl", "tyv", "tzl",
            "udm", "uig", "ukr", "umb", "urd", "uzb", "vec", "vep", "vie", "vol",
            "vro", "war", "wln", "wol", "wuu", "xal", "xho", "xmf", "xqa", "yid",
            "yor", "yua", "yue", "zea", "zgh", "zho", "zlm", "zsm", "zul", "zza"
    };
    File dbFile;

    public TatoebaDbWrapper(String dbPath) {
        dbFile = new File(dbPath);
    }

    @Nullable
    public String getSentence(int id) {
        String out = null;
        SQLiteStatement st = null;
        SQLiteConnection database = new SQLiteConnection(dbFile);
        try {
            database.openReadonly();
            st = database.prepare("SELECT text FROM sentences WHERE id = ?");
            st.bind(1, id);

            if (st.step()) {
                out = st.columnString(0); // first column = "text"
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (st != null) st.dispose();
            database.dispose();
        }
        return out;
    }

    @NonNull
    public String[] getSentences(int[] ids) {
        String[] out = new String[ids.length];
        if (ids.length == 0) return out;

        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) ph.append(",");
            ph.append("?");
        }

        String sql = "SELECT id, text FROM sentences WHERE id IN (" + ph + ")";

        SQLiteStatement st = null;
        SQLiteConnection database = new SQLiteConnection(dbFile);
        try {
            database.openReadonly();
            st = database.prepare(sql);

            // bind is 1-based
            for (int i = 0; i < ids.length; i++) {
                st.bind(i + 1, ids[i]);
            }

            // IMPORTANT: IN(...) order is not guaranteed.
            // If you need to preserve input order, map id -> text then reorder.
            java.util.HashMap<Integer, String> byId = new java.util.HashMap<>();
            while (st.step()) {
                int id = st.columnInt(0);
                String text = st.columnString(1);
                byId.put(id, text);
            }

            for (int i = 0; i < ids.length; i++) {
                out[i] = byId.get(ids[i]); // null if missing
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
            out = new String[0];
        } finally {
            if (st != null) st.dispose();
            database.dispose();
        }
        return out;
    }

    @Nullable
    public LinksData.DataMap getLinkData(String srcLang, String tgtLang) {
        //long time = System.currentTimeMillis();
        LinksData.DataMap result = null;
        SQLiteConnection database = new SQLiteConnection(dbFile);
        SQLiteStatement st = null;
        try {
            database.openReadonly();
            //Log.d("perf_tatoeba_db", "Tatoeba connection db in "+(System.currentTimeMillis()-time)+" ms");
            //time = System.currentTimeMillis();
            st = database.prepare(
                    "SELECT data FROM links WHERE srcLang = ? AND tgtLang = ? LIMIT 1"
            );
            st.bind(1, srcLang);
            st.bind(2, tgtLang);

            if (st.step()) {
                //Log.d("perf_tatoeba_db", "Tatoeba search db in "+(System.currentTimeMillis()-time)+" ms");
                //time = System.currentTimeMillis();

                // Key point: stream the BLOB (no CursorWindow)
                try (InputStream in = st.columnStream(0)) {
                    /*CodedInputStream input = CodedInputStream.newInstance(in, 64 * 1024);
                    result = LinksData.DataMap.parseFrom(input);
                    input.checkLastTagWas(0);*/
                    result = LinksData.DataMap.parseFrom(in);
                }
                //Log.d("perf_tatoeba_db", "Tatoeba blob fetch db in "+(System.currentTimeMillis()-time)+" ms");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (st != null) st.dispose();
            database.dispose();
        }
        return result;
    }

    public static ArrayList<CustomLocale> getTatoebaSupportedLanguages(Context context){
        ArrayList<CustomLocale> languages = new ArrayList<>(LANGUAGES.length);
        for(String langCode: LANGUAGES){
            languages.add(new CustomLocale(context, langCode));
        }
        return languages;
    }
}

    /*public void close(){
        database.dispose();
    }*/

    /*try {
            long rowid;
            SQLiteStatement st = database.prepare("SELECT rowid FROM links WHERE srcLang = ? AND tgtLang = ? LIMIT 1");
            try {
                st.bind(1, srcLang);
                st.bind(2, tgtLang);
                if (!st.step()) return null;
                rowid = st.columnLong(0);
            } finally {
                st.dispose();
            }

            // Incremental BLOB I/O: no CursorWindow involved
            SQLiteBlob blob = database.blob("main", "links", "data", rowid, false);
            try (InputStream in = blob.getInputStream()) {
                return LinksData.DataMap.parseFrom(in);
            } finally {
                blob.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }*/
