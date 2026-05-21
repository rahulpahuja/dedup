#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFirebaseDbUrl(JNIEnv* env, jobject /* this */) {
    // This URL is safe to keep here or move to BuildConfig.
    // It's obfuscated in the native binary.
    return env->NewStringUTF("https://dedup-7dd7b.firebasestorage.app");
}
