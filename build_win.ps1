# build full version
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DBUILD_SHARED_LIBS=0 -DGGML_STATIC=1 
cmake --build build --config Release
cmake --install build
rm -r -fo build
mv .\src\main\resources\win-amd64\whisper-jni.dll .\src\main\resources\win-amd64\whisper-jni_full.dll

# build full version with CUDA
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DBUILD_SHARED_LIBS=0 -DGGML_STATIC=1 -DGGML_CUDA=ON
cmake --build build --config Release
cmake --install build
rm -r -fo build
mv .\src\main\resources\win-amd64\whisper-jni.dll .\src\main\resources\win-amd64\whisper-jni_full_cuda.dll

# build full version with vulkan
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DBUILD_SHARED_LIBS=0 -DGGML_STATIC=1 -DGGML_VULKAN=ON
cmake --build build --config Release
cmake --install build
rm -r -fo build
mv .\src\main\resources\win-amd64\whisper-jni.dll .\src\main\resources\win-amd64\whisper-jni_full_vulkan.dll

# build wrapper for external dll version
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64
cmake --build build --config Release
cmake --install build
rm -r -fo build
rm -r -fo src/main/resources/win-amd64/*.lib
rm -r -fo src/main/resources/win-amd64/whisper.dll
rm -r -fo src/main/resources/win-amd64/ggml.dll
