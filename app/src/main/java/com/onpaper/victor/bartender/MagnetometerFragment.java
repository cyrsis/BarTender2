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

import android.util.Log;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;
import com.mbientlab.metawear.AsyncOperation;
import com.mbientlab.metawear.Message;
import com.mbientlab.metawear.RouteManager;
import com.mbientlab.metawear.UnsupportedModuleException;
import com.mbientlab.metawear.data.CartesianFloat;
import com.mbientlab.metawear.module.Bmm150Magnetometer;
import com.mbientlab.metawear.module.Bmm150Magnetometer.PowerPreset;
import com.onpaper.victor.bartender.help.HelpOptionAdapter;

import java.net.InetAddress;

/**
 * Created by etsai on 1/12/2016.
 */
public class MagnetometerFragment extends ThreeAxisChartFragment {
    private static final float B_FIELD_RANGE= 250.f, MAG_ODR= 10.f;
    private static final String MAGSTREAM_KEY = "b_field_stream";
    private String ipAddress = "192.168.100.136";
    private int port = 7474;
    private OSCPortOut oscPortOut = null;

    private Bmm150Magnetometer magModule= null;

    public MagnetometerFragment() {
        super("field", R.layout.fragment_sensor,R.string.navigation_fragment_magnetometer,
                MAGSTREAM_KEY, -B_FIELD_RANGE, B_FIELD_RANGE, MAG_ODR);
    }

    @Override
    protected void boardReady() throws UnsupportedModuleException {
        magModule= mwBoard.getModule(Bmm150Magnetometer.class);
    }

    @Override
    protected void fillHelpOptionAdapter(HelpOptionAdapter adapter) {

    }

    @Override
    protected void setup() {
        magModule.setPowerPrsest(PowerPreset.LOW_POWER);

        AsyncOperation<RouteManager> magrouteManagerResult= magModule.routeData().fromBField().stream(MAGSTREAM_KEY).commit();
        magrouteManagerResult.onComplete(dataStreamManager);
        magrouteManagerResult.onComplete(new AsyncOperation.CompletionHandler<RouteManager>() {
            @Override
            public void success(RouteManager result) {

                result.subscribe(MAGSTREAM_KEY, new RouteManager.MessageHandler() {
                            @Override
                            public void process(Message msg) {
                                Log.i("Mag", "Mag : " + msg.getData(CartesianFloat.class));
                                sendOSC("/Mag/ "+msg.getData(CartesianFloat.class).x()+ " "+msg.getData(CartesianFloat.class).y()+" "+msg.getData(CartesianFloat.class).z());

                            }
                });

                magModule.enableBFieldSampling();
                magModule.start();
            }
        });
        initializeOSC();
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

    public void sendOSC(String message) {
        try {
            new AsyncSendOSCTask(this,this.oscPortOut).execute(new OSCMessage(message));
        } catch (Exception exp) {
            Log.i("OSC Mag", "***Cannt send Message "+ exp);
        }
    }

    @Override
    protected void clean() {
        magModule.stop();
        magModule.disableBFieldSampling();
    }
}
