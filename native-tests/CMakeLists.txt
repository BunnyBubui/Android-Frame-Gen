cmake_minimum_required(VERSION 3.16)
project(framegen_core_tests LANGUAGES CXX)
enable_testing()
add_executable(framegen_core_tests
    test_interpolation.cpp
    ../app/src/main/cpp/interpolation_core.cpp)
target_include_directories(framegen_core_tests PRIVATE ../app/src/main/cpp)
target_compile_features(framegen_core_tests PRIVATE cxx_std_17)
target_compile_options(framegen_core_tests PRIVATE -Wall -Wextra -Wpedantic -Werror)
add_test(NAME framegen_core_tests COMMAND framegen_core_tests)
