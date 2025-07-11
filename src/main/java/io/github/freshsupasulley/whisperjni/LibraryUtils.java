package io.github.freshsupasulley.whisperjni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

/**
 * Adapted from <a href="https://github.com/henkelmax/rnnoise4j/blob/master/src/main/java/de/maxhenkel/rnnoise4j/LibraryLoader.java">RNNoise4J</a>.
 * 
 * <p>
 * Clients should not need to use this class.
 * </p>
 */
class LibraryUtils {
	
	/** OS name */
	public static final String OS_NAME = System.getProperty("os.name").toLowerCase();
	/** OS architecture */
	public static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
	
	/**
	 * We need to load multiple files but all in order. This is only applicable to the libs that aren't statically linked.
	 * 
	 * <p>
	 * Linux's ggml lib is named that insane string and happens to not get picked up in the correct order but somehow it still works
	 * </p>
	 */
	private static final List<String> loadOrder = Arrays.asList("ggml-base", "ggml-cpu", "ggml-vulkan", "ggml", "whisper", "whisper-jni");
	private static final String[] LIB_NAMES = {".so", ".dylib", ".dll"};
	
	private LibraryUtils()
	{
	}
	
	public static String getArchitecture()
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
	
	public static String getPlatform() throws IOException
	{
		if(isWindows())
		{
			return "windows";
		}
		else if(isMac())
		{
			return "mac";
		}
		else if(isLinux())
		{
			return "linux";
		}
		else
		{
			throw new IOException(String.format("Unknown operating system: %s", OS_NAME));
		}
	}
	
	public static boolean isWindows()
	{
		return OS_NAME.contains("win");
	}
	
	private static boolean isMac()
	{
		return OS_NAME.contains("mac");
	}
	
	private static boolean isLinux()
	{
		return OS_NAME.contains("nux");
	}
	
	/**
	 * A system with <code>vulkan-1.dll</code> present on their SystemRoot indicates it can use Vulkan.
	 * 
	 * @return path to <code>vulkan-1.dll</code>, or <code>null</code> if not found
	 */
	public static Path getVulkanDLL()
	{
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
	
	/**
	 * Loads the native library, should be called at first. This must be called before whisper native methods are invoked.
	 * 
	 * <p>
	 * You can alternatively call {@link #loadVulkan()} if on the right machine.
	 * </p>
	 * 
	 * @param logger SLF4J {@link Logger}
	 * @throws IOException when unable to load the native library
	 */
	public static void loadLibrary(Logger logger) throws IOException
	{
		logger.info("Loading natives for whisper-jni");
		Path tempLib = extractFolderToTemp(logger, getPlatform() + "-" + getArchitecture());
		loadInOrder(logger, tempLib);
	}
	
	/**
	 * Loads Vulkan natives. Use {@link #canUseVulkan()} before calling this method.
	 * 
	 * @param logger SLF4J {@link Logger}
	 */
	public static void loadVulkan(Logger logger)
	{
		if(!WhisperJNI.canUseVulkan())
			throw new IllegalStateException("This system can't use Vulkan natives");
		
		logger.info("Loading Vulkan natives for whisper-jni");
		
		try
		{
			String vulkanPath = getVulkanDLL().toAbsolutePath().toString();
			logger.info("Loading Vulkan DLL at {}", vulkanPath);
			System.load(vulkanPath);
			
			// Now load our dependencies in this specific order
			Path tempDir = extractFolderToTemp(logger, "windows-x64-vulkan");
			loadInOrder(logger, tempDir);
			// System.load(tempDir.resolve("ggml-base.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-cpu.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-vulkan.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("whisper.dll").toAbsolutePath().toString());
			// Statically built now yippie!!
			// String whisperJNIPath = tempDir.resolve("whisper-jni.dll").toAbsolutePath().toString();
			// logger.info("Loading Whisper JNI at {}", whisperJNIPath);
			// System.load(whisperJNIPath);
		} catch(Exception e)
		{
			logger.error("Failed to load Vulkan natives", e);
		}
	}
	
	private static void loadInOrder(Logger logger, Path tempDir) throws IOException
	{
		// Now load everything in the correct order
		List<String> natives = Stream.of(tempDir.toFile().listFiles()).sorted(Comparator.comparing(file ->
		{
			for(int i = 0; i < loadOrder.size(); i++)
			{
				// adding the . differentiates between files like 'ggml' and 'ggml-base', ensuring its at the suffix
				if(file.getName().contains(loadOrder.get(i) + "."))
				{
					return i; // return index of match as priority
				}
			}
			
			logger.warn("File not handled in load order: {}", file);
			return Integer.MAX_VALUE; // unknown files go last
		})).map(file -> file.getAbsolutePath()).filter(file -> Stream.of(LIB_NAMES).anyMatch(suffix -> file.matches(".*\\" + suffix + "(\\.\\d+)*$"))).collect(Collectors.toUnmodifiableList());
		
		// ^ collecting into a list because the consumer doesn't declare IOException
		for(String path : natives)
		{
			logger.info("Loading {}", path);
			
			try
			{
				System.load(path);
			} catch(Exception e)
			{
				// Pass into parent
				logger.error("Failed to load {}. Is the loading order incorrect?", path, e);
				throw new IOException(e);
			}
		}
		
		logger.info("Done loading natives");
	}
	
	private static Path extractFolderToTemp(Logger logger, String folderName) throws IOException
	{
		logger.info("Extracting libs from {} (OS: {}, arch: {})", folderName, OS_NAME, OS_ARCH);
		
		Path originDir = getInternalResource(logger, folderName);
		
		Path tmpDir = Files.createTempDirectory("whisperjni_");
		logger.info("Copying natives to temporary dir {}", tmpDir);
		
		Files.walk(originDir).forEach(p ->
		{
			try
			{
				Path dest = tmpDir.resolve(originDir.relativize(p).toString());
				
				// Should never happen but because Files.walk traverses depth-first this would properly copy everything by making the parent folders first
				if(Files.isDirectory(p))
				{
					Files.createDirectories(dest);
				}
				else
				{
					Files.createDirectories(dest.getParent()); // probably unnecessary
					logger.debug("Copying {} to {}", p, dest);
					Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch(IOException ex)
			{
				throw new UncheckedIOException(ex);
			}
		});
		
		logger.info("Finished extracting natives");
		return tmpDir;
	}
	
	public static Path getInternalResource(Logger logger, String resourceName) throws IOException
	{
		URI uri;
		
		try
		{
			uri = LibraryUtils.class.getResource("/" + resourceName).toURI();
		} catch(URISyntaxException | NullPointerException e)
		{
			throw new IOException("Resource '" + resourceName + "' not found on classpath", e);
		}
		
		logger.info("URI: {}", uri);
		FileSystem fs = null;
		
		if("jar".equals(uri.getScheme()))
		{
			try
			{
				fs = FileSystems.newFileSystem(uri, Map.of());
			} catch(FileSystemAlreadyExistsException e)
			{
				fs = FileSystems.getFileSystem(uri); // reuse the one that’s open
			}
			
			logger.debug("Resource is inside JAR: {}", uri);
			return fs.getPath("/" + resourceName);
		}
		
		Path originDir = Paths.get(uri);
		logger.debug("Resource is a directory on disk: {}", originDir);
		return originDir;
	}
}
