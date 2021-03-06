/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights
 * granted under the terms of a software license agreement between the user who
 * downloaded the software, his/her employer (which must be your employer) and
 * MbientLab Inc, (the "License").  You may not use this Software unless you
 * agree to abide by the terms of the License which can be found at
 * www.mbientlab.com/terms . The License limits your use, and you acknowledge,
 * that the  Software may not be modified, copied or distributed and can be used
 * solely and exclusively in conjunction with a MbientLab Inc, product.  Other
 * than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this
 * Software and/or its documentation for any purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE
 * PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL
 * MBIENTLAB OR ITS LICENSORS BE LIABLE OR OBLIGATED UNDER CONTRACT, NEGLIGENCE,
 * STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED
 * TO ANY INCIDENTAL, SPECIAL, INDIRECT, PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST
 * PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY
 * DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software,
 * contact MbientLab Inc, at www.mbientlab.com.
 */

package com.onpaper.victor.bartender;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Barometer;
import com.mbientlab.metawear.module.Bme280Barometer;
import com.mbientlab.metawear.module.Bmp280Barometer;
import com.mbientlab.metawear.module.Bmp280Barometer.FilterMode;
import com.mbientlab.metawear.module.Bmp280Barometer.OversamplingMode;
import com.onpaper.victor.bartender.help.HelpOptionAdapter;

import java.io.FileOutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by etsai on 8/22/2015.
 */
public class BarometerFragment extends SensorFragment {
    private static final float BAROMETER_SAMPLE_FREQ = 26.32f, LIGHT_SAMPLE_PERIOD= 1 / BAROMETER_SAMPLE_FREQ;
    private static String PRESSURE_STREAM_KEY= "pressure_stream", ALTITUDE_STREAM_KEY= "altitude";

    private Barometer barometerModule;
    private float altitudeMin, altitudeMax;

    private RouteManager altitudeRouteManager= null;
    private final ArrayList<Entry> altitudeData= new ArrayList<>(), pressureData= new ArrayList<>();

    private String ipAddress = "192.168.100.136";
    private int port = 7474;
    private OSCPortOut oscPortOut = null;

    private class BarometerMessageHandler implements RouteManager.MessageHandler {
        private final ArrayList<Entry> dataEntries;
        private final int setIndex;

        public BarometerMessageHandler(ArrayList<Entry> dataEntries, int setIndex) {
            this.dataEntries= dataEntries;
            this.setIndex= setIndex;
        }
        @Override
        public void process(Message message) {
            final Float pressureValue = message.getData(Float.class);

            LineData data = chart.getData();
            if (dataEntries.size() >= sampleCount) {
                data.addXValue(String.format(Locale.US, "%.2f", sampleCount * LIGHT_SAMPLE_PERIOD));
                sampleCount++;
            }
            data.addEntry(new Entry(pressureValue, sampleCount), setIndex);
        }
    }
    public BarometerFragment() {
        super(R.string.navigation_fragment_barometer, R.layout.fragment_sensor, 80000, 110000);
        altitudeMin= -300;
        altitudeMax= 1500;
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException {
        barometerModule= mwBoard.getModule(Barometer.class);
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {

    }

    @Override
    protected void setup() {
        if (barometerModule instanceof Bmp280Barometer) {
            ((Bmp280Barometer) barometerModule).configure()
                    .setPressureOversampling(OversamplingMode.ULTRA_HIGH)
                    .setFilterMode(FilterMode.OFF)
                    .setStandbyTime(Bmp280Barometer.StandbyTime.TIME_0_5)
                    .commit();
            ((Bmp280Barometer) barometerModule).enableAltitudeSampling();
        } else if (barometerModule instanceof Bme280Barometer) {
            ((Bme280Barometer) barometerModule).configure()
                    .setPressureOversampling(OversamplingMode.ULTRA_HIGH)
                    .setFilterMode(FilterMode.OFF)
                    .setStandbyTime(Bme280Barometer.StandbyTime.TIME_0_5)
                    .commit();
            ((Bme280Barometer) barometerModule).enableAltitudeSampling();
        }
        barometerModule.routeData().fromPressure().stream(PRESSURE_STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        streamRouteManager= result;
                        result.subscribe(PRESSURE_STREAM_KEY, new BarometerMessageHandler(pressureData, 0){
                            @Override
                            public void process(Message msg) {
                                Log.i("Bar", "Bar: " + msg.getData(Float.class));
                                sendOSC("/Bar/ "+msg.getData(Float.class));

                            }
                        })
                        ;

                    }
                });
        barometerModule.routeData().fromAltitude().stream(ALTITUDE_STREAM_KEY).commit()
                .onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
                    @Override
                    public void success(RouteManager result) {
                        altitudeRouteManager= result;
                        result.subscribe(ALTITUDE_STREAM_KEY, new BarometerMessageHandler(altitudeData, 1));

                        barometerModule.start();
                    }
                });

        initializeOSC();
    }
    public void sendOSC(String message) {
        try {
            new AsyncSendOSCTask(this,this.oscPortOut).execute(new OSCMessage(message));
        } catch (Exception exp) {
            Log.i("test", "Cannt send Message "+ exp);
        }
    }
    private void initializeOSC() {
        try {

            if(oscPortOut != null) {
                oscPortOut.close();
            }

            oscPortOut = new OSCPortOut(InetAddress.getByName(ipAddress), port);
        }
        catch(Exception exp) {
            Log.i("OSC Port Error" ,"Cannt make the port");
            oscPortOut = null;
        }
    }

    @Override
    protected void initializeChart() {
        ///< configure axis settings
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setStartAtZero(false);
        leftAxis.setAxisMaxValue(max);
        leftAxis.setAxisMinValue(min);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setStartAtZero(false);
        rightAxis.setAxisMaxValue(altitudeMax);
        rightAxis.setAxisMinValue(altitudeMin);
    }

    @Override
    protected void clean() {
        barometerModule.stop();

        if (altitudeRouteManager != null) {
            altitudeRouteManager.remove();
            altitudeRouteManager= null;
        }
    }

    @Override
    protected String saveData() {
        final String CSV_HEADER = String.format("time,pressure,altitude%n");
        String filename = String.format(Locale.US, "%s_%tY%<tm%<td-%<tH%<tM%<tS%<tL.csv", getContext().getString(sensorResId), Calendar.getInstance());

        try {
            FileOutputStream fos = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(CSV_HEADER.getBytes());

            LineData data = chart.getLineData();
            LineDataSet pressureDataSet = data.getDataSetByIndex(0), altitudeDataSet = data.getDataSetByIndex(1);
            for (int i = 0; i < data.getXValCount(); i++) {
                fos.write(String.format(Locale.US, "%.3f,%.3f,%.3f%n", i * LIGHT_SAMPLE_PERIOD,
                        pressureDataSet.getEntryForXIndex(i).getVal(),
                        altitudeDataSet.getEntryForXIndex(i).getVal()).getBytes());
            }
            fos.close();
            return filename;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void resetData(boolean clearData) {
        if (clearData) {
            sampleCount = 0;
            chartXValues.clear();
            altitudeData.clear();
            pressureData.clear();
        }

        ArrayList<LineDataSet> spinAxisData= new ArrayList<>();
        spinAxisData.add(new LineDataSet(pressureData, "pressure"));
        spinAxisData.get(0).setAxisDependency(YAxis.AxisDependency.LEFT);
        spinAxisData.get(0).setColor(Color.RED);
        spinAxisData.get(0).setDrawCircles(false);

        spinAxisData.add(new LineDataSet(altitudeData, "altitude"));
        spinAxisData.get(1).setAxisDependency(YAxis.AxisDependency.RIGHT);
        spinAxisData.get(1).setColor(Color.GREEN);
        spinAxisData.get(1).setDrawCircles(false);

        LineData data= new LineData(chartXValues);
        for(LineDataSet set: spinAxisData) {
            data.addDataSet(set);
        }
        data.setDrawValues(false);
        chart.setData(data);
    }
}
