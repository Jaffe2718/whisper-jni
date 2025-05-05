# build full version with CUDA
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DGGML_CUDA=ON -DGGML_STATIC=ON -DBUILD_SHARED_LIBS=OFF -DCMAKE_CUDA_ARCHITECTURES="61;75;86;89"
cmake --build build --config Release
cmake --install build
rm -r -fo build
mv .\src\main\resources\win-amd64\whisper-jni.dll .\src\main\resources\win-amd64\whisper-jni_full_cuda.dll
