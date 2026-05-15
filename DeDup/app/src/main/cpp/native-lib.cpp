#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getGoogleWebClientId(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("300582514488-0se0sjakfc04r7rfegpm8h0jnd7t7luh.apps.googleusercontent.com");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFacebookAppId(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("2079982969241932");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFacebookClientToken(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("b97bc53e216db1ec8788e054d8261bfb");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFirebaseDbUrl(JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("https://dedup-7dd7b.firebasestorage.app");
}
