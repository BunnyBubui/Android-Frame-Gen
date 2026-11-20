package com.universalframegen.interpolation;

/** Host-JVM JNI signature mirror of the Android Kotlin object. */
public final class NativeInterpolator {
    static { System.loadLibrary("framegen_native"); }
    public native int[] interpolateNative(int[] frameA, int[] frameB, int width, int height,
                                          float position, int blockSize, int searchRadius);
}
