package io.github.givimad.whisperjni.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
	
	/**
	 * We need to load multiple files but all in order.
	 * 
	 * <p>
	 * ... apparently Linux works just fine when its ggml lib is named that insane string and thus doesn't get picked up in the correct order.
	 * </p>
	 */
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
			for(int i = 0; i < loadOrder.size(); i++)
			{
				// adding the . differentiates between files like 'ggml' and 'ggml-base', ensuring its at the suffix
				if(file.getName().contains(loadOrder.get(i) + "."))
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
	// public static Path extractFolderToTemp(Logger logger, String folderName) throws IOException
	// {
	// logger.info("Extracting libs from {} (OS: {}, arch: {})", folderName, OS_NAME, OS_ARCH);
	//
	// // Find the path to this library's JAR
	// URL resourceURL = LibraryUtils.class.getProtectionDomain().getCodeSource().getLocation();
	//
	// System.out.println(resourceURL);
	// // By default, assume its a file: protocol
	// String jarFile = resourceURL.getFile();
	//
	// if(resourceURL.getProtocol().equals("jar"))
	// {
	// JarURLConnection connection = (JarURLConnection) resourceURL.openConnection();
	//
	// try
	// {
	// jarFile = new File(connection.getJarFileURL().toURI()).getAbsolutePath();
	// } catch(URISyntaxException e)
	// {
	// throw new IOException("Failed to open connection to jar", e);
	// }
	//
	// logger.info("Reading JAR from {} (file: {})", resourceURL, jarFile);
	//
	// Path tmpDir = Files.createTempDirectory("jscribe_natives_");
	// tmpDir.toFile().deleteOnExit();
	//
	// logger.info("Creating temp directory at {}", tmpDir);
	//
	// try(JarFile jar = new JarFile(jarFile))
	// {
	// Enumeration<JarEntry> entries = jar.entries();
	//
	// while(entries.hasMoreElements())
	// {
	// JarEntry entry = entries.nextElement();
	//
	// // Only load the files, not the directory
	// if(entry.getName().startsWith(folderName) && !entry.isDirectory())
	// {
	// // Get the input stream for the file in the JAR
	// try(InputStream inputStream = jar.getInputStream(entry))
	// {
	// // Copy the file to a temporary location
	// Path targetPath = tmpDir.resolve(entry.getName());
	// Files.createDirectories(targetPath.getParent());
	// logger.info("Extracting native {} to {}", entry.getName(), targetPath);
	// Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
	// }
	// }
	// }
	// }
	//
	// logger.info("Finished loading natives");
	// return tmpDir.resolve(folderName);
	// }
	// else if(resourceURL.toString().endsWith(".jar"))
	// {
	//
	// }
	// else
	// {
	// logger.info("We are likely in an IDE, resource is on disk");
	// return Path.of("src", "main", "resources", folderName);
	// }
	// }
	
	public static Path extractFolderToTemp(Logger log, String folderName) throws IOException
	{
		log.info("Extracting libs from {} (OS: {}, arch: {})", folderName, OS_NAME, OS_ARCH);
		
		/* 1️⃣ Resolve the resource to a URI */
		URI folderUri;
		try
		{
			folderUri = LibraryUtils.class.getResource("/" + folderName).toURI();
		} catch(URISyntaxException | NullPointerException e)
		{
			throw new IOException("Resource '" + folderName + "' not found on classpath", e);
		}
		
		FileSystem fs = null;
		Path originDir;
		if("jar".equals(folderUri.getScheme()))
		{
			fs = FileSystems.newFileSystem(folderUri, Map.of());
			originDir = fs.getPath("/" + folderName);
			log.debug("Resource is inside JAR: {}", folderUri);
		}
		else
		{
			originDir = Paths.get(folderUri);
			log.debug("Resource is a directory on disk: {}", originDir);
		}
		
		/* 3️⃣ Copy (recursively) to a temp directory */
		Path tmpDir = Files.createTempDirectory("whisperjni_");
		log.info("Copying natives to temporary dir {}", tmpDir);
		
		Files.walk(originDir).forEach(p ->
		{
			try
			{
				Path dest = tmpDir.resolve(originDir.relativize(p).toString());
				if(Files.isDirectory(p))
				{
					Files.createDirectories(dest);
				}
				else
				{
					Files.createDirectories(dest.getParent());
					Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch(IOException ex)
			{
				throw new UncheckedIOException(ex);
			}
		});
		
		/* 4️⃣ Clean up FileSystem if we opened one */
		if(fs != null)
		{
			fs.close();
		}
		
		log.info("Finished extracting natives");
		return tmpDir;
	}
	
	private void extractJar(Logger logger, String folderName)
	{
		
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
			/// ^ nvm, whisper-jni has private dependencies
			Path tempDir = extractFolderToTemp(logger, "windows-x64-vulkan");
			// System.load(tempDir.resolve("ggml-base.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-cpu.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml-vulkan.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("ggml.dll").toAbsolutePath().toString());
			// System.load(tempDir.resolve("whisper.dll").toAbsolutePath().toString());
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
