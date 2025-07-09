# THIS IS MEANT TO BE RUN IN THE ROOT OF THE REPO, WHERE YOU INIT SUBMODULE FIRST
# ... since whisper JNI builds whisper anyways this top part of this script might not be necessary anymore
$buildDir = "src/main/resources/windows-x64-vulkan-build"
$releaseDir = "src/main/resources/windows-x64-vulkan"

mkdir $buildDir -Force
mkdir $releaseDir -Force

echo $buildDir
echo "Building JNI"

# Build whisper-jni
cmake -B build "-DCMAKE_INSTALL_PREFIX=$buildDir" -DGGML_VULKAN=ON -DGGML_STATIC=1 -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
cmake --install build --config Release

# move DLLs from whisper vulkan build to win-vulkan-x64-build
Copy-Item -Path "$buildDir\*.dll* -Destination $releaseDir -Force

# I don't think cleanup is necessary in GH actions cause we're just uploading the release dir
#rm -r -fo build
# We don't need the .lib
#rm -r -fo "$buildDir/*lib"
#rm -r -fo "$buildDir/cmake"
#rm -r -fo "$buildDir/pkgconfig"
