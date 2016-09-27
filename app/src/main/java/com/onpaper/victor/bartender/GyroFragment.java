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

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.mikephil.charting.components.YAxis;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Gyro;
import com.onpaper.victor.bartender.help.HelpOption;
import com.onpaper.victor.bartender.help.HelpOptionAdapter;

import java.net.InetAddress;

/**
 * Created by etsai on 8/19/2015.
 */
public class GyroFragment extends ThreeAxisChartFragment {
    private static final float[] GYRAVAILABLE_RANGES= {125.f, 250.f, 500.f, 1000.f, 2000.f};
    private static final float GYRINITIAL_RANGE= 125.f, GYR_ODR= 25.f;
    private static final String GYR_STREAM_KEY= "gyro_stream";

    private String ipAddress = "192.168.100.136";
    private int port = 7474;
    private OSCPortOut oscPortOut = null;

    private Gyro gyroModule= null;
    private int GYRrangeIndex = 0;

    public GyroFragment() {
        super("rotation", R.layout.fragment_sensor_config_spinner,
                R.string.navigation_fragment_gyro, GYR_STREAM_KEY, -GYRINITIAL_RANGE, GYRINITIAL_RANGE, GYR_ODR);
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException {
        gyroModule= mwBoard.getModule(Gyro.class);

        initializeOSC();
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {
        adapter.add(new HelpOption(R.string.config_name_gyro_range, R.string.config_desc_gyro_range));
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final YAxis leftAxis = chart.getAxisLeft();

        ((TextView) view.findViewById(R.id.config_option_title)).setText(R.string.config_name_gyro_range);

        Spinner rotationRangeSelection= (Spinner) view.findViewById(R.id.config_option_spinner);
        rotationRangeSelection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                GYRrangeIndex = position;
                leftAxis.setAxisMaxValue(GYRAVAILABLE_RANGES[GYRrangeIndex]);
                leftAxis.setAxisMinValue(-GYRAVAILABLE_RANGES[GYRrangeIndex]);

                refreshChart(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        ArrayAdapter<CharSequence> spinnerAdapter= ArrayAdapter.createFromResource(getContext(), R.array.values_gyro_range, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rotationRangeSelection.setAdapter(spinnerAdapter);
        rotationRangeSelection.setSelection(GYRrangeIndex);
    }

    @Override
    protected void setup() {
        gyroModule.setOutputDataRate(GYR_ODR);
        gyroModule.setAngularRateRange(GYRAVAILABLE_RANGES[GYRrangeIndex]);

        AsyncOperation<RouteManager> routeManagerResult= gyroModule.routeData().fromAxes().stream(GYR_STREAM_KEY).commit();
        routeManagerResult.onComplete(dataStreamManager);
        routeManagerResult.onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {

                result.subscribe(GYR_STREAM_KEY,  new RouteManager.MessageHandler(){
                    @Override
                    public void process(Message msg) {
                        Log.i("Gyro", "Gyro: " + msg.getData(CartesianFloat.class));
                        sendOSC("/Gyro/ "+msg.getData(CartesianFloat.class).x()+ " "+msg.getData(CartesianFloat.class).y()+" "+msg.getData(CartesianFloat.class).z());

                    }
                })
                ;

                gyroModule.start();
            }
        });


    }

    public void sendOSC(String message) {
        try {
            new AsyncSendOSCTask(this,oscPortOut).execute(new OSCMessage(message));
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
    protected void clean() {
        gyroModule.stop();
    }
}
