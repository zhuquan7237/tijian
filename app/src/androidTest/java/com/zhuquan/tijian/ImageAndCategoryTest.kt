package com.zhuquan.tijian

import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.ai.AiClient
import com.zhuquan.tijian.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class ImageAndCategoryTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun originalBytesAndDimensionsSurviveImportAndCropKeepsPixels() = runBlocking {
        val db = Db(ctx, "image-regression.db")
        val source = File(ctx.cacheDir, "source-regression.png")
        val crop = File(ctx.cacheDir, "crop-regression.png")
        try {
            val bitmap = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(Color.WHITE); drawRect(800f, 600f, 2400f, 1800f, Paint().apply { color = Color.RED }) }
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val importer = Importer(ctx, db, AiClient())
            val image = importer.ingestOnly(Uri.fromFile(source))
            assertEquals(3200, image.width); assertEquals(2400, image.height)
            assertArrayEquals(source.readBytes(), File(image.path).readBytes())
            cropOriginal(File(image.path), RectF(.25f, .25f, .75f, .75f), crop)
            val decoded = BitmapFactory.decodeFile(crop.path)
            assertEquals(1600, decoded.width); assertEquals(1200, decoded.height); assertEquals(Color.RED, decoded.getPixel(500, 500)); decoded.recycle()
            val edited = importer.ingestOnly(Uri.fromFile(crop)); db.linkOriginal(edited.id, image.id)
            assertEquals(image.path, db.originalImagePath(edited.id))
            assertArrayEquals(source.readBytes(), File(image.path).readBytes())
            val again = importer.ingestOnly(Uri.fromFile(source)); assertEquals(image.id, again.id); assertTrue(again.duplicate)
        } finally { db.close(); ctx.deleteDatabase("image-regression.db"); source.delete(); crop.delete() }
    }
    @Test fun rotatedAndMirroredImagesCropInDisplayedCoordinates() {
        for (orientation in listOf(2, 5, 6, 7, 8)) {
            val source = File(ctx.cacheDir, "orientation-$orientation.jpg")
            val target = File(ctx.cacheDir, "orientation-$orientation.png")
            try {
                val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.BLUE)
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }; bitmap.recycle()
                ExifInterface(source.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
                cropOriginal(source, RectF(0f, 0f, 1f, .5f), target)
                val decoded = BitmapFactory.decodeFile(target.path)
                assertEquals(if (orientation == 2) 800 else 400, decoded.width)
                assertEquals(if (orientation == 2) 200 else 400, decoded.height); decoded.recycle()
            } finally { source.delete(); target.delete() }
        }
    }
    @Test fun sameNamedSubcategoriesStayInTheirSubjectAndDeletionKeepsQuestions() {
        val db = Db(ctx, "category-regression.db")
        try {
            val math = db.ensureCategory("数学/基础", false)
            val english = db.ensureCategory("英语/基础", false)
            assertNotEquals(math, english); assertEquals(math, db.ensureCategory("数学/基础"))
            assertEquals("数学", db.category(math)?.subjectGroup())
            val other = db.ensureCategory("无法确定/例题"); assertEquals("其他", db.category(other)?.subjectGroup())
            val q = db.insertQuestion(DraftQuestion(imageId = "", imagePath = "", stem = "回归题"), math, null, "数学")
            db.deleteCategory(math)
            assertNotNull(db.question(q)); assertEquals(Db.UNCAT, db.question(q)?.categoryId)
            assertNotNull(db.category(english))
        } finally { db.close(); ctx.deleteDatabase("category-regression.db") }
    }
    @Test fun versionOneUpgradeKeepsExistingImageRows() {
        val name = "migration-regression.db"
        ctx.deleteDatabase(name)
        val old = ctx.openOrCreateDatabase(name, 0, null)
        old.execSQL("CREATE TABLE image(id TEXT PRIMARY KEY,path TEXT)")
        old.execSQL("INSERT INTO image VALUES('legacy','/original.jpg')")
        old.version = 1; old.close()
        val db = Db(ctx, name)
        try { assertEquals("/original.jpg", db.originalImagePath("legacy")) }
        finally { db.close(); ctx.deleteDatabase(name) }
    }
}
