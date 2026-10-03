cmake_minimum_required(VERSION 3.22)
project(infill_audio CXX)

# Oboe comes from Maven as a prefab package.
find_package(oboe REQUIRED CONFIG)

add_library(infill_audio SHARED
        engine.cpp
        synth/synth.cpp)

target_include_directories(infill_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(infill_audio oboe::oboe log)
