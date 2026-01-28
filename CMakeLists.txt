cmake_minimum_required(VERSION 3.21)

project(whisper-jni C CXX)

# ---- Set options ----

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(GGML_AVX                 "ggml: enable AVX"                ON )
option(GGML_AVX2                "ggml: enable AVX2"               ON )
option(GGML_FMA                 "ggml: enable FMA"                ON )
option(GGML_F16C                "ggml: enable F16c"               ON )
option(GGML_STATIC              "ggml: static link libraries"     OFF)
option(GGML_NATIVE              "ggml: enable -march=native flag" OFF)
option(BUILD_SHARED_LIBS        "whisper: build shared libs"      ON )
option(GGML_CUDA                "ggml: enable CUDA backend"       OFF)
option(GGML_VULKAN              "ggml: enable Vulkan backend"     OFF)
option(GGML_BLAS                "ggml: BLAS CPU support"          OFF)

set(JAVA_AWT_LIBRARY NotNeeded)
set(JAVA_JVM_LIBRARY NotNeeded)

find_package(JNI REQUIRED)
if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
endif()

# Base C and CXX flags, start from current variables
set(EXTERNAL_C_FLAGS ${CMAKE_C_FLAGS})
set(EXTERNAL_CXX_FLAGS ${CMAKE_CXX_FLAGS})

if(MSVC)
  # Append /MD and the define to flags for MSVC
  string(APPEND EXTERNAL_C_FLAGS " /MD /D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR")
  string(APPEND EXTERNAL_CXX_FLAGS " /MD /D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR")
else()
  # For other compilers, just add the macro define
  string(APPEND EXTERNAL_C_FLAGS " -D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR")
  string(APPEND EXTERNAL_CXX_FLAGS " -D_DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR")
endif()

if(MSVC)
  # Force compiler to use UTF-8 for IPA constants
  add_compile_options("$<$<C_COMPILER_ID:MSVC>:/utf-8>")
  add_compile_options("$<$<CXX_COMPILER_ID:MSVC>:/utf-8>")
elseif(NOT APPLE)
  string(APPEND CMAKE_CXX_FLAGS " -Wall -Wextra -Wl,-rpath,'$ORIGIN'")
  string(APPEND CMAKE_C_FLAGS " -Wall -Wextra")
  set(CMAKE_INSTALL_RPATH '$ORIGIN')
elseif(APPLE)
  set(CMAKE_INSTALL_RPATH "@loader_path")
endif()

# ---- Declare libraries ----

add_subdirectory(src/main/native/whisper)

add_library(whisper-jni SHARED
  src/main/native/io_github_jaffe2718_whisperjni_WhisperJNI.cpp
  # Needed
  src/main/native/whisper/examples/grammar-parser.cpp
)

# This is required to make whisper's release build work (particularly on Windows, unsure about other platforms)
target_compile_definitions(whisper-jni PRIVATE _DISABLE_CONSTEXPR_MUTEX_CONSTRUCTOR)

if((NOT MSVC) AND (NOT APPLE))
  # Linux flags
  string(APPEND CMAKE_CXX_FLAGS " -Wall -Wextra -Wl,-rpath,'$ORIGIN'")
  string(APPEND CMAKE_C_FLAGS " -Wall -Wextra")
endif()

if(GGML_VULKAN)
  message(STATUS "GGML_VULKAN is enabled")
  message(STATUS "Vulkan SDK path: $ENV{VULKAN_SDK}")
  
  # Find the Vulkan package
  find_package(Vulkan REQUIRED)
  
  # Check that Vulkan was found
  if(Vulkan_FOUND)
    message(STATUS "Found Vulkan SDK at: ${Vulkan_INCLUDE_DIRS}")
    message(STATUS "Found Vulkan library: ${Vulkan_LIBRARIES}")

    # Link Vulkan to your target
    target_link_libraries(whisper-jni PRIVATE whisper ggml ggml-base ggml-cpu Vulkan::Vulkan)

    # Optionally include Vulkan directories if needed
    # ... is this required? probably
    target_include_directories(whisper-jni PRIVATE ${Vulkan_INCLUDE_DIRS})
  else()
    message(FATAL_ERROR "Vulkan SDK not found. Please ensure it is installed correctly.")
  endif()
elseif(GGML_CUDA)
    target_compile_definitions(whisper-jni PUBLIC GGML_USE_CUDA)
    target_link_libraries(whisper-jni PRIVATE whisper ggml ggml-base ggml-cpu ggml-cuda)
else()
	target_link_libraries(whisper-jni PRIVATE whisper ggml ggml-base ggml-cpu)
endif()


target_include_directories(whisper-jni PRIVATE
  ${JNI_INCLUDE_DIRS}
  src/main/native
  src/main/native/whisper/include
  # For grammar-parser.h. We include this in our cpp code
  src/main/native/whisper/examples
)

# ---- Declare install targets ----

install(
  TARGETS whisper-jni
  DESTINATION ${CMAKE_INSTALL_PREFIX}
)

install(
  DIRECTORY ${CMAKE_BINARY_DIR}/lib/
  DESTINATION ${CMAKE_INSTALL_PREFIX}
  USE_SOURCE_PERMISSIONS
  FILES_MATCHING
  PATTERN "*.so.*"
  PATTERN "*.so"
  PATTERN "*.dylib"
)

install(
  DIRECTORY ${CMAKE_BINARY_DIR}/bin/
  DESTINATION ${CMAKE_INSTALL_PREFIX}
  USE_SOURCE_PERMISSIONS
  FILES_MATCHING
  PATTERN "*.dll"
)
