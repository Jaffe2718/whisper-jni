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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

/**
 * Used for advanced library loading utils.
 * 
 * <p>
 * Adapted from <a href="https://github.com/henkelmax/rnnoise4j/blob/master/src/main/java/de/maxhenkel/rnnoise4j/LibraryLoader.java">RNNoise4J</a>.
 * </p>
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
	private static final List<String> loadOrder = Arrays.asList("ggml-base", "ggml-cpu", "ggml-metal", "ggml-vulkan", "ggml", "whisper", "whisper-jni");
	private static final String[] LIB_NAMES = {".so", ".dylib", ".dll"};
	
	private LibraryUtils()
	{
	}
	
	/**
	 * Returns a generalized name of this machine's architecture. Can be useful for determining which natives to load.
	 * 
	 * <p>
	 * Here's the options that can be returned:
	 * </p>
	 * 
	 * <ul>
	 * <li>"x86", 32-bit processor</li>
	 * <li>"x64", 64-bit processor</li>
	 * <li>"arm64", 64-bit ARM processor</li>
	 * </ul>
	 * 
	 * <p>
	 * The raw architecture name is returned if it wasn't matched to a known architecture name.
	 * </p>
	 * 
	 * @return processor architecture name, or the raw name of the system's architecture if not matched
	 */
	public static String getArchitecture()
	{
		return switch(OS_ARCH)
		{
			case "i386", "i486", "i586", "i686", "x86", "x86_32" -> "x86";
			case "amd64", "x86_64", "x86-64" -> "x64";
			case "aarch64" -> "arm64";
			default -> OS_ARCH;
		};
	}
	
	/**
	 * Returns the operating system name of this machine.
	 * 
	 * @return OS of this machine as a string
	 * @throws IOException if this OS isn't supported
	 */
	public static String getOS() throws IOException
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
	
	/**
	 * Determines if this OS is Windows.
	 * 
	 * @return true if Windows
	 */
	public static boolean isWindows()
	{
		return OS_NAME.contains("win");
	}
	
	/**
	 * Determines if this OS is Mac.
	 * 
	 * @return true if Windows
	 */
	public static boolean isMac()
	{
		return OS_NAME.contains("mac");
	}
	
	/**
	 * Determines if this OS is Linux.
	 * 
	 * @return true if Linux
	 */
	public static boolean isLinux()
	{
		return OS_NAME.contains("nux");
	}
	
	/**
	 * Extracts the bundled <code>ggml-silero-v5.1.2</code> model to a directory on your machine.
	 * 
	 * <p>
	 * Example usage:
	 * </p>
	 * 
	 * <pre>
	 * {@code
	 * Path tempVAD = Files.createTempFile("tempVAD", ".bin");
	 * LibraryUtils.exportVADModel(tempVAD);
	 * }
	 * </pre>
	 * 
	 * <p>
	 * After exporting, you can use the path to fill {@link WhisperFullParams#vad_model_path}.
	 * </p>
	 * 
	 * @param destination path to store the model
	 * @throws IOException if something goes wrong (like the path being malformed)
	 */
	public static void exportVADModel(Path destination) throws IOException
	{
		// Shouldn't ever throw an error, but wrap it just in case
		try
		{
			Path path = LibraryUtils.getPathToResource(LibraryUtils.class.getResource("/ggml-silero-v5.1.2.bin").toURI());
			Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch(URISyntaxException e)
		{
			throw new IOException(e);
		}
	}
	
	/**
	 * Tries to find the Vulkan runtime library on this machine by looking in well known paths according to the operating system.
	 * 
	 * <p>
	 * Unless it's already on the system's loader search path, you need to manually load the Vulkan runtime yourself using {@link System#load(String)}.
	 * </p>
	 * 
	 * @return path to Vulkan runtime library, or <code>null</code> if not found
	 */
	public static Path findVulkanRuntime()
	{
		// Simplifications can be made if we only consider x64 systems
		List<Path> possiblePaths = new ArrayList<Path>();
		
		if(isWindows())
		{
			String systemRoot = System.getenv("SystemRoot");
			possiblePaths.add(Path.of(systemRoot, "System32", "vulkan-1.dll"));
			possiblePaths.add(Path.of(systemRoot, "SysWOW64", "vulkan-1.dll"));
		}
		else if(isLinux())
		{
			possiblePaths.add(Path.of("/usr/lib/libvulkan.so.1"));
			possiblePaths.add(Path.of("/usr/lib/x86_64-linux-gnu/libvulkan.so.1"));
			possiblePaths.add(Path.of("/usr/local/lib/libvulkan.so.1"));
		}
		else if(isMac())
		{
			possiblePaths.add(Path.of("/usr/local/lib/libvulkan.1.dylib"));
			possiblePaths.add(Path.of("/opt/homebrew/lib/libvulkan.1.dylib")); // Apple Silicon / brew
			possiblePaths.add(Path.of("/usr/lib/libvulkan.1.dylib")); // less common
			// Vulkan SDK will have it too
			String sdkRoot = System.getenv("VULKAN_SDK");
			if(sdkRoot != null)
			{
				possiblePaths.add(Path.of(sdkRoot, "lib", "libvulkan.1.dylib"));
			}
		}
		
		for(Path path : possiblePaths)
		{
			if(Files.exists(path))
			{
				return path;
			}
		}
		
		return null;
	}
	
	/**
	 * Tries to find and load the Vulkan runtime library on this machine by looking in well known paths according to the operating system.
	 * 
	 * <p>
	 * Unless it's already on the system's loader search path, you need to manually load the Vulkan runtime yourself using {@link System#load(String)}.
	 * </p>
	 * 
	 * @return if the runtime was found
	 */
	public static boolean findAndLoadVulkanRuntime()
	{
		Path runtime = findVulkanRuntime();
		boolean found = runtime != null;
		
		if(found)
		{
			System.load(runtime.toAbsolutePath().toString());
		}
		
		return found;
	}
	
	/**
	 * Returns a {@link Path} object the internal resource specified by the URI.
	 * 
	 * @param uri URI to internal resource
	 * @return {@link Path} object
	 * @throws IOException if something goes wrong
	 */
	public static Path getPathToResource(URI uri) throws IOException
	{
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
			
			return fs.getPath(uri.getPath());
		}
		
		Path originDir = Paths.get(uri);
		return originDir;
	}
	
	/**
	 * Helper method that extracts internal resources to a temporary directory.
	 * 
	 * @param logger SLF4J {@link Logger}
	 * @param uri    internal resource
	 * @return path to newly created temporary directory
	 * @throws IOException if something goes wrong
	 */
	public static Path extractFolderToTemp(Logger logger, URI uri) throws IOException
	{
		logger.info("Extracting libs from {} (OS: {}, architecture: {})", uri, OS_NAME, OS_ARCH);
		
		Path originDir = getPathToResource(uri);
		
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
	
	/**
	 * Sequentially loads each native in the provided directory according to the Whisper JNI load order.
	 * 
	 * <p>
	 * <B>NOTE:</b> If you are loading Vulkan natives, <b>you are responsible for loading the Vulkan runtime!</b>. You can use {@link #findVulkanRuntime()} to try
	 * to find a Vulkan runtime. Also ensure you load the natives that match the machine's OS / architecture. This class provides helpful utility methods for that
	 * logic as well (such as {@link #getOS()} and {@link #getArchitecture()}).
	 * </p>
	 * 
	 * <p>
	 * Loading Vulkan natives example:
	 * </p>
	 * 
	 * <pre>
	 * // Ensure you have some kind of logic to match the natives to the machine's OS / arch
	 * Path vulkanNatives = Path.of("path", "to", "whisperjni-vulkan-natives");
	 * if(LibraryUtils.findAndLoadVulkanRuntime(true))
	 * {
	 * 	logger.info("Found and loaded the Vulkan runtime! Loading the Vulkan natives");
	 * 	LibraryUtils.loadLibrary(logger, vulkanNatives);
	 * }
	 * </pre>
	 * 
	 * <p>
	 * You may need to load the natives from within a JAR instead of from a fixed path on disk. Extracting resources from a JAR is notoriously awkward in Java, but
	 * this class provides helper methods to extract your bundled natives to a temporary directory to then load afterwards. See
	 * {@link WhisperJNI#loadLibrary(Logger)} for an example.
	 * </p>
	 * 
	 * <p>
	 * About the load order: it's an internal assorted array of the expected names of the natives from least-dependent to most-dependent on other natives within the
	 * same directory. Sorting the natives is required, as you can't load a native that depends one that isn't loaded yet. However, this problem can be avoided by
	 * adding the natives directory to <code>java.library.path</code> and invoking <code>System.loadLibrary("whisper-jni")</code> to handle the dependency logic
	 * automatically.
	 * </p>
	 * 
	 * @param logger     SLF4J {@link Logger} instance
	 * @param nativesDir path to the directory containing the natives to load
	 * @throws IOException if something goes wrong
	 */
	public static void loadLibrary(Logger logger, Path nativesDir) throws IOException
	{
		// Now load everything in the correct order
		List<String> natives = Stream.of(nativesDir.toFile().listFiles()).sorted(Comparator.comparing(file ->
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
		
		if(natives.isEmpty())
		{
			logger.error("Failed to find any natives. If you're running in an IDE, make sure you build the natives for your platform before testing using the build scripts");
		}
		else
		{
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
	}
}
