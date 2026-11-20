#include <jni.h>
#include <vector>
#include <stdexcept>
#include "interpolation_core.h"

extern "C" JNIEXPORT jintArray JNICALL
Java_com_universalframegen_interpolation_NativeInterpolator_interpolateNative(
    JNIEnv* env, jobject /* self */, jintArray a, jintArray b,
    jint width, jint height, jfloat t, jint block, jint search) {
    const jsize na = a ? env->GetArrayLength(a) : 0;
    const jsize nb = b ? env->GetArrayLength(b) : 0;
    const int64_t count = static_cast<int64_t>(width) * height;
    if (width <= 0 || height <= 0 || count > 1920LL * 1080 || na != count || nb != count) {
        jclass err = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(err, "Frame dimensions and input arrays differ");
        return nullptr;
    }
    std::vector<jint> pixels_a(static_cast<size_t>(count));
    std::vector<jint> pixels_b(static_cast<size_t>(count));
    env->GetIntArrayRegion(a, 0, na, pixels_a.data());
    env->GetIntArrayRegion(b, 0, nb, pixels_b.data());
    if (env->ExceptionCheck()) return nullptr;
    try {
        const auto result = framegen::interpolate(
            reinterpret_cast<const uint32_t*>(pixels_a.data()),
            reinterpret_cast<const uint32_t*>(pixels_b.data()),
            width, height, t, block, search);
        jintArray output = env->NewIntArray(static_cast<jsize>(result.size()));
        if (!output) return nullptr;
        env->SetIntArrayRegion(output, 0, static_cast<jsize>(result.size()),
                               reinterpret_cast<const jint*>(result.data()));
        return output;
    } catch (const std::exception& e) {
        jclass err = env->FindClass("java/lang/IllegalArgumentException");
        env->ThrowNew(err, e.what());
        return nullptr;
    }
}
