package com.unlock.core

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes + caches launcher icons off the main thread. */
object IconLoader {

    private val cache = LruCache<String, ImageBitmap>(512)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = 144, height = 144)
                bitmap.asImageBitmap().also { cache.put(packageName, it) }
            }.getOrNull()
        }
    }
}
