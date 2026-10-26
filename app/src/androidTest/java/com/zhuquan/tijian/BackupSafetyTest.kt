package com.zhuquan.tijian

import androidx.test.platform.app.InstrumentationRegistry
import com.zhuquan.tijian.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import android.database.sqlite.SQLiteDatabase

class BackupSafetyTest {
    @Test fun backupRemovesKeysFromCurrentAndSavedProviders() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "backup-test-${System.nanoTime()}.db"
        val db = Db(ctx, name)
        val extracted = File(ctx.cacheDir, "backup-check-${System.nanoTime()}.db")
        try {
            db.saveProvider(ProviderConfig("https://example.com/v1", "secret-current", "vision"))
            db.saveProviderProfiles(listOf(ProviderProfile("test", "https://example.com/v1", "secret-saved", "vision", "chat")))
            db.saveDrafts(listOf(DraftQuestion(imageId = "", imagePath = "", stem = "备份题目")), null)
            val output = ByteArrayOutputStream()
            Backup.export(ctx, db, output)
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
                var entry = zip.nextEntry
                while(entry != null) {
                    if(entry.name == "db/tijian.db") extracted.outputStream().use { zip.copyTo(it) }
                    entry = zip.nextEntry
                }
            }
            SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
                sql.rawQuery("SELECT v FROM setting WHERE k IN ('ai.apiKey','ai.profiles')", null).use { c ->
                    while(c.moveToNext()) {
                        assertFalse(c.getString(0).contains("secret-current"))
                        assertFalse(c.getString(0).contains("secret-saved"))
                    }
                }
                sql.rawQuery("SELECT COUNT(*) FROM question", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            }
            val raw = extracted.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(raw.contains("secret-current"))
            assertFalse(raw.contains("secret-saved"))
            assertEquals("secret-current", db.provider().apiKey)
        } finally { db.close(); ctx.deleteDatabase(name); extracted.delete() }
    }
}
