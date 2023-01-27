package com.termdo.core;



import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.common.UniModule;

public class Core extends UniModule {
    //register
    @UniJSMethod(uiThread = false)
    public int createTerm(String cmd, int rows, int columns) {
        return JNI.createTerm(cmd, rows, columns);
    }

    @UniJSMethod(uiThread = false)
    public int closeTerm(int fdm) {
        return JNI.closeTerm(fdm);
    }

    @UniJSMethod(uiThread = false)
    public int write(int fdm, String data) {
        return JNI.write(fdm, data);
    }

    @UniJSMethod(uiThread = false)
    public void openRead(final int fdm) {
        final String fdmS = String.valueOf(fdm);
        new ReadThread(fdmS) {
            @Override
            public void run() {
                while (!exitFlag) {
                    Map<String, Object> params = new HashMap<>();
                    String value = JNI.read(fdm);
                    params.put("data", value);
                    Log.d("TermDo", value);
                    mUniSDKInstance.fireGlobalEventCallback(fdmS, params);
                }
            }
        }.start();
    }

    @UniJSMethod(uiThread = false)
    public int closeRead(int fdm) {
        final String fdmS = String.valueOf(fdm);
        ReadThread t = getThreadByName(fdmS);
        if (t == null) return -1;
        t.exitFlag = true;
        return 0;
    }

    @UniJSMethod(uiThread = false)
    public int changeSize(int fdm, int rows, int columns) {
        return JNI.changeSize(fdm, rows, columns);
    }

    //tools
    private ReadThread getThreadByName(String threadName) {
        for (Thread t : ReadThread.getAllStackTraces().keySet()) {
            if (t.getName().equals(threadName)) {
                return (ReadThread) t;
            }
        }
        return null;
    }

    private static class ReadThread extends Thread {
        public volatile boolean exitFlag = false;
        public ReadThread(String fdmS) {
            this.setName(fdmS);
        }
    }
}