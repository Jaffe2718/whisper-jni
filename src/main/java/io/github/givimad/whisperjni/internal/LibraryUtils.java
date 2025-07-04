package io.github.givimad.whisperjni.internal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LibraryUtils {
	/** OS name */
	public static final String OS_NAME = System.getProperty("os.name").toLowerCase();
	/** OS architecture */
	public static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();

    private static final String TEMP_FOLDER_PREFIX = "whisper-jni-";
    private static Path libraryDir = null;
    private LibraryUtils() {
    }
    private static void createLibraryFromInputStream(String filename, InputStream is) throws IOException {
        Path libraryPath = libraryDir.resolve(filename);
        try (is) {
            Files.copy(is, libraryPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.delete(libraryPath);
            } catch (IOException ignored) {}
            throw e;
        } catch (NullPointerException e) {
            try {
                Files.delete(libraryPath);
            } catch (IOException ignored) {}
            throw new FileNotFoundException("File" + libraryPath + "not found.");
        }
        libraryPath.toFile().deleteOnExit();
    }
    /**
     * Loads library from current JAR archive
     *
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after
     * exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     *
     * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters
     * (restriction of {@link File#createTempFile(java.lang.String, java.lang.String)}).
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    private static void copyFromSystem(Path path, String filename, Logger logger) throws IOException {
        if(libraryDir == null) {
            libraryDir = createTempDirectory(TEMP_FOLDER_PREFIX);
        }
        if (null == path) {
            throw new IllegalArgumentException("Missing path.");
        }
        logger.info("Copping "+ path + " into " + libraryDir.resolve(filename));
        try (var is = Files.newInputStream(path)) {
            createLibraryFromInputStream(filename, is);
        }
    }
    /**
     * Loads library from current JAR archive
     *
     * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after
     * exiting.
     * Method uses String as filename because the pathname is "abstract", not system-dependent.
     *
     * @param path The path of file inside JAR as absolute path (beginning with '/'), e.g. /package/File.ext
     * @throws IOException If temporary file creation or read/write operation fails
     * @throws IllegalArgumentException If source file (param path) does not exist
     * @throws IllegalArgumentException If the path is not absolute or if the filename is shorter than three characters
     * (restriction of {@link File#createTempFile(java.lang.String, java.lang.String)}).
     * @throws FileNotFoundException If the file could not be found inside the JAR.
     */
    public static void extractLibraryFromJar(String path, String filename, Logger logger) throws IOException {
        if(libraryDir == null) {
            libraryDir = createTempDirectory(TEMP_FOLDER_PREFIX);
        }
        if (null == path || !path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }
        logger.info("Extracting "+ path + " into " + libraryDir.resolve(filename));
        createLibraryFromInputStream(filename, LibraryUtils.class.getResourceAsStream(path));
    }
    private static Path createTempDirectory(String prefix) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        File generatedDir = new File(tempDir, prefix + System.nanoTime());
        if (!generatedDir.mkdir())
            throw new IOException("Failed to create temp directory " + generatedDir.getName());
        return Paths.get(generatedDir.getAbsolutePath());
    }

    /**
     * Register the native library, should be called at first.
     * @throws IOException when unable to load the native library
     */
    public static void loadLibrary(Logger logger) throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        String altLibDirProperty = System.getProperty("io.github.givimad.whisperjni.libdir");
        if (altLibDirProperty != null) {
            try {
                libraryDir = Paths.get(altLibDirProperty);
            } catch (InvalidPathException e) {
                logger.warn("Ignoring invalid directory path " + altLibDirProperty);
            }
        }
        var libraryPaths = getJarLibraryPaths(logger, libraryDir != null);
        if (libraryDir == null) {
            if (!osName.contains("win")) {
                LibraryUtils.extractLibraryFromJar(libraryPaths.whisperPath, libraryPaths.whisperFilename, logger);
                LibraryUtils.extractLibraryFromJar(libraryPaths.ggmlPath, libraryPaths.ggmlFilename, logger);
            }
            LibraryUtils.extractLibraryFromJar(libraryPaths.whisperJNIPath, libraryPaths.whisperJNIFilename, logger);
        }
        System.load(libraryDir.resolve(libraryPaths.whisperJNIFilename).toAbsolutePath().toString());
    }
    
	/**
	 * Loads Vulkan natives. Use {@link #canUseVulkan()} before calling this method.
	 * 
	 * @param logger SLF4J {@link Logger}
	 */
	public static void loadVulkan(Logger logger) {
		if(!canUseVulkan())
			throw new IllegalStateException("This system can't use Vulkan natives");
		
		logger.info("Loading Vulkan natives for whisper-jni");
		
		try
		{
			// First load vulkan-1.dll
			String vulkanPath = getVulkanDLL().toAbsolutePath().toString();
			logger.info("Loading Vulkan DLL at {}", vulkanPath);
			System.load(vulkanPath);
			
			// Now load our dependencies in this specific order
			String subpath = "/win-amd64-vulkan/";
			extractLibFromJar(subpath, "ggml-base.dll", logger);
			extractLibFromJar(subpath, "ggml-cpu.dll", logger);
			extractLibFromJar(subpath, "ggml-vulkan.dll", logger);
			extractLibFromJar(subpath, "ggml.dll", logger);
			extractLibFromJar(subpath, "whisper.dll", logger);
			extractLibFromJar(subpath, "whisper-jni.dll", logger);
		} catch(Exception e)
		{
			logger.error("Failed to load Vulkan natives", e);
		}
	}

	/**
	 * Loads Vulkan natives with a default logger. Use {@link #canUseVulkan()} before calling this method.
	 */
	public static void loadVulkan() {
		loadVulkan(LoggerFactory.getLogger(LibraryUtils.class));
	}

	private static void extractLibFromJar(String folder, String filename, Logger logger) throws IOException
	{
		LibraryUtils.extractLibraryFromJar(folder + filename, filename, logger);
		// Immediately load the file
		System.load(libraryDir.resolve(filename).toAbsolutePath().toString());
	}

	private static boolean isWindows()
	{
		return OS_NAME.contains("win");
	}

	private static String getArchitecture()
	{
		switch(OS_ARCH)
		{
			case "i386":
			case "i486":
			case "i586":
			case "i686":
			case "x86":
			case "x86_32":
				return "x86";
			case "amd64":
			case "x86_64":
			case "x86-64":
				return "x64";
			case "aarch64":
				return "aarch64";
			default:
				return OS_ARCH;
		}
	}

	/**
	 * Use to determine if this system can custom-built Vulkan libs. Must be on Windows with AMD 64-bit processor with <code>vulkan-1.dll</code> installed.
	 * 
	 * @return true if this system can use Vulkan, false otherwise
	 */
	public static boolean canUseVulkan() {
		return isWindows() && getArchitecture().equals("x64") && getVulkanDLL() != null;
	}

	/**
	 * A system with <code>vulkan-1.dll</code> present on their SystemRoot indicates it can use Vulkan.
	 * 
	 * @return path to <code>vulkan-1.dll</code>, or <code>null</code> if not found
	 */
	public static Path getVulkanDLL() {
		// do I bother checking SysWOW64?? I thought this was x64 only
		List<Path> commonPaths = List.of(Path.of(System.getenv("SystemRoot"), "System32", "vulkan-1.dll"), Path.of(System.getenv("SystemRoot"), "SysWOW64", "vulkan-1.dll"));
		
		for(Path path : commonPaths)
		{
			if(Files.exists(path))
			{
				return path;
			}
		}
		
		return null;
	}
	
    private static LibraryPaths getJarLibraryPaths(Logger logger, boolean customLibraryPath) throws IOException {
        LibraryPaths.Builder builder = new LibraryPaths.Builder();
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        if (osName.contains("win")) {
            logger.info("OS detected: Windows.");
            builder.setWhisperJNIFilename("whisper-jni.dll");
            if(osArch.contains("amd64") || osArch.contains("x86_64")) {
                logger.info("Compatible amd64 architecture detected.");
                logger.info("Looking for whisper.dll in $env:PATH.");
                if(customLibraryPath || isWhisperDLLInstalled()) {
                    logger.info("File whisper.dll found, it will be used.");
                    builder.setWhisperJNIPath("/win-amd64/whisper-jni.dll");
                } else {
                    logger.info("File whisper.dll not found, loading full version.");
                    builder.setWhisperJNIPath("/win-amd64/whisper-jni_full.dll");
                }
            }
        } else if (osName.contains("nix") || osName.contains("nux")
                || osName.contains("aix")) {
            logger.info("OS detected: Linux.");
            builder.setWhisperJNIFilename("libwhisper-jni.so");
            builder.setWhisperFilename("libwhisper.so.1");
            builder.setGgmlFilename("libggml.so");
            String cpuInfo;
            try {
                cpuInfo = Files.readString(Path.of("/proc/cpuinfo"));
            } catch (IOException ignored) {
                cpuInfo = "";
            }
            if(osArch.contains("amd64") || osArch.contains("x86_64")) {
                logger.info("Compatible amd64 architecture detected.");
                builder.setWhisperJNIPath("/debian-amd64/libwhisper-jni.so");
                builder.setWhisperPath("/debian-amd64/libwhisper.so.1");
                if(cpuInfo.contains("avx2") && cpuInfo.contains("fma") && cpuInfo.contains("f16c") && cpuInfo.contains("avx")) {
                    logger.info("Using ggml with extra cpu features (mf16c, mfma, mavx, mavx2)");
                    builder.setGgmlPath("/debian-amd64/libggml+mf16c+mfma+mavx+mavx2.so");
                } else {
                    builder.setGgmlPath("/debian-amd64/libggml.so");
                }
            } else if(osArch.contains("aarch64") || osArch.contains("arm64")) {
                logger.info("Compatible arm64 architecture detected.");
                builder.setWhisperJNIPath("/debian-arm64/libwhisper-jni.so");
                builder.setWhisperPath("/debian-arm64/libwhisper.so.1");
                if(cpuInfo.contains("fphp")) {
                    logger.info("Using ggml with extra cpu features (fp16)");
                    builder.setGgmlPath("/debian-arm64/libggml+fp16.so");
                } else if(cpuInfo.contains("crc32")) {
                    builder.setGgmlPath("/debian-arm64/libggml.so");
                }
            } else if(osArch.contains("armv7") || osArch.contains("arm")) {
                logger.info("Compatible arm architecture detected.");
                builder.setWhisperJNIPath("/debian-armv7l/libwhisper-jni.so");
                builder.setWhisperPath("/debian-armv7l/libwhisper.so.1");
                if(cpuInfo.contains("crc32")) {
                    logger.info("Using ggml with extra cpu features (crc)");
                    builder.setGgmlPath("/debian-armv7l/libggml+crc.so");
                } else {
                    builder.setGgmlPath("/debian-armv7l/libggml.so");
                }
            } else {
                throw new IOException("Unknown OS architecture");
            }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            logger.info("OS detected: macOS.");
            builder.setWhisperJNIFilename("libwhisper-jni.dylib");
            builder.setWhisperFilename("libwhisper.1.dylib");
            builder.setGgmlFilename("libggml.dylib");
            if(osArch.contains("amd64") || osArch.contains("x86_64")) {
                logger.info("Compatible amd64 architecture detected.");
                builder.setWhisperJNIPath("/macos-amd64/libwhisper-jni.dylib");
                builder.setWhisperPath("/macos-amd64/libwhisper.1.dylib");
                builder.setGgmlPath("/macos-amd64/libggml.dylib");
            } else if(osArch.contains("aarch64") || osArch.contains("arm64")) {
                logger.info("Compatible arm64 architecture detected.");
                builder.setWhisperJNIPath("/macos-arm64/libwhisper-jni.dylib");
                builder.setWhisperPath("/macos-arm64/libwhisper.1.dylib");
                builder.setGgmlPath("/macos-arm64/libggml.dylib");
            } else {
                throw new IOException("Unknown OS architecture");
            }
        } else {
            throw new IOException("Unknown OS");
        }
        return builder.build();
    }
    
    private static boolean isWhisperDLLInstalled() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return false;
        }
        
        return Arrays.stream(pathEnv.split(Pattern.quote(File.pathSeparator)))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .map(path -> {
                         try {
                             return Paths.get(path).resolve("whisper.dll");
                         } catch (InvalidPathException e) {
                             return null;
                         }
                     })
                     .filter(Objects::nonNull)
                     .anyMatch(Files::exists);
    }
    
    private static final class LibraryPaths {
        final String whisperJNIPath;
        final String whisperJNIFilename;
        final String whisperPath;
        final String whisperFilename;
        final String ggmlFilename;
        final String ggmlPath;

        private LibraryPaths(String whisperJNIPath, String whisperJNIFilename, String whisperPath, String whisperFilename, String ggmlFilename, String ggmlPath) {
            this.whisperJNIPath = whisperJNIPath;
            this.whisperJNIFilename = whisperJNIFilename;
            this.whisperPath = whisperPath;
            this.whisperFilename = whisperFilename;
            this.ggmlFilename = ggmlFilename;
            this.ggmlPath = ggmlPath;
        }


        static final class Builder {
            private String whisperJNIPath;
            private String whisperJNIFilename;
            private String whisperPath;
            private String whisperFilename;
            private String ggmlFilename;
            private String ggmlPath;
            public void setGgmlFilename(String ggmlFilename) {
                this.ggmlFilename = ggmlFilename;
            }
            public void setGgmlPath(String ggmlPath) {
                this.ggmlPath = ggmlPath;
            }
            public void setWhisperFilename(String whisperFilename) {
                this.whisperFilename = whisperFilename;
            }
            public void setWhisperJNIFilename(String whisperJNIFilename) {
                this.whisperJNIFilename = whisperJNIFilename;
            }
            public void setWhisperJNIPath(String whisperJNIPath) {
                this.whisperJNIPath = whisperJNIPath;
            }
            public void setWhisperPath(String whisperPath) {
                this.whisperPath = whisperPath;
            }
            public LibraryPaths build() {
                return new LibraryPaths(whisperJNIPath,
                  whisperJNIFilename,
                  whisperPath,
                  whisperFilename,
                  ggmlFilename,
                  ggmlPath);
            }
        }

    }
}
