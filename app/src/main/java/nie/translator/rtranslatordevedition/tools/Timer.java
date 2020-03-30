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

import android.os.Handler;
import android.os.Looper;

public class Timer {
    private final long hourMillis=3600000;
    private final long minuteMillis=60000;
    private final long secondMillis=1000;
    private CustomCountDownTimer timer;
    private long duration;
    private Handler mainHandler;

    public Timer(long durationMillis, final DateCallback dateCallback){
        mainHandler = new Handler(Looper.getMainLooper());
        duration=durationMillis;
        timer= new CustomCountDownTimer(durationMillis,1000) {
            @Override
            public void onTick(long millisUntilEnd) {
                int[] date=convertIntoDate(millisUntilEnd);
                dateCallback.onTick(date[0],date[1],date[2]);
            }

            @Override
            public void onFinish() {
                dateCallback.onEnd();
            }
        };
    }

    public Timer(long durationMillis, long intervalMillis, final Callback dateCallback){
        mainHandler = new Handler(Looper.getMainLooper());
        duration=durationMillis;
        timer= new CustomCountDownTimer(durationMillis,intervalMillis) {
            @Override
            public void onTick(long millisUntilEnd) {
                dateCallback.onTick(millisUntilEnd);
            }

            @Override
            public void onFinish() {
                dateCallback.onEnd();
            }
        };
    }

    public void start(){
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                timer.start();
            }
        });
    }

    public void cancel(){
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                timer.cancel();
            }
        });
    }

    public int[] getDuration(){
        return convertIntoDate(duration);
    }

    private int[] convertIntoDate(long millis){
        int hours=0;
        int minutes=0;
        int seconds=0;
        if(millis>hourMillis){
            long rest=millis%hourMillis;
            hours= (int) ((millis-rest)/hourMillis);
            millis=rest;
        }
        if(millis>minuteMillis){
            long rest=millis%minuteMillis;
            minutes= (int) ((millis-rest)/minuteMillis);
            millis=rest;
        }
        if(millis>secondMillis){
            long rest=millis%secondMillis;
            seconds= (int) ((millis-rest)/secondMillis);
        }

        return new int[]{hours,minutes,seconds};
    }

    public interface DateCallback {
        void onTick(int hoursUntilEnd, int minutesUntilEnd, int secondsUntilEnd);
        void onEnd();
    }

    public interface Callback {
        void onTick(long millisUntilEnd);
        void onEnd();
    }
}
