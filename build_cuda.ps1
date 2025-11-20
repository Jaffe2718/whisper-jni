# Fill from env
$TMP_DIR="tmp-build"
$TARGET_DIR="whisperjni-build"

New-Item -Path $TARGET_DIR -ItemType Directory -Force
cmake -B build -DCMAKE_BUILD_TYPE=Release "-DCMAKE_INSTALL_PREFIX=$TMP_DIR" -DGGML_CUDA=ON
cmake --build build --config Release
cmake --install build

# Move all DLLs from build dir to prod dir
Move-Item -Path "$TMP_DIR\*.dll" -Destination $TARGET_DIR -Force