package com.aiyougame.companion.speech

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {

    // AudioRecorder, WhisperEngine, and VoiceRecognitionManager all have @Inject constructors
    // with Hilt-provided dependencies. Hilt injects them automatically.
    // Only provide SpeechToTextEngine interface (implemented by WhisperEngine)
    @Provides
    @Singleton
    fun provideSpeechToTextEngine(whisperEngine: WhisperEngine): SpeechToTextEngine = whisperEngine
}
