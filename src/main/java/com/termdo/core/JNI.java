package com.termdo.core;

public final class JNI {
    static {
        System.loadLibrary("TermDo");
    }


    public static native int createTerm(String cmd, int rows, int columns);

    public static native int closeTerm(int fdm);

    public static native int write(int fdm, String data);

    public static native String read(int fdm);

    public static native int changeSize(int fdm, int rows, int columns);

}
