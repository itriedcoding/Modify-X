package dev.modifyx.ui.components

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.modifyx.data.FileCategory
import dev.modifyx.data.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preview of a real document. Images are decoded and downsampled from the URI the user
 * granted; everything else shows its extension instead of a fake image.
 */
@Composable
fun FilePreview(
    resolver: ContentResolver,
    file: ScannedFile,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = Thumbnails.cached(file.uri), file.uri) {
        if (value == null && file.category == FileCategory.IMAGE) {
            value = withContext(Dispatchers.IO) { Thumbnails.load(resolver, file.uri) }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = file.name,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
        } else {
            Text(
                text = label(file),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun label(file: ScannedFile): String {
    val extension = file.name.substringAfterLast('.', "")
    return if (extension.isNotBlank() && extension.length <= 4) extension.uppercase() else file.category.label.take(3).uppercase()
}

private object Thumbnails {

    private val cache = LruCache<String, ImageBitmap>(96)

    fun cached(uri: String): ImageBitmap? = cache.get(uri)

    fun load(resolver: ContentResolver, uriString: String): ImageBitmap? {
        cache.get(uriString)?.let { return it }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 200 && bounds.outHeight / (sample * 2) >= 200) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null
        val image = bitmap.asImageBitmap()
        cache.put(uriString, image)
        return image
    }
}
