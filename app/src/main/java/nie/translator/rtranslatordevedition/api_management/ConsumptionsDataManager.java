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

package nie.translator.rtranslatordevedition.api_management;

import android.content.Context;
import android.util.Log;
import androidx.room.Room;
import java.util.Date;
import java.util.HashMap;
import nie.translator.rtranslatordevedition.database.AppDatabase;
import nie.translator.rtranslatordevedition.database.dao.MyDao;
import nie.translator.rtranslatordevedition.database.entities.Hour;
import nie.translator.rtranslatordevedition.tools.CustomTime;


public class ConsumptionsDataManager {
    private AppDatabase database;

    public ConsumptionsDataManager(Context context) {
        database = Room.databaseBuilder(context, AppDatabase.class, "consumption_credit_dp").build();
    }

    public void addUsage(float credit) { //dovrà essere richiamato a ogni utilizzo dell' api
        CustomTime currentDate = new CustomTime(new Date(System.currentTimeMillis()));
        Hour currentHour = new Hour();
        Hour ultimateHour = null;
        boolean isCurrentHourUltimateHour = false;
        MyDao dao = database.myDao();

        currentHour.id = currentDate.getHour();
        currentHour.day = currentDate.getDay();
        currentHour.month = currentDate.getMonth();
        currentHour.year = currentDate.getYear();
        ultimateHour = dao.loadUltimateHour();
        if (ultimateHour != null) {
            if (ultimateHour.id == currentHour.id){
                isCurrentHourUltimateHour = true;
            }
        }

        if (isCurrentHourUltimateHour) {
            ultimateHour.consumption += credit;
            dao.updateHours(ultimateHour);
            Log.e("consuptionUpdated", "last hour consuption: " + ultimateHour.consumption);
        } else {
            currentHour.consumption = credit;
            Log.e("consuptionUpdated", "last hour created, consuption: " + currentHour.consumption);
            dao.insertHours(currentHour);
        }
    }

    public HashMap<Integer, Float> getDayData(CustomTime date) {  //dovrà essere richiamato a ogni istanza del grafico
        HashMap<Integer, Float> hashMap = new HashMap<>();
        int id = date.getHour();
        int day = date.getDay();
        int month = date.getMonth();
        int year = date.getYear();

        //collection of all hours with the year, month and day indicated + midnight of the following day
        Hour[] partialData = database.myDao().loadDayHours(year, month, day);
        Hour ultimateHour = database.myDao().loadDayHour(year, month, day + 1, 0);

        for (int i = 0; i < partialData.length; i++) {
            hashMap.put(partialData[i].id, partialData[i].consumption);
        }
        if (ultimateHour != null) {
            hashMap.put(24, ultimateHour.consumption);
        }

        return hashMap;
    }

    public CustomTime getOlderDate() {
        Hour firstHour = database.myDao().loadFirstHour();
        CustomTime date = null;
        if (firstHour != null) {
            date = new CustomTime(firstHour.year, firstHour.month, firstHour.day, 0);
        }
        return date;
    }

    public int getHoursCount() {
        return database.myDao().loadHoursCount();
    }
}
