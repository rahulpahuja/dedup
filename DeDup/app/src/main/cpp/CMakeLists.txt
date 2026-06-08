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

# Inject secrets from Gradle into the native binary (not in DEX/BuildConfig)
target_compile_definitions(dedup-native PRIVATE
    "GOOGLE_WEB_CLIENT_ID=\"${GOOGLE_WEB_CLIENT_ID}\""
    "FACEBOOK_APP_ID=\"${FACEBOOK_APP_ID}\""
    "FACEBOOK_CLIENT_TOKEN=\"${FACEBOOK_CLIENT_TOKEN}\""
)
