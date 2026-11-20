package com.universalframegen.interpolation

/**
 * CPU-only software reference for a synthetic comparison demo.
 * This is not the GPU FrameGen pipeline, and is not an Android game-FPS modifier.
 */
object NativeInterpolator {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("framegen_native") }.isSuccess
    }

    external fun interpolateNative(
        frameA: IntArray,
        frameB: IntArray,
        width: Int,
        height: Int,
        position: Float,
        blockSize: Int,
        searchRadius: Int
    ): IntArray

    fun interpolate(frameA: IntArray, frameB: IntArray, width: Int, height: Int, t: Float): IntArray {
        check(available) { "Native framegen library unavailable" }
        require(frameA.size == width * height && frameB.size == width * height)
        return interpolateNative(frameA, frameB, width, height, t, 8, 8)
    }
}
