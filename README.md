# WhisperJNI

A JNI wrapper for [whisper.cpp](https://github.com/ggerganov/whisper.cpp), allows transcribe speech to text in Java. Forked from whisper-jni by GiviMAD.

## Platform support

This library aims to support Windows x64, Mac (both AMD x64 and arm64), and Linux (both AMD x64 and arm64).

Default native binaries for those platforms are included in the distributed jar. They only utilize the CPU. For much faster transcription results by utilizing the GPU, you can download the Vulkan natives from the releases and load them using `LibraryUtils` (example below).

## Installation

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.freshsupasulley/whisper-jni).

## Basic Example

```java
var whisper = new WhisperJNI();
whisper.loadLibrary(); // loads the built-in CPU natives
// Alternatively, you can load Vulkan natives yourself
// First check if your machine can use the Vulkan natives!
// if(LibraryUtils.canUseVulkan()) {
//     LibraryUtils.loadVulkan(myLogger, Path.of("windows-x64-vulkan"));
// }

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

## Grammar usage

This wonderful functionality added in whisper.cpp v1.5.0 was integrated into the wrapper.
It makes use of the grammar parser implementation provided among the whisper.cpp examples,
so you can use the [gbnf grammar](https://github.com/ggerganov/whisper.cpp/blob/master/grammars/) to improve the transcriptions results.
```java
        ...
        try (WhisperGrammar grammar = whisper.parseGrammar(Paths.of("/my_grammar.gbnf"))) {
            var params = new WhisperFullParams();
            params.grammar = grammar;
            params.grammarPenalty = 100f;
            ...
            int result = whisper.full(ctx, params, samples, samples.length);
            ...
        }
        ...
```
## Building / Testing

1. Submodule whisper.cpp by running `git submodule update --init`.
2. Download the test models using the scripts 'download-test-model' and 'download-vad-model'. Then move `silero-v5.1.2.bin` to *src/main/resources*!
3. Run the appropriate build script for your platform (`build_linux.sh`, `build_mac.sh` or `build_windows.ps1`). It will build the library to */whisperjni-build*, which the test script will load from (you can alternatively move it to its respective folder in *src/main/resources*.
4. Test the project with `./gradlew test`

## Extending the native api

If you want to add any missing whisper.cpp functionality you need to:

- Add the native method description in `WhisperJNI.java`.
- Run the `generateHeaders` gradle task to regenerate the src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.h header file.
- Add the native method implementation in src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.cpp.
- Add a new test for it in `WhisperJNITest.java`.
