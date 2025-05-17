# THIS IS MEANT TO BE RUN IN THE ROOT OF THE REPO, WHERE YOU INIT SUBMODULE FIRST
$buildDir = "src/main/resources/win-amd64-vulkan"
mkdir $buildDir -Force
#rm -r -fo .\src\main\resources\win-vulkan-x64\whisper-jni.dll

# Build whisper with Vulkan (https://gist.github.com/thewh1teagle/2525627cc0f70f5b92a01fb925d78669)
#git clone https://github.com/ggerganov/whisper.cpp
cd src/main/native/whisper
#rm -rfo build

# already at this version
#git checkout ebca09a3 # match commit for whisper-jni

$env:GGML_OPENBLAS = "0" # needed?

# Release has some outstanding bugs for some reason, dgaf to fix it
cmake -B build -DBUILD_SHARED_LIBS=ON -DGGML_VULKAN=ON -DGGML_STATIC=ON -DGGML_CCACHE=OFF -DCMAKE_BUILD_TYPE=Debug -DCMAKE_C_FLAGS_RELEASE="/MD" -DCMAKE_CXX_FLAGS_RELEASE="/MD"
cmake --build build --config Debug

# Test whisper (i aint)
#wget.exe https://github.com/thewh1teagle/sherpa-rs/releases/download/v0.1.0/motivation.wav
#.\build\bin\Release\main.exe -m C:\Users\Sullbeans\eclipse-workspace\censorcraft\forge\src\main\resources\tiny.en.bin -f .\motivation.wav

echo "Done building whisper"

# go back to root of repo
cd ../../../..

# move DLLs from whisper vulkan build to win-vulkan-x64
Copy-Item -Path src\main\native\whisper\build\bin\Debug\ggml.dll -Destination $buildDir -Force
Copy-Item -Path src\main\native\whisper\build\bin\Debug\whisper.dll -Destination $buildDir -Force

echo $buildDir
echo "Building JNI"

# Build whisper-jni
cmake -B build "-DCMAKE_INSTALL_PREFIX=$buildDir"
cmake --build build --config Release
cmake --install build --config Release
rm -r -fo build

# We don't need the .lib
rm -fo "$buildDir/*lib"