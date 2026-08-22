package com.ohmz.tday.services

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** A picture that passed validation and is ready to be written to disk. */
data class SanitizedImage(
    val bytes: ByteArray,
    val contentType: String,
    val width: Int,
    val height: Int,
    val thumbnailBytes: ByteArray,
) {
    // Data class equality on ByteArray compares references, which is never what a caller wants.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

sealed interface ImageRejection {
    data object UnsupportedType : ImageRejection
    data object TooLarge : ImageRejection
    data object TooManyPixels : ImageRejection
    data object Unreadable : ImageRejection
}

/**
 * Validates, sanitizes, and stores attachment bytes on disk.
 *
 * Three things this deliberately does not trust:
 *
 * 1. The declared content type. The format is decided by the file's magic bytes, because a client
 *    can label anything `image/jpeg`.
 * 2. The client filename. It is stored as metadata only and never appears in a path — storage keys
 *    are server-generated, which is what makes path traversal impossible rather than something that
 *    has to be filtered for.
 * 3. The pixel dimensions implied by the byte count. A few kilobytes of PNG can decode to a
 *    multi-gigabyte raster, so dimensions are read from the header and checked before any decode.
 *
 * Images are decoded and re-encoded rather than stored verbatim. That drops all EXIF — including
 * the GPS coordinates phone cameras embed by default — which matters more here than the small
 * quality cost of a JPEG round-trip, for an app that already encrypts task titles at rest.
 */
class AttachmentStorage(rootDirectory: String) {

    private val root: File = File(rootDirectory).absoluteFile

    init {
        // Fail loudly at startup rather than on the user's first upload.
        if (!root.exists() && !root.mkdirs()) {
            System.err.println("[attachments] Unable to create attachment directory: $root")
        }
    }

    val isWritable: Boolean get() = root.isDirectory && root.canWrite()

    /**
     * Decodes [raw], enforces the limits, and produces normalized bytes plus a thumbnail.
     * Returns a rejection reason instead of throwing so routes can map it to a status code.
     */
    fun sanitize(raw: ByteArray, maxBytes: Long): Result<SanitizedImage> {
        if (raw.size > maxBytes) return Result.failure(ImageRejectedException(ImageRejection.TooLarge))

        val format = ImageFormat.detect(raw)
            ?: return Result.failure(ImageRejectedException(ImageRejection.UnsupportedType))

        val header = readDimensions(raw)
            ?: return Result.failure(ImageRejectedException(ImageRejection.Unreadable))
        if (header.first.toLong() * header.second.toLong() > MAX_PIXELS) {
            return Result.failure(ImageRejectedException(ImageRejection.TooManyPixels))
        }

        val decoded = runCatching { ImageIO.read(ByteArrayInputStream(raw)) }.getOrNull()
            ?: return Result.failure(ImageRejectedException(ImageRejection.Unreadable))

        val normalized = runCatching { encode(decoded, format) }.getOrNull()
            ?: return Result.failure(ImageRejectedException(ImageRejection.Unreadable))
        val thumbnail = runCatching { encode(scaleToThumbnail(decoded), format) }.getOrNull()
            ?: return Result.failure(ImageRejectedException(ImageRejection.Unreadable))

        return Result.success(
            SanitizedImage(
                bytes = normalized,
                contentType = format.contentType,
                width = decoded.width,
                height = decoded.height,
                thumbnailBytes = thumbnail,
            ),
        )
    }

    /**
     * Writes [bytes] under a server-generated key derived from [attachmentId].
     *
     * Sharded by the id's first two characters so one directory does not accumulate every
     * attachment on the instance.
     */
    fun write(attachmentId: String, suffix: String, bytes: ByteArray, format: String): String {
        val shard = attachmentId.take(2).lowercase().ifEmpty { "00" }
        val key = "$shard/$attachmentId$suffix.$format"
        val target = resolve(key)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return key
    }

    fun read(storageKey: String): ByteArray? {
        val file = resolve(storageKey)
        return if (file.isFile) file.readBytes() else null
    }

    fun delete(storageKey: String?) {
        val key = storageKey ?: return
        runCatching { resolve(key).delete() }
    }

    /**
     * Resolves a stored key under the attachment root, refusing anything that escapes it.
     *
     * Keys are server-generated, so this should never trip — it is here so that a future bug that
     * lets a key be influenced from outside cannot turn into arbitrary filesystem access.
     */
    private fun resolve(storageKey: String): File {
        val candidate = File(root, storageKey).canonicalFile
        val rootPath = root.canonicalFile.toPath()
        require(candidate.toPath().startsWith(rootPath)) { "attachment key escapes storage root" }
        return candidate
    }

    private fun encode(image: BufferedImage, format: ImageFormat): ByteArray {
        val output = ByteArrayOutputStream()
        // JPEG has no alpha channel; writing an image that has one produces a corrupt file rather
        // than an error, so it is flattened onto white first.
        val source = if (format == ImageFormat.JPEG && image.colorModel.hasAlpha()) {
            flatten(image)
        } else {
            image
        }
        if (!ImageIO.write(source, format.writerName, output)) {
            error("no writer for ${format.writerName}")
        }
        return output.toByteArray()
    }

    private fun flatten(image: BufferedImage): BufferedImage {
        val flattened = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = flattened.createGraphics()
        graphics.drawImage(image, 0, 0, java.awt.Color.WHITE, null)
        graphics.dispose()
        return flattened
    }

    private fun scaleToThumbnail(image: BufferedImage): BufferedImage {
        val scale = THUMBNAIL_MAX_EDGE.toDouble() / maxOf(image.width, image.height)
        if (scale >= 1.0) return image

        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val scaled = BufferedImage(width, height, type)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    /** Reads width/height from the header without decoding the raster. */
    private fun readDimensions(raw: ByteArray): Pair<Int, Int>? {
        ImageIO.createImageInputStream(ByteArrayInputStream(raw)).use { stream ->
            if (stream == null) return null
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            return try {
                reader.input = stream
                reader.getWidth(reader.minIndex) to reader.getHeight(reader.minIndex)
            } catch (_: Exception) {
                null
            } finally {
                reader.dispose()
            }
        }
    }

    companion object {
        const val THUMBNAIL_SUFFIX = "_thumb"

        private const val THUMBNAIL_MAX_EDGE = 400

        /**
         * ~40 megapixels. Comfortably above any phone camera, far below what it takes to exhaust
         * heap on decode (a 40MP ARGB raster is already ~160MB).
         */
        private const val MAX_PIXELS = 40_000_000L
    }
}

class ImageRejectedException(val rejection: ImageRejection) : Exception(rejection.toString())

/**
 * Formats identified by magic bytes rather than by the declared content type.
 *
 * JPEG and PNG only: a stock JDK's ImageIO can neither read nor write WebP, and pulling in an
 * image-codec dependency to a self-hosted app that otherwise needs only Postgres is a worse trade
 * than asking clients to transcode.
 */
enum class ImageFormat(val contentType: String, val writerName: String, val fileSuffix: String) {
    JPEG("image/jpeg", "jpeg", "jpg"),
    PNG("image/png", "png", "png"),
    ;

    companion object {
        fun detect(bytes: ByteArray): ImageFormat? = when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> JPEG

            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() &&
                bytes[3] == 'G'.code.toByte() &&
                bytes[4] == 0x0D.toByte() &&
                bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0x0A.toByte() -> PNG

            else -> null
        }

        fun fromContentType(contentType: String?): ImageFormat? =
            entries.firstOrNull { it.contentType.equals(contentType, ignoreCase = true) }
    }
}
