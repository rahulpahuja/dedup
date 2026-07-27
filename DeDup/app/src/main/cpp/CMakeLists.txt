cmake_minimum_required(VERSION 3.22.1)

project("dedup-native")

add_library(
    dedup-native
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    dedup-native
    ${log-lib}
)

# Align ELF PT_LOAD segments to 16 KB so the library works on devices with
# 16 KB page sizes (required for Android 15+ devices with new kernels).
target_link_options(dedup-native PRIVATE "-Wl,-z,max-page-size=16384")

# Inject secrets from Gradle into the native binary (not in DEX/BuildConfig)
target_compile_definitions(dedup-native PRIVATE
    "GOOGLE_WEB_CLIENT_ID=\"${GOOGLE_WEB_CLIENT_ID}\""
    "FACEBOOK_APP_ID=\"${FACEBOOK_APP_ID}\""
    "FACEBOOK_CLIENT_TOKEN=\"${FACEBOOK_CLIENT_TOKEN}\""
)
