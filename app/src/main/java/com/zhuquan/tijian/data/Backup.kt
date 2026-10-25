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
        // 持有写事务期间其他线程无法提交写入，checkpoint 之后复制主库文件才是一致快照
        val sql = db.writableDatabase
        sql.beginTransactionNonExclusive()
        val tmpDb = File(ctx.cacheDir, "backup_export.db")
        try {
            sql.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            val dbFile = ctx.getDatabasePath(DB)
            if (dbFile.exists()) dbFile.copyTo(tmpDb, overwrite = true)
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        try {
            if (tmpDb.exists()) {
                SQLiteDatabase.openDatabase(tmpDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { t ->
                    t.execSQL("DELETE FROM setting WHERE k='ai.apiKey'")
                }
            }
            var count = 0
            ZipOutputStream(out).use { zip ->
                if (tmpDb.exists()) {
                    zip.putNextEntry(ZipEntry("db/$DB"))
                    tmpDb.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry(); count++
                }
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

        db.close()
        val dbFile = ctx.getDatabasePath(DB)
        dbFile.parentFile?.mkdirs()
        dbBackup.copyTo(dbFile, overwrite = true)
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()

        val images = File(ctx.filesDir, "images").apply { mkdirs() }
        tmp.listFiles()?.forEach { f ->
            if (f.name.startsWith("images_")) {
                f.copyTo(File(images, f.name.removePrefix("images_")), overwrite = true)
            }
        }
        tmp.deleteRecursively()

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
