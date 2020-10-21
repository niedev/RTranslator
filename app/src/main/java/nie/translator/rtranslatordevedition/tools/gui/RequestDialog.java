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

package nie.translator.rtranslatordevedition.tools.gui;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.tools.Timer;

public class RequestDialog {
    private final int SHOW_TIMER_SECONDS=5;
    private TextView time;
    private long timeout=-1;
    private AlertDialog alertDialog;

    public RequestDialog(Activity context, String message, long timeout, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener){
        final View dialogLayout= context.getLayoutInflater().inflate(R.layout.dialog_connection_request,null);
        TextView textViewMessage=dialogLayout.findViewById(R.id.connection_request_text);
        this.time=dialogLayout.findViewById(R.id.countDown);
        this.timeout=timeout;
        textViewMessage.setText(message);
        //dialog creation
        final AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setView(dialogLayout);
        builder.setCancelable(true);
        builder.setPositiveButton(android.R.string.yes, positiveListener);
        builder.setNegativeButton(android.R.string.no, negativeListener);

        alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
    }

    public RequestDialog(Activity context, String message, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener){
        //dialog creation
        final AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setMessage(message);
        builder.setCancelable(true);
        builder.setPositiveButton(android.R.string.yes, positiveListener);
        builder.setNegativeButton(android.R.string.no, negativeListener);

        alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(false);
    }

    public void show(){
        alertDialog.show();
        if(timeout!=-1) {
            new Timer(timeout, new Timer.DateCallback() {
                @Override
                public void onTick(int hoursUntilEnd, int minutesUntilEnd, int secondsUntilEnd) {
                    if (secondsUntilEnd <= SHOW_TIMER_SECONDS) {
                        if (time.getVisibility() == View.INVISIBLE) {
                            time.setVisibility(View.VISIBLE);
                        }
                        time.setText(String.valueOf(secondsUntilEnd));
                    }
                }

                @Override
                public void onEnd() {
                    alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                }
            }).start();
        }
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener) {
        alertDialog.setOnCancelListener(onCancelListener);
    }

    public void cancel() {
        alertDialog.cancel();
    }
}
