package com.zhuquan.tijian.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份 = 数据库 + 图片，整包 zip。
 * 恢复会覆盖数据库并重启进程，属于「换机/重装」级别操作。
 */
object Backup {

    private const val DB = "tijian.db"

    fun export(ctx: Context, db: Db, out: OutputStream): Int {
        db.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        var count = 0
        ZipOutputStream(out).use { zip ->
            val dbFile = ctx.getDatabasePath(DB)
            if (dbFile.exists()) {
                zip.putNextEntry(ZipEntry("db/$DB"))
                dbFile.inputStream().use { it.copyTo(zip) }
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
        return count
    }
}
