#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <string>
#include <utility>
#include <vector>
#include <thread>
#include <mutex>
#include <ostream>
#include <sstream>
#include <mutex>
#include <ostream>
#include "llm/llm.hpp"

#include <sstream>
#include <mutex>
#include <string>
#include "diffusion_session.h"
#include <chrono>
#include <sys/stat.h>
#include <dirent.h>
#include "mls_log.h"
using MNN::Transformer::Llm;
using mls::DiffusionSession;


class MNN_PUBLIC LlmStreamBuffer : public std::streambuf {
public:
    using CallBack = std::function<void(const char* str, size_t len)>;;
    explicit LlmStreamBuffer(CallBack callback) : callback_(std::move(callback)) {}

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) {
            callback_(s, n);
        }
        return n;
    }

private:
    CallBack callback_ = nullptr;
};

using PromptItem = std::pair<std::string, std::string>;
static std::vector<PromptItem> history{};
static bool stop_requested = false;

int utf8CharLength(unsigned char byte) {
    if ((byte & 0x80) == 0) return 1;
    if ((byte & 0xE0) == 0xC0) return 2;
    if ((byte & 0xF0) == 0xE0) return 3;
    if ((byte & 0xF8) == 0xF0) return 4;
    return 0;
}

class Utf8StreamProcessor {
public:
    explicit Utf8StreamProcessor(std::function<void(const std::string&)> callback)
            : callback(std::move(callback)) {}

    void processStream(const char* str, size_t len) {
        utf8Buffer.append(str, len);

        size_t i = 0;
        std::string completeChars;
        while (i < utf8Buffer.size()) {
            int length = utf8CharLength(static_cast<unsigned char>(utf8Buffer[i]));
            if (length == 0 || i + length > utf8Buffer.size()) {
                break;
            }
            completeChars.append(utf8Buffer, i, length);
            i += length;
        }
        utf8Buffer = utf8Buffer.substr(i);
        if (!completeChars.empty()) {
            callback(completeChars);
        }
    }

private:
    std::string utf8Buffer;
    std::function<void(const std::string&)> callback;
};


extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "MNN_DEBUG", "JNI_OnLoad");
    __android_log_print(ANDROID_LOG_DEBUG, "MNN_DEBUG", "HELLO ONLOAD FUNCTION JUST RAN");
    return JNI_VERSION_1_4;
}


JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "MNN_DEBUG", "JNI_OnUnload");
}

JNIEXPORT jlong JNICALL Java_com_example_mnn_1llm_1test_MnnLlmJni_initNative(JNIEnv* env, jobject thiz,
                                                                             jstring modelDir,
                                                                             jboolean use_tmp_path,
                                                                             jobject chat_history,
                                                                             jboolean is_diffusion) {
    MNN_DEBUG("=== initNative Start ===");
    MNN_DEBUG("Parameters received:");
    MNN_DEBUG("- use_tmp_path: %d", use_tmp_path);
    MNN_DEBUG("- is_diffusion: %d", is_diffusion);

    const char* model_dir = env->GetStringUTFChars(modelDir, 0);
    MNN_DEBUG("Model directory path: %s", model_dir);

    // Check if model directory exists
    struct stat buffer;
    if (stat(model_dir, &buffer) != 0) {
        MNN_DEBUG("Error: Model directory does not exist!");
        env->ReleaseStringUTFChars(modelDir, model_dir);
        return 0;
    }
    MNN_DEBUG("Model directory exists");

    // List directory contents
    DIR *dir;
    struct dirent *ent;
    if ((dir = opendir(model_dir)) != NULL) {
        MNN_DEBUG("Contents of model directory:");
        while ((ent = readdir(dir)) != NULL) {
            MNN_DEBUG("- %s", ent->d_name);
        }
        closedir(dir);
    }

    if (is_diffusion) {
        MNN_DEBUG("Creating DiffusionSession...");
        auto diffusion = new DiffusionSession(model_dir);
        MNN_DEBUG("DiffusionSession created successfully");
        env->ReleaseStringUTFChars(modelDir, model_dir);
        return reinterpret_cast<jlong>(diffusion);
    }

    MNN_DEBUG("Creating LLM instance...");
    auto llm = Llm::createLLM(model_dir);
    if (!llm) {
        MNN_DEBUG("Error: Failed to create LLM instance!");
        env->ReleaseStringUTFChars(modelDir, model_dir);
        return 0;
    }
    MNN_DEBUG("LLM instance created successfully");

    if (use_tmp_path) {
        MNN_DEBUG("Setting up temporary directory configuration");
        auto model_dir_str = std::string(model_dir);
        std::string model_dir_parent = model_dir_str.substr(0, model_dir_str.find_last_of('/'));
        std::string temp_dir = model_dir_parent + R"(/tmp")";

        // Create tmp directory if it doesn't exist
        if (stat(temp_dir.c_str(), &buffer) != 0) {
            MNN_DEBUG("Creating temporary directory: %s", temp_dir.c_str());
            mkdir(temp_dir.c_str(), 0777);
        }

        auto extra_config = R"({"tmp_path":")" + temp_dir + R"(,"reuse_kv":true, "backend_type":"opencl"})";
        MNN_DEBUG("Setting extra configuration: %s", extra_config.c_str());

        try {
            llm->set_config(temp_dir);
            MNN_DEBUG("Configuration set successfully");
        } catch (const std::exception& e) {
            MNN_DEBUG("Error setting configuration: %s", e.what());
        }
    } else {
        MNN_DEBUG("Skipping temporary directory configuration (use_tmp_path is false)");
    }

    MNN_DEBUG("Initializing conversation history");
    history.clear();
    history.emplace_back("system", "You are a helpful assistant in a mobile app designed for visually impaired users. Responses will be read aloud using text-to-speech, so keep them short, clear, and easy to understand. Avoid unnecessary details or long sentences. Be direct and helpful, using everyday language.");
    MNN_DEBUG("System prompt added to history");

    if (chat_history != nullptr) {
        MNN_DEBUG("Processing existing chat history");
        jclass listClass = env->GetObjectClass(chat_history);
        if (!listClass) {
            MNN_DEBUG("Error: Failed to get chat history class");
            env->ReleaseStringUTFChars(modelDir, model_dir);
            return 0;
        }

        jmethodID sizeMethod = env->GetMethodID(listClass, "size", "()I");
        jmethodID getMethod = env->GetMethodID(listClass, "get", "(I)Ljava/lang/Object;");

        if (!sizeMethod || !getMethod) {
            MNN_DEBUG("Error: Failed to get chat history methods");
            env->ReleaseStringUTFChars(modelDir, model_dir);
            return 0;
        }

        jint listSize = env->CallIntMethod(chat_history, sizeMethod);
        MNN_DEBUG("Chat history size: %d", listSize);

        for (jint i = 0; i < listSize; i++) {
            jobject element = env->CallObjectMethod(chat_history, getMethod, i);
            if (!element) {
                MNN_DEBUG("Error: Null element at index %d", i);
                continue;
            }

            const char *elementCStr = env->GetStringUTFChars((jstring)element, nullptr);
            std::string role = (i == 0) ? "user" : "assistant";
            MNN_DEBUG("Adding history entry %d - Role: %s, Content: %s", i, role.c_str(), elementCStr);

            history.emplace_back(role, elementCStr);
            env->ReleaseStringUTFChars((jstring)element, elementCStr);
            env->DeleteLocalRef(element);
        }
    } else {
        MNN_DEBUG("No existing chat history provided");
    }

    MNN_DEBUG("Loading model...");
    try {
        llm->load();
        MNN_DEBUG("Model loaded successfully");
    } catch (const std::exception& e) {
        MNN_DEBUG("Error loading model: %s", e.what());
        env->ReleaseStringUTFChars(modelDir, model_dir);
        return 0;
    }

    jlong ptr = reinterpret_cast<jlong>(llm);
    MNN_DEBUG("Model initialization complete. Native pointer: %ld", ptr);

    // Print final history state
    MNN_DEBUG("Final conversation history state (%zu entries):", history.size());
    for (size_t i = 0; i < history.size(); i++) {
        MNN_DEBUG("History entry %zu - Role: %s, Content: %s",
                  i,
                  history[i].first.c_str(),
                  history[i].second.c_str());
    }

    env->ReleaseStringUTFChars(modelDir, model_dir);
    MNN_DEBUG("=== initNative End ===");
    return ptr;
}

JNIEXPORT jobject JNICALL Java_com_example_mnn_1llm_1test_MnnLlmJni_submitNative(JNIEnv* env, jobject thiz,
                                                                                 jlong llmPtr, jstring inputStr, jboolean keepHistory,
                                                                                 jobject progressListener) {
    MNN_DEBUG("submitNative called with parameters:");
    MNN_DEBUG("llmPtr: %ld", llmPtr);
    MNN_DEBUG("keepHistory: %d", keepHistory);

    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    if (!llm) {
        MNN_DEBUG("Error: Chat is not ready (null llm pointer)");
        return env->NewStringUTF("Failed, Chat is not ready!");
    }

    stop_requested = false;
    if (!keepHistory) {
        MNN_DEBUG("Clearing history (keepHistory is false)");
        history.resize(1);
        MNN_DEBUG("History cleared, only keeping system prompt");
    } else {
        MNN_DEBUG("Keeping existing history (keepHistory is true)");
    }

    const char* input_str = env->GetStringUTFChars(inputStr, nullptr);
    MNN_DEBUG("Input string received: '%s'", input_str);

    std::stringstream response_buffer;
    jclass progressListenerClass = env->GetObjectClass(progressListener);
    jmethodID onProgressMethod = env->GetMethodID(progressListenerClass, "onProgress", "(Ljava/lang/String;)Z");

    if (!onProgressMethod) {
        MNN_DEBUG("Error: ProgressListener onProgress method not found");
    } else {
        MNN_DEBUG("ProgressListener successfully initialized");
    }

    MNN_DEBUG("Setting up UTF8 stream processor");
    Utf8StreamProcessor processor([&response_buffer, env, progressListener, onProgressMethod](const std::string& utf8Char) {
        bool is_eop = utf8Char.find("<eop>") != std::string::npos;
        if (!is_eop) {
            response_buffer << utf8Char;
            MNN_DEBUG("Processing response chunk: '%s'", utf8Char.c_str());
        } else {
            std::string response_result = response_buffer.str();
            history.emplace_back("assistant", response_result);
            MNN_DEBUG("Complete response received: '%s'", response_result.c_str());
        }

        if (progressListener && onProgressMethod) {
            jstring javaString = is_eop ? nullptr : env->NewStringUTF(utf8Char.c_str());
            stop_requested = is_eop || env->CallBooleanMethod(progressListener, onProgressMethod, javaString);
            if (stop_requested) {
                MNN_DEBUG("Generation stopped by progress listener");
            }
            env->DeleteLocalRef(javaString);
        }
    });

    MNN_DEBUG("Setting up stream buffer");
    LlmStreamBuffer stream_buffer{[&processor](const char* str, size_t len){
        processor.processStream(str, len);
    }};
    std::ostream output_ostream(&stream_buffer);

    history.emplace_back("user", input_str);
    MNN_DEBUG("Current conversation history (%zu entries):", history.size());
    for (size_t i = 0; i < history.size(); ++i) {
        const auto& entry = history[i];
        MNN_DEBUG("Entry %zu: [%s] '%s'", i, entry.first.c_str(), entry.second.c_str());
    }

    MNN_DEBUG("Starting model response generation");
    llm->response(history, &output_ostream, "<eop>", 1);

    MNN_DEBUG("Entering generation loop");
    int generation_steps = 0;
    while (!stop_requested && llm->getState().gen_seq_len_ < 512) {
        llm->generate(1);
        generation_steps++;
        if (generation_steps % 10 == 0) {
            MNN_DEBUG("Generated %d tokens so far", llm->getState().gen_seq_len_);
        }
    }
    MNN_DEBUG("Generation complete after %d steps", generation_steps);

    auto& state = llm->getState();
    int64_t prompt_len = state.prompt_len_;
    int64_t decode_len = state.gen_seq_len_;
    int64_t vision_time = state.vision_us_;
    int64_t audio_time = state.audio_us_;
    int64_t prefill_time = state.prefill_us_;
    int64_t decode_time = state.decode_us_;

    MNN_DEBUG("Model performance metrics:");
    MNN_DEBUG("- Prompt length: %ld tokens", prompt_len);
    MNN_DEBUG("- Generated length: %ld tokens", decode_len);
    MNN_DEBUG("- Vision processing time: %ld μs", vision_time);
    MNN_DEBUG("- Audio processing time: %ld μs", audio_time);
    MNN_DEBUG("- Prefill time: %ld μs", prefill_time);
    MNN_DEBUG("- Decode time: %ld μs", decode_time);

    MNN_DEBUG("Creating return HashMap");
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);

    // Add metrics to HashMap with detailed logging
    MNN_DEBUG("Adding metrics to HashMap");
    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("prompt_len"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         prompt_len));

    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("decode_len"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         decode_len));

    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("vision_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         vision_time));

    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("audio_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         audio_time));

    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("prefill_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         prefill_time));

    env->CallObjectMethod(hashMap, putMethod,
                          env->NewStringUTF("decode_time"),
                          env->NewObject(env->FindClass("java/lang/Long"),
                                         env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"),
                                         decode_time));

    MNN_DEBUG("submitNative complete, returning metrics");
    env->ReleaseStringUTFChars(inputStr, input_str);
    MNN_DEBUG("Final response buffer content: '%s'", response_buffer.str().c_str());

    return hashMap;
}


JNIEXPORT void JNICALL Java_com_example_mnn_1llm_1test_MnnLlmJni_resetNative(JNIEnv* env, jobject thiz, jlong llmPtr) {
    history.resize(1);
    Llm* llm = reinterpret_cast<Llm*>(llmPtr);
    if (llm) {
        llm->reset();
    }
}

JNIEXPORT void JNICALL Java_com_example_mnn_1llm_1test_MnnLlmJni_releaseNative(JNIEnv* env,
                                                                                      jobject thiz,
                                                                                      jlong objecPtr,
                                                                                      jboolean isDiffusion) {
    MNN_DEBUG("Java_com_example_mnn_llm_test_ChatSession_releaseNative\n");
    if (isDiffusion) {
        auto* diffusion = reinterpret_cast<DiffusionSession*>(objecPtr);
        delete diffusion;
    } else {
        Llm* llm = reinterpret_cast<Llm*>(objecPtr);
        delete llm;
    }
}

JNIEXPORT jobject JNICALL
Java_com_example_mnn_1llm_1test_MnnLlmJni_submitDiffusionNative(JNIEnv *env, jobject thiz,
                                                                       jlong instance_id,
                                                                       jstring input,
                                                                       jstring joutput_path,
                                                                       jobject progressListener) {
    auto* diffusion = reinterpret_cast<DiffusionSession*>(instance_id); // Cast back to Llm*
    if (!diffusion) {
        return nullptr;
    }
    jclass progressListenerClass = env->GetObjectClass(progressListener);
    jmethodID onProgressMethod = env->GetMethodID(progressListenerClass, "onProgress", "(Ljava/lang/String;)Z");
    if (!onProgressMethod) {
        MNN_DEBUG("ProgressListener onProgress method not found.");
    }
    std::string prompt = env->GetStringUTFChars(input, nullptr);
    std::string output_path = env->GetStringUTFChars(joutput_path, nullptr);
    auto start = std::chrono::high_resolution_clock::now();
    diffusion->Run(prompt, output_path, [env, progressListener, onProgressMethod](int progress) {
        if (progressListener && onProgressMethod) {
            jstring javaString =  env->NewStringUTF(std::to_string(progress).c_str());
            env->CallBooleanMethod(progressListener, onProgressMethod,  javaString);
            env->DeleteLocalRef(javaString);
        }
    });
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start).count();
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);
    env->CallObjectMethod(hashMap, putMethod, env->NewStringUTF("total_timeus"), env->NewObject(env->FindClass("java/lang/Long"), env->GetMethodID(env->FindClass("java/lang/Long"), "<init>", "(J)V"), duration));
    return hashMap;
}
}