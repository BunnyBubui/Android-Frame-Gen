cmake_minimum_required(VERSION 3.22.1)
project(framegen_native LANGUAGES CXX)

add_library(framegen_core STATIC interpolation_core.cpp)
target_compile_features(framegen_core PUBLIC cxx_std_17)
target_compile_options(framegen_core PRIVATE -Wall -Wextra -Wpedantic)
set_target_properties(framegen_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(framegen_native SHARED framegen_jni.cpp vulkan_probe.cpp)
target_compile_features(framegen_native PRIVATE cxx_std_17)
target_link_libraries(framegen_native PRIVATE framegen_core dl)
