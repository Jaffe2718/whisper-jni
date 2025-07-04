# THIS IS MEANT TO BE RUN IN THE ROOT OF THE REPO, WHERE YOU INIT SUBMODULE FIRST
# ... since whisper JNI builds whisper anyways this top part of this script might not be necessary anymore
$buildDir = "src/main/resources/windows-x86-64-vulkan"
mkdir $buildDir -Force
#rm -r -fo .\src\main\resources\win-vulkan-x64\whisper-jni.dll

echo $buildDir
echo "Building JNI"

# Build whisper-jni
cmake -B build "-DCMAKE_INSTALL_PREFIX=$buildDir" -DGGML_VULKAN=ON -DGGML_STATIC=1 -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
cmake --install build --config Release

# move DLLs from whisper vulkan build to win-vulkan-x64-build
Copy-Item -Path src\main\native\whisper\build\bin\Release\ggml.dll -Destination $buildDir -Force
Copy-Item -Path src\main\native\whisper\build\bin\Release\ggml-base.dll -Destination $buildDir -Force
Copy-Item -Path src\main\native\whisper\build\bin\Release\ggml-cpu.dll -Destination $buildDir -Force
Copy-Item -Path src\main\native\whisper\build\bin\Release\ggml-vulkan.dll -Destination $buildDir -Force
Copy-Item -Path src\main\native\whisper\build\bin\Release\whisper.dll -Destination $buildDir -Force

rm -r -fo build

# We don't need the .lib
rm -r -fo "$buildDir/*lib"
rm -r -fo "$buildDir/cmake"
rm -r -fo "$buildDir/pkgconfig"
