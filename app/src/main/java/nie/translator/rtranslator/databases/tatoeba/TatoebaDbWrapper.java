package nie.translator.rtranslator.databases.tatoeba;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.niedev.sqlite4java.SQLiteConnection;
import com.niedev.sqlite4java.SQLiteException;
import com.niedev.sqlite4java.SQLiteStatement;

import java.io.File;
import java.io.InputStream;

public class TatoebaDbWrapper {
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
    public LinksData.DataMap getLinkData(String srcLang,
                                         String tgtLang) {
        LinksData.DataMap result = null;
        SQLiteConnection database = new SQLiteConnection(dbFile);
        SQLiteStatement st = null;
        try {
            database.openReadonly();
            st = database.prepare(
                    "SELECT data FROM links WHERE srcLang = ? AND tgtLang = ? LIMIT 1"
            );
            st.bind(1, srcLang);
            st.bind(2, tgtLang);

            if (st.step()) {
                // Key point: stream the BLOB (no CursorWindow)
                try (InputStream in = st.columnStream(0)) {
                    result = LinksData.DataMap.parseFrom(in);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (st != null) st.dispose();
            database.dispose();
        }
        return result;
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
