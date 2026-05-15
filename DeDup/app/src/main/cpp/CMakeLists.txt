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
