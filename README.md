# WhisperJNI

A JNI wrapper for [whisper.cpp](https://github.com/ggerganov/whisper.cpp), allows transcribing speech to text in Java 17+. Forked from [whisper-jni by GiviMAD](https://github.com/GiviMAD/whisper-jni).

## Platform support

This library aims to support Windows x64, and AMD x64 / arm64 of Mac and Linux.

Default CPU binaries for those platforms are included in the distributed jar. You can utilize your GPU to achieve much faster transcription results by loading custom-built Vulkan natives (see [examples](#examples)).
> To use the Mac Vulkan natives, install [MoltenVK](https://github.com/KhronosGroup/MoltenVK). Note that the default natives for Mac use Metal and can be quite fast already. Vulkan natives make more of a difference on Linux / Windows.

## Installation

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.freshsupasulley/whisper-jni):

### Gradle

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.freshsupasulley:whisper-jni:+' // gets the latest version
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.freshsupasulley</groupId>
    <artifactId>whisper-jni</artifactId>
    <version>$version</version> <!-- replace with a specific version -->
</dependency>
```

## Examples

```java
var whisper = new WhisperJNI();
whisper.loadLibrary(); // loads the built-in CPU natives
float[] samples = readJFKFileSamples();
var ctx = whisper.init(Path.of('ggml-tiny.bin'));
var params = new WhisperFullParams();
int result = whisper.full(ctx, params, samples, samples.length);
if(result != 0) {
    throw new RuntimeException("Transcription failed with code " + result);
}
int numSegments = whisper.fullNSegments(ctx);
assertEquals(1, numSegments);
String text = whisper.fullGetSegmentText(ctx,0);
assertEquals(" And so my fellow Americans ask not what your country can do for you ask what you can do for your country.", text);
ctx.close(); // free native memory, should be called when we don't need the context anymore
```

### Using the Vulkan Natives

You can find the Vulkan natives in [releases](https://github.com/FreshSupaSulley/whisper-jni/releases). You'll need to download and load them using `LibraryUtils`:

```java
Path vulkanNatives = Path.of("path", "to", "whisperjni-vulkan-natives");
if(LibraryUtils.findAndLoadVulkanRuntime()) {
    logger.info("Found the Vulkan runtime! Loading the Vulkan natives");
    LibraryUtils.loadLibrary(logger, vulkanNatives);
} else {
    logger.info("Loading standard natives");
    LibraryUtils.loadLibrary(logger, vulkanNatives);
}
```

If you depend on whisper-jni and need to extract the Vulkan natives from a folder within the JAR, `LibraryUtils` has helper methods for this to extract and load them to/from a temporary folder. If you need to know which natives to load based on the machine's OS / architecture, there's methods for that too. See the `LibraryUtils` Javadoc!

## Grammar

This wonderful functionality added in whisper.cpp v1.5.0 was integrated into the wrapper.
It makes use of the grammar parser implementation provided among the whisper.cpp examples,
so you can use the [gbnf grammar](https://github.com/ggerganov/whisper.cpp/blob/master/grammars/) to improve the transcriptions results.
```java
try (WhisperGrammar grammar = whisper.parseGrammar(Paths.of("/my_grammar.gbnf"))) {
    var params = new WhisperFullParams();
    params.grammar = grammar;
    params.grammarPenalty = 100f;
    ...
    int result = whisper.full(ctx, params, samples, samples.length);
    ...
}
```

## Building / Testing

1. Submodule whisper.cpp by running `git submodule update --init`.
2. Download the test models using the scripts `download-test-model` and `download-vad-model`. Then move `silero-v6.2.0.bin` to `src/main/resources`!
3. Run the appropriate build script for your platform (`build_linux.sh`, `build_mac.sh` or `build_windows.ps1`). It will build the library to `/whisperjni-build`, which the JUnit test file will load from.
> Although this shouldn't cause any problems, if your machine can use Vulkan, the test script will consider the natives in `/whisperjni-build` to be Vulkan natives for CI/CD reasons.
> You can alternatively move the natives from `/whisperjni-build` to its respective subfolder in `src/main/resources` and delete the build directory.
4. `./gradlew test`

## Extending the Native API

If you want to add any missing whisper.cpp functionality, you need to:

- Add the native method description in `WhisperJNI.java`.
- Run the `generateHeaders` gradle task to regenerate the `src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.h` header file.
- Add the native method implementation in `src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.cpp`.
- Add a new test for it in `WhisperJNITest.java`.
