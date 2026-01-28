#include <iostream>
#include <cstring>
#include <queue>
#include <map>
#include <jni.h>
#include "io_github_jaffe2718_whisperjni_WhisperJNI.h"
#include "whisper.h"
#include "grammar-parser.h"


std::map<int, whisper_context *> contextMap;
std::map<int, whisper_state *> stateMap;
std::map<int, grammar_parser::parse_state *> grammarMap;

int getContextId()
{
  int i = 0;
  while (i++ < 1000)
  {
    int id = rand();
    if (!contextMap.count(id))
    {
      return id;
    }
  }
  throw std::runtime_error("Wrapper error: Unable to get config id");
}
int getStateId()
{
  int i = 0;
  while (i++ < 1000)
  {
    int id = rand();
    if (!stateMap.count(id))
    {
      return id;
    }
  }
  throw std::runtime_error("Wrapper error: Unable to get state id");
}
int getGrammarId()
{
  int i = 0;
  while (i++ < 1000)
  {
    int id = rand();
    if (!grammarMap.count(id))
    {
      return id;
    }
  }
  throw std::runtime_error("Wrapper error: Unable to get grammar id");
}
int insertModel(whisper_context *ctx)
{
  int ref = getContextId();
  contextMap.insert({ref, ctx});
  return ref;
}

struct whisper_context_params newWhisperContextParams(JNIEnv *env, jobject jParams)
{
  jclass paramsJClass = env->GetObjectClass(jParams);
  struct whisper_context_params params = whisper_context_default_params();
  params.use_gpu = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "useGPU", "Z"));
  return params;
}

void freeWhisperFullParams(JNIEnv *env, jobject jParams, whisper_full_params params)
{
  jclass paramsJClass = env->GetObjectClass(jParams);
  jstring language = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "language", "Ljava/lang/String;"));
  if (language)
  {
    env->ReleaseStringUTFChars(language, params.language);
  }
  jstring initialPrompt = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "initialPrompt", "Ljava/lang/String;"));
  if (initialPrompt)
  {
    env->ReleaseStringUTFChars(initialPrompt, params.initial_prompt);
  }
  jstring vadPath = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "vad_model_path", "Ljava/lang/String;"));
  if (vadPath)
  {
    env->ReleaseStringUTFChars(vadPath, params.vad_model_path);
  }
}

struct whisper_full_params newWhisperFullParams(JNIEnv *env, jobject jParams)
{
  jclass paramsJClass = env->GetObjectClass(jParams);

  whisper_sampling_strategy samplingStrategy = (whisper_sampling_strategy)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "strategy", "I"));
  whisper_full_params params = whisper_full_default_params(samplingStrategy);

  int nThreads = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "nThreads", "I"));
  if (nThreads > 0)
  {
    params.n_threads = nThreads;
  }
  params.audio_ctx = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "audioCtx", "I"));
  params.n_max_text_ctx = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "nMaxTextCtx", "I"));
  params.offset_ms = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "offsetMs", "I"));
  params.duration_ms = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "durationMs", "I"));

  jstring language = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "language", "Ljava/lang/String;"));
  params.language = language == NULL ? nullptr : env->GetStringUTFChars(language, NULL);
  jstring initialPrompt = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "initialPrompt", "Ljava/lang/String;"));
  params.initial_prompt = initialPrompt == NULL ? nullptr : env->GetStringUTFChars(initialPrompt, NULL);

  params.translate = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "translate", "Z"));
  params.no_timestamps = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "noTimestamps", "Z"));
  params.no_context = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "noContext", "Z"));
  params.single_segment = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "singleSegment", "Z"));
  params.print_special = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "printSpecial", "Z"));
  params.print_progress = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "printProgress", "Z"));
  params.print_realtime = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "printRealtime", "Z"));
  params.print_timestamps = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "printTimestamps", "Z"));
  params.detect_language = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "detectLanguage", "Z"));
  params.suppress_blank = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "suppressBlank", "Z"));
  // new name
  params.suppress_nst = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "suppressNonSpeechTokens", "Z"));

  params.temperature = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "temperature", "F"));
  params.max_initial_ts = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "maxInitialTs", "F"));
  params.length_penalty = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "lengthPenalty", "F"));
  params.temperature_inc = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "temperatureInc", "F"));
  params.entropy_thold = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "entropyThold", "F"));
  params.logprob_thold = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "logprobThold", "F"));
  params.no_speech_thold = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "noSpeechThold", "F"));

  switch (params.strategy)
  {
  case WHISPER_SAMPLING_GREEDY:
  {
    params.greedy.best_of = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "greedyBestOf", "I"));
  }
  break;
  case WHISPER_SAMPLING_BEAM_SEARCH:
  {
    params.beam_search.beam_size = (jint)env->GetIntField(jParams, env->GetFieldID(paramsJClass, "beamSearchBeamSize", "I"));
    params.beam_search.patience = (jfloat)env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "beamSearchPatience", "F"));
  }
  break;
  }

  // VAD
  params.vad = (jboolean)env->GetBooleanField(jParams, env->GetFieldID(paramsJClass, "vad", "Z"));
  jstring jPath = (jstring)env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "vad_model_path", "Ljava/lang/String;"));
  params.vad_model_path = jPath == NULL ? nullptr : env->GetStringUTFChars(jPath, NULL);

  // VAD arams
  whisper_vad_params vadParams = whisper_vad_default_params();
  jfieldID fidVADParams = env->GetFieldID(paramsJClass, "vadParams", "Lio/github/jaffe2718/whisperjni/WhisperFullParams$VADParams;");
  jobject jVadParamsObj = env->GetObjectField(jParams, fidVADParams);
  jclass vadCls = env->GetObjectClass(jVadParamsObj);
  // Fill
  vadParams.threshold = env->GetFloatField(jVadParamsObj, env->GetFieldID(vadCls, "threshold", "F"));
  vadParams.min_speech_duration_ms = env->GetIntField(jVadParamsObj, env->GetFieldID(vadCls, "min_speech_duration_ms", "I"));
  vadParams.min_silence_duration_ms = env->GetIntField(jVadParamsObj, env->GetFieldID(vadCls, "min_silence_duration_ms", "I"));
  vadParams.max_speech_duration_s = env->GetFloatField(jVadParamsObj, env->GetFieldID(vadCls, "max_speech_duration_s", "F"));
  vadParams.speech_pad_ms = env->GetIntField(jVadParamsObj, env->GetFieldID(vadCls, "speech_pad_ms", "I"));
  vadParams.samples_overlap = env->GetFloatField(jVadParamsObj, env->GetFieldID(vadCls, "samples_overlap", "F"));
  // Set and return
  params.vad_params = vadParams;

  // Release jPath (out VAD model string)
  // ^ nah we release that with the context
  // env->ReleaseStringUTFChars(jPath, params.vad_model_path);

  return params;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env = NULL;
  jint result = -1;

  if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
    return result;
  }

  ggml_backend_load_all();
  result = JNI_VERSION_1_4;
  return result;
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_init(JNIEnv *env, jobject thisObject, jstring modelPath, jobject jParams)
{
  const char *path = env->GetStringUTFChars(modelPath, NULL);
  struct whisper_context *context = whisper_init_from_file_with_params(path, newWhisperContextParams(env, jParams));
  env->ReleaseStringUTFChars(modelPath, path);
  if (!context)
  {
    return -1;
  }
  return insertModel(context);
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_initFromInputStream(JNIEnv *env, jobject thiz, jobject jInputStream, jobject jParams, jboolean initState)
{
    // Get InputStream class and read method
    jclass inputStreamClass = env->FindClass("java/io/InputStream");
    jmethodID readMethod = env->GetMethodID(inputStreamClass, "read", "([B)I");
    jbyteArray buffer = env->NewByteArray(8192);

    // Read all data from input stream into buffer
    std::queue<uint8_t> model_data;
    jint bytesRead;
    // Read all data from FileInputStream into model_data
    do {
        bytesRead = env->CallIntMethod(jInputStream, readMethod, buffer);
        if (bytesRead > 0) {
            jbyte *bytes = env->GetByteArrayElements(buffer, nullptr);
            // Append read bytes to model data vector
            for (int i = 0; i < bytesRead; i++) {
                model_data.push(reinterpret_cast<uint8_t *>(bytes)[i]);
            }
            env->ReleaseByteArrayElements(buffer, bytes, JNI_ABORT); // No need to copy back
        }
    } while (bytesRead != -1); // -1 indicates end of stream

    // Clean up local references
    env->DeleteLocalRef(buffer);
    env->DeleteLocalRef(inputStreamClass);

    // Check for empty model data
    if (model_data.empty()) return -1;

    whisper_model_loader loader = {};
    loader.context = &model_data;
    loader.read = [](void *ctx, void *output, size_t read_size) -> size_t {
        std::queue<uint8_t> *ctx_queue = (std::queue<uint8_t> *) ctx;
        size_t toRead = std::min(read_size, ctx_queue->size());
        if (toRead > 0) {
            for (size_t i = 0; i < toRead; i++) {
                ((uint8_t *) output)[i] = ctx_queue->front();
                ctx_queue->pop();
            }
        }
        return toRead;
    };
    loader.eof = [](void *ctx) {
        std::queue<uint8_t> *ctx_queue = (std::queue<uint8_t> *) ctx;
        return ctx_queue->empty();
    };
    loader.close = [](void *ctx) {
        std::queue<uint8_t> *ctx_queue = (std::queue<uint8_t> *) ctx;
        while (!ctx_queue->empty()) {
            ctx_queue->pop();
        }
    };
    struct whisper_context *context = initState ? whisper_init_with_params(&loader, newWhisperContextParams(env, jParams)) : whisper_init_with_params_no_state(&loader, newWhisperContextParams(env, jParams));
    return context ? insertModel(context) : -1;
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_initNoState(JNIEnv *env, jobject thisObject, jstring modelPath, jobject jParams)
{
  const char *path = env->GetStringUTFChars(modelPath, NULL);
  struct whisper_context *context = whisper_init_from_file_with_params_no_state(path, newWhisperContextParams(env, jParams));
  env->ReleaseStringUTFChars(modelPath, path);
  if (!context)
  {
    return -1;
  }
  return insertModel(context);
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_initState(JNIEnv *env, jobject thisObject, jint ctxRef)
{
  int stateRef = getStateId();
  whisper_state *state = whisper_init_state(contextMap.at(ctxRef));
  if (!state)
  {
    return -1;
  }
  stateMap.insert({stateRef, state});
  return stateRef;
}

JNIEXPORT void JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_initOpenVINOEncoder(JNIEnv *env, jobject thisObject, jint ctxRef, jstring deviceString)
{
  const char *device = env->GetStringUTFChars(deviceString, NULL);
  whisper_ctx_init_openvino_encoder(contextMap.at(ctxRef), nullptr, device, nullptr);
  env->ReleaseStringUTFChars(deviceString, device);
}

JNIEXPORT jboolean JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_isMultilingual(JNIEnv *env, jobject thisObject, jint ctxRef)
{
  return whisper_is_multilingual(contextMap.at(ctxRef));
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_full(JNIEnv *env, jobject thisObject, jint ctxRef, jobject jParams, jfloatArray samples, jint jNumSamples)
{
  int numSamples = static_cast<int>(jNumSamples);
  whisper_full_params params = newWhisperFullParams(env, jParams);
  // I was unable to handle the grammar inside the newWhisperFullParams fn
  jclass paramsJClass = env->GetObjectClass(jParams);
  jobject jGrammar = env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "grammar", "Lio/github/jaffe2718/whisperjni/WhisperGrammar;"));
  float grammarPenalty = env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "grammarPenalty", "F"));
  std::vector<const whisper_grammar_element *> grammar_rules; // I don't know why, this one needs to be declared outside the 'if' statement.
  if (jGrammar)
  {
    jclass grammarJClass = env->GetObjectClass(jGrammar);
    int grammarRef = env->GetIntField(jGrammar, env->GetFieldID(grammarJClass, "ref", "I"));
    grammar_parser::parse_state *grammar_parsed = grammarMap.at(grammarRef);
    grammar_rules = grammar_parsed->c_rules();
    if (!grammar_parsed->rules.empty() && grammar_parsed->symbol_ids.find("root") != grammar_parsed->symbol_ids.end())
    {
      params.grammar_rules = grammar_rules.data();
      params.n_grammar_rules = grammar_rules.size();
      params.i_start_rule = grammar_parsed->symbol_ids.at("root");
      params.grammar_penalty = grammarPenalty;
    }
  }

  jfloat *samplesPointer = env->GetFloatArrayElements(samples, NULL);
  int result = whisper_full(contextMap.at(ctxRef), params, samplesPointer, numSamples);
  freeWhisperFullParams(env, jParams, params);
  env->ReleaseFloatArrayElements(samples, samplesPointer, 0);
  return result;
}

// Ripped from whisper.cpp (not exposed in header file)
// Time conversion utility functions for whisper VAD
static int cs_to_samples(int64_t cs)
{
  return (int)((cs / 100.0) * WHISPER_SAMPLE_RATE + 0.5);
}

JNIEXPORT jstring JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_vadState(JNIEnv *env, jobject thisObject, jint ctxRef, jint stateRef, jobject jParams, jobject jVADCxtParams, jfloatArray samples, jint jNumSamples)
{
  // Setup
  whisper_full_params params = newWhisperFullParams(env, jParams);
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  whisper_state *state = stateMap.at(stateRef);

  // VAD context init with default params
  whisper_vad_context_params vadCtxParams = whisper_vad_default_context_params();
  // Fill from java class
  // First, get the Java class for WhisperVADContextParams
  jclass vadCtxParamsClass = env->GetObjectClass(jVADCxtParams); // it better exist otherwise we crash the JVM lol
  // These also better exist
  jfieldID fid_n_threads = env->GetFieldID(vadCtxParamsClass, "n_threads", "I");
  jfieldID fid_use_gpu = env->GetFieldID(vadCtxParamsClass, "use_gpu", "Z");
  jfieldID fid_gpu_device = env->GetFieldID(vadCtxParamsClass, "gpu_device", "I");
  // Override with Java values
  vadCtxParams.n_threads = env->GetIntField(jVADCxtParams, fid_n_threads);
  vadCtxParams.use_gpu = env->GetBooleanField(jVADCxtParams, fid_use_gpu);
  vadCtxParams.gpu_device = env->GetIntField(jVADCxtParams, fid_gpu_device);

  // Init VAD context using the params
  whisper_vad_context *vadCtx = whisper_vad_init_from_file_with_params(params.vad_model_path, vadCtxParams);

  jfloat *nativeSamples = env->GetFloatArrayElements(samples, NULL);
  // Don't confuse the compilier (didn't work on my machine but worked fine in gh actions)
  int numSamples = static_cast<int>(jNumSamples);
  whisper_vad_segments *segments = whisper_vad_segments_from_samples(vadCtx, params.vad_params, nativeSamples, numSamples);

  if(!segments)
  {
    env->ReleaseFloatArrayElements(samples, nativeSamples, 0);
    whisper_vad_free(vadCtx);
    //return env->NewStringUTF("[VAD failed]");
    jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exceptionClass, "Transcription failed");
    return NULL;
  }

  int numSegments = whisper_vad_segments_n_segments(segments);
  if (numSegments == 0)
  {
    env->ReleaseFloatArrayElements(samples, nativeSamples, 0);
    whisper_vad_free_segments(segments);
    whisper_vad_free(vadCtx);
    //return env->NewStringUTF("[no speech detected]");
    return NULL;
  }

  // Calculate total samples for filtered audio
  int silence_samples = static_cast<int>(0.1f * WHISPER_SAMPLE_RATE);
  int total_samples = 0;

  for (int i = 0; i < numSegments; i++)
  {
    float t0 = whisper_vad_segments_get_segment_t0(segments, i);
    float t1 = whisper_vad_segments_get_segment_t1(segments, i);
    int start = cs_to_samples(t0);
    int end = cs_to_samples(t1);
    if (i < numSegments - 1)
    {
      end += silence_samples;
    }
    total_samples += std::max(0, end - start);
    if (i < numSegments - 1)
    {
      total_samples += silence_samples;
    }
  }

  std::vector<float> filtered_samples(total_samples);
  int offset = 0;

  for (int i = 0; i < numSegments; i++)
  {
    float t0 = whisper_vad_segments_get_segment_t0(segments, i);
    float t1 = whisper_vad_segments_get_segment_t1(segments, i);
    int start = std::min(cs_to_samples(t0), numSamples - 1);
    int end = std::min(cs_to_samples(t1), numSamples);

    // Calculate overlap in samples from vad_params
    float overlap_seconds = params.vad_params.samples_overlap;
    int overlap_samples = static_cast<int>(overlap_seconds * WHISPER_SAMPLE_RATE);
    if (i < numSegments - 1)
    {
      // Add overlap to segment end to preserve continuity between segments
      end = std::min(end + overlap_samples, numSamples);
    }

    int len = std::max(0, end - start);

    if (len > 0)
    {
      memcpy(filtered_samples.data() + offset, nativeSamples + start, len * sizeof(float));
      offset += len;
    }

    if (i < numSegments - 1)
    {
      memset(filtered_samples.data() + offset, 0, silence_samples * sizeof(float));
      offset += silence_samples;
    }
  }

  // Transcribe filtered samples
  int result = whisper_full_with_state(whisper_ctx, state, params, filtered_samples.data(), offset);

  std::string output;
  if (result == 0)
  {
    int n_segments = whisper_full_n_segments_from_state(state);
    for (int i = 0; i < n_segments; ++i)
    {
      const char *text = whisper_full_get_segment_text_from_state(state, i);
      output += text;
    }
  }
  else
  {
    jclass exceptionClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exceptionClass, "Transcription failed");
  }

  // Cleanup
  env->ReleaseFloatArrayElements(samples, nativeSamples, 0);
  whisper_vad_free_segments(segments);
  whisper_vad_free(vadCtx);
  return output.empty() ? NULL : env->NewStringUTF(output.c_str());
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullWithState(JNIEnv *env, jobject thisObject, jint ctxRef, jint stateRef, jobject jParams, jfloatArray samples, jint jNumSamples)
{
  int numSamples = static_cast<int>(jNumSamples);
  whisper_full_params params = newWhisperFullParams(env, jParams);
  // I was unable to handle the grammar inside the newWhisperFullParams fn
  jclass paramsJClass = env->GetObjectClass(jParams);
  jobject jGrammar = env->GetObjectField(jParams, env->GetFieldID(paramsJClass, "grammar", "Lio/github/jaffe2718/whisperjni/WhisperGrammar;"));
  float grammarPenalty = env->GetFloatField(jParams, env->GetFieldID(paramsJClass, "grammarPenalty", "F"));
  std::vector<const whisper_grammar_element *> grammar_rules; // I don't know why, this one needs to be declared outside the 'if' statement.
  if (jGrammar)
  {
    jclass grammarJClass = env->GetObjectClass(jGrammar);
    int grammarRef = env->GetIntField(jGrammar, env->GetFieldID(grammarJClass, "ref", "I"));
    grammar_parser::parse_state *grammar_parsed = grammarMap.at(grammarRef);
    grammar_rules = grammar_parsed->c_rules();
    if (!grammar_parsed->rules.empty() && grammar_parsed->symbol_ids.find("root") != grammar_parsed->symbol_ids.end())
    {
      params.grammar_rules = grammar_rules.data();
      params.n_grammar_rules = grammar_rules.size();
      params.i_start_rule = grammar_parsed->symbol_ids.at("root");
      params.grammar_penalty = grammarPenalty;
    }
  }
  jfloat *samplesPointer = env->GetFloatArrayElements(samples, NULL);
  int result = whisper_full_with_state(contextMap.at(ctxRef), stateMap.at(stateRef), params, samplesPointer, numSamples);
  freeWhisperFullParams(env, jParams, params);
  env->ReleaseFloatArrayElements(samples, samplesPointer, 0);
  return result;
}

// START SUPASULLEY EPIC METHODS
JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullNTokens(JNIEnv *env, jobject thisObject, jint ctxRef, jint segment)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  return whisper_full_n_tokens(whisper_ctx, segment);
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullNTokensFromState(JNIEnv *env, jobject thisObject, jint stateRef, jint segment)
{
  whisper_state *state = stateMap.at(stateRef);
  return whisper_full_n_tokens_from_state(state, segment);
}

static jobject createTokenData(JNIEnv *env, const char *tokenText, whisper_token_data td)
{
  jstring jTok = env->NewStringUTF(tokenText);
  // Build the java wrapper
  jclass cls = env->FindClass("io/github/jaffe2718/whisperjni/TokenData");
  jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;IIFFFFJJJF)V");
  jobject obj = env->NewObject(cls, ctor,
                               jTok,
                               (jint)td.id,
                               (jint)td.tid,
                               (jfloat)td.p,
                               (jfloat)td.plog,
                               (jfloat)td.pt,
                               (jfloat)td.ptsum,
                               (jlong)td.t0,
                               (jlong)td.t1,
                               (jlong)td.t_dtw,
                               (jfloat)td.vlen);
  // Prevent ref buildup
  env->DeleteLocalRef(jTok);
  return obj;
}

JNIEXPORT jobject JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_getTokenData(JNIEnv *env, jobject thisObject, jint ctxRef, jint segment, jint token)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  whisper_token_data td = whisper_full_get_token_data(whisper_ctx, segment, token);
  // Get the text of this token
  const char *tokenText = whisper_full_get_token_text(whisper_ctx, segment, token);
  return createTokenData(env, tokenText, td);
}

JNIEXPORT jobject JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_getTokenDataFromState(JNIEnv *env, jobject thisObject, jint ctxRef, jint stateRef, jint segment, jint token)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  whisper_state *state = stateMap.at(stateRef);
  // God knows why this doesn't require the context but the text does...
  whisper_token_data td = whisper_full_get_token_data_from_state(state, segment, token);
  // Get the text of this token
  const char *tokenText = whisper_full_get_token_text_from_state(whisper_ctx, state, segment, token);
  return createTokenData(env, tokenText, td);
}

// END TOKEN SCHENANIGANS

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullNSegments(JNIEnv *env, jobject thisObject, jint ctxRef)
{
  return whisper_full_n_segments(contextMap.at(ctxRef));
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullNSegmentsFromState(JNIEnv *env, jobject thisObject, jint stateRef)
{
  return whisper_full_n_segments_from_state(stateMap.at(stateRef));
}

JNIEXPORT jlong JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentTimestamp0(JNIEnv *env, jobject thisObject, jint ctxRef, jint index)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  int nSegments = whisper_full_n_segments(whisper_ctx);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return 0L;
  }
  return whisper_full_get_segment_t0(whisper_ctx, index);
}

JNIEXPORT jlong JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentTimestamp1(JNIEnv *env, jobject thisObject, jint ctxRef, jint index)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  int nSegments = whisper_full_n_segments(whisper_ctx);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return 0L;
  }
  return whisper_full_get_segment_t1(whisper_ctx, index);
}

JNIEXPORT jstring JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentText(JNIEnv *env, jobject thisObject, jint ctxRef, jint index)
{
  whisper_context *whisper_ctx = contextMap.at(ctxRef);
  int nSegments = whisper_full_n_segments(whisper_ctx);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return NULL;
  }
  const char *text = whisper_full_get_segment_text(whisper_ctx, index);
  return env->NewStringUTF(text);
}

JNIEXPORT jlong JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentTimestamp0FromState(JNIEnv *env, jobject thisObject, jint stateRef, jint index)
{
  whisper_state *state = stateMap.at(stateRef);
  int nSegments = whisper_full_n_segments_from_state(state);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return 0L;
  }
  return whisper_full_get_segment_t0_from_state(state, index);
}

JNIEXPORT jlong JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentTimestamp1FromState(JNIEnv *env, jobject thisObject, jint stateRef, jint index)
{
  whisper_state *state = stateMap.at(stateRef);
  int nSegments = whisper_full_n_segments_from_state(state);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return 0L;
  }
  return whisper_full_get_segment_t1_from_state(state, index);
}

JNIEXPORT jstring JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_fullGetSegmentTextFromState(JNIEnv *env, jobject thisObject, jint stateRef, jint index)
{
  whisper_state *state = stateMap.at(stateRef);
  int nSegments = whisper_full_n_segments_from_state(state);
  if (nSegments < index + 1)
  {
    jclass exClass = env->FindClass("java/lang/IndexOutOfBoundsException");
    env->ThrowNew(exClass, "Index out of range");
    return NULL;
  }
  const char *text = whisper_full_get_segment_text_from_state(state, index);
  return env->NewStringUTF(text);
}

JNIEXPORT jint JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_loadGrammar(JNIEnv *env, jobject thisObject, jstring grammarText)
{
  const char *grammarChars = env->GetStringUTFChars(grammarText, NULL);
  grammar_parser::parse_state *grammarPointer = new grammar_parser::parse_state{};
  try
  {
    *grammarPointer = grammar_parser::parse(grammarChars);
  }
  catch (const std::exception &e)
  {
    env->ReleaseStringUTFChars(grammarText, grammarChars);
    jclass exClass = env->FindClass("java/io/IOException");
    env->ThrowNew(exClass, e.what());
    return -1;
  }
  env->ReleaseStringUTFChars(grammarText, grammarChars);
  int grammarRef = getGrammarId();
  grammarMap.insert({grammarRef, grammarPointer});
  return grammarRef;
}

JNIEXPORT jstring JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_printSystemInfo(JNIEnv *env, jobject thisObject)
{
  const char *text = whisper_print_system_info();
  return env->NewStringUTF(text);
}
JNIEXPORT void JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_freeContext(JNIEnv *env, jobject thisObject, jint ctxRef)
{
  whisper_free(contextMap.at(ctxRef));
  contextMap.erase(ctxRef);
}

JNIEXPORT void JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_freeState(JNIEnv *env, jobject thisObject, jint stateRef)
{
  whisper_free_state(stateMap.at(stateRef));
  stateMap.erase(stateRef);
}
JNIEXPORT void JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_freeGrammar(JNIEnv *env, jobject thisClass, jint grammarRef)
{
  free(grammarMap.at(grammarRef));
  stateMap.erase(grammarRef);
}

// Logging
static jobject globalLoggerRef = NULL;
static JavaVM *jvm = NULL;

static void whisper_log_proxy(enum ggml_log_level level, const char *text, void *user_data)
{
  if (!jvm || !globalLoggerRef || !text)
    return;

  JNIEnv *env;
  if (jvm->AttachCurrentThread((void **)&env, NULL) != JNI_OK)
  {
    return;
  }

  jclass loggerClass = env->GetObjectClass(globalLoggerRef);
  if (!loggerClass)
  {
    jvm->DetachCurrentThread();
    return;
  }

  jstring jMessage = env->NewStringUTF(text);
  if (!jMessage)
  {
    env->DeleteLocalRef(loggerClass);
    jvm->DetachCurrentThread();
    return;
  }

  jmethodID logMethod = NULL;

  switch (level)
  {
  case GGML_LOG_LEVEL_ERROR:
    logMethod = env->GetMethodID(loggerClass, "error", "(Ljava/lang/String;)V");
    break;
  case GGML_LOG_LEVEL_WARN:
    logMethod = env->GetMethodID(loggerClass, "warn", "(Ljava/lang/String;)V");
    break;
  case GGML_LOG_LEVEL_INFO:
    logMethod = env->GetMethodID(loggerClass, "info", "(Ljava/lang/String;)V");
    break;
  case GGML_LOG_LEVEL_DEBUG:
    logMethod = env->GetMethodID(loggerClass, "debug", "(Ljava/lang/String;)V");
    break;
  case GGML_LOG_LEVEL_CONT:
  case GGML_LOG_LEVEL_NONE:
  default:
    // Treat CONT and NONE as INFO or skip
    logMethod = env->GetMethodID(loggerClass, "info", "(Ljava/lang/String;)V");
    break;
  }

  if (logMethod)
  {
    // Raw logs come with \n bs appended to them so we're going to call Java's .stripTrailing
    jclass stringClass = env->FindClass("java/lang/String");
    jmethodID stripTrailingMethod = env->GetMethodID(stringClass, "stripTrailing", "()Ljava/lang/String;");
    jobject strippedMessage = env->CallObjectMethod(jMessage, stripTrailingMethod);
    // Pass the stripped message to the logger
    env->CallVoidMethod(globalLoggerRef, logMethod, strippedMessage);
    env->DeleteLocalRef(strippedMessage);
    env->DeleteLocalRef(stringClass);
  }

  env->DeleteLocalRef(jMessage);
  env->DeleteLocalRef(loggerClass);

  jvm->DetachCurrentThread();
}

/*
 * Class:     io_github_jaffe2718_whisperjni_WhisperJNI
 * Method:    setLogger
 * Signature: (Lorg/slf4j/Logger;)V
 */
JNIEXPORT void JNICALL Java_io_github_jaffe2718_whisperjni_WhisperJNI_setLogger(JNIEnv *env, jclass thisClass, jobject logger)
{
  // We need this for later
  if (!jvm && env->GetJavaVM(&jvm) != JNI_OK)
  {
    jclass exClass = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(exClass, "Failed getting reference to Java VM");
    return;
  }
  // Clean up previous logger reference
  if (globalLoggerRef != NULL)
  {
    env->DeleteGlobalRef(globalLoggerRef);
  }
  // Store new logger as a global reference
  globalLoggerRef = env->NewGlobalRef(logger);
  // Register the proxy log function
  whisper_log_set(whisper_log_proxy, nullptr);
}