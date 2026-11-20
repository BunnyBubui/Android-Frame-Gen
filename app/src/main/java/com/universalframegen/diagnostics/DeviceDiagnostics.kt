package com.universalframegen.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Device feature declarations are estimates: a real Vulkan rendering test comes later. */
data class DeviceReport(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val soc: String,
    val vulkanApi: String,
    val vulkanLevel: String,
    val vulkanAdvertised: Boolean,
    val refreshRates: String
)

object DeviceDiagnostics {
    fun scan(context: Context): DeviceReport {
        val pm = context.packageManager
        val features = pm.systemAvailableFeatures.toList()
        fun featureVersion(name: String): Int? =
            features.firstOrNull { it.name == name }?.version
        val v = featureVersion(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        val level = featureVersion(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val display = context.display
        val modes = display?.supportedModes?.map { "%.0f Hz".format(it.refreshRate) }
            ?.distinct()?.joinToString(" / ") ?: "Unknown"
        return DeviceReport(
            model = "${Build.BRAND} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            soc = "${Build.SOC_MANUFACTURER} · ${Build.SOC_MODEL}",
            vulkanApi = v?.let { "${it ushr 22}.${(it ushr 12) and 0x3ff}.${it and 0xfff}" } ?: "Not advertised",
            vulkanLevel = level?.toString() ?: "Not advertised",
            vulkanAdvertised = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION),
            refreshRates = modes
        )
    }
}
