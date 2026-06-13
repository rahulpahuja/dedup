package com.rp.dedup.core.search

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder

class EmbedderProvider(context: Context) {

    companion object {
        private const val TAG         = "EmbedderProvider"
        private const val MODEL_ASSET = "universal_sentence_encoder.tflite"
    }

    private val appContext = context.applicationContext

    private val embedder: TextEmbedder? by lazy {
        try {
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(Delegate.CPU)
                        .build()
                )
                .build()
            val emb = TextEmbedder.createFromOptions(appContext, options)
            // Warm-up: catches Java-level init failures before the first real call.
            try {
                emb.embed("init")
            } catch (t: Throwable) {
                Log.e(TAG, "Embedder warm-up failed — disabling semantic search", t)
                try { emb.close() } catch (_: Exception) {}
                return@lazy null
            }
            emb
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load TextEmbedder — is the model in assets/?", t)
            null
        }
    }

    val isAvailable: Boolean get() = embedder != null

    fun embed(text: String): FloatArray? {
        val emb = embedder ?: return null
        return try {
            val embeddings = emb.embed(text).embeddingResult().embeddings()
            if (embeddings.isEmpty()) return null
            // floatEmbedding() returns a Java ImmutableList<Float> — convert explicitly.
            val floatList = embeddings[0].floatEmbedding()
            FloatArray(floatList.size) { i -> floatList[i] }
        } catch (t: Throwable) {
            Log.e(TAG, "embed() failed for text='$text'", t)
            null
        }
    }

    fun close() = embedder?.close()
}
