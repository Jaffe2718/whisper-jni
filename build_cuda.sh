#!/bin/bash
set -xe

# User env required:
# - CUDA Toolkit installed

TMP_DIR=tmp-build
TARGET_DIR=whisperjni-build

build_lib() {

    mkdir -p $TMP_DIR $TARGET_DIR

    # Set up MUSL environment variables
    if [[ -n "$CC" && "$CC" =~ musl ]] || [[ -n "$CXX" && "$CXX" =~ musl ]]; then
        MUSL_CFLAGS="-static-libgcc -static-libstdc++ -fPIC"
        MUSL_LDFLAGS="-L${MUSL_ROOT}/lib -lc -lm -static-libgcc -static-libstdc++"
    fi

    cmake -B build $CMAKE_ARGS \
        -D_GLIBCXX_USE_CXX11_ABI=0 \
        -DCMAKE_C_COMPILER=${CC:-gcc} \
        -DCMAKE_CXX_COMPILER=${CXX:-g++} \
        -DCMAKE_C_FLAGS="${MUSL_CFLAGS}" \
        -DCMAKE_CXX_FLAGS="${MUSL_CFLAGS}" \
        -DCMAKE_SHARED_LINKER_FLAGS="${MUSL_LDFLAGS}" \
        -DCMAKE_INSTALL_PREFIX=$TMP_DIR \
        -DGGML_CUDA=ON
    cmake --build build --config Release
    cmake --install build
    mkdir -p "$TARGET_DIR"

    # copy all .so, .so.1, .so.2 that were installed in $TMP_DIR
    cp -f "$TMP_DIR"/*.so* "$TARGET_DIR"/
    cp -f "$TMP_DIR"/lib/*.so* "$TARGET_DIR"/
    ls "$TARGET_DIR"

    # copy libc.so from musl into $TMP_DIR && rename it as `libc-musl.so` && patchelf
    if [[ -n "$MUSL_LDFLAGS" ]]; then
        MUSL_LIBC_PATH="${MUSL_ROOT}/lib/libc.so"
        cp -f "${MUSL_LIBC_PATH}" "${TARGET_DIR}/libc-musl.so"
        echo "Copied musl libc.so to ${TARGET_DIR}/libc-musl.so"

        for SO_FILE in "${TARGET_DIR}"/*.so*; do
            if [[ -f "$SO_FILE" && -x "$SO_FILE" ]]; then
                if readelf -d "$SO_FILE" | grep -q "NEEDED.*libc\.so"; then
                    patchelf --replace-needed libc.so libc-musl.so "$SO_FILE"
                    patchelf --set-rpath "\$ORIGIN" "$SO_FILE"
                    patchelf --force-rpath "$SO_FILE"
                    echo "Patched $SO_FILE: replaced libc.so with libc-musl.so and set rpath"
                fi
            fi
        done
    fi

    # Rename the optimized variant to libggml.so (overwriting default if needed)
    if [[ -n "$LIB_VARIANT" && -f "$TARGET_DIR/libggml.so" ]]; then
            echo "Overwriting libggml.so with optimized variant: $LIB_VARIANT"
            mv "$TARGET_DIR/libggml.so" "$TARGET_DIR/libggml$LIB_VARIANT.so"
            cp "$TARGET_DIR/libggml$LIB_VARIANT.so" "$TARGET_DIR/libggml.so"
    fi
    rm -rf "$TMP_DIR"
}

LIB_VARIANT="+mf16c+mfma+mavx+mavx2" CMAKE_ARGS="-DGGML_AVX=ON -DGGML_AVX2=ON -DGGML_FMA=ON -DGGML_F16C=ON" build_lib
CMAKE_ARGS="-DGGML_AVX=OFF -DGGML_AVX2=OFF -DGGML_FMA=OFF -DGGML_F16C=OFF" build_lib

# analyze the resulting library
readelf -d "$TARGET_DIR"/libwhisper-jni.so | grep NEEDED