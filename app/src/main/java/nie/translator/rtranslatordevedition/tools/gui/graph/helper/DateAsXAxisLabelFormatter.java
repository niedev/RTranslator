/**
 * GraphView
 * Copyright 2016 Jonas Gehring
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
package nie.translator.rtranslatordevedition.tools.gui.graph.helper;

import android.content.Context;

import java.text.DateFormat;
import java.util.Calendar;

import nie.translator.rtranslatordevedition.tools.gui.graph.DefaultLabelFormatter;

/**
 * Helper class to use date objects as x-values.
 * This will use your own Date Format or by default
 * the Android default date format to convert
 * the x-values (that has to be millis from
 * 01-01-1970) into a formatted date string.
 *
 * See the DateAsXAxis example in the GraphView-Demos project
 * to see a working example.
 *
 * @author jjoe64
 */
public class DateAsXAxisLabelFormatter extends DefaultLabelFormatter {
    /**
     * the date format that will convert
     * the unix timestamp to string
     */
    protected final DateFormat mDateFormat;

    /**
     * calendar to avoid creating new date objects
     */
    protected final Calendar mCalendar;

    /**
     * create the formatter with the Android default date format to convert
     * the x-values.
     *
     * @param context the application context
     */
    public DateAsXAxisLabelFormatter(Context context) {
        mDateFormat = android.text.format.DateFormat.getDateFormat(context);
        mCalendar = Calendar.getInstance();
    }

    /**
     * create the formatter with your own custom
     * date format to convert the x-values.
     *
     * @param context the application context
     * @param dateFormat custom date format
     */
    public DateAsXAxisLabelFormatter(Context context, DateFormat dateFormat) {
        mDateFormat = dateFormat;
        mCalendar = Calendar.getInstance();
    }

    /**
     * formats the x-values as date string.
     *
     * @param value raw value
     * @param isValueX true if it's a x value, otherwise false
     * @return value converted to string
     */
    @Override
    public String formatLabel(double value, boolean isValueX) {
        if (isValueX) {
            // format as date
            mCalendar.setTimeInMillis((long) value);
            return mDateFormat.format(mCalendar.getTimeInMillis());
        } else {
            return super.formatLabel(value, isValueX);
        }
    }
}
