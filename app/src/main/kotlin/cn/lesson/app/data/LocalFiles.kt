package cn.lesson.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import cn.lesson.core.*
import java.io.*
import java.util.UUID
import java.util.zip.*

object LocalFiles {
    fun name(context: Context, uri: Uri): String = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "课表文件"
    fun read(context: Context, uri: Uri, limit: Int = 10*1024*1024): ByteArray =
        requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开文件" }.use { readLimited(it,limit) }
    private fun readLimited(stream: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val size = stream.read(buffer); if (size < 0) break
            require(output.size()+size <= limit) { "文件内容过大" }; output.write(buffer,0,size)
        }
        return output.toByteArray()
    }
    fun saveBackground(context: Context, uri: Uri): String = storeImage(context,read(context,uri,25*1024*1024))
    private fun storeImage(context: Context, bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "这不是可读取的图片" }
        var sample = 1
        while (maxOf(bounds.outWidth,bounds.outHeight)/sample > 1800) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }))
        val orientation=runCatching {ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix().apply {
            when(orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {postRotate(90f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {postRotate(270f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        val bitmap=if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also {if(it!==decoded) decoded.recycle()}
        val dir = File(context.filesDir,"backgrounds").apply { mkdirs() }
        val file = File(dir,"${UUID.randomUUID()}.jpg")
        try { file.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)) { "图片保存失败" } } }
        finally { bitmap.recycle() }
        return file.absolutePath
    }
    fun exportBackup(context: Context, state: AppState, uri: Uri) {
        val portable = state.copy(preferences = state.preferences.copy(backgrounds=listOf("","","")))
        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法写入备份" }.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("state.json")); zip.write(BackupCodec.encode(portable).toByteArray()); zip.closeEntry()
                state.preferences.backgrounds.forEachIndexed { index,path ->
                    if (path.isNotBlank() && File(path).isFile) {
                        zip.putNextEntry(ZipEntry("backgrounds/$index.jpg")); File(path).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
        }
    }
    fun readBackup(context: Context, uri: Uri): AppState {
        val bytes = read(context,uri,30*1024*1024)
        val entries = mutableMapOf<String,ByteArray>(); var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entry.name in listOf("state.json","backgrounds/0.jpg","backgrounds/1.jpg","backgrounds/2.jpg") && !entries.containsKey(entry.name)) { "备份包含未知或重复的内容" }
                val content = readLimited(zip,25*1024*1024-total); total += content.size
                entries[entry.name] = content; zip.closeEntry()
            }
        }
        val state = BackupCodec.decode(requireNotNull(entries["state.json"]) { "备份中缺少数据" }.toString(Charsets.UTF_8))
        val images = (0..2).map { index -> entries["backgrounds/$index.jpg"]?.let { storeImage(context,it) }.orEmpty() }
        return state.copy(preferences = state.preferences.copy(backgrounds=images))
    }
}
