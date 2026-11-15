package edu.androidsec.notes.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Фото читаем только из private photos; испорченный файл не рушит экран. */
@RunWith(AndroidJUnit4::class)
class PhotoStoreSecurityTest {
    @Test fun outsidePathAndDamagedImageDoNotProducePreview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photoStore = PhotoStore(context)
        val outside = File(context.cacheDir, "not-an-image.jpg").apply { writeText("invalid") }
        val inside = File(context.filesDir, "photos/corrupt.jpg")
        inside.parentFile?.mkdirs()
        inside.writeText("invalid")
        try {
            assertNull(photoStore.loadPreview(outside.absolutePath))
            assertNull(photoStore.loadPreview(inside.absolutePath))
        } finally {
            outside.delete()
            inside.delete()
        }
    }
}
