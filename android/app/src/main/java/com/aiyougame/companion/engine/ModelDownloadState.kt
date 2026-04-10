package com.aiyougame.companion.engine

/**
 * Represents the current state of model download.
 * Used by UI to observe download progress.
 */
sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class Downloading(val progress: Int) : ModelDownloadState()
    object Completed : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}
