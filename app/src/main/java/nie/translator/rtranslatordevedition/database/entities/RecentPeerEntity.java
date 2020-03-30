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

package nie.translator.rtranslatordevedition.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import nie.translator.rtranslatordevedition.voice_translation._conversation_mode.communication.recent_peer.RecentPeer;


@Entity
public class RecentPeerEntity {
    @androidx.annotation.NonNull
    @PrimaryKey
    public String deviceId="";
    public String uniqueName;
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public byte[] userImage;

    public RecentPeer getRecentPeer(){
        return new RecentPeer(deviceId,uniqueName,userImage);
    }
}
