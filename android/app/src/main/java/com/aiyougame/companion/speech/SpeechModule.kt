package com.aiyougame.companion.speech

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for speech recognition components.
 *
 * All speech components ([AudioRecorder], [WhisperEngine], [VoiceRecognitionManager])
 * use `@Inject` constructors with Hilt-provided dependencies, so no explicit
 * `@Provides` methods are needed here.
 *
 * The `@Named("download") OkHttpClient` is provided by [com.aiyougame.companion.data.network.NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SpeechModule
