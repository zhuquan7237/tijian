package com.zhuquan.tijian.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份 = 数据库 + 图片，整包 zip。
 * 备份包不含 AI API Key（避免分享备份时泄漏）；恢复会覆盖数据库并重启进程，
 * 属于「换机/重装」级别操作。
 */
object Backup {

    private const val DB = "tijian.db"

    fun export(ctx: Context, db: Db, out: OutputStream): Int {
        // 默认 rollback-journal 模式下持有写事务，阻止写入并复制一致快照。
        val sql = db.writableDatabase
        sql.beginTransactionNonExclusive()
        val tmpDb = File.createTempFile("backup_export_", ".db", ctx.cacheDir)
        try {
            check(!sql.isWriteAheadLoggingEnabled) { "请关闭数据库 WAL 后备份，以保证完整快照" }
            val dbFile = ctx.getDatabasePath(db.databaseName)
            check(dbFile.exists()) { "数据库不存在" }
            dbFile.copyTo(tmpDb, overwrite = true)
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        try {
            if (tmpDb.exists()) {
                SQLiteDatabase.openDatabase(tmpDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { t ->
                    t.execSQL("DELETE FROM setting WHERE k='ai.apiKey'")
                    t.rawQuery("SELECT v FROM setting WHERE k='ai.profiles'", null).use { c ->
                        if(c.moveToFirst()) {
                            val profiles = org.json.JSONArray(c.getString(0))
                            for(i in 0 until profiles.length()) profiles.getJSONObject(i).remove("apiKey")
                            t.execSQL("UPDATE setting SET v=? WHERE k='ai.profiles'", arrayOf(profiles.toString()))
                        }
                    }
                }
            }
            var count = 0
            ZipOutputStream(out).use { zip ->
                if (tmpDb.exists()) {
                    zip.putNextEntry(ZipEntry("db/$DB"))
                    tmpDb.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry(); count++
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(org.json.JSONObject().put("version", 1).put("questions", db.stats().total)
                    .put("createdAt", System.currentTimeMillis()).toString().toByteArray())
                zip.closeEntry()
                val images = File(ctx.filesDir, "images")
                images.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        zip.putNextEntry(ZipEntry("images/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry(); count++
                    }
                }
            }
            return count
        } finally {
            tmpDb.delete()
            File(tmpDb.path + "-wal").delete()
            File(tmpDb.path + "-shm").delete()
        }
    }

    fun import(ctx: Context, db: Db, input: InputStream): Int {
        val tmp = File(ctx.cacheDir, "restore_${System.currentTimeMillis()}")
        tmp.mkdirs()
        var count = 0
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && (name.startsWith("db/") || name.startsWith("images/"))) {
                    val target = File(tmp, name.replace('/', '_'))
                    target.outputStream().use { zip.copyTo(it) }
                    count++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val dbBackup = File(tmp, "db_$DB")
        if (!dbBackup.exists()) throw IllegalStateException("备份包缺少数据库")

        // Validate before touching live data or creating the rollback snapshot.
        SQLiteDatabase.openDatabase(dbBackup.path, null, SQLiteDatabase.OPEN_READWRITE).use { candidate ->
            candidate.rawQuery("PRAGMA integrity_check", null).use { c ->
                check(c.moveToFirst() && c.getString(0) == "ok") { "备份数据库损坏" }
            }
            candidate.rawQuery("SELECT id, stem FROM question LIMIT 1", null).close()
            candidate.rawQuery("SELECT id, path FROM image", null).use { c ->
                while(c.moveToNext()) {
                    val name = File(c.getString(1)).name
                    check(File(tmp, "images_$name").exists()) { "备份缺少图片：$name" }
                    candidate.execSQL("UPDATE image SET path=? WHERE id=?",
                        arrayOf(File(ctx.filesDir, "images/$name").path, c.getString(0)))
                }
            }
            candidate.rawQuery("SELECT v FROM setting WHERE k='drafts.v1'", null).use { c ->
                if(c.moveToFirst()) {
                    val drafts = SessionCodec.decodeDrafts(c.getString(0)).map { d ->
                        if(d.imagePath.isBlank()) d else d.copy(imagePath = File(ctx.filesDir, "images/" + File(d.imagePath).name).path)
                    }
                    candidate.execSQL("UPDATE setting SET v=? WHERE k='drafts.v1'", arrayOf(SessionCodec.encodeDrafts(drafts)))
                }
            }
        }
        val recovery = File(ctx.filesDir, "recovery-before-restore.zip")
        val nextRecovery = File(ctx.filesDir, "recovery-next.zip")
        nextRecovery.outputStream().use { export(ctx, db, it) }
        check(nextRecovery.renameTo(recovery)) { "无法保存恢复前快照" }
        // Stage all images before replacing the database. Existing image IDs are immutable.
        val imageDir = File(ctx.filesDir, "images").apply { mkdirs() }
        tmp.listFiles()?.filter { it.name.startsWith("images_") }?.forEach { f ->
            f.copyTo(File(imageDir, f.name.removePrefix("images_")), overwrite = true)
        }
        db.close()
        val dbFile = ctx.getDatabasePath(DB)
        dbFile.parentFile?.mkdirs()
        val replacement = File(dbFile.parentFile, "restore-next.db")
        dbBackup.copyTo(replacement, overwrite = true)
        check(replacement.renameTo(dbFile)) { "替换数据库失败，原数据与恢复前快照仍保留" }
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()

        tmp.deleteRecursively()
        File(ctx.filesDir, "capture_staging").deleteRecursively()

        // 旧进程里可能还有别的协程握着打开的连接，直接换文件会读到半新半旧状态。
        // 安排一次重启，让所有连接随进程一起重建。
        val relaunch = Intent(ctx, com.zhuquan.tijian.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pi = PendingIntent.getActivity(ctx, 0, relaunch, PendingIntent.FLAG_IMMUTABLE)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 400, pi)
        Thread.sleep(500)
        Runtime.getRuntime().exit(0)
        @Suppress("UNREACHABLE_CODE")
        return count
    }
}
