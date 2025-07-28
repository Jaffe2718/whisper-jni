package io.github.freshsupasulley.whisperjni;

import static io.github.freshsupasulley.whisperjni.WhisperGrammar.assertValidGrammar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @Disabled
public class WhisperJNITest {
	
	private static Path testModelPath = Path.of("ggml-tiny.bin");
	private static Path samplePath = Path.of("src/main/native/whisper/samples/jfk.wav");
	// private static Path sample2Path = Path.of("src/test/resources/progress.wav");
	private static Path sampleAssistantGrammar = Path.of("src/main/native/whisper/grammars/assistant.gbnf");
	private static Path sampleChessGrammar = Path.of("src/main/native/whisper/grammars/chess.gbnf");
	private static Path sampleColorsGrammar = Path.of("src/main/native/whisper/grammars/colors.gbnf");
	private static WhisperJNI whisper;
	
	private static Logger logger = LoggerFactory.getLogger(WhisperJNITest.class);
	
	private static Path tempVAD;
	
	@BeforeAll
	public static void beforeAll() throws IOException
	{
		var modelFile = testModelPath.toFile();
		var sampleFile = samplePath.toFile();
		
		if(!modelFile.exists() || !modelFile.isFile())
		{
			throw new RuntimeException("Missing model file: " + testModelPath.toAbsolutePath());
		}
		if(!sampleFile.exists() || !sampleFile.isFile())
		{
			throw new RuntimeException("Missing sample file");
		}
		
		// Test extracting the VAD model
		tempVAD = Files.createTempFile("tempVAD", ".bin");
		LibraryUtils.exportVADModel(tempVAD);
		
		// Initialize before loading natives
		whisper = new WhisperJNI();
		
		// Check if we have Vulkan natives
		Path whisperJNIBuild = Path.of("whisperjni-build"); // for CI/CD
		
		// For CI/CD purposes, if you can use Vulkan, then you best believe the natives better be built for Vulkan too
		if(LibraryUtils.canUseVulkan() && Files.isDirectory(whisperJNIBuild))
		{
			LibraryUtils.loadVulkan(logger, whisperJNIBuild);
		}
		else
		{
			// Move build dir into where WhisperJNI expects the natives to be
			Path destinationDir = Path.of("src", "main", "resources", LibraryUtils.getOS() + "-" + LibraryUtils.getArchitecture());
			logger.info("Copying test natives into expected dir: {}", destinationDir.toAbsolutePath());
			
			try(DirectoryStream<Path> stream = Files.newDirectoryStream(whisperJNIBuild))
			{
				for(Path entry : stream)
				{
					Path dest = destinationDir.resolve(entry.getFileName());
					logger.info("Copying {} to {}", entry, dest);
					Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			
			whisper.loadLibrary(logger);
			WhisperJNI.setLogger(logger);
		}
	}
	
	@Test
	public void testInit() throws IOException
	{
		var ctx = whisper.init(testModelPath);
		assertNotNull(ctx);
		ctx.close();
	}
	
	@Test
	public void testInitNoState() throws IOException
	{
		var ctx = whisper.initNoState(testModelPath);
		assertNotNull(ctx);
		ctx.close();
	}
	
	@Test
	public void testContextIsMultilingual() throws IOException
	{
		var ctx = whisper.initNoState(testModelPath);
		assertNotNull(ctx);
		assertTrue(whisper.isMultilingual(ctx));
		ctx.close();
	}
	
	@Test
	public void testNewState() throws IOException
	{
		try(var ctx = whisper.initNoState(testModelPath))
		{
			assertNotNull(ctx);
			WhisperState state = whisper.initState(ctx);
			assertNotNull(state);
			state.close();
		}
	}
	
	@Test
	public void testSegmentIndexException() throws IOException
	{
		var ctx = whisper.init(testModelPath);
		Exception exception = assertThrows(IndexOutOfBoundsException.class, () ->
		{
			whisper.fullGetSegmentText(ctx, 1);
		});
		ctx.close();
		assertEquals("Index out of range", exception.getMessage());
	}
	
	@Test
	public void testPointerUnavailableException() throws UnsupportedAudioFileException, IOException
	{
		var ctx = whisper.init(testModelPath);
		float[] samples = readFileSamples(samplePath);
		var params = new WhisperFullParams();
		ctx.close();
		Exception exception = assertThrows(RuntimeException.class, () ->
		{
			whisper.full(ctx, params, samples, samples.length);
		});
		assertEquals("Unavailable pointer, object is closed", exception.getMessage());
	}
	
	@Test
	public void testTokens() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.init(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.BEAM_SEARCH);
			params.printTimestamps = false;
			int result = whisper.full(ctx, params, samples, samples.length);
			if(result != 0)
			{
				throw new RuntimeException("Transcription failed with code " + result);
			}
			int numSegments = whisper.fullNSegments(ctx);
			assertEquals(1, numSegments);
			String text = whisper.fullGetSegmentText(ctx, 0);
			assertEquals(" And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.", text);
			
			// Grab tokens from each segment
			for(int i = 0; i < numSegments; i++)
			{
				TokenData[] tokens = whisper.getTokens(ctx, i);
				
				for(TokenData token : tokens)
				{
					logger.info("TOKEN: '{}'", token.token);
				}
				
				assertEquals(tokens.length, 26);
			}
		}
	}
	
	@Test
	public void testTokensWithState() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.initNoState(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			params.noTimestamps = true;
			params.printProgress = false;
			params.printRealtime = false;
			params.printSpecial = false;
			try(var state = whisper.initState(ctx))
			{
				assertNotNull(state);
				int result = whisper.fullWithState(ctx, state, params, samples, samples.length);
				if(result != 0)
				{
					throw new RuntimeException("Transcription failed with code " + result);
				}
				int numSegments = whisper.fullNSegmentsFromState(state);
				assertEquals(1, numSegments);
				
				// Grab tokens from each segment
				for(int i = 0; i < numSegments; i++)
				{
					TokenData[] tokens = whisper.getTokensFromState(ctx, state, i);
					
					for(TokenData token : tokens)
					{
						logger.info("TOKEN: '{}'", token.token);
					}
					
					assertEquals(tokens.length, 23);
				}
			}
		}
	}
	
	@Test
	public void testVADFull() throws Exception
	{
		try(var ctx = whisper.init(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			params.vad = true;
			params.vad_model_path = tempVAD.toAbsolutePath().toString();
			
			var vadParams = params.vadParams;
			vadParams.threshold = 0.995f;
			// vadParams.min_speech_duration_ms = 200;
			// vadParams.min_silence_duration_ms = 100;
			// vadParams.max_speech_duration_s = 10.0f;
			// vadParams.speech_pad_ms = 30;
			// vadParams.samples_overlap = 0.1f;
			
			float[] samples = readFileSamples(samplePath);
			int result = whisper.full(ctx, params, samples, samples.length);
			
			if(result != 0)
			{
				throw new RuntimeException("Transcription failed with code " + result);
			}
			
			final int segments = whisper.fullNSegments(ctx);
			
			logger.info("{} total segments after VAD filtering", segments);
			
			for(int i = 0; i < segments; i++)
			{
				String text = whisper.fullGetSegmentText(ctx, i);
				logger.info("VAD #{}: {}", i + 1, text);
				// It should be pretty short (America)
				assert text.length() < 30;
			}
		}
	}
	
	// It seems, weirdly, that state doesn't work with VAD?? Check out the whisper.cpp file to see for yourself
	
	@Test
	public void testVADState() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		
		try(var ctx = whisper.initNoState(testModelPath); var state = whisper.initState(ctx))
		{
			assertNotNull(ctx);
			assertNotNull(state);
			
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			params.vad = true;
			params.vad_model_path = tempVAD.toAbsolutePath().toString();
			
			// Keep default
			var vadParams = params.vadParams;
			vadParams.threshold = 0.995f;
			// vadParams.min_speech_duration_ms = 200;
			// vadParams.min_silence_duration_ms = 100;
			// vadParams.max_speech_duration_s = 10.0f;
			// vadParams.speech_pad_ms = 30;
			// vadParams.samples_overlap = 0.1f;
			
			String result = whisper.vadState(ctx, state, params, new WhisperVADContextParams(), samples, samples.length);
			logger.info("VAD result: {}", result);
		}
	}
	
	@Test
	public void testBlankVADState() throws Exception
	{
		float[] samples = new float[(int) Math.pow(2, 16)];
		
		try(var ctx = whisper.initNoState(testModelPath); var state = whisper.initState(ctx))
		{
			assertNotNull(ctx);
			assertNotNull(state);
			
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			params.vad = true;
			params.vad_model_path = tempVAD.toAbsolutePath().toString();
			
			// Keep default
			var vadParams = params.vadParams;
			vadParams.threshold = 0.995f;
			// vadParams.min_speech_duration_ms = 200;
			// vadParams.min_silence_duration_ms = 100;
			// vadParams.max_speech_duration_s = 10.0f;
			// vadParams.speech_pad_ms = 30;
			// vadParams.samples_overlap = 0.1f;
			
			String result = whisper.vadState(ctx, state, params, new WhisperVADContextParams(), samples, samples.length);
			logger.info("Result: {}", result);
			assert result == null;
		}
	}
	
	@Test
	public void testFull() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.init(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			int result = whisper.full(ctx, params, samples, samples.length);
			if(result != 0)
			{
				throw new RuntimeException("Transcription failed with code " + result);
			}
			int numSegments = whisper.fullNSegments(ctx);
			assertEquals(1, numSegments);
			long startTime = whisper.fullGetSegmentTimestamp0(ctx, 0);
			long endTime = whisper.fullGetSegmentTimestamp1(ctx, 0);
			String text = whisper.fullGetSegmentText(ctx, 0);
			assertEquals(0, startTime);
			assertEquals(1050, endTime);
			assertEquals(" And so my fellow Americans ask not what your country can do for you, ask what you can do for your country.", text);
		}
	}
	
	@Test
	public void testFullBeamSearch() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.init(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.BEAM_SEARCH);
			params.printTimestamps = false;
			int result = whisper.full(ctx, params, samples, samples.length);
			if(result != 0)
			{
				throw new RuntimeException("Transcription failed with code " + result);
			}
			int numSegments = whisper.fullNSegments(ctx);
			assertEquals(1, numSegments);
			String text = whisper.fullGetSegmentText(ctx, 0);
			assertEquals(" And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.", text);
		}
	}
	
	@Test
	public void testFullWithState() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.initNoState(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
			try(var state = whisper.initState(ctx))
			{
				assertNotNull(state);
				int result = whisper.fullWithState(ctx, state, params, samples, samples.length);
				if(result != 0)
				{
					throw new RuntimeException("Transcription failed with code " + result);
				}
				int numSegments = whisper.fullNSegmentsFromState(state);
				assertEquals(1, numSegments);
				long startTime = whisper.fullGetSegmentTimestamp0FromState(state, 0);
				long endTime = whisper.fullGetSegmentTimestamp1FromState(state, 0);
				String text = whisper.fullGetSegmentTextFromState(state, 0);
				assertEquals(0, startTime);
				assertEquals(1050, endTime);
				assertEquals(" And so my fellow Americans ask not what your country can do for you, ask what you can do for your country.", text);
			}
		}
	}
	
	@Test
	public void testFullWithStateBeamSearch() throws Exception
	{
		float[] samples = readFileSamples(samplePath);
		try(var ctx = whisper.initNoState(testModelPath))
		{
			assertNotNull(ctx);
			var params = new WhisperFullParams(WhisperSamplingStrategy.BEAM_SEARCH);
			params.printTimestamps = false;
			try(var state = whisper.initState(ctx))
			{
				assertNotNull(state);
				int result = whisper.fullWithState(ctx, state, params, samples, samples.length);
				if(result != 0)
				{
					throw new RuntimeException("Transcription failed with code " + result);
				}
				int numSegments = whisper.fullNSegmentsFromState(state);
				assertEquals(1, numSegments);
				String text = whisper.fullGetSegmentTextFromState(state, 0);
				assertEquals(" And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.", text);
			}
		}
	}
	
	@Test
	public void testFullWithGrammar() throws Exception
	{
		// Init trailing space is important
		String grammarText = "root ::= \" And so, my fellow American, ask not what your country can do for you, ask what you can do for your country.\"";
		float[] samples = readFileSamples(samplePath);
		try(WhisperGrammar grammar = whisper.parseGrammar(grammarText))
		{
			assertNotNull(grammar);
			try(var ctx = whisper.init(testModelPath))
			{
				assertNotNull(ctx);
				var params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
				params.grammar = grammar;
				int result = whisper.full(ctx, params, samples, samples.length);
				if(result != 0)
				{
					throw new RuntimeException("Transcription failed with code " + result);
				}
				int numSegments = whisper.fullNSegments(ctx);
				assertEquals(1, numSegments);
				String text = whisper.fullGetSegmentText(ctx, 0);
				assertEquals(" And so, my fellow American, ask not what your country can do for you, ask what you can do for your country.", text);
			}
		}
	}
	
	@Test
	public void printSystemInfo() throws Exception
	{
		String whisperCPPSystemInfo = whisper.getSystemInfo();
		assertFalse(whisperCPPSystemInfo.isBlank());
		logger.info("whisper.cpp library info: {}", whisperCPPSystemInfo);
	}
	
	@Test
	public void initOpenVINO() throws Exception
	{
		try(var ctx = whisper.initNoState(testModelPath))
		{
			assertNotNull(ctx);
			whisper.initOpenVINO(ctx, "CPU");
		}
	}
	
	@Test
	public void validateGrammar() throws ParseException, IOException
	{
		assertValidGrammar(sampleAssistantGrammar);
		assertValidGrammar(sampleColorsGrammar);
		assertValidGrammar(sampleChessGrammar);
	}
	
	private float[] readFileSamples(Path samplePath) throws UnsupportedAudioFileException, IOException
	{
		// sample is a 16 bit int 16000hz little endian wav file
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(samplePath.toFile());
		// read all the available data to a little endian capture buffer
		ByteBuffer captureBuffer = ByteBuffer.allocate(audioInputStream.available());
		captureBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int read = audioInputStream.read(captureBuffer.array());
		if(read == -1)
		{
			throw new IOException("Empty file");
		}
		// obtain the 16 int audio samples, short type in java
		var shortBuffer = captureBuffer.asShortBuffer();
		// transform the samples to f32 samples
		float[] samples = new float[captureBuffer.capacity() / 2];
		var i = 0;
		while(shortBuffer.hasRemaining())
		{
			samples[i++] = Float.max(-1f, Float.min(((float) shortBuffer.get()) / (float) Short.MAX_VALUE, 1f));
		}
		return samples;
	}
	
}