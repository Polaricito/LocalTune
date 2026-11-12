#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

std::string trim(const std::string & value) {
    size_t begin = 0;
    while (begin < value.size() && std::isspace(static_cast<unsigned char>(value[begin]))) {
        ++begin;
    }
    size_t end = value.size();
    while (end > begin && std::isspace(static_cast<unsigned char>(value[end - 1]))) {
        --end;
    }
    return value.substr(begin, end - begin);
}

bool is_non_speech(const std::string & text) {
    if (text.empty()) return true;

    const std::string lower = [&text]() {
        std::string result = text;
        std::transform(result.begin(), result.end(), result.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        return result;
    }();

    if (lower.size() >= 2 &&
        ((lower.front() == '[' && lower.back() == ']') ||
         (lower.front() == '(' && lower.back() == ')'))) {
        const std::string inner = lower.substr(1, lower.size() - 2);
        if (inner.find("music") != std::string::npos ||
            inner.find("applause") != std::string::npos ||
            inner.find("laughter") != std::string::npos ||
            inner.find("silence") != std::string::npos ||
            inner.find("instrumental") != std::string::npos ||
            inner.find("intro") != std::string::npos ||
            inner.find("outro") != std::string::npos) {
            return true;
        }
    }

    // Ignore lines made only of musical symbols and punctuation.
    for (unsigned char c : text) {
        if (std::isalnum(c) || c >= 0x80) return false;
    }
    return true;
}

std::string format_lrc_timestamp(int64_t milliseconds) {
    const int64_t totalMs = std::max<int64_t>(0, milliseconds);
    const int64_t minutes = totalMs / 60000;
    const int64_t seconds = (totalMs % 60000) / 1000;
    const int64_t hundredths = (totalMs % 1000) / 10;
    char buffer[32];
    std::snprintf(buffer, sizeof(buffer), "[%02lld:%02lld.%02lld]",
                  static_cast<long long>(minutes),
                  static_cast<long long>(seconds),
                  static_cast<long long>(hundredths));
    return buffer;
}

std::string sanitize_utf8(const std::string & value) {
    // whisper.cpp emits UTF-8. Replace the handful of control characters that
    // occasionally leak into the output so the .lrc file stays readable.
    std::string result;
    result.reserve(value.size());
    for (unsigned char c : value) {
        if (c == '\n' || c == '\r' || c == '\t') {
            result.push_back(' ');
        } else if (c >= 0x20) {
            result.push_back(static_cast<char>(c));
        }
    }
    return result;
}

struct Segment {
    int64_t start_ms;
    int64_t end_ms;
    std::string text;
};

bool run_transcription(
        whisper_context * context,
        float * samples,
        int sample_count,
        int thread_count,
        const std::string & language,
        std::vector<Segment> & out_segments) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::clamp<int>(thread_count, 1, 8);
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = false;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.token_timestamps = false;
    params.max_len = 80;
    params.split_on_word = true;
    params.suppress_nst = true;
    params.language = language.c_str();

    const int result = whisper_full(context, params, samples, sample_count);
    if (result != 0) {
        LOGW("whisper_full failed with code %d", result);
        return false;
    }

    const int segment_count = whisper_full_n_segments(context);
    std::string previous_line;
    for (int i = 0; i < segment_count; ++i) {
        const char * raw_text = whisper_full_get_segment_text(context, i);
        if (raw_text == nullptr) continue;

        const std::string text = trim(sanitize_utf8(raw_text));
        if (text.empty() || is_non_speech(text)) continue;
        if (!previous_line.empty() && text == previous_line) continue;

        previous_line = text;
        Segment segment{};
        segment.start_ms = whisper_full_get_segment_t0(context, i) * 10;
        segment.end_ms = whisper_full_get_segment_t1(context, i) * 10;
        segment.text = text;
        out_segments.push_back(segment);
    }

    return true;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_moe_rukamori_archivetune_lyrics_WhisperEngine_nativeInit(
        JNIEnv * env, jobject /* thiz */, jstring model_path) {
    if (model_path == nullptr) return 0;

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;

    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    context_params.flash_attn = false;

    whisper_context * context = whisper_init_from_file_with_params(path, context_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (context == nullptr) {
        LOGW("Could not load whisper model");
        return 0;
    }

    LOGI("Whisper model loaded");
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT void JNICALL
Java_moe_rukamori_archivetune_lyrics_WhisperEngine_nativeRelease(
        JNIEnv * /* env */, jobject /* thiz */, jlong context_ptr) {
    if (context_ptr == 0) return;
    whisper_free(reinterpret_cast<whisper_context *>(context_ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_moe_rukamori_archivetune_lyrics_WhisperEngine_nativeTranscribe(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr,
        jfloatArray samples_array,
        jint thread_count,
        jstring language_value) {
    if (context_ptr == 0 || samples_array == nullptr) return nullptr;

    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    const jsize sample_count = env->GetArrayLength(samples_array);
    if (sample_count <= 0) return env->NewStringUTF("");

    jfloat * samples = env->GetFloatArrayElements(samples_array, nullptr);
    if (samples == nullptr) return nullptr;

    std::string language = "auto";
    if (language_value != nullptr) {
        const char * language_chars = env->GetStringUTFChars(language_value, nullptr);
        if (language_chars != nullptr) {
            language = language_chars;
            env->ReleaseStringUTFChars(language_value, language_chars);
        }
    }
    if (language.empty()) language = "auto";

    std::vector<Segment> segments;
    const bool ok = run_transcription(
        context, samples, sample_count, static_cast<int>(thread_count), language, segments);
    env->ReleaseFloatArrayElements(samples_array, samples, JNI_ABORT);

    if (!ok || segments.empty()) {
        return env->NewStringUTF("");
    }

    std::string lrc = "[by:LocalTune]\n[re:whisper.cpp]\n";
    for (const Segment & segment : segments) {
        lrc += format_lrc_timestamp(segment.start_ms);
        lrc += segment.text;
        lrc += '\n';
    }

    return env->NewStringUTF(lrc.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_moe_rukamori_archivetune_lyrics_WhisperEngine_nativeTranscribeSegments(
        JNIEnv * env,
        jobject /* thiz */,
        jlong context_ptr,
        jfloatArray samples_array,
        jint thread_count,
        jstring language_value) {
    if (context_ptr == 0 || samples_array == nullptr) return nullptr;

    auto * context = reinterpret_cast<whisper_context *>(context_ptr);
    const jsize sample_count = env->GetArrayLength(samples_array);
    if (sample_count <= 0) return env->NewStringUTF("");

    jfloat * samples = env->GetFloatArrayElements(samples_array, nullptr);
    if (samples == nullptr) return nullptr;

    std::string language = "auto";
    if (language_value != nullptr) {
        const char * language_chars = env->GetStringUTFChars(language_value, nullptr);
        if (language_chars != nullptr) {
            language = language_chars;
            env->ReleaseStringUTFChars(language_value, language_chars);
        }
    }
    if (language.empty()) language = "auto";

    std::vector<Segment> segments;
    const bool ok = run_transcription(
        context, samples, sample_count, static_cast<int>(thread_count), language, segments);
    env->ReleaseFloatArrayElements(samples_array, samples, JNI_ABORT);

    if (!ok || segments.empty()) {
        return env->NewStringUTF("");
    }

    std::string dump;
    for (const Segment & segment : segments) {
        dump += std::to_string(segment.start_ms);
        dump += '\t';
        dump += std::to_string(segment.end_ms);
        dump += '\t';
        dump += segment.text;
        dump += '\n';
    }

    return env->NewStringUTF(dump.c_str());
}
