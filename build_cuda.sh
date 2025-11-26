#!/bin/bash
set -xe

# User env required:
# - CUDA Toolkit installed

build_lib() {
  TMP_DIR=tmp-build
  TARGET_DIR=whisperjni-build
  mkdir -p $TMP_DIR $TARGET_DIR
  cmake -B build $CMAKE_ARGS -DCMAKE_CXX_FLAGS="-std=c++20" -DCMAKE_INSTALL_PREFIX=$TMP_DIR -DGGML_CUDA=ON
  cmake --build build --config Release
  cmake --install build
  mkdir -p "$TARGET_DIR"
  # copy all .so, .so.1, .so.2 that were installed
  cp "$TMP_DIR"/*.so*  "$TARGET_DIR"/
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