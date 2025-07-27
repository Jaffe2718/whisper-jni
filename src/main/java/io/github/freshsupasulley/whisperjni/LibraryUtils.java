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
	private static final List<String> loadOrder = Arrays.asList("ggml-base", "ggml-cpu", "ggml-vulkan", "ggml", "whisper", "whisper-jni");
	private static final String[] LIB_NAMES = {".so", ".dylib", ".dll"};
	
	private LibraryUtils()
	{
	}
	
	static String getArchitecture()
	{
		return switch(OS_ARCH)
		{
			case "i386", "i486", "i586", "i686", "x86", "x86_32" -> "x86";
			case "amd64", "x86_64", "x86-64" -> "x64";
			case "aarch64" -> "aarch64";
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
	 * @param logger      SLF4J {@link Logger}
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
	 * A system with <code>vulkan-1.dll</code> present on their SystemRoot indicates it can use Vulkan.
	 * 
	 * @return path to <code>vulkan-1.dll</code>, or <code>null</code> if not found
	 */
	public static Path getVulkanDLL()
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
		// ^ Vulkan on Mac isn't a thing + metal is fast enough already so we're not considering it
		
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
	 * Determines if this system has the Vulkan DLL installed and can therefore attempt to load the Vulkan natives for whisper.cpp.
	 * 
	 * <p>
	 * Only relevant on Windows and Linux systems, as Mac doesn't support Vulkan natively (and Metal is quite fast already).
	 * </p>
	 * 
	 * @return true if this system can use the Vulkan natives, false otherwise
	 */
	public static boolean canUseVulkan()
	{
		return LibraryUtils.getVulkanDLL() != null;
	}
	
	/**
	 * Loads the custom Vulkan natives. Use {@link WhisperJNI#canUseVulkan()} before calling this method.
	 * 
	 * <p>
	 * If you're loading the natives from a fixed location on your hard disk, providing the path to the natives folder statically will work fine. However, if you're
	 * bundling your natives into a jar and need to extract and load them at runtime, you should use {@link #extractFolderToTemp(Logger, URI)} first and use the
	 * result as the native directory for this method.
	 * </p>
	 * 
	 * <p>
	 * <B>NOTE:</b> Natives are built for particular platforms, so ensure your logic only loads the correct ones. You may use the getters in this class for your
	 * conditional logic.
	 * </p>
	 * 
	 * @param logger     SLF4J {@link Logger}
	 * @param nativesDir path to the directory containing all natives to load
	 */
	public static void loadVulkan(Logger logger, Path nativesDir)
	{
		if(!canUseVulkan())
			throw new IllegalStateException("This system can't use Vulkan natives");
		
		logger.info("Loading Vulkan natives for whisper-jni");
		
		try
		{
			String vulkanPath = getVulkanDLL().toAbsolutePath().toString();
			logger.info("Loading Vulkan DLL at {}", vulkanPath);
			System.load(vulkanPath);
			
			// Now load our Vulkan dependencies
			loadInOrder(logger, nativesDir);
		} catch(Exception e)
		{
			logger.error("Failed to load Vulkan natives", e);
		}
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
	 * Loads all natives defined in the provided directory.
	 * 
	 * <p>
	 * If loading resources internally (from a jar), you should first extract them to a temp directory using {@link #extractFolderToTemp(Logger, URI)}.
	 * </p>
	 * 
	 * @param logger     SLF4J {@link Logger} instance
	 * @param nativesDir path to the directory containing the natives to load
	 * @throws IOException if something goes wrong
	 */
	public static void loadInOrder(Logger logger, Path nativesDir) throws IOException
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
