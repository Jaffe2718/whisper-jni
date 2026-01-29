# Fill from env
$vulkanEnabled = $env:VULKAN
$TMP_DIR="tmp-build"
$TARGET_DIR="whisperjni-build"

New-Item -Path $TARGET_DIR -ItemType Directory -Force

if ($vulkanEnabled -eq "ON") {
	Write-Host "Building with Vulkan"
}

cmake -B build -DCMAKE_BUILD_TYPE=Release "-DCMAKE_INSTALL_PREFIX=$TMP_DIR" -DGGML_STATIC=1 "-DGGML_VULKAN=$vulkanEnabled"
cmake --build build --config Release
cmake --install build

# Move all DLLs from build dir to prod dir
Write-Host "[INFO] Recursively copying all DLLs from $TMP_DIR to $TARGET_DIR" -ForegroundColor Cyan
Get-ChildItem -Path $TMP_DIR -Filter "*.dll" -Recurse -File | ForEach-Object {
	Copy-Item -Path $_.FullName -Destination $TARGET_DIR -Force
	Write-Host "Copied: $($_.FullName) to $TARGET_DIR"
}

Write-Host "`n[SUCCESS] All DLLs copied to $TARGET_DIR successfully!`n" -ForegroundColor Green
