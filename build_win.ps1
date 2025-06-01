# build full version
#cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DBUILD_SHARED_LIBS=0 -DGGML_STATIC=1 
#cmake --build build --config Release
#cmake --install build
#rm -r -fo build
#mv .\src\main\resources\win-amd64\whisper-jni.dll .\src\main\resources\win-amd64\whisper-jni_full.dll

# build wrapper for external dll version
# Definitely didn't take me at least 50 hours of work to find this
# https://github.com/tdlib/td/issues/2912#issuecomment-2156135946
# Debug always works, but Release requires the fix in the link above (it's present in this fork)
$ver = "Release"
cmake -B build -DCMAKE_INSTALL_PREFIX=src/main/resources/win-amd64 -DGGML_VULKAN=ON -DGGML_STATIC=1 -DCMAKE_BUILD_TYPE=$ver
cmake --build build --config $ver
cmake --install build --config $ver
rm -r -fo build
rm -r -fo src/main/resources/win-amd64/*.lib
#rm -r -fo src/main/resources/win-amd64/whisper.dll
#rm -r -fo src/main/resources/win-amd64/ggml.dll