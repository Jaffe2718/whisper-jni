# Fill from env
$TMP_DIR="tmp-build"
$TARGET_DIR="whisperjni-build"

New-Item -Path $TARGET_DIR -ItemType Directory -Force
cmake -B build -DCMAKE_BUILD_TYPE=Debug "-DCMAKE_INSTALL_PREFIX=$TMP_DIR" -DGGML_CUDA=ON -DGGML_STATIC=ON
cmake --build build --config Debug -j 4
cmake --install build

# Move all DLLs from build dir to prod dir
Move-Item -Path "$TMP_DIR\*.dll" -Destination $TARGET_DIR -Force