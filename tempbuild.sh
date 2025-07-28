OUTPUT_DIR="whisperjni-build"
docker build -f dockerfile . -t whisperjni_binary:test --load
docker run \
  -v "$(pwd)/$OUTPUT_DIR:/app/$OUTPUT_DIR" \
  -v "$(pwd)/test-results:/app/build/test-results" \
  -e VULKAN_ARG=OFF \
  whisperjni_binary:test
