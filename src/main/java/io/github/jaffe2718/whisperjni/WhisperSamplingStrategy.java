package io.github.jaffe2718.whisperjni;

/**
 * The {@link WhisperContext} enum to configure whisper's sampling strategy
 *
 * @author Miguel Alvarez Díez - Initial contribution
 */
public interface WhisperSamplingStrategy
{
	/**
	 * Similar to OpenAI's GreedyDecoder
	 */
	int GREEDY = 0;
	/**
	 * Similar to OpenAI's BeamSearchDecoder
	 */
	int BEAM_SEARCH = 1;
}