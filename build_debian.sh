#!/bin/bash
set -xe
build_lib() {
  TMP_DIR=src/main/resources/linux-build
  TARGET_DIR=src/main/resources/linux-"$OUT_ARCH"
  
  # Define Vulkan as an environment variable
  export VULKAN_ARG=${VULKAN_ARG:-OFF} # set through CI/CD
  
  cmake -B build $CMAKE_ARGS -DCMAKE_C_FLAGS="$CMAKE_CFLAGS" -DCMAKE_INSTALL_PREFIX=$TMP_DIR -DGGML_VULKAN=${VULKAN_ARG}
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

# ------------------------- architecture map ------------------------
RAW_ARCH=$(dpkg --print-architecture)

case "$RAW_ARCH" in
  amd64) OUT_ARCH="x64" ;;
  arm64) OUT_ARCH="aarch64" ;;
  armhf|armv7l) OUT_ARCH="armv7l" ;;
  *)      OUT_ARCH="$RAW_ARCH" ;;        # fallback: use raw value
esac


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
