/*
 * Corona-Warn-Companion. An app that shows COVID-19 Exposure Notifications details.
 * Copyright (C) 2020  Michael Huebler <corona-warn-companion@tosl.org> and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.tosl.coronawarncompanion.matchentries;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.tosl.coronawarncompanion.CWCApplication;
import org.tosl.coronawarncompanion.R;
import org.tosl.coronawarncompanion.diagnosiskeys.DiagnosisKeysProtos;
import org.tosl.coronawarncompanion.gmsreadout.ContactRecordsProtos;
import org.tosl.coronawarncompanion.matchentries.MatchEntryContent.DailyMatchEntries;
import org.tosl.coronawarncompanion.matcher.Matcher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.tosl.coronawarncompanion.tools.Utils.byteArrayToHexString;
import static org.tosl.coronawarncompanion.tools.Utils.getMillisFromSeconds;
import static org.tosl.coronawarncompanion.tools.Utils.resolveColorAttr;
import static org.tosl.coronawarncompanion.tools.Utils.xorTwoByteArrays;

/**
 * {@link RecyclerView.Adapter} that can display a {@link org.tosl.coronawarncompanion.matcher.Matcher.MatchEntry}.
 */
public class MatchesRecyclerViewAdapter extends RecyclerView.Adapter<MatchesRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "CRRecyclerViewAdapter";
    private final int lineColor;
    private static final int redColor = Color.parseColor("#FF0000");
    private static final int orangeColor = Color.parseColor("#FFA500");
    private static final int yellowColor = Color.parseColor("#FFFF00");
    private static final int greenColor = Color.parseColor("#00FF00");
    private final float textScalingFactor;

    private final ArrayList<Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries>> mValues;
    private final Context mContext;

    private boolean showAllScans = false;

    public MatchesRecyclerViewAdapter(DailyMatchEntries dailyMatchEntries, Context context) {
        this.mContext = context;
        this.mValues = new ArrayList<>();
        TreeMap<Integer, Pair<DiagnosisKeysProtos.TemporaryExposureKey,
                MatchEntryContent.GroupedByDkMatchEntries>> treeMap = new TreeMap<>();
        for (Map.Entry<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries> entry :
                dailyMatchEntries.getMap().entrySet()) {
            treeMap.put(entry.getValue().getList().get(0).startTimestampUTC, new Pair<>(entry.getKey(), entry.getValue()));
        }
        mValues.addAll(treeMap.values());
        DisplayMetrics metrics = this.mContext.getResources().getDisplayMetrics();
        this.textScalingFactor = metrics.scaledDensity/metrics.density;
        this.lineColor = resolveColorAttr(android.R.attr.textColorSecondary, context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.match_card_fragment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mMatchEntriesPair = mValues.get(position);
        DiagnosisKeysProtos.TemporaryExposureKey dk = holder.mMatchEntriesPair.first;
        MatchEntryContent.GroupedByDkMatchEntries groupedByDkMatchEntries = holder.mMatchEntriesPair.second;

        ArrayList<Matcher.MatchEntry> list = groupedByDkMatchEntries.getList();

        // Text View:

        // set date label formatter
        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

        boolean hasTransmissionRiskLevel = false;
        int transmissionRiskLevel = 0;
        //noinspection deprecation
        if (dk.hasTransmissionRiskLevel()) {
            //noinspection deprecation
            transmissionRiskLevel = dk.getTransmissionRiskLevel();
            hasTransmissionRiskLevel = true;
        }
        boolean hasReportType = false;
        DiagnosisKeysProtos.TemporaryExposureKey.ReportType reportType = DiagnosisKeysProtos.TemporaryExposureKey.ReportType.UNKNOWN;
        if (dk.hasReportType()) {
            //noinspection UnusedAssignment
            reportType = dk.getReportType();
            //noinspection UnusedAssignment
            hasReportType = true;
        }

        MatchEntryDetails matchEntryDetails = getMatchEntryDetails(list, CWCApplication.getTimeZoneOffsetSeconds());
        int minTimestampLocalTZDay0 = matchEntryDetails.minTimestampLocalTZDay0;
        int maxTimestampLocalTZDay0 = matchEntryDetails.maxTimestampLocalTZDay0;

        Date startDate = new Date(getMillisFromSeconds(minTimestampLocalTZDay0));
        Date endDate = new Date(getMillisFromSeconds(maxTimestampLocalTZDay0));
        String startDateStr = dateFormat.format(startDate);
        String endDateStr = dateFormat.format(endDate);

        String text = this.mContext.getResources().getString(R.string.time);
        text += " ";
        if (startDateStr.equals(endDateStr)) {
            text += startDateStr;
        } else {
            text += startDateStr+"-"+endDateStr;
        }
        text += "\n";

        text += "\n";
        //if (hasReportType) {
        //    text += CWCApplication.getAppContext().getResources().getString(R.string.report_type) + ": " + getReportTypeStr(reportType) + "\n";
        //}
        // text += CWCApplication.getAppContext().getResources().getString(R.string.min_attenuation)+": "+minAttenuation+"dB\n";
        // text += "("+byteArrayToHex(dk.getKeyData().toByteArray())+")";
        text += this.mContext.getResources().getString(R.string.distance_shown_as_attenuation)+":";
        holder.mTextView1.setText(text);

        text = "";
        if (hasTransmissionRiskLevel) {
            text = this.mContext.getResources().getString(R.string.transmission_risk_level) + ": " + transmissionRiskLevel;
        }
        holder.mTextView2.setText(text);

        // Graph:
        configureDetailsChart(holder.mChartView, matchEntryDetails.dataPointsMinAttenuation, matchEntryDetails.dotColorsMinAttenuation,
                matchEntryDetails.dataPoints, matchEntryDetails.dotColors,
                minTimestampLocalTZDay0, maxTimestampLocalTZDay0, mContext);
        holder.mChartView.getLineData().getDataSetByIndex(1).setVisible(this.showAllScans);

        if (this.showAllScans) {
            String txPowerStr;
            if (matchEntryDetails.minTxPower == matchEntryDetails.maxTxPower) {
                txPowerStr = String.valueOf(matchEntryDetails.minTxPower);
            } else {
                txPowerStr = String.valueOf(matchEntryDetails.minTxPower) + ".." +
                        String.valueOf(matchEntryDetails.maxTxPower);
            }
            holder.mTextView3.setText(this.mContext.getResources().getString(R.string.tx_power, txPowerStr));
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) holder.mTextView3.getLayoutParams();
            params.height = this.mContext.getResources().getDimensionPixelSize(R.dimen.details_text_view_3_height);
            holder.mTextView3.setLayoutParams(params);
        } else {
            holder.mTextView3.setText("");
            ViewGroup.LayoutParams params = (ViewGroup.LayoutParams) holder.mTextView3.getLayoutParams();
            params.height = this.mContext.getResources().getDimensionPixelSize(R.dimen.details_text_view_3_height_disabled);
            holder.mTextView3.setLayoutParams(params);
        }
    }

    public static class MatchEntryDetails {
        public ArrayList<Entry> dataPoints;
        public ArrayList<Integer> dotColors;
        public ArrayList<Entry> dataPointsMinAttenuation;
        public ArrayList<Integer> dotColorsMinAttenuation;
        public int minAttenuation;
        public byte minTxPower;
        public byte maxTxPower;
        public int minTimestampLocalTZDay0;
        public int maxTimestampLocalTZDay0;
        public MatchEntryDetails() {
            this.dataPoints = new ArrayList<>();
            this.dotColors = new ArrayList<>();
        }
    }

    public static MatchEntryDetails getMatchEntryDetails(ArrayList<Matcher.MatchEntry> list,
                                                         int timeZoneOffset) {
        // Threshold value for break detection:
        final int pauseThresholdSeconds = 10;

        MatchEntryDetails result = new MatchEntryDetails();
        result.minTimestampLocalTZDay0 = Integer.MAX_VALUE;
        result.maxTimestampLocalTZDay0 = Integer.MIN_VALUE;
        result.dataPoints = new ArrayList<>();
        result.dotColors = new ArrayList<>();
        result.dataPointsMinAttenuation = new ArrayList<>();
        result.dotColorsMinAttenuation = new ArrayList<>();
        result.minAttenuation = Integer.MAX_VALUE;
        result.minTxPower = Byte.MAX_VALUE;
        result.maxTxPower = Byte.MIN_VALUE;

        TreeMap<Integer, Integer> dataPointsInterimMap = new TreeMap<>();

        // First step: Create a "flat" sorted list (TreeMap) from all scan records from all matchEntries
        for (Matcher.MatchEntry matchEntry : list) {  // process each matchEntry separately
            for (ContactRecordsProtos.ScanRecord scanRecord : matchEntry.contactRecords.getRecordList()) {
                byte[] aem = xorTwoByteArrays(scanRecord.getAem().toByteArray(), matchEntry.aemXorBytes);
                if ((aem[0] != 0x40) || (aem[2] != 0x00) || (aem[3] != 0x00)) {
                    Log.w(TAG, "WARNING: Invalid AEM: " + byteArrayToHexString(aem));
                }
                byte txPower = aem[1];
                //Log.d(TAG, "TXPower: "+txPower+" dBm");
                int rssi = (int) scanRecord.getRssi();
                //Log.d(TAG, "RSSI: "+rssi+" dBm");
                int attenuation = txPower - rssi;
                //Log.d(TAG, "Attenuation: "+attenuation+" dB");

                int timestampLocalTZ = scanRecord.getTimestamp() + timeZoneOffset;
                // reduce to "day0", to improve resolution within the float x value:
                int timestampLocalTZDay0 = timestampLocalTZ % (24*3600);

                // store to temporary buffers:
                dataPointsInterimMap.put(timestampLocalTZDay0, attenuation);

                // if found, store max/min values
                if (result.minTxPower > txPower) {
                    result.minTxPower = txPower;
                }
                if (result.maxTxPower < txPower) {
                    result.maxTxPower = txPower;
                }
                if (result.minAttenuation > attenuation) {
                    result.minAttenuation = attenuation;
                }
                if (result.minTimestampLocalTZDay0 > timestampLocalTZDay0) {
                    result.minTimestampLocalTZDay0 = timestampLocalTZDay0;
                }
                if (result.maxTimestampLocalTZDay0 < timestampLocalTZDay0) {
                    result.maxTimestampLocalTZDay0 = timestampLocalTZDay0;
                }
            }
        }

        // Second step: Process each scan record, group them, find the minimum attenuation in each group
        ArrayList<Entry> dataPointsBuffer = new ArrayList<>();
        ArrayList<Integer> dotColorsBuffer = new ArrayList<>();
        int lastTimestampLocalTZDay0 = 0;
        int localMinAttenuation = Integer.MAX_VALUE;

        int numLastScanRecord = dataPointsInterimMap.size() - 1;
        int i = 0;
        for(Map.Entry<Integer, Integer> mapEntry : dataPointsInterimMap.entrySet()) {
            // iterate over sorted TreeMap
            int timestampLocalTZDay0 = mapEntry.getKey();
            int attenuation = mapEntry.getValue();

            // Second step: look for a break (>= pauseThresholdSeconds)
            // suppress break detection at the very first entry
            if ((i != 0) && (timestampLocalTZDay0 >= lastTimestampLocalTZDay0 + pauseThresholdSeconds)) {
                /*
                String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hms");
                DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                // UTC because we don't want DateFormat to do additional time zone compensation
                */
                //Log.d(TAG, "lastTimestampLocalTZDay0: "+dateFormat.format(new Date(getMillisFromSeconds((int) lastTimestampLocalTZDay0)))+
                //        ", timestampLocalTZDay0: "+dateFormat.format(new Date(getMillisFromSeconds((int) timestampLocalTZDay0))));

                // break found: Now copy the block we have collected so far
                boolean minHandled = false;
                for (int pos = 0; pos < dataPointsBuffer.size(); pos++) {
                    Entry entry = dataPointsBuffer.get(pos);
                    int color = dotColorsBuffer.get(pos);
                    //Log.d(TAG, "Entry at: "+dateFormat.format(new Date(getMillisFromSeconds((int) entry.getX()))));
                    if (!minHandled && entry.getY() <= localMinAttenuation) {
                        // This is the minimum, store in the "minimum" list
                        //Log.d(TAG, "Minimum found.");
                        result.dataPointsMinAttenuation.add(entry);
                        result.dotColorsMinAttenuation.add(color);
                        minHandled = true;
                    } else {
                        // This is one of the other entries, store in the "normal" list
                        result.dataPoints.add(entry);
                        result.dotColors.add(color);
                    }
                }
                // clear the temporary buffer
                dataPointsBuffer.clear();
                dotColorsBuffer.clear();
                // reset search for local minimum:
                localMinAttenuation = Integer.MAX_VALUE;
            }

            // store to temporary buffers:
            dataPointsBuffer.add(new Entry(timestampLocalTZDay0, attenuation));
            dotColorsBuffer.add(getDotColorForAttenuation(attenuation));
            // if found, store local min value
            if (localMinAttenuation > attenuation) {
                localMinAttenuation = attenuation;
            }

            // if this is the last entry, handle the situation separately
            if (i == numLastScanRecord) {
                // Now copy the block we have collected so far
                boolean minHandled = false;
                for (int pos = 0; pos < dataPointsBuffer.size(); pos++) {
                    Entry entry = dataPointsBuffer.get(pos);
                    int color = dotColorsBuffer.get(pos);
                    //Log.d(TAG, "Entry at: "+dateFormat.format(new Date(getMillisFromSeconds((int) entry.getX()))));
                    if (!minHandled && entry.getY() <= localMinAttenuation) {
                        // This is the minimum, store in the "minimum" list
                        //Log.d(TAG, "Minimum found.");
                        result.dataPointsMinAttenuation.add(entry);
                        result.dotColorsMinAttenuation.add(color);
                        minHandled = true;
                    } else {
                        // This is one of the other entries, store in the "normal" list
                        result.dataPoints.add(entry);
                        result.dotColors.add(color);
                    }
                }
            }

            // prepare break detection
            lastTimestampLocalTZDay0 = timestampLocalTZDay0;
            i++;
        }
        return result;
    }

    private static int getDotColorForAttenuation(int attenuation) {
        if (attenuation < 55) {
            return redColor;
        } else if (attenuation <= 63) {
            return orangeColor;
        } else if (attenuation <= 73) {
            return yellowColor;
        } else {
            return greenColor;
        }
    }

    private void configureDetailsChart(LineChart chartView, List<Entry> dataPointsMinAttenuation, ArrayList<Integer> dotColorsMinAttenuation,
                                       List<Entry> dataPoints, ArrayList<Integer> dotColors,
                                       int minTimestampLocalTZDay0, int maxTimestampLocalTZDay0, Context context) {
        LineDataSet dataSetMin = new LineDataSet(dataPointsMinAttenuation, "Minimum Attenuation"); // add entries to dataSetMin
        dataSetMin.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSetMin.setCircleColors(dotColorsMinAttenuation);
        dataSetMin.setDrawCircleHole(false);
        dataSetMin.setCircleHoleColor(resolveColorAttr(android.R.attr.colorBackgroundFloating, context));
        //dataSetMin.enableDashedLine(0, 1, 0);
        dataSetMin.setColor(lineColor);
        dataSetMin.setDrawValues(false);
        dataSetMin.setHighlightEnabled(false);
        dataSetMin.setCircleRadius(5.0f);
        dataSetMin.setCircleHoleRadius(2.5f);

        LineDataSet dataSetRest = new LineDataSet(dataPoints, "Attenuation"); // add entries to dataSetRest
        dataSetRest.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSetRest.setCircleColors(dotColors);
        dataSetRest.setDrawCircleHole(false);
        dataSetRest.setCircleHoleColor(resolveColorAttr(android.R.attr.colorBackgroundFloating, context));
        dataSetRest.enableDashedLine(0, 1, 0);  // these parameters mean: do not show line
        dataSetRest.setDrawValues(false);
        dataSetRest.setHighlightEnabled(false);
        dataSetRest.setCircleRadius(3.0f);
        dataSetRest.setCircleHoleRadius(1.5f);

        LineData lineData = new LineData(dataSetMin);
        lineData.addDataSet(dataSetRest);
        chartView.setData(lineData);

        String deviceDateFormat = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
        DateFormat dateFormat = new SimpleDateFormat(deviceDateFormat, Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        // UTC because we don't want DateFormat to do additional time zone compensation

        // the labels that should be drawn on the XAxis
        ValueFormatter xAxisFormatter = new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return dateFormat.format(new Date(getMillisFromSeconds((int) value)));
            }
        };
        // the labels that should be drawn on the YAxis
        ValueFormatter yAxisFormatter = new ValueFormatter() {
            @SuppressLint("DefaultLocale")
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                return String.format("%2d", (int) value);
            }
        };

        XAxis xAxis = chartView.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(xAxisFormatter);
        xAxis.setGranularity(60.0f); // minimum axis-step (interval) is 60 seconds
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisMinimum(minTimestampLocalTZDay0-60);
        xAxis.setAxisMaximum(maxTimestampLocalTZDay0+60);
        xAxis.setTextSize(11.0f*this.textScalingFactor);
        xAxis.setTextColor(resolveColorAttr(android.R.attr.textColorPrimary, context));
        chartView.setExtraBottomOffset(3.0f);

        YAxis yAxis = chartView.getAxisLeft();
        yAxis.setGranularity(1.0f); // minimum axis-step (interval) is 1
        yAxis.setGranularityEnabled(true);
        yAxis.setAxisMinimum(0.0f);
        yAxis.setGridColor(ContextCompat.getColor(context, R.color.colorGridLines));
        yAxis.setGridLineWidth(1.0f);
        yAxis.setDrawGridLines(true);
        yAxis.setValueFormatter(yAxisFormatter);
        yAxis.setInverted(true);
        yAxis.setTextColor(resolveColorAttr(android.R.attr.textColorPrimary, context));

        chartView.getAxisRight().setAxisMinimum(0.0f);
        chartView.getAxisRight().setDrawLabels(false);
        chartView.getAxisRight().setDrawGridLines(false);
        chartView.getLegend().setEnabled(false);
        chartView.getDescription().setEnabled(false);
        chartView.setScaleYEnabled(false);
        int span = maxTimestampLocalTZDay0-minTimestampLocalTZDay0;
        float maximumScaleX = span / 700.0f;
        if (maximumScaleX < 1.0f) {
            maximumScaleX = 1.0f;
        }
        //Log.d(TAG, "maximumScaleX: "+maximumScaleX);
        chartView.getViewPortHandler().setMaximumScaleX(maximumScaleX);
        chartView.invalidate(); // refresh
    }

    private String getReportTypeStr(DiagnosisKeysProtos.TemporaryExposureKey.ReportType reportType) {
        switch (reportType) {
            case REVOKED:
                return(mContext.getString(R.string.report_type_revoked));
            case UNKNOWN:
                return(mContext.getString(R.string.report_type_unknown));
            case RECURSIVE:
                return(mContext.getString(R.string.report_type_recursive));
            case SELF_REPORT:
                return(mContext.getString(R.string.report_type_self_report));
            case CONFIRMED_TEST:
                return(mContext.getString(R.string.report_type_confirmed_test));
            case CONFIRMED_CLINICAL_DIAGNOSIS:
                return(mContext.getString(R.string.report_type_clinical_diagnosis));
            default:
                return mContext.getString(R.string.invalid);
        }
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView mTextView1;
        public final TextView mTextView2;
        public final TextView mTextView3;
        public final LineChart mChartView;
        public Pair<DiagnosisKeysProtos.TemporaryExposureKey, MatchEntryContent.GroupedByDkMatchEntries> mMatchEntriesPair;

        public ViewHolder(View view) {
            super(view);
            mTextView1 = view.findViewById(R.id.textView1);
            mTextView2 = view.findViewById(R.id.textView2);
            mTextView3 = view.findViewById(R.id.textView3);
            mChartView = view.findViewById(R.id.chart);
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString() + " '" + mTextView1.getText() + "'";
        }
    }

    public void toggleShowAllScans() {
        this.showAllScans = !this.showAllScans;
        this.notifyDataSetChanged();
    }
}