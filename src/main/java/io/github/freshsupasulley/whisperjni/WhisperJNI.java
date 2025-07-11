package io.github.freshsupasulley.whisperjni;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WhisperJNI} class allows to use whisper.cpp thought the JNI.
 *
 * @author Miguel Alvarez Díez - Initial contribution
 */
public class WhisperJNI {
	
	private static boolean libraryLoaded;
	
	// region native api
	private native int init(String model, WhisperContextParams params);
	
	private native int initNoState(String model, WhisperContextParams params);
	
	private native int initState(int model);
	
	private native int loadGrammar(String text);
	
	private native void initOpenVINOEncoder(int model, String device);
	
	private native boolean isMultilingual(int model);
	
	private native int full(int context, WhisperFullParams params, float[] samples, int numSamples);
	
	private native int fullWithState(int context, int state, WhisperFullParams params, float[] samples, int numSamples);
	
	private native int fullNTokens(int context, int segment);
	
	private native int fullNTokensFromState(int state, int segment);
	
	private native TokenData getTokenData(int context, int segment, int token);
	
	private native TokenData getTokenDataFromState(int context, int state, int segment, int token);
	
	private native int fullNSegments(int context);
	
	private native int fullNSegmentsFromState(int state);
	
	private native long fullGetSegmentTimestamp0(int context, int index);
	
	private native long fullGetSegmentTimestamp1(int context, int index);
	
	private native String fullGetSegmentText(int context, int index);
	
	private native long fullGetSegmentTimestamp0FromState(int state, int index);
	
	private native long fullGetSegmentTimestamp1FromState(int state, int index);
	
	private native String fullGetSegmentTextFromState(int state, int index);
	
	private native void freeContext(int context);
	
	private native void freeState(int state);
	
	private native void freeGrammar(int grammar);
	
	private native String printSystemInfo();
	
	private native static void setLogger(Logger logger);
	
	// endregion
	
	/**
	 * Sets the SLF4J {@link Logger} to receive internal whisper events.
	 * 
	 * @param logger {@link Logger} SLF4J instance
	 */
	public void setWhisperLogger(Logger logger)
	{
		setLogger(logger);
	}
	
	/**
	 * Creates a new whisper context.
	 *
	 * @param model {@link Path} to the whisper ggml model file.
	 * @return A new {@link WhisperContext}.
	 * @throws IOException if model file is missing.
	 */
	public WhisperContext init(Path model) throws IOException
	{
		return init(model, null);
	}
	
	/**
	 * Creates a new whisper context.
	 *
	 * @param model  {@link Path} to the whisper ggml model file.
	 * @param params {@link WhisperContextParams} params for context initialization.
	 * @return A new {@link WhisperContext}.
	 * @throws IOException if model file is missing.
	 */
	public WhisperContext init(Path model, WhisperContextParams params) throws IOException
	{
		assertModelExists(model);
		if(params == null)
		{
			params = new WhisperContextParams();
		}
		int ref = init(model.toAbsolutePath().toString(), params);
		if(ref == -1)
		{
			return null;
		}
		return new WhisperContext(this, ref);
	}
	
	/**
	 * Creates a new whisper context without state.
	 *
	 * @param model {@link Path} to the whisper ggml model file.
	 * @return A new {@link WhisperContext} without state.
	 * @throws IOException if model file is missing.
	 */
	public WhisperContext initNoState(Path model) throws IOException
	{
		return initNoState(model, null);
	}
	
	/**
	 * Creates a new whisper context without state.
	 *
	 * @param model  {@link Path} to the whisper ggml model file.
	 * @param params {@link WhisperContextParams} params for context initialization.
	 * @return A new {@link WhisperContext} without state.
	 * @throws IOException if model file is missing.
	 */
	public WhisperContext initNoState(Path model, WhisperContextParams params) throws IOException
	{
		assertModelExists(model);
		if(params == null)
		{
			params = new WhisperContextParams();
		}
		int ref = initNoState(model.toAbsolutePath().toString(), params);
		if(ref == -1)
		{
			return null;
		}
		return new WhisperContext(this, ref);
	}
	
	/**
	 * Creates a new whisper.cpp state for the provided context.
	 *
	 * @param context the {@link WhisperContext} of this state.
	 * @return A new {@link WhisperContext}.
	 */
	public WhisperState initState(WhisperContext context)
	{
		WhisperJNIPointer.assertAvailable(context);
		int ref = initState(context.ref);
		if(ref == -1)
		{
			return null;
		}
		return new WhisperState(this, ref, context);
	}
	
	public WhisperGrammar parseGrammar(Path grammarPath) throws IOException
	{
		if(!Files.exists(grammarPath) || Files.isDirectory(grammarPath))
		{
			throw new FileNotFoundException("Grammar file not found");
		}
		return parseGrammar(Files.readString(grammarPath));
	}
	
	public WhisperGrammar parseGrammar(String text) throws IOException
	{
		if(text.isBlank())
		{
			throw new IOException("Grammar text is blank");
		}
		int ref = loadGrammar(text);
		if(ref == -1)
		{
			return null;
		}
		return new WhisperGrammar(this, ref, text);
	}
	
	/**
	 * Initializes OpenVino encoder.
	 *
	 * @param context a {@link WhisperContext} instance.
	 * @param device  the device name.
	 */
	public void initOpenVINO(WhisperContext context, String device)
	{
		WhisperJNIPointer.assertAvailable(context);
		initOpenVINOEncoder(context.ref, device);
	}
	
	/**
	 * Is multilingual.
	 *
	 * @param context the {@link WhisperContext} to check.
	 * @return true if model support multiple languages
	 */
	public boolean isMultilingual(WhisperContext context)
	{
		WhisperJNIPointer.assertAvailable(context);
		return isMultilingual(context.ref);
	}
	
	/**
	 * Run whisper.cpp full audio transcription.
	 *
	 * @param context    the {@link WhisperContext} used to transcribe.
	 * @param params     a {@link WhisperFullParams} instance with the desired configuration.
	 * @param samples    the audio samples (f32 encoded samples with sample rate 16000).
	 * @param numSamples the number of audio samples provided.
	 * @return a result code, values other than 0 indicates problems.
	 */
	public int full(WhisperContext context, WhisperFullParams params, float[] samples, int numSamples)
	{
		WhisperJNIPointer.assertAvailable(context);
		if(params.grammar != null)
		{
			WhisperJNIPointer.assertAvailable(params.grammar);
		}
		return full(context.ref, params, samples, numSamples);
	}
	
	/**
	 * Run whisper.cpp full audio transcription.
	 *
	 * @param context    the {@link WhisperContext} used to transcribe.
	 * @param state      the {@link WhisperState} used to transcribe.
	 * @param params     a {@link WhisperFullParams} instance with the desired configuration.
	 * @param samples    the audio samples (f32 encoded samples with sample rate 16000).
	 * @param numSamples the number of audio samples provided.
	 * @return a result code, values other than 0 indicates problems.
	 */
	public int fullWithState(WhisperContext context, WhisperState state, WhisperFullParams params, float[] samples, int numSamples)
	{
		WhisperJNIPointer.assertAvailable(context);
		WhisperJNIPointer.assertAvailable(state);
		if(params.grammar != null)
		{
			WhisperJNIPointer.assertAvailable(params.grammar);
		}
		return fullWithState(context.ref, state.ref, params, samples, numSamples);
	}
	
	/**
	 * Gets the tokens in the specified segment.
	 * 
	 * <p>
	 * Whisper includes extra tokens like timestamps or start / end of segments. These are removed.
	 * </p>
	 * 
	 * @param context the {@link WhisperContext} used to transcribe.
	 * @param segment segment index
	 * @return tokens in this segment
	 */
	public TokenData[] getTokens(WhisperContext context, int segment)
	{
		TokenData[] tokens = new TokenData[fullNTokens(context.ref, segment)];
		for(int i = 0; i < tokens.length; i++)
		{
			tokens[i] = getTokenData(context.ref, segment, i);
		}
		return filterTokens(tokens);
	}
	
	/**
	 * Gets the tokens in the specified segment.
	 * 
	 * <p>
	 * Whisper includes extra tokens like timestamps or start / end of segments. These are removed.
	 * </p>
	 * 
	 * @param context the {@link WhisperContext} used to transcribe
	 * @param state   the {@link WhisperState} used to transcribe
	 * @param segment segment index
	 * @return tokens in this segment
	 */
	public TokenData[] getTokensFromState(WhisperContext context, WhisperState state, int segment)
	{
		// whisper_full_n_tokens
		TokenData[] tokens = new TokenData[fullNTokensFromState(state.ref, segment)];
		for(int i = 0; i < tokens.length; i++)
		{
			tokens[i] = getTokenDataFromState(context.ref, state.ref, segment, i);
		}
		return filterTokens(tokens);
	}
	
	private TokenData[] filterTokens(TokenData[] tokens)
	{
		// Check if it's a special token
		// ... whisper does something similar so I feel less bad about it, but this is still ugly:
		// if (text.rfind("[_", 0) == 0) {
		// An alternative would be grabbing the special token IDs, either programatically or hard coding them
		return Stream.of(tokens).filter(token -> !token.token.startsWith("[_") && !token.token.startsWith("<|")).toArray(TokenData[]::new);
	}
	
	/**
	 * Gets the available number of text segments.
	 *
	 * @param state the {@link WhisperState} used to transcribe
	 * @return available number of segments
	 */
	public int fullNSegmentsFromState(WhisperState state)
	{
		WhisperJNIPointer.assertAvailable(state);
		return fullNSegmentsFromState(state.ref);
	}
	
	/**
	 * Gets the available number of text segments.
	 *
	 * @param context the {@link WhisperContext} used to transcribe
	 * @return available number of segments
	 */
	public int fullNSegments(WhisperContext context)
	{
		WhisperJNIPointer.assertAvailable(context);
		return fullNSegments(context.ref);
	}
	
	/**
	 * Gets start timestamp of text segment by index.
	 *
	 * @param context a {@link WhisperContext} used to transcribe
	 * @param index   the segment index
	 * @return start timestamp of segment text, 800 -> 8s
	 */
	public long fullGetSegmentTimestamp0(WhisperContext context, int index)
	{
		WhisperJNIPointer.assertAvailable(context);
		return fullGetSegmentTimestamp0(context.ref, index);
	}
	
	/**
	 * Gets end timestamp of text segment by index.
	 *
	 * @param context a {@link WhisperContext} used to transcribe
	 * @param index   the segment index
	 * @return end timestamp of segment text, 1050 -> 10.5s
	 */
	public long fullGetSegmentTimestamp1(WhisperContext context, int index)
	{
		WhisperJNIPointer.assertAvailable(context);
		return fullGetSegmentTimestamp1(context.ref, index);
	}
	
	/**
	 * Gets text segment by index.
	 *
	 * @param context a {@link WhisperContext} used to transcribe
	 * @param index   the segment index
	 * @return the segment text
	 */
	public String fullGetSegmentText(WhisperContext context, int index)
	{
		WhisperJNIPointer.assertAvailable(context);
		return fullGetSegmentText(context.ref, index);
	}
	
	/**
	 * Gets start timestamp of text segment by index.
	 *
	 * @param state a {@link WhisperState} used to transcribe
	 * @param index the segment index
	 * @return start timestamp of segment text, 1050 -> 10.5s
	 */
	public long fullGetSegmentTimestamp0FromState(WhisperState state, int index)
	{
		WhisperJNIPointer.assertAvailable(state);
		return fullGetSegmentTimestamp0FromState(state.ref, index);
	}
	
	/**
	 * Gets end timestamp of text segment by index.
	 *
	 * @param state a {@link WhisperState} used to transcribe
	 * @param index the segment index
	 * @return end timestamp of segment text, 1050 -> 10.5s
	 */
	public long fullGetSegmentTimestamp1FromState(WhisperState state, int index)
	{
		WhisperJNIPointer.assertAvailable(state);
		return fullGetSegmentTimestamp1FromState(state.ref, index);
	}
	
	/**
	 * Gets text segment by index.
	 *
	 * @param state a {@link WhisperState} used to transcribe
	 * @param index the segment index
	 * @return the segment text
	 */
	public String fullGetSegmentTextFromState(WhisperState state, int index)
	{
		WhisperJNIPointer.assertAvailable(state);
		return fullGetSegmentTextFromState(state.ref, index);
	}
	
	/**
	 * Release context memory in native implementation.
	 *
	 * @param context the {@link WhisperContext} to release
	 */
	public void free(WhisperJNIPointer context)
	{
		if(context.isReleased())
		{
			return;
		}
		freeContext(context.ref);
		context.release();
	}
	
	/**
	 * Release state memory in native implementation.
	 *
	 * @param state the {@link WhisperState} to release
	 */
	public void free(WhisperState state)
	{
		if(state.isReleased())
		{
			return;
		}
		freeState(state.ref);
		state.release();
	}
	
	/**
	 * Release grammar memory in native implementation.
	 *
	 * @param grammar the {@link WhisperGrammar} to release
	 */
	public void free(WhisperGrammar grammar)
	{
		if(grammar.isReleased())
		{
			return;
		}
		freeGrammar(grammar.ref);
		grammar.release();
	}
	
	/**
	 * Get whisper.cpp system info stream, to check enabled features in whisper.
	 *
	 * @return the whisper.cpp system info stream.
	 */
	public String getSystemInfo()
	{
		return printSystemInfo();
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
		Path path = LibraryUtils.getInternalResource(logger, "ggml-silero-v5.1.2.bin");
		
		logger.debug("Copying {} to {}", path, destination);
		Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
	}
	
	/**
	 * Register the native library, should be called at first.
	 * 
	 * <p>
	 * If you want to use Vulkan natives instead, manually call {@link LibraryUtils#loadVulkan(Logger)}.
	 * </p>
	 * 
	 * @throws IOException when unable to load the native library
	 */
	public static void loadLibrary() throws IOException
	{
		loadLibrary(LoggerFactory.getLogger(WhisperJNI.class));
	}
	
	/**
	 * Register the native library, should be called at first.
	 *
	 * @param logger SLF4J {@link Logger}.
	 * @throws IOException when unable to load the native library.
	 */
	public static void loadLibrary(Logger logger) throws IOException
	{
		if(libraryLoaded)
		{
			return;
		}
		
		LibraryUtils.loadLibrary(logger);
		libraryLoaded = true;
	}
	
	/**
	 * Use to determine if this system can custom-built Vulkan libs. Must be on Windows with a 64-bit processor, and <b>most importantly</b>
	 * <code>vulkan-1.dll</code> at <code>/System32/vulkan-1.dll</code>.
	 * 
	 * @return true if this system can use the Vulkan natives, false otherwise
	 */
	public static boolean canUseVulkan()
	{
		return LibraryUtils.isWindows() && LibraryUtils.getArchitecture().equals("x64") && LibraryUtils.getVulkanDLL() != null;
	}
	
	/**
	 * Loads Vulkan natives with a default logger. Use {@link #canUseVulkan()} before calling this method.
	 * 
	 * @throws IOException if something went wrong loading Vulkan natives
	 */
	public static void loadVulkan() throws IOException
	{
		LibraryUtils.loadVulkan(LoggerFactory.getLogger(WhisperJNI.class));
	}
	
	/**
	 * Loads Vulkan natives. Use {@link #canUseVulkan()} before calling this method.
	 * 
	 * @param logger SLF4J {@link Logger}
	 * @throws IOException if something went wrong loading Vulkan natives
	 */
	public static void loadVulkan(Logger logger) throws IOException
	{
		LibraryUtils.loadVulkan(logger);
	}
	
	/**
	 * In order to avoid sharing pointers between the c++ and java, we use this util base class which holds a random integer id generated in the whisper.cpp
	 * wrapper.
	 *
	 * @author Miguel Alvarez Díez - Initial contribution
	 */
	protected static abstract class WhisperJNIPointer implements AutoCloseable {
		
		/**
		 * Native pointer reference identifier.
		 */
		protected final int ref;
		private boolean released;
		
		/**
		 * Asserts the provided pointer is still available.
		 *
		 * @param pointer a {@link WhisperJNIPointer} instance representing a pointer.
		 */
		protected static void assertAvailable(WhisperJNIPointer pointer)
		{
			if(pointer.isReleased())
			{
				throw new RuntimeException("Unavailable pointer, object is closed");
			}
		}
		
		/**
		 * Creates a new object used to represent a struct pointer on the native library.
		 *
		 * @param ref a random integer id generated by the native wrapper
		 */
		protected WhisperJNIPointer(int ref)
		{
			this.ref = ref;
		}
		
		/**
		 * Return true if native memory is free
		 *
		 * @return a boolean indicating if the native data was already released
		 */
		protected boolean isReleased()
		{
			return released;
		}
		
		/**
		 * Mark the point as released
		 */
		protected void release()
		{
			released = true;
		}
	}
	
	private static void assertModelExists(Path model) throws IOException
	{
		if(!Files.exists(model) || Files.isDirectory(model))
		{
			throw new IOException("Missing model file: " + model);
		}
	}
}
