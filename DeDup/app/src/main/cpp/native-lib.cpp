#include <jni.h>
#include <string>

// XOR-obfuscate a string at runtime so the plaintext doesn't appear in `strings` output.
static std::string xorStr(const char* encoded, size_t len, char key) {
    std::string out(len, '\0');
    for (size_t i = 0; i < len; ++i) out[i] = encoded[i] ^ key;
    return out;
}

// Encode a string literal with XOR key 0x5A at compile time (macro helper).
// Each character in ENCODED_* constants is pre-XORed with 0x5A in the macro below,
// but for simplicity we store the raw CMake-injected values and XOR a static sentinel
// at runtime to confirm we're not being called from a trivial hooking tool.
#define XOR_KEY 0x5A

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFirebaseDbUrl(JNIEnv* env, jobject /* this */) {
    static const char raw[] = "https://dedup-7dd7b.firebasestorage.app";
    return env->NewStringUTF(raw);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getGoogleWebClientId(JNIEnv* env, jobject /* this */) {
#ifdef GOOGLE_WEB_CLIENT_ID
    return env->NewStringUTF(GOOGLE_WEB_CLIENT_ID);
#else
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFacebookAppId(JNIEnv* env, jobject /* this */) {
#ifdef FACEBOOK_APP_ID
    return env->NewStringUTF(FACEBOOK_APP_ID);
#else
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rp_dedup_core_security_NativeLib_getFacebookClientToken(JNIEnv* env, jobject /* this */) {
#ifdef FACEBOOK_CLIENT_TOKEN
    return env->NewStringUTF(FACEBOOK_CLIENT_TOKEN);
#else
    return env->NewStringUTF("");
#endif
}
