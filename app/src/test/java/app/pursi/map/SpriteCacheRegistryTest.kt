package app.pursi.map

import android.graphics.Bitmap
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import io.mockk.clearAllMocks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteCacheRegistryTest {

    @After
    fun tearDown() {
        SpriteCacheRegistry.recycleAll()
        clearAllMocks()
    }

    private fun mockBitmap(recycled: Boolean = false): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.isRecycled } returns recycled
        return bmp
    }

    @Test
    fun `track and untrack`() {
        val bmp = mockBitmap()
        SpriteCacheRegistry.track(bmp, "test")
        assertEquals(1, SpriteCacheRegistry.trackedCount)

        SpriteCacheRegistry.untrack(bmp)
        assertEquals(0, SpriteCacheRegistry.trackedCount)
        // untrack should NOT call recycle
        verify(exactly = 0) { bmp.recycle() }
    }

    @Test
    fun `recycleByLabel recycles matching bitmaps and skips non-matching`() {
        val a = mockBitmap()
        val b = mockBitmap()
        val other = mockBitmap()

        SpriteCacheRegistry.track(a, "sprite-foo")
        SpriteCacheRegistry.track(b, "sprite-bar")
        SpriteCacheRegistry.track(other, "other-xxx")

        val recycled = SpriteCacheRegistry.recycleByLabel("sprite-")
        assertEquals(2, recycled)

        verify { a.recycle() }
        verify { b.recycle() }
        verify(exactly = 0) { other.recycle() }
        assertEquals(1, SpriteCacheRegistry.trackedCount)
    }

    @Test
    fun `recycleByLabel skips already-recycled bitmaps`() {
        val bmp = mockBitmap(recycled = true)
        SpriteCacheRegistry.track(bmp, "test")

        val recycled = SpriteCacheRegistry.recycleByLabel("test")
        assertEquals(0, recycled)
        verify(exactly = 0) { bmp.recycle() }
        assertEquals(0, SpriteCacheRegistry.trackedCount)
    }

    @Test
    fun `recycleAll clears everything`() {
        val a = mockBitmap()
        val b = mockBitmap()
        SpriteCacheRegistry.track(a, "a")
        SpriteCacheRegistry.track(b, "b")

        val recycled = SpriteCacheRegistry.recycleAll()
        assertEquals(2, recycled)
        verify { a.recycle() }
        verify { b.recycle() }
        assertEquals(0, SpriteCacheRegistry.trackedCount)
    }

    @Test
    fun `untrack removes without recycling`() {
        val bmp = mockBitmap()
        SpriteCacheRegistry.track(bmp, "test")
        SpriteCacheRegistry.untrack(bmp)
        assertEquals(0, SpriteCacheRegistry.trackedCount)
        verify(exactly = 0) { bmp.recycle() }
    }

    @Test
    fun `same bitmap tracked multiple times keeps single entry`() {
        val bmp = mockBitmap()
        SpriteCacheRegistry.track(bmp, "a")
        SpriteCacheRegistry.track(bmp, "b")

        assertEquals(1, SpriteCacheRegistry.trackedCount)

        val recycled = SpriteCacheRegistry.recycleAll()
        assertEquals(1, recycled)
        verify(exactly = 1) { bmp.recycle() }
        assertEquals(0, SpriteCacheRegistry.trackedCount)
    }

    @Test
    fun `recycleByLabel with empty prefix returns zero`() {
        val bmp = mockBitmap()
        SpriteCacheRegistry.track(bmp, "test")
        val recycled = SpriteCacheRegistry.recycleByLabel("nonexistent")
        assertEquals(0, recycled)
        verify(exactly = 0) { bmp.recycle() }
        assertEquals(1, SpriteCacheRegistry.trackedCount)
    }

    @Test
    fun `recycleAll on empty registry returns zero`() {
        val recycled = SpriteCacheRegistry.recycleAll()
        assertEquals(0, recycled)
    }
}
