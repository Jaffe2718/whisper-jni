package io.github.freshsupasulley.whisperjni;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WhisperJNI} class allows to use whisper.cpp thought the JNI.
 * 
 * <p>
 * Be sure to load the natives using {@link #loadLibrary()} before invoking any native methods!
 * </p>
 *
 * @author Miguel Alvarez Díez - Initial contribution
 */
public class WhisperJNI {
	
	private native int init(String model, WhisperContextParams params);

    private native int initFromInputStream(InputStream inputStream, WhisperContextParams params);
	
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
	
	// New convenience method yipee
	private native String vadState(int content, int state, WhisperFullParams params, WhisperVADContextParams vadContextParams, float[] samples, int numSamples);
	
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
	
	/**
	 * Sets the whisper.cpp logger.
	 * 
	 * <p>
	 * You must first load the natives before calling this method.
	 * </p>
	 * 
	 * @param logger SLF4J {@link Logger}
	 */
	public static native void setLogger(Logger logger);
	
	/**
	 * Loads the default natives bundled with the library with a {@link Logger} instance to listen to important library loading events / problems.
	 * 
	 * <p>
	 * You can alternatively load your own natives (such as the faster Vulkan natives) using {@link LibraryUtils}.
	 * </p>
	 * 
	 * <p>
	 * After this method finishes successfully, consider setting {@link WhisperJNI#setLogger(Logger)} to listen to whisper.cpp events.
	 * </p>
	 *
	 * @param logger SLF4J {@link Logger} to log library loading events
	 * @throws IOException if something goes wrong loading the built-in natives
	 */
	public void loadLibrary(Logger logger) throws IOException
	{
		try
		{
			// the leading / is needed (same with extracting the ggml model in LibraryUtils)
			Path tempLib = LibraryUtils.extractResource(logger, WhisperJNI.class.getClassLoader().getResource(LibraryUtils.getOS() + "-" + LibraryUtils.getArchitecture()).toURI());
			LibraryUtils.loadLibrary(logger, tempLib);
		} catch(URISyntaxException | NullPointerException e)
		{
			logger.error("Failed to load built-in whisper-jni natives", e);
			throw new IOException(e);
		}
	}
	
	/**
	 * Loads the default natives bundled with the library.
	 * 
	 * <p>
	 * You can alternatively load your own natives (such as the faster Vulkan natives) using {@link LibraryUtils}.
	 * </p>
	 *
	 * @throws IOException if something goes wrong loading the built-in natives
	 */
	public void loadLibrary() throws IOException
	{
		loadLibrary(LoggerFactory.getLogger(WhisperJNI.class));
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
     * Creates a new whisper context from an {@link InputStream}.
     *
     * @param inputStream {@link InputStream} to the whisper ggml model file.
     * @return A new {@link WhisperContext}.
     * @throws IOException if model file is missing.
     */
    public WhisperContext init(InputStream inputStream) throws IOException
	{
		return init(inputStream, null);
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
     * Creates a new whisper context from an {@link InputStream}.
     *
     * @param inputStream {@link InputStream} to the whisper ggml model file.
     * @param params      {@link WhisperContextParams} params for context initialization.
     * @return A new {@link WhisperContext}.
     * @throws IOException if model file is missing.
     */
    public WhisperContext init(InputStream inputStream, WhisperContextParams params) throws IOException
	{
		if(params == null)
		{
			params = new WhisperContextParams();
		}
		int ref = initFromInputStream(inputStream, params);
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
	
	public String vadState(WhisperContext context, WhisperState state, WhisperFullParams params, WhisperVADContextParams vadContextParams, float[] samples, int numSamples)
	{
		return vadState(context.ref, state.ref, params, vadContextParams, samples, numSamples);
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
	 * In order to avoid sharing pointers between the c++ and java, we use this util base class which holds a random integer id generated in the whisper.cpp
	 * wrapper.
	 *
	 * @author Miguel Alvarez Díez - Initial contribution
	 */
	static abstract class WhisperJNIPointer implements AutoCloseable {
		
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
		static void assertAvailable(WhisperJNIPointer pointer)
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
		WhisperJNIPointer(int ref)
		{
			this.ref = ref;
		}
		
		/**
		 * Return true if native memory is free
		 *
		 * @return a boolean indicating if the native data was already released
		 */
		boolean isReleased()
		{
			return released;
		}
		
		/**
		 * Mark the point as released
		 */
		void release()
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
