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
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import nie.translator.rtranslatordevedition.R;
import nie.translator.rtranslatordevedition.api_management.ConsumptionsDataManager;
import nie.translator.rtranslatordevedition.tools.CustomTime;
import nie.translator.rtranslatordevedition.tools.Tools;
import nie.translator.rtranslatordevedition.tools.gui.graph.GraphView;
import nie.translator.rtranslatordevedition.tools.gui.graph.GridLabelRenderer;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.DataPoint;
import nie.translator.rtranslatordevedition.tools.gui.graph.series.LineGraphSeries;

public class CustomDayGraphsPagerAdapter extends PagerAdapter {
    private Activity activity;
    private CustomTime currentDate;
    private CustomTime olderDate;
    private ConsumptionsDataManager databaseManager;
    private ArrayList<GraphDrawer> graphDrawers = new ArrayList<>();
    private Handler selfHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            DataPoint[] dataPoints = (DataPoint[]) msg.getData().getParcelableArray("dataPoints");
            CustomTime itemDate = msg.getData().getParcelable("itemDate");
            //graph values initialization
            LineGraphSeries<DataPoint> seriesLine = new LineGraphSeries<>(dataPoints);
            // seriesLine interface customization
            seriesLine.setThickness(Tools.convertDpToPixels(activity, 2));
            seriesLine.setAnimated(true);
            seriesLine.setDrawBackground(true);
            seriesLine.setColor(GuiTools.getColor(activity, R.color.secondary));
            seriesLine.setBackgroundColor(GuiTools.getColor(activity, R.color.secondaryTransparent));

            // search for the indicated graph and drawing of the line
            int index = graphDrawers.indexOf(new GraphDrawer(itemDate));
            if (index != -1) {
                DateGraph dateGraph = graphDrawers.get(index).getGraph();
                if (seriesLine.getHighestValueY() < 0.008) {
                    dateGraph.getViewport().setYAxisBoundsManual(true);
                    dateGraph.getViewport().setMaxY(0.008);
                }
                dateGraph.getViewport().setXAxisBoundsManual(true);
                dateGraph.getViewport().setMaxX(24);
                dateGraph.addSeries(seriesLine);
            }
            return false;
        }
    });

    public CustomDayGraphsPagerAdapter(@NonNull Activity activity, CustomTime olderDate, final ConsumptionsDataManager databaseManager) {
        this.activity = activity;
        this.currentDate = new CustomTime(new Date(System.currentTimeMillis()));
        this.databaseManager = databaseManager;
        this.olderDate = olderDate;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, final int position) {
        // graphView creation
        ViewGroup layout = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.component_credit_graph, container, false);
        final DateGraph graphView = layout.findViewById(R.id.graph);
        final CustomTime itemDate = new CustomTime(olderDate.getTime());
        itemDate.addDays(position);
        graphView.setDate(itemDate);
        layout.removeView(graphView);
        container.addView(graphView);
        GraphDrawer graphDrawer = new GraphDrawer(graphView);
        graphDrawers.add(graphDrawer);
        graphDrawer.start();

        // graphView interface customization        graphView.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        graphView.getGridLabelRenderer().setHorizontalLabelsSpace(Tools.convertSpToPixels(activity, 20));
        graphView.getGridLabelRenderer().setVerticalLabelsSpace(Tools.convertDpToPixels(activity, 4));
        graphView.getGridLabelRenderer().setVerticalLabelsAlign(Paint.Align.LEFT);
        graphView.getGridLabelRenderer().setHorizontalPadding(Tools.convertDpToPixels(activity, 12));
        graphView.getGridLabelRenderer().setTextSize(Tools.convertSpToPixels(activity, 13));
        graphView.getGridLabelRenderer().setNumHorizontalLabels(9);
        graphView.getGridLabelRenderer().setGridColor(GuiTools.getColor(activity, R.color.very_light_gray));
        graphView.getGridLabelRenderer().setHorizontalLabelsColor(GuiTools.getColor(activity, R.color.gray));
        graphView.getGridLabelRenderer().setVerticalLabelsColor(GuiTools.getColor(activity, R.color.gray));
        graphView.getGridLabelRenderer().setVerticalLabelsVAlign(GridLabelRenderer.VerticalLabelsVAlign.BELOW);
        return graphView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object object) {
        if (object instanceof DateGraph) {
            DateGraph graphView = (DateGraph) object;
            int index = graphDrawers.indexOf(new GraphDrawer(graphView.getDate()));
            if (index != -1) {
                graphDrawers.get(index).interrupt();
                graphDrawers.remove(index);
                collection.removeView(graphView);
            }
        }
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (object instanceof DateGraph) {
            return CustomTime.getTimeBetween(((DateGraph) object).getDate(), olderDate, CustomTime.DAY);
        } else {
            return -1;
        }
    }

    public CustomTime getDate(int index) {
        CustomTime date = new CustomTime(olderDate.getTime());
        date.addDays(index);
        return date;
    }

    public void drawGraphLine(DataPoint[] dataPoints, CustomTime itemDate) {
        Messenger selfMessenger = new Messenger(selfHandler);
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putParcelableArray("dataPoints", dataPoints);
        bundle.putParcelable("itemDate", itemDate);
        message.setData(bundle);
        try {
            selfMessenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getCount() {
        return CustomTime.getTimeBetween(currentDate, olderDate, CustomTime.DAY);
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
        return view.equals(o);
    }

    public static class DateGraph extends GraphView {
        private CustomTime date;

        public DateGraph(Context context) {
            super(context);
        }

        public DateGraph(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public DateGraph(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public CustomTime getDate() {
            return date;
        }

        public void setDate(CustomTime date) {
            this.date = date;
        }
    }

    public class GraphDrawer {
        private DateGraph graph;
        private CustomTime itemDate;
        private Thread thread = new Thread("graphDrawer") {
            @Override
            public void run() {
                DataPoint[] dataPoints = new DataPoint[25];
                int initialValue = 0;
                if (!graph.getDate().equals(currentDate, CustomTime.DAY)) {
                    dataPoints = new DataPoint[25];
                } else {
                    dataPoints = new DataPoint[currentDate.getHour()];
                }
                if (graph.getDate().equals(olderDate, CustomTime.DAY)) {
                    initialValue = olderDate.getHour();
                }
                HashMap<Integer, Float> dayData = databaseManager.getDayData(graph.getDate());
                for (int i = initialValue; i < dataPoints.length; i++) {
                    boolean isDataExist = false;
                    if (dayData != null) {
                        if (dayData.containsKey(i)) {
                            isDataExist = true;
                        }
                    }

                    if (isDataExist) {
                        dataPoints[i] = new DataPoint(i, dayData.get(i));
                    } else {
                        dataPoints[i] = new DataPoint(i, 0);
                    }
                }
                /*  (for make a fake graph for the example screenshots)
                dataPoints[0] = new DataPoint(0, 0);
                dataPoints[1] = new DataPoint(1, 0);
                dataPoints[2] = new DataPoint(2, 0);
                dataPoints[3] = new DataPoint(3, 0);
                dataPoints[4] = new DataPoint(4, 0);
                dataPoints[5] = new DataPoint(5, 0);
                dataPoints[6] = new DataPoint(6, 0);
                dataPoints[7] = new DataPoint(7, 0);
                dataPoints[8] = new DataPoint(8, 0.3);
                dataPoints[9] = new DataPoint(9, 0.7);
                dataPoints[10] = new DataPoint(10, 1.3);
                dataPoints[11] = new DataPoint(11, 0.5);
                dataPoints[12] = new DataPoint(12, 0.7);
                dataPoints[13] = new DataPoint(13, 0.9);
                dataPoints[14] = new DataPoint(14, 0.4);
                dataPoints[15] = new DataPoint(15, 0.8);
                dataPoints[16] = new DataPoint(16, 1.8);
                dataPoints[17] = new DataPoint(17, 1);
                dataPoints[18] = new DataPoint(18, 1.4);
                dataPoints[19] = new DataPoint(19, 0.7);
                dataPoints[20] = new DataPoint(20, 0);
                dataPoints[21] = new DataPoint(21, 0);
                dataPoints[22] = new DataPoint(22, 0);
                dataPoints[23] = new DataPoint(23, 0);
                dataPoints[24] = new DataPoint(24, 0);
                */
                drawGraphLine(dataPoints, itemDate);
            }
        };

        private GraphDrawer(DateGraph dateGraph) {
            this.graph = dateGraph;
            this.itemDate = graph.getDate();
        }

        private GraphDrawer(CustomTime date) {
            this.itemDate = date;
        }

        public DateGraph getGraph() {
            return graph;
        }

        public void setGraph(DateGraph graph) {
            this.graph = graph;
        }

        public Thread getThread() {
            return thread;
        }

        public void setThread(Thread thread) {
            this.thread = thread;
        }

        public void start() {
            thread.start();
        }

        private void interrupt() {
            thread.interrupt();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj instanceof GraphDrawer) {
                return ((GraphDrawer) obj).getItemDate().equals(itemDate);
            } else {
                return false;
            }
        }

        private CustomTime getItemDate() {
            return itemDate;
        }
    }

}
