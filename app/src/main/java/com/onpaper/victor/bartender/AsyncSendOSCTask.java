package com.onpaper.victor.bartender;

import android.os.AsyncTask;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

/**
 * Created by cyber on 15/9/2016.
 */
public class AsyncSendOSCTask extends AsyncTask<OSCMessage, Void, Boolean> {
    private final OSCPortOut oscPortOut;

    public AsyncSendOSCTask(BarometerFragment accelerometerFragment, OSCPortOut oscPortOut) {
        this.oscPortOut  = oscPortOut;
    }

    public AsyncSendOSCTask(AccelerometerFragment accelerometerFragment, OSCPortOut oscPortOut) {
        this.oscPortOut  = oscPortOut;
    }

    public AsyncSendOSCTask(MagnetometerFragment magnetometerFragment, OSCPortOut oscPortOut) {
        this.oscPortOut  = oscPortOut;
    }

    public AsyncSendOSCTask(GyroFragment gyroFragment, OSCPortOut oscPortOut) {
        this.oscPortOut = oscPortOut;
    }

    @Override
    protected Boolean doInBackground(OSCMessage... params) {
        try {
            this.oscPortOut.send(params[0]);
            return Boolean.TRUE;
        }
        catch(Exception exp) {
            return Boolean.FALSE;
        }
    }
}
