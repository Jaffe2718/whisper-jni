# Fill from env
$TMP_DIR="tmp-build"
$TARGET_DIR="whisperjni-build"

New-Item -Path $TARGET_DIR -ItemType Directory -Force
cmake -B build -DCMAKE_BUILD_TYPE=Release "-DCMAKE_INSTALL_PREFIX=$TMP_DIR" -DGGML_CUDA=ON -DGGML_STATIC=ON
cmake --build build --config Release -j 4
cmake --install build --config Release

# Move all DLLs from build dir to prod dir
Move-Item -Path "$TMP_DIR\*.dll" -Destination $TARGET_DIR -Force


# TODO: engineering compromise
# Due to the some incompatibility between ggml-cuda.dll in RELEASE mode and JNI program,
# we copy the ggml-cuda.dll in DEBUG mode to the prod dir.

# clean build dir & tmp dir
Remove-Item -Path build -Recurse -Force
Remove-Item -Path $TMP_DIR -Recurse -Force

cmake -B build -DCMAKE_BUILD_TYPE=Debug "-DCMAKE_INSTALL_PREFIX=$TMP_DIR" -DGGML_CUDA=ON -DGGML_STATIC=ON
cmake --build build --config Debug -j 4
cmake --install build --config Debug

# Move ggml-cuda.dll from build dir to prod dir
Move-Item -Path "$TMP_DIR\ggml-cuda.dll" -Destination $TARGET_DIR -Force
