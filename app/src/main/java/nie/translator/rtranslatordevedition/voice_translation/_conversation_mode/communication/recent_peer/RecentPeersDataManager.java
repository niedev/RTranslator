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

package nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Room;
import java.util.ArrayList;
import nie.translator.rtranslatordevedition.database.AppDatabase;
import nie.translator.rtranslatordevedition.database.dao.MyDao;
import nie.translator.rtranslatordevedition.database.entities.RecentPeerEntity;
import nie.translator.rtranslatordevedition.tools.Tools;

public class RecentPeersDataManager {
    private AppDatabase database;
    private ArrayList<RecentPeer> cachedRecentPeers;

    public RecentPeersDataManager(Context context) {
        database = Room.databaseBuilder(context, AppDatabase.class, "consumption_credit_dp").build();
    }

    public void insertRecentPeer(@NonNull final String deviceId, final String uniqueName, final Bitmap userImage) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MyDao dao = database.myDao();
                RecentPeerEntity recentPeerEntity = new RecentPeerEntity();
                recentPeerEntity.deviceId = deviceId;
                recentPeerEntity.uniqueName = uniqueName;
                if (userImage != null) {
                    recentPeerEntity.userImage = Tools.convertBitmapToBytes(userImage);
                }
                dao.insertRecentPeers(recentPeerEntity);
            }
        }).start();
        // updating the cache
        if (cachedRecentPeers != null) {
            RecentPeer recentPeer2 =
                    new RecentPeer(deviceId, uniqueName, userImage);

            boolean found = false;
            for (int i = 0; i < cachedRecentPeers.size() && !found; i++) {
                if (cachedRecentPeers.get(i).getDeviceID().equals(deviceId)) {
                    found = true;
                    cachedRecentPeers.set(i, recentPeer2);
                }
            }
            if (!found) {
                cachedRecentPeers.add(recentPeer2);
            }
        }
    }

    public void deleteRecentPeer(final RecentPeer peer) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MyDao dao = database.myDao();
                RecentPeerEntity recentPeerEntity = new RecentPeerEntity();
                recentPeerEntity.deviceId = peer.getDeviceID();
                recentPeerEntity.uniqueName = peer.getUniqueName();
                recentPeerEntity.userImage = peer.getUserImageData();
                dao.deleteRecentPeers(recentPeerEntity);
            }
        }, "deleteRecentPeer").start();
        // updating the cache
        if (cachedRecentPeers != null) {
            boolean found = false;
            for (int i = 0; i < cachedRecentPeers.size() && !found; i++) {
                if (cachedRecentPeers.get(i).getDeviceID().equals(peer.getDeviceID())) {
                    found = true;
                    cachedRecentPeers.remove(i);
                }
            }
        }
    }


    public void getRecentPeers(final RecentPeersListener responseListener) {
        if (cachedRecentPeers == null) {
            new ObtainRecentPeersTask(responseListener).execute(database.myDao());
        } else {
            responseListener.onRecentPeersObtained(cachedRecentPeers);
        }
    }

    private static class ObtainRecentPeersTask extends AsyncTask<MyDao, Void, ArrayList<RecentPeer>> {
        private RecentPeersListener responseListener;

        private ObtainRecentPeersTask(RecentPeersListener responseListener) {
            this.responseListener = responseListener;
        }

        @Override
        protected ArrayList<RecentPeer> doInBackground(MyDao... myDaos) {
            RecentPeerEntity[] recentPeersArrayEntity;
            ArrayList<RecentPeer> recentPeers = new ArrayList<>();

            if (myDaos[0] != null) {
                recentPeersArrayEntity = myDaos[0].loadRecentPeers();
                for (RecentPeerEntity aRecentPeersArrayEntity : recentPeersArrayEntity) {
                    recentPeers.add(aRecentPeersArrayEntity.getRecentPeer());
                }
                return recentPeers;
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(@Nullable ArrayList<RecentPeer> recentPeers) {
            super.onPostExecute(recentPeers);
            if (recentPeers != null) {
                responseListener.onRecentPeersObtained(recentPeers);
            }
        }
    }

    public interface RecentPeersListener {
        void onRecentPeersObtained(ArrayList<RecentPeer> recentPeers);
    }


    public void getRecentPeer(final String deviceID, final RecentPeerListener responseListener) {
        if (cachedRecentPeers == null) {
            new ObtainRecentPeerTask(deviceID, responseListener).execute(database.myDao());
        } else {
            int index = cachedRecentPeers.indexOf(new RecentPeer(deviceID));
            if (index != -1) {
                responseListener.onRecentPeerObtained(cachedRecentPeers.get(index));
            } else {
                responseListener.onRecentPeerObtained(null);
            }
        }
    }

    private static class ObtainRecentPeerTask extends AsyncTask<MyDao, Void, RecentPeer> {
        private RecentPeerListener responseListener;
        private String deviceID;

        private ObtainRecentPeerTask(String deviceID, RecentPeerListener responseListener) {
            if (deviceID == null) {
                this.deviceID = "";
            } else {
                this.deviceID = deviceID;
            }
            this.responseListener = responseListener;
        }

        @Override
        protected RecentPeer doInBackground(MyDao... myDaos) {
            if (myDaos[0] != null) {
                RecentPeerEntity recentPeerEntity = myDaos[0].loadRecentPeer(deviceID);
                if (recentPeerEntity != null) {
                    return recentPeerEntity.getRecentPeer();
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(@Nullable RecentPeer recentPeer) {
            super.onPostExecute(recentPeer);
            responseListener.onRecentPeerObtained(recentPeer);
        }
    }

    public void getRecentPeerByName(final String uniqueName, final RecentPeerListener responseListener) {
        if (cachedRecentPeers == null) {
            new ObtainRecentPeerByNameTask(uniqueName, responseListener).execute(database.myDao());
        } else {
            int index=-1;
            for (int i = 0; i < cachedRecentPeers.size(); i++) {
                RecentPeer recentPeer = cachedRecentPeers.get(i);
                String uniqueName1 = recentPeer.getUniqueName();
                if (uniqueName1.length() > 0 && uniqueName1.equals(uniqueName)) {
                    index=i;
                }
            }

            if (index != -1) {
                responseListener.onRecentPeerObtained(cachedRecentPeers.get(index));
            } else {
                responseListener.onRecentPeerObtained(null);
            }
        }
    }

    private static class ObtainRecentPeerByNameTask extends AsyncTask<MyDao, Void, RecentPeer> {
        private RecentPeerListener responseListener;
        private String uniqueName;

        private ObtainRecentPeerByNameTask(String uniqueName, RecentPeerListener responseListener) {
            if (uniqueName == null) {
                this.uniqueName = "";
            } else {
                this.uniqueName = uniqueName;
            }
            this.responseListener = responseListener;
        }

        @Override
        protected RecentPeer doInBackground(MyDao... myDaos) {
            if (myDaos[0] != null) {
                RecentPeerEntity recentPeerEntity = myDaos[0].loadRecentPeerByName(uniqueName);
                if (recentPeerEntity != null) {
                    return recentPeerEntity.getRecentPeer();
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(RecentPeer recentPeer) {
            super.onPostExecute(recentPeer);
            responseListener.onRecentPeerObtained(recentPeer);
        }
    }

    public interface RecentPeerListener {
        void onRecentPeerObtained(@Nullable RecentPeer recentPeer);
    }
}
