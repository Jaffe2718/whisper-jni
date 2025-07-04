package io.github.givimad.whisperjni.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapted from <a href="https://github.com/henkelmax/rnnoise4j/blob/master/src/main/java/de/maxhenkel/rnnoise4j/LibraryLoader.java">RNNoise4J</a> <3
 */
public class LibraryUtils {
	
	/** OS name */
	public static final String OS_NAME = System.getProperty("os.name").toLowerCase();
	/** OS architecture */
	public static final String OS_ARCH = System.getProperty("os.arch").toLowerCase();
	
	/** We need to load multiple files but all in order */
	private static final List<String> loadOrder = Arrays.asList("ggml-base", "ggml-cpu", "ggml-vulkan", "ggml", "whisper", "whisper-jni");
	
	private static final String[] LIB_NAMES = {".so", ".dylib", ".dll"};
	
	private LibraryUtils()
	{
	}
	
	/**
	 * Register the native library, should be called at first.
	 * 
	 * @throws IOException when unable to load the native library
	 */
	public static void loadLibrary(Logger logger) throws IOException
	{
		Path tempLib = extractFolderToTemp(logger, getPlatform() + "-" + getArchitecture());
		loadInOrder(logger, tempLib);
	}
	
	private static void loadInOrder(Logger logger, Path tempDir)
	{
		// Now load everything in the correct order
		Stream.of(tempDir.toFile().listFiles()).sorted(Comparator.comparing(file ->
		{
			String name = file.getName();
			for(int i = 0; i < loadOrder.size(); i++)
			{
				if(name.contains(loadOrder.get(i) + "."))
				{
					return i; // return index of match as priority
				}
			}
			return Integer.MAX_VALUE; // unknown files go last
		})).map(file -> file.getAbsolutePath()).filter(file -> Stream.of(LIB_NAMES).anyMatch(suffix -> file.endsWith(suffix))).forEach(path ->
		{
			logger.info("Loading {}", path);
			System.load(path);
		});
	}
	
	/**
	 * Extracts a folder from this jar to a temp directory which is deleted at JVM exit.
	 * 
	 * @param folderName name of the folder (i.e. "natives/win-amd64-vulkan")
	 * @return temp directory path
	 * @throws IOException if something went wrong
	 */
	public static Path extractFolderToTemp(Logger logger, String folderName) throws IOException
	{
		logger.info("Extracting libs from {} (OS: {}, arch: {})", folderName, OS_NAME, OS_ARCH);
		
		// Find the path to this library's JAR
		URL resourceURL = LibraryUtils.class.getProtectionDomain().getCodeSource().getLocation();
		
		// By default, assume its a file: protocol
		String jarFile = resourceURL.getFile();
		
		// If it's a jar: protocol
		switch(resourceURL.getProtocol())
		{
			case "jar":
			{
				JarURLConnection connection = (JarURLConnection) resourceURL.openConnection();
				
				try
				{
					jarFile = new File(connection.getJarFileURL().toURI()).getAbsolutePath();
				} catch(URISyntaxException e)
				{
					throw new IOException("Failed to open connection to jar", e);
				}
				
				logger.info("Reading JAR from {} (file: {})", resourceURL, jarFile);
				
				Path tmpDir = Files.createTempDirectory("jscribe_natives_");
				tmpDir.toFile().deleteOnExit();
				
				logger.info("Creating temp directory at {}", tmpDir);
				
				try(JarFile jar = new JarFile(jarFile))
				{
					Enumeration<JarEntry> entries = jar.entries();
					
					while(entries.hasMoreElements())
					{
						JarEntry entry = entries.nextElement();
						
						// Only load the files, not the directory
						if(entry.getName().startsWith(folderName) && !entry.isDirectory())
						{
							// Get the input stream for the file in the JAR
							try(InputStream inputStream = jar.getInputStream(entry))
							{
								// Copy the file to a temporary location
								Path targetPath = tmpDir.resolve(entry.getName());
								Files.createDirectories(targetPath.getParent());
								logger.info("Extracting native {} to {}", entry.getName(), targetPath);
								Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
							}
						}
					}
				}
				
				logger.info("Finished loading natives");
				return tmpDir.resolve(folderName);
			}
			case "file":
			{
				logger.info("We are likely in an IDE, resource is on disk");
				return Path.of("src", "main", "resources", folderName);
			}
			default:
			{
				throw new IOException("Unknown protocol: " + resourceURL);
			}
		}
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
			// First load vulkan-1.dll
			String vulkanPath = getVulkanDLL().toAbsolutePath().toString();
			logger.info("Loading Vulkan DLL at {}", vulkanPath);
			System.load(vulkanPath);
			
			// Now load our dependencies in this specific order
			Path tempDir = extractFolderToTemp(logger, "win-amd64-vulkan");
			System.load(tempDir.resolve("ggml-base.dll").toAbsolutePath().toString());
			System.load(tempDir.resolve("ggml-cpu.dll").toAbsolutePath().toString());
			System.load(tempDir.resolve("ggml-vulkan.dll").toAbsolutePath().toString());
			System.load(tempDir.resolve("ggml.dll").toAbsolutePath().toString());
			System.load(tempDir.resolve("whisper.dll").toAbsolutePath().toString());
			System.load(tempDir.resolve("whisper-jni.dll").toAbsolutePath().toString());
			loadInOrder(logger, tempDir);
		} catch(Exception e)
		{
			logger.error("Failed to load Vulkan natives", e);
		}
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
	
	/**
	 * Use to determine if this system can custom-built Vulkan libs. Must be on Windows with AMD 64-bit processor with <code>vulkan-1.dll</code> installed.
	 * 
	 * @return true if this system can use Vulkan, false otherwise
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
}
