package io.github.freshsupasulley.whisperjni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
import org.slf4j.LoggerFactory;

/**
 * Adapted from <a href="https://github.com/henkelmax/rnnoise4j/blob/master/src/main/java/de/maxhenkel/rnnoise4j/LibraryLoader.java">RNNoise4J</a>
 */
public class LibraryUtils {
	
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
		Path tempLib = extractFolderToTemp(logger, getPlatform() + "-" + getArchitecture());
		loadInOrder(logger, tempLib);
	}
	
	/**
	 * Loads Vulkan natives with a default logger. Use {@link #canUseVulkan()} before calling this method.
	 */
	public static void loadVulkan()
	{
		loadVulkan(LoggerFactory.getLogger(LibraryUtils.class));
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
	
	private static String getPlatform() throws IOException
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
	
	private static boolean isWindows()
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
	
	private static Path getInternalResource(Logger logger, String resourceName) throws IOException
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
	
	/**
	 * Extracts the bundled <code>ggml-silero-v5.1.2</code> model to a directory on your machine.
	 * 
	 * <p>
	 * After exporting, you can use that path to fill {@link WhisperFullParams#vad_model_path}.
	 * </p>
	 * 
	 * @param logger      SLF4J {@link Logger}
	 * @param destination path to store the model
	 * @throws IOException if something goes wrong (like the path being malformed)
	 */
	public static void exportVADModel(Logger logger, Path destination) throws IOException
	{
		logger.info("Looking for VAD model");
		Path path = getInternalResource(logger, "ggml-silero-v5.1.2.bin");
		
		logger.debug("Copying {} to {}", path, destination);
		Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
	}
	
	/**
	 * Loads Vulkan natives. Use {@link #canUseVulkan()} before calling this method.
	 * 
	 * @param logger SLF4J {@link Logger}
	 */
	public static void loadVulkan(Logger logger)
	{
		if(!canUseVulkan())
			throw new IllegalStateException("This system can't use Vulkan natives");
		
		logger.info("Loading Vulkan natives for whisper-jni");
		
		try
		{
			String vulkanPath = getVulkanDLL().toAbsolutePath().toString();
			logger.info("Loading Vulkan DLL at {}", vulkanPath);
			System.load(vulkanPath);
			
			// Now load our dependencies in this specific order
			/// ^ nvm, whisper-jni statically links everything
			Path tempDir = extractFolderToTemp(logger, "windows-x64-vulkan");
			// System.load(tempDir.resolve("ggml-base.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-cpu.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-vulkan.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("whisper.dll").toAbsolutePath().toString());
			// Statically built now yippie!!
			String whisperJNIPath = tempDir.resolve("whisper-jni.dll").toAbsolutePath().toString();
			logger.info("Loading Whisper JNI at {}", whisperJNIPath);
			System.load(whisperJNIPath);
			// loadInOrder(logger, tempDir);
		} catch(Exception e)
		{
			logger.error("Failed to load Vulkan natives", e);
		}
	}
	
	/**
	 * Use to determine if this system can custom-built Vulkan libs. Must be on Windows with a 64-bit processor, and <b>most importantly</b>
	 * <code>vulkan-1.dll</code> at <code>/System32/vulkan-1.dll</code>.
	 * 
	 * @return true if this system can use the Vulkan natives, false otherwise
	 */
	public static boolean canUseVulkan()
	{
		return isWindows() && getArchitecture().equals("x64") && getVulkanDLL() != null;
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
	
	// /**
	// * Checks if <code>vulkan-1.dll</code> is on the path.
	// *
	// * @param logger SLF4J {@link Logger}
	// * @return true if Vulkan is available on the path
	// */
	// private static boolean isVulkanOnPath(Logger logger)
	// {
	// String path = System.getenv("PATH");
	// logger.debug("Path: {}", path);
	//
	// // Edge case
	// if(path == null || path.isEmpty())
	// {
	// return false;
	// }
	//
	// for(String entry : path.split(";"))
	// {
	// logger.debug("Path entry: {}", entry);
	//
	// try
	// {
	// Path dllPath = Path.of(entry.trim(), "vulkan-1.dll");
	//
	// if(Files.exists(dllPath))
	// {
	// return true;
	// }
	// } catch(InvalidPathException e)
	// {
	// logger.warn("Invalid path entry at {}", entry, e);
	// }
	// }
	//
	// logger.warn("Unable to find Vulkan on path");
	// return false;
	// }
}
