package com.ohmz.tday.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers what the attachment pipeline refuses as much as what it accepts — the rejections are the
 * security boundary between "a user attached a photo" and "a user handed the server an arbitrary
 * file to write somewhere".
 */
class AttachmentStorageTest {

    @TempDir
    lateinit var tempDir: File

    private fun storage() = AttachmentStorage(tempDir.absolutePath)

    private fun imageBytes(
        format: String,
        width: Int = 120,
        height: Int = 80,
        withAlpha: Boolean = false,
    ): ByteArray {
        val type = if (withAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val graphics = image.createGraphics()
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, format, output)
        return output.toByteArray()
    }

    // MARK: - Format detection

    @Test
    fun `detects jpeg and png from magic bytes`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(imageBytes("jpeg")))
        assertEquals(ImageFormat.PNG, ImageFormat.detect(imageBytes("png")))
    }

    @Test
    fun `rejects a non-image that claims to be one`() {
        assertNull(ImageFormat.detect("not an image at all".toByteArray()))
    }

    /** The declared content type is attacker-controlled; only the bytes decide the format. */
    @Test
    fun `content type is not trusted over magic bytes`() {
        val result = storage().sanitize("<?php echo 1; ?>".toByteArray(), maxBytes = 1_000_000)
        assertTrue(result.isFailure)
        assertEquals(
            ImageRejection.UnsupportedType,
            (result.exceptionOrNull() as ImageRejectedException).rejection,
        )
    }

    @Test
    fun `rejects gif even though ImageIO can decode it`() {
        assertNull(ImageFormat.detect(imageBytes("gif")))
    }

    // MARK: - Limits

    @Test
    fun `rejects bytes over the size cap`() {
        val result = storage().sanitize(imageBytes("png"), maxBytes = 10)
        assertTrue(result.isFailure)
        assertEquals(
            ImageRejection.TooLarge,
            (result.exceptionOrNull() as ImageRejectedException).rejection,
        )
    }

    // MARK: - Sanitizing

    @Test
    fun `sanitize returns normalized bytes a thumbnail and real dimensions`() {
        val result = storage().sanitize(imageBytes("jpeg", width = 900, height = 600), maxBytes = 5_000_000)
        val image = result.getOrThrow()

        assertEquals("image/jpeg", image.contentType)
        assertEquals(900, image.width)
        assertEquals(600, image.height)
        assertTrue(image.bytes.isNotEmpty())
        assertTrue(image.thumbnailBytes.isNotEmpty())
        assertTrue(
            image.thumbnailBytes.size < image.bytes.size,
            "thumbnail should be smaller than the full image",
        )
    }

    /**
     * The whole reason images are decoded and re-encoded: EXIF must not survive, because phone
     * cameras write GPS coordinates into it by default and a task attachment would otherwise
     * publish where the photo was taken.
     */
    @Test
    fun `sanitize strips an EXIF segment from the stored image`() {
        val withExif = jpegWithExifSegment(imageBytes("jpeg"))
        assertTrue(containsExifMarker(withExif), "test fixture should start with an EXIF segment")

        val sanitized = storage().sanitize(withExif, maxBytes = 5_000_000).getOrThrow()

        assertFalse(containsExifMarker(sanitized.bytes), "EXIF must not survive into the stored image")
        assertFalse(containsExifMarker(sanitized.thumbnailBytes), "EXIF must not survive into the thumbnail")
    }

    /** Splices an APP1 "Exif" segment in directly after the SOI marker. */
    private fun jpegWithExifSegment(jpeg: ByteArray): ByteArray {
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + "GPSLatitude=51.5074".toByteArray()
        val segmentLength = payload.size + 2
        val header = byteArrayOf(
            0xFF.toByte(),
            0xE1.toByte(),
            ((segmentLength shr 8) and 0xFF).toByte(),
            (segmentLength and 0xFF).toByte(),
        )
        return jpeg.copyOfRange(0, 2) + header + payload + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun containsExifMarker(bytes: ByteArray): Boolean =
        String(bytes, Charsets.ISO_8859_1).contains("GPSLatitude")

    /** JPEG has no alpha channel; an un-flattened ARGB source silently produces a broken file. */
    @Test
    fun `flattens transparency when the source has an alpha channel`() {
        val sanitized = storage().sanitize(imageBytes("png", withAlpha = true), maxBytes = 5_000_000)
        assertTrue(sanitized.isSuccess)
        assertNotNull(ImageIO.read(sanitized.getOrThrow().bytes.inputStream()))
    }

    @Test
    fun `small images still produce a readable thumbnail`() {
        val sanitized = storage().sanitize(imageBytes("png", width = 10, height = 10), maxBytes = 5_000_000).getOrThrow()
        assertNotNull(ImageIO.read(sanitized.thumbnailBytes.inputStream()))
    }

    // MARK: - Writing and reading

    @Test
    fun `write shards by id prefix and read returns the same bytes`() {
        val store = storage()
        val payload = imageBytes("png")
        val key = store.write("abcd1234", "", payload, "png")

        assertEquals("ab/abcd1234.png", key)
        assertTrue(File(tempDir, key).isFile)
        assertTrue(payload.contentEquals(store.read(key)!!))
    }

    @Test
    fun `read returns null for a key with no file`() {
        assertNull(storage().read("zz/missing.png"))
    }

    @Test
    fun `delete removes the file and tolerates a null key`() {
        val store = storage()
        val key = store.write("abcd1234", "", imageBytes("png"), "png")
        store.delete(key)
        assertFalse(File(tempDir, key).exists())
        store.delete(null)
    }

    /**
     * Storage keys are server-generated, so this should be unreachable. It is asserted anyway so a
     * future change that lets a key be influenced from outside fails here rather than writing
     * outside the attachment root.
     */
    @Test
    fun `refuses a key that escapes the storage root`() {
        val store = storage()
        val escaped = runCatching { store.read("../../etc/passwd") }
        assertTrue(escaped.isFailure, "a traversing key must not resolve")
    }
}
