#include <jni.h>
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <algorithm>
#include <sstream>
#include <string>
#include <vector>

// Runtime capability check ONLY. It does not import AHardwareBuffer, create a
// presentation swapchain or execute interpolation on the GPU.
namespace {
std::string probeVulkan() {
    void* lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) return "libvulkan.so unavailable";
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(lib, "vkGetInstanceProcAddr"));
    if (!get) { dlclose(lib); return "vkGetInstanceProcAddr unavailable"; }
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE, "vkCreateInstance"));
    auto enumerateExt = reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(
        get(VK_NULL_HANDLE, "vkEnumerateInstanceExtensionProperties"));
    if (!create || !enumerateExt) {
        dlclose(lib);
        return "Vulkan instance functions unavailable";
    }

    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "UniversalFrameGen Probe";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &app;
    VkInstance instance = VK_NULL_HANDLE;
    const VkResult result = create(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        dlclose(lib);
        return "vkCreateInstance failed: " + std::to_string(result);
    }

    auto devicesFn = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
        get(instance, "vkEnumeratePhysicalDevices"));
    auto propsFn = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
        get(instance, "vkGetPhysicalDeviceProperties"));
    auto extFn = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
        get(instance, "vkEnumerateDeviceExtensionProperties"));
    auto destroy = reinterpret_cast<PFN_vkDestroyInstance>(get(instance, "vkDestroyInstance"));
    if (!destroy || !devicesFn || !propsFn || !extFn) {
        if (destroy) destroy(instance, nullptr);
        dlclose(lib);
        return "Required Vulkan device entrypoints unavailable";
    }
    std::ostringstream info;
    uint32_t count = 0;
    VkResult countResult = devicesFn(instance, &count, nullptr);
    if (countResult != VK_SUCCESS || count == 0 || count > 16) {
        info << "Vulkan device enumeration failed or returned 0 devices (" << countResult << ")";
    } else {
        std::vector<VkPhysicalDevice> devices(count);
        if (devicesFn(instance, &count, devices.data()) == VK_SUCCESS) {
            for (uint32_t i = 0; i < count; ++i) {
                VkPhysicalDeviceProperties props{};
                propsFn(devices[i], &props);
                const uint32_t version = props.apiVersion;
                uint32_t extCount = 0;
                bool ahb = false;
                if (extFn(devices[i], nullptr, &extCount, nullptr) == VK_SUCCESS && extCount < 4096) {
                    std::vector<VkExtensionProperties> extensions(extCount);
                    if (extFn(devices[i], nullptr, &extCount, extensions.data()) == VK_SUCCESS) {
                        for (const auto& ext : extensions) {
                            if (std::string(ext.extensionName) ==
                                "VK_ANDROID_external_memory_android_hardware_buffer") ahb = true;
                        }
                    }
                }
                if (i) info << "\n";
                info << props.deviceName << " | Vulkan " << VK_VERSION_MAJOR(version) << "."
                     << VK_VERSION_MINOR(version) << "." << VK_VERSION_PATCH(version)
                     << " | AHB external memory extension: " << (ahb ? "advertised" : "not advertised");
            }
        } else {
            info << "Unable to query physical devices";
        }
    }
    destroy(instance, nullptr);
    dlclose(lib);
    return info.str();
}
} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_universalframegen_diagnostics_NativeVulkanProbe_probeNative(JNIEnv* env, jobject) {
    const std::string report = probeVulkan();
    return env->NewStringUTF(report.c_str());
}
