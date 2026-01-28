#!/bin/bash
set -xe
build_lib() {
    TMP_DIR=tmp-build
    TARGET_DIR=whisperjni-build

    mkdir -p $TMP_DIR $TARGET_DIR

    # Define Vulkan as an environment variable
    VULKAN_ARG=${VULKAN:-OFF} # set through CI/CD

    cmake -B build $CMAKE_ARGS \
        -DCMAKE_C_COMPILER=musl-gcc \
        -DCMAKE_CXX_COMPILER=musl-g++ \
        -DCMAKE_C_FLAGS="$CMAKE_CFLAGS" \
        -DCMAKE_CXX_FLAGS="-std=c++20" \
        -DCMAKE_SHARED_LINKER_FLAGS="-static-libgcc -static-libstdc++" \
        -DCMAKE_INSTALL_PREFIX=$TMP_DIR \
        -DGGML_VULKAN=${VULKAN_ARG}
    cmake --build build --config Release
    cmake --install build
    mkdir -p "$TARGET_DIR"
    # copy all .so, .so.1, .so.2 that were installed
    cp "$TMP_DIR"/*.so*    "$TARGET_DIR"/
    # Rename the optimized variant to libggml.so (overwriting default if needed)
    if [[ -n "$LIB_VARIANT" && -f "$TARGET_DIR/libggml.so" ]]; then
            echo "Overwriting libggml.so with optimized variant: $LIB_VARIANT"
            mv "$TARGET_DIR/libggml.so" "$TARGET_DIR/libggml$LIB_VARIANT.so"
            cp "$TARGET_DIR/libggml$LIB_VARIANT.so" "$TARGET_DIR/libggml.so"
    fi
    rm -rf "$TMP_DIR"
}

# We aren't building for armv7l (at least right now) but functionality is still here
AARCH=$(dpkg --print-architecture)
case $AARCH in
    amd64)
        LIB_VARIANT="+mf16c+mfma+mavx+mavx2" CMAKE_ARGS="-DGGML_AVX=ON -DGGML_AVX2=ON -DGGML_FMA=ON -DGGML_F16C=ON" build_lib
        ADD_WRAPPER=true CMAKE_ARGS="-DGGML_AVX=OFF -DGGML_AVX2=OFF -DGGML_FMA=OFF -DGGML_F16C=OFF" build_lib
        ;;
    arm64)
        LIB_VARIANT="+fp16" CMAKE_CFLAGS="-march=armv8.2-a+fp16" build_lib
        ADD_WRAPPER=true LIB_VARIANT="+crc" CMAKE_CFLAGS="-march=armv8.1-a+crc" build_lib
        ;;
    armhf|armv7l)
        AARCH=armv7l
        LIB_VARIANT="+crc" CMAKE_CFLAGS="-march=armv8-a+crc -mfpu=neon-fp-armv8 -mfp16-format=ieee -mno-unaligned-access" build_lib
        ADD_WRAPPER=true CMAKE_CFLAGS="-mfpu=neon -mfp16-format=ieee -mno-unaligned-access" build_lib
        ;;
esac
