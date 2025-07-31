set -xe

AARCH=${1:-$(uname -m)}
VULKAN_ARG=${VULKAN:-OFF} # fill from CI/CD

case "$AARCH" in
  x86_64|amd64|x64)
    AARCH=x86_64
    TARGET_VERSION=11.0
    ;;
  arm64|aarch64)
    AARCH=arm64
    TARGET_VERSION=11.0
    ;;
  *)
    echo Unsupported arch $AARCH
    exit 1
    ;;
    
esac

echo "Detected architecture: $AARCH"

INCLUDE_JAVA="-I $JAVA_HOME/include -I $JAVA_HOME/include/darwin"
# Is this ever used??
#TARGET=$AARCH-apple-macosx$TARGET_VERSION
TMP_DIR=tmp-build
TARGET_DIR=whisperjni-build

mkdir -p $TMP_DIR $TARGET_DIR
# Static linking seems to be a pain in the ass
cmake -Bbuild -DCMAKE_INSTALL_PREFIX=$TMP_DIR -DCMAKE_OSX_DEPLOYMENT_TARGET=$TARGET_VERSION -DCMAKE_OSX_ARCHITECTURES=$AARCH -DGGML_VULKAN=${VULKAN_ARG}
cmake --build build --config Release
cmake --install build
rm -rf build

# Clear target dir of old libs
rm -f $TARGET_DIR/*.dylib

cp $TMP_DIR/*.dylib $TARGET_DIR
#cp $TMP_DIR/libwhisper.1.dylib $TARGET_DIR
#cp $TMP_DIR/libwhisper-jni.dylib $TARGET_DIR

# I want to keep it to make rebuilding faster
#rm -rf $TMP_DIR