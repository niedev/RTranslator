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

package nie.translator.rtranslatordevedition.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import nie.translator.rtranslatordevedition.database.entities.Hour;
import nie.translator.rtranslatordevedition.database.entities.RecentPeerEntity;

@Dao
public interface MyDao{
    //inserts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHours(Hour... hours);
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecentPeers(RecentPeerEntity... recentPeerEntities);

    //updates
    @Update
    void updateHours(Hour... hours);
    @Update
    void updateRecentPeers(RecentPeerEntity... recentPeerEntities);

    //deletes
    @Delete
    void deleteRecentPeers(RecentPeerEntity... recentPeerEntities);

    //select
    @Query("SELECT COUNT(id) FROM Hour")
    int loadHoursCount();

    @Query("SELECT * FROM Hour order by year DESC,month DESC,day DESC,id DESC LIMIT 1")
    Hour loadUltimateHour();

    @Query("SELECT * FROM Hour order by year,month,day,id LIMIT 1")
    Hour loadFirstHour();

    @Query("SELECT * FROM Hour where year=:year AND month=:month AND day=:day AND id=:hour")
    Hour loadDayHour(int year, int month, int day, int hour);

    @Query("SELECT * FROM Hour where year=:year AND month=:month AND day=:day")
    Hour[] loadDayHours(int year, int month, int day);

    @Query("SELECT * FROM RecentPeerEntity")
    RecentPeerEntity[] loadRecentPeers();

    @Query("SELECT * FROM RecentPeerEntity where deviceId=:deviceId")
    RecentPeerEntity loadRecentPeer(String deviceId);

    @Query("SELECT * FROM RecentPeerEntity where uniqueName=:uniqueName")
    RecentPeerEntity loadRecentPeerByName(String uniqueName);

}
