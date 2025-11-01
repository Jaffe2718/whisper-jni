#!/bin/bash
set -e

# Configuration variables
BUILD_DIR="whisperjni-build/android"
JNI_SRC_DIR="src/main/native"
JAR_OUTPUT_DIR="build/libs"
VAD_MODEL="ggml-silero-v5.1.2.bin"
RESOURCES_DIR="src/main/resources"


# Function to prepare build environment
prepare_environment() {
    echo "Preparing environment..."
    
    # Create output directories
    mkdir -p "$BUILD_DIR" "$JAR_OUTPUT_DIR"

    echo "Environment preparation completed"
}

# Function to compile native libraries for Android
compile_native_libraries() {
    echo "Compiling Android native libraries..."

    # Configure CMake parameters
    CMAKE_TOOLCHAIN="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake"
    CMAKE_ARGS="-DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_TOOLCHAIN=clang -DGGML_ARCH=arm64-v8a"
    CMAKE_CFLAGS="-march=armv8.7a"
    CMAKE_CXXFLAGS="-march=armv8.7a"

    # Clean previous build
    rm -rf build CMakeCache.txt CMakeFiles/
    
    # Configure CMake
    cmake -B build -S . \
        -DCMAKE_TOOLCHAIN_FILE="${CMAKE_TOOLCHAIN}" \
        ${CMAKE_ARGS} \
        -DCMAKE_C_FLAGS=${CMAKE_CFLAGS} \
        -DCMAKE_CXX_FLAGS=${CMAKE_CXXFLAGS} \
        -DCMAKE_BUILD_TYPE=Release \
        -DGGML_VULKAN=OFF \
        -DBUILD_SHARED_LIBS=ON
    
    # Build project
    cmake --build build --config Release -j$(nproc)
    
    # Create architecture-specific output directory
    ANDROID_ARCH_DIR="${BUILD_DIR}/arm64-v8a"
    mkdir -p "$ANDROID_ARCH_DIR"
    
    # Copy built libraries to target directory
    find build -name "*.so" -type f -exec cp {} "$ANDROID_ARCH_DIR" ;
    
    # Verify .so files were generated
    if [ -z "$(ls -A "$ANDROID_ARCH_DIR"/*.so 2> /dev/null)" ]; then
        echo "Error: No .so files were generated"
        exit 1
    fi
    
    echo "Android native libraries compilation completed, output location: $ANDROID_ARCH_DIR"
}

# Function to build Java JAR file
build_java_jar() {
    echo "Building Java JAR file..."
    
    # Copy compiled native libraries to resources directory
    ANDROID_RESOURCES_DIR="${RESOURCES_DIR}/android-arm64"
    # clean resources directory
    mkdir -p "$ANDROID_RESOURCES_DIR"
    cp -f "${BUILD_DIR}/arm64-v8a"/*.so "$ANDROID_RESOURCES_DIR/"
    cp models/ggml-silero-v5.1.2.bin "$ANDROID_RESOURCES_DIR/"
    # Build JAR using Gradle
    chmod +x gradlew
    ./gradlew clean build
    
    echo "Java JAR file build completed, output location: $JAR_OUTPUT_DIR"
}

# Main function
main() {
    echo "Starting Android native library and JAR build process..."

    prepare_environment
    compile_native_libraries
    build_java_jar
    
    echo "Build process completed successfully!"
    echo "Android native libraries located at: $BUILD_DIR"
    echo "JAR files located at: $JAR_OUTPUT_DIR"
}

# Execute main function
main
