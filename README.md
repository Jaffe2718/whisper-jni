# WhisperJNI

A JNI wrapper for [whisper.cpp](https://github.com/ggerganov/whisper.cpp), allows transcribe speech to text in Java. Forked from whisper-jni by GiviMAD.

## Platform support

This library aims to support Windows x64, Mac (both AMD x64 and ARM), and Linux (both AMD x64 and ARM).

The native binaries for those platforms are included in the distributed jar.
Please open an issue if you found it don't work on any of the supported platforms.

## Installation

The package is distributed through [Maven Central](https://central.sonatype.com/artifact/io.github.freshsupasulley/whisper-jni).

## Basic Example

```java
        ...
        WhisperJNI.loadLibrary(); // load platform binaries
        var whisper = new WhisperJNI();
        float[] samples = readJFKFileSamples();
        var ctx = whisper.init(Path.of(System.getProperty("user.home"), 'ggml-tiny.bin'));
        var params = new WhisperFullParams();
        int result = whisper.full(ctx, params, samples, samples.length);
        if(result != 0) {
            throw new RuntimeException("Transcription failed with code " + result);
        }
        int numSegments = whisper.fullNSegments(ctx);
        assertEquals(1, numSegments);
        String text = whisper.fullGetSegmentText(ctx,0);
        assertEquals(" And so my fellow Americans ask not what your country can do for you ask what you can do for your country.", text);
        ctx.close(); // free native memory, should be called when we don't need the context anymore.
        ...
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
## Building and testing the project.

You need Java and Cpp setup.

After cloning the project you need to init the whisper.cpp submodule by running:

```sh
git submodule update --init
```

Then you need to download the model used in the tests using the script 'download-test-model.sh' or 'download-test-model.ps1', the ggml-tiny model.

Run the appropriate build script for your platform (build_debian.sh, build_macos.sh or build_win.ps1), it will place the native library file on the resources directory.

Finally, you can run the project tests to confirm it works:

```sh
./gradlew test
```

## Extending the native api

If you want to add any missing whisper.cpp functionality you need to:

- Add the native method description in `WhisperJNI.java`.
- Run the `generateHeaders` gradle task to regenerate the src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.h header file.
- Add the native method implementation in src/main/native/io_github_freshsupasulley_whisperjni_WhisperJNI.cpp.
- Add a new test for it in `WhisperJNITest.java`.
