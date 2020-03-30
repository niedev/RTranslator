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

package nie.translator.rtranslatordevedition.tools;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class CustomTime implements Parcelable {
    public static final int HOUR=0;
    public static final int DAY=1;
    public static final int MONTH=2;
    public static final int YEAR=3;
    private static final long HOUR_IN_MILLIS=60*60*1000;
    public static final long DAY_IN_MILLIS=24*HOUR_IN_MILLIS;
    private static final long MONTH_IN_MILLIS=30*DAY_IN_MILLIS;
    private static final long YEAR_IN_MILLIS= 365*MONTH_IN_MILLIS;
    private SparseArray<String> monthsNames;
    private Date date;

    public CustomTime(@NonNull Date date){
        this.date=date;
        Map<String, Integer> monthsNamesReverseMap=Calendar.getInstance().getDisplayNames(Calendar.MONTH,Calendar.LONG,Locale.getDefault());
        monthsNames = new SparseArray<>();
        for(Map.Entry<String, Integer> entry : monthsNamesReverseMap.entrySet()){
            monthsNames.put(entry.getValue(), entry.getKey());
        }
    }

    public CustomTime(int year,int month,int day,int hour){
        Calendar c = Calendar.getInstance();
        c.set(year, month-1, day, hour, 0);
        date=c.getTime();
        Map<String, Integer> monthsNamesReverseMap=Calendar.getInstance().getDisplayNames(Calendar.MONTH,Calendar.LONG,Locale.getDefault());
        monthsNames = new SparseArray<>();
        for(Map.Entry<String, Integer> entry : monthsNamesReverseMap.entrySet()){
            monthsNames.put(entry.getValue(), entry.getKey());
        }
    }

    public CustomTime(long time) {
        this.date=new Date(time);
        Map<String, Integer> monthsNamesReverseMap=Calendar.getInstance().getDisplayNames(Calendar.MONTH,Calendar.LONG,Locale.getDefault());
        monthsNames = new SparseArray<>();
        for(Map.Entry<String, Integer> entry : monthsNamesReverseMap.entrySet()){
            monthsNames.put(entry.getValue(), entry.getKey());
        }
    }

    public int getYear(){
        return Integer.parseInt(android.text.format.DateFormat.format("yyyy", date).toString());
    }

    public int getMonth(){
        return Integer.parseInt(android.text.format.DateFormat.format("MM", date).toString());
    }

    public int getDay(){
        return Integer.parseInt(android.text.format.DateFormat.format("dd", date).toString());
    }

    public int getHour(){
        return Integer.parseInt(android.text.format.DateFormat.format("HH", date).toString());
    }

    public static int getTimeBetween(CustomTime time1, CustomTime time2, int outputType){
        double timeBetween=Math.abs(time1.getTime()-time2.getTime());
        switch (outputType){
            case YEAR:{
                return (int) Math.ceil(timeBetween/YEAR_IN_MILLIS);
            }
            case MONTH:{
                return (int) Math.ceil(timeBetween/MONTH_IN_MILLIS);
            }
            case DAY:{
                double res1=timeBetween/DAY_IN_MILLIS;
                double res= Math.ceil(res1);
                return (int) res;
            }
            case HOUR:{
                return (int) Math.ceil(timeBetween/HOUR_IN_MILLIS);
            }
        }
        return -1;
    }

    public long getTime() {
        return date.getTime();
    }

    public String getMonthName() {
        return monthsNames.get(getMonth()-1);
    }

    public void addDays(int days){
        this.date.setTime(this.date.getTime()+days*DAY_IN_MILLIS);
    }

    public boolean equals(CustomTime date, int accuracy) {
       if(getYear()==date.getYear()){
           if(accuracy==YEAR){
               return true;
           }
           if(getMonth()==date.getMonth()){
               if(accuracy==MONTH){
                   return true;
               }
               if(getDay()==date.getDay()){
                   if (accuracy==DAY){
                       return true;
                   }
                   if(getHour()==date.getHour()){
                       if(accuracy==HOUR){
                           return true;
                       }
                   }
               }
           }
       }
       return false;
    }

    //parcel implementation
    public static final Creator<CustomTime> CREATOR = new Creator<CustomTime>() {
        @Override
        public CustomTime createFromParcel(Parcel in) {
            return new CustomTime(in);
        }

        @Override
        public CustomTime[] newArray(int size) {
            return new CustomTime[size];
        }
    };

    protected CustomTime(Parcel in) {
        Map<String, Integer> monthsNamesReverseMap=Calendar.getInstance().getDisplayNames(Calendar.MONTH,Calendar.LONG,Locale.getDefault());
        monthsNames = new SparseArray<>();
        for(Map.Entry<String, Integer> entry : monthsNamesReverseMap.entrySet()){
            monthsNames.put(entry.getValue(), entry.getKey());
        }
        date= new Date(in.readLong());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(date.getTime());
    }

    public Date getDate() {
        return date;
    }
}
