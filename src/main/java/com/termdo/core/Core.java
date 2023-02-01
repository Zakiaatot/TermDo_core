package com.termdo.core;


//import android.util.Log;


import java.util.HashMap;
import java.util.Map;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.common.UniModule;

public class Core extends UniModule {
    //receive key
    final String recKey = "data";

    //global map
    Map<Integer, Integer> fdmAndProcessId = new HashMap<>();

    //key_flag
    //boolean ctrl_key_flag = false;
    //boolean alt_key_flag = false;
    //已被js实现

    //register
    @UniJSMethod(uiThread = false)
    public int createTerm(String cmd, int rows, int columns, String cwd, String[] args, String[] envVars) {
        final int[] array = JNI.createTerm(cmd, rows, columns, cwd, args, envVars);
        fdmAndProcessId.put(array[0], array[1]);
        new Thread() {
            @Override
            public void run() {
                int code = JNI.waitTermExit(array[1]);//阻塞 等待进程结束
                closeTerm(array[0]);//关闭终端
                //广播最后一条消息
                Map<String, Object> params = new HashMap<>();
                params.put(recKey, "\n\rGoodBye!\n\rexit with code:" + String.valueOf(code) + "\n\r");
                mUniSDKInstance.fireGlobalEventCallback(String.valueOf(array[0]), params);
            }
        }.start();
        return array[0];
    }

    @UniJSMethod(uiThread = false)
    public void closeTerm(final int fdm) {
        if (!fdmAndProcessId.containsKey(fdm)) return;
        closeRead(fdm);
        JNI.closeFdm(fdm);
        JNI.closeTerm(fdmAndProcessId.get(fdm));
    }

    @UniJSMethod(uiThread = false)
    public int write(int fdm, String data) {
        return JNI.write(fdm, data);
    }

    @UniJSMethod(uiThread = false)
    public void openRead(final int fdm) {
        new ReadThread(String.valueOf(fdm)) {
            @Override
            public void run() {
                while (!exitFlag) {
                    Map<String, Object> params = new HashMap<>();
                    String value = JNI.read(fdm);
                    if (!value.isEmpty()) {
                        params.put(recKey, value);
//                        Log.d("TermDo-j", "get: " + value);
                        mUniSDKInstance.fireGlobalEventCallback(this.getName(), params);
                    }
                    try {
                        sleep(50); //50ms 读一次
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
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

    //已被js实现
    //ctrl_key and alt_key
//    @UniJSMethod(uiThread = false)
//    public void handleKeys(final String keyName, final boolean option) {
//        //Instrumentation 需要线程
//        new Thread() {
//            @Override
//            public void run() {
//                try {
//                    Instrumentation inst = new Instrumentation();
//
//                    if (option) {  //down
//                        if (keyName == "ctrl") {
//                            inst.sendKeySync(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 26, 0));
//                            ctrl_key_flag = true;
//                        } else {
//                            inst.sendKeySync(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, 18, 0));
//                            alt_key_flag = true;
//                        }
//                    } else {   //up
//                        if (keyName == "ctrl") {
//                            inst.sendKeySync(new KeyEvent(0, 0, KeyEvent.ACTION_UP, 17, 0));
//                            ctrl_key_flag = false;
//                        } else {
//                            inst.sendKeySync(new KeyEvent(0, 0, KeyEvent.ACTION_UP, 18, 0));
//                            alt_key_flag = false;
//                        }
//                    }
//                    Log.d("TermDo-j", keyName + " op:" + option);
//                } catch (Exception e) {
//                    // TODO: handle exception
//                    Log.d("TermDo-j", e.getMessage());
//                }
//            }
//        }.start();
//
//    }
//
//    @UniJSMethod(uiThread = false)
//    public boolean getKeyStatus(String keyName) {
//        if (keyName == "ctrl") return ctrl_key_flag;
//        return alt_key_flag;
//    }

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