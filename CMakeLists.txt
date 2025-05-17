cmake_minimum_required(VERSION 3.5)
project(whisper-jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)
if (JNI_FOUND)
    message(STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
endif()

# Paths to your custom built whisper.cpp
set(WHISPER_CPP_DIR "src/main/native/whisper")
set(WHISPER_INCLUDE_DIR "${WHISPER_CPP_DIR}/include")
set(GGML_INCLUDE_DIR "${WHISPER_CPP_DIR}/ggml/include")

set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} /MD")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MD")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} /MDd")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} /MDd")

# JNI shared library
add_library(whisper-jni SHARED
    src/main/native/io_github_givimad_whisperjni_WhisperJNI.cpp
    src/main/native/whisper/examples/grammar-parser.cpp
)

# Include headers
target_include_directories(whisper-jni PUBLIC
    ${JNI_INCLUDE_DIRS}
    src/main/native
    ${WHISPER_INCLUDE_DIR}
    ${GGML_INCLUDE_DIR}
    "${WHISPER_CPP_DIR}/examples"
)

# Define full path to built .lib files
set(WHISPER_LIB "C:\\Users\\Sullbeans\\eclipse-workspace\\whisper-jni\\src\\main\\native\\whisper\\build\\src\\Debug\\whisper.lib")
set(GGML_LIB "C:\\Users\\Sullbeans\\eclipse-workspace\\whisper-jni\\src\\main\\native\\whisper\\build\\ggml\\src\\Debug\\ggml.lib")

add_library(whisper STATIC IMPORTED)
set_target_properties(whisper PROPERTIES IMPORTED_LOCATION "${WHISPER_LIB}")

add_library(ggml STATIC IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION "${GGML_LIB}")

target_link_libraries(whisper-jni whisper ggml)

# Optional: installation rule
install(TARGETS whisper-jni DESTINATION ${CMAKE_INSTALL_PREFIX})
