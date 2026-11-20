package com.universalframegen.diagnostics

import com.universalframegen.interpolation.NativeInterpolator

/** Native Vulkan device/extension enumeration, not proof of buffer import. */
object NativeVulkanProbe {
    private external fun probeNative(): String

    fun report(): String = if (NativeInterpolator.available) {
        runCatching { probeNative() }.getOrElse { "Vulkan probe failed: ${it.javaClass.simpleName}: ${it.message}" }
    } else "Native library did not load"
}
