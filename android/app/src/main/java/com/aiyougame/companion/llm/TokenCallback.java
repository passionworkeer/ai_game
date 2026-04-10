package com.aiyougame.companion.llm;

/**
 * JNI callback interface for streaming token delivery.
 * Implemented by LlamaEngineImpl and passed to nativeGenerateStream.
 * Using Java interface (not Kotlin interface) for reliable JNI method lookup.
 */
public interface TokenCallback {
    void onToken(String token);
    void onDone(String fullText);
    void onError(String error);
}
