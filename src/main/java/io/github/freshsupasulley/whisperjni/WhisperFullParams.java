package io.github.freshsupasulley.whisperjni;

/**
 * The {@link WhisperFullParams} instances needed to configure full whisper execution
 *
 * @author Miguel Alvarez Díez - Initial contribution
 */
public class WhisperFullParams {
	
	/**
	 * Whisper search strategy.
	 */
	private final int strategy;
	
	/**
	 * Number of thread, 0 for max cores
	 */
	public int nThreads = 0;
	/**
	 * Overwrite the audio context size (0 = use default)
	 */
	public int audioCtx;
	/**
	 * Max tokens to use from past text as prompt for the decoder
	 */
	public int nMaxTextCtx = 16384;
	/**
	 * Start offset in ms
	 */
	public int offsetMs;
	/**
	 * Audio duration to process in ms
	 */
	public int durationMs;
	/**
	 * Translate
	 */
	public boolean translate;
	/**
	 * Do not generate timestamps
	 */
	public boolean noTimestamps;
	/**
	 * Detect language
	 */
	public boolean detectLanguage;
	/**
	 * Language
	 */
	public String language = "en";
	/**
	 * Initial prompt
	 */
	public String initialPrompt;
	/**
	 * Do not use past transcription (if any) as initial prompt for the decoder
	 */
	public boolean noContext = true;
	/**
	 * Force single segment output (useful for streaming)
	 */
	public boolean singleSegment;
	/**
	 * Print special tokens
	 */
	public boolean printSpecial;
	/**
	 * Print progress information
	 */
	public boolean printProgress = true;
	/**
	 * Print results from within whisper.cpp (avoid it, use callback instead)
	 */
	public boolean printRealtime;
	/**
	 * Print timestamps for each text segment when printing realtime
	 */
	public boolean printTimestamps = true;
	/**
	 * Decoder option
	 */
	public boolean suppressBlank = true;
	/**
	 * Tokenizer option
	 */
	public boolean suppressNonSpeechTokens;
	/**
	 * Initial decoding temperature
	 */
	public float temperature = 0.0f;
	/**
	 * Refer to library
	 */
	public float maxInitialTs = 1.0f;
	/**
	 * Refer to library
	 */
	public float lengthPenalty = -1.0f;
	/**
	 * Refer to library
	 */
	public float temperatureInc = 0.4f;
	/**
	 * Refer to library
	 */
	public float entropyThold = 2.4f;
	/**
	 * Refer to library
	 */
	public float logprobThold = -1.0f;
	/**
	 * Refer to library
	 */
	public float noSpeechThold = 0.6f;
	/**
	 * Specific to greedy sampling strategy
	 */
	public int greedyBestOf = -1;
	/**
	 * Specific to beam search sampling strategy
	 */
	public int beamSearchBeamSize = 2;
	/**
	 * Specific to beam search sampling strategy
	 */
	public float beamSearchPatience = -1.0f;
	/**
	 * GBNF grammar.
	 */
	public WhisperGrammar grammar;
	/**
	 * Penalty for non grammar tokens.
	 */
	public float grammarPenalty = 100f;
	
	// Voice Activity Detection (VAD) params
	/** Enable VAD */
	public boolean vad;
	/** Path to VAD model */
	public String vad_model_path;
	
	public final VADParams vadParams = new VADParams(); // needs to be initialized to something cause cpp side won't null check it
	
	/**
	 * Creates a new {@link WhisperFullParams} instance using the provided {@link WhisperSamplingStrategy}
	 *
	 * @param strategy the required {@link WhisperSamplingStrategy}
	 */
	public WhisperFullParams(WhisperSamplingStrategy strategy)
	{
		this.strategy = strategy.ordinal();
	}
	
	/**
	 * Creates a new {@link WhisperFullParams} instance using the greedy {@link WhisperSamplingStrategy}
	 */
	public WhisperFullParams()
	{
		this(WhisperSamplingStrategy.BEAM_SEARCH);
	}
	
	public class VADParams {
		
		/** Probability threshold to consider as speech */
		public float threshold;
		/** Min duration for a valid speech segment */
		public int min_speech_duration_ms;
		/** Min silence duration to consider speech as ended */
		public int min_silence_duration_ms;
		/** Max duration of a speech segment before forcing a new segment */
		public float max_speech_duration_s;
		/** Padding added before and after speech segments */
		public int speech_pad_ms;
		/** Overlap in seconds when copying audio samples from speech segment */
		public float samples_overlap;
	}
}