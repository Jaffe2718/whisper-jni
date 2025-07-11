# THIS IS MEANT TO BE RUN IN THE ROOT OF THE REPO, WHERE YOU INIT SUBMODULE FIRST
# ... since whisper JNI builds whisper anyways this top part of this script might not be necessary anymore
$buildDir = "src/main/resources/windows-x64-vulkan-build"
$releaseDir = "src/main/resources/windows-x64-vulkan"

mkdir $buildDir -Force
mkdir $releaseDir -Force

echo "Building JNI at $buildDir"

# Build whisper-jni
cmake -B build "-DCMAKE_INSTALL_PREFIX=$buildDir" -DGGML_VULKAN=ON -DGGML_STATIC=1 -DCMAKE_BUILD_TYPE=Release
cmake --build build --config Release
cmake --install build --config Release

# move DLLs from whisper vulkan build to win-vulkan-x64-build
echo "Moving DLLs into release dir at $releaseDir"
Copy-Item -Path "$buildDir\*.dll" -Destination $releaseDir -Force

# In a perfect world, this would be a sh script so I can clear the build dir without dumb shit code
