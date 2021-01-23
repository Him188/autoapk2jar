import java.io.*
import java.util.*
import java.util.zip.*


private val REGEX_X = Regex("x")
private val RANDOM_CHAR_CANDIDATES = arrayOf("a", "b", "c", "d", "e", "f", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9")


fun File.copyToFiltered(target: File, overwrite: Boolean = false, bufferSize: Int = DEFAULT_BUFFER_SIZE, filter: (File) -> Boolean) {
    walk()
        .filter { it.isFile }
        .filter(filter)
        .forEach { file ->
            file.copyTo(target.resolve(file.relativeTo(this)), overwrite, bufferSize)
        }
}

fun createVisitorId(): String =
    "xxxxxxxx-xxxx-4xxx-xxxx-xxxxxxxxxxxx".replace(REGEX_X) { RANDOM_CHAR_CANDIDATES.random() }

@Throws(IOException::class)
fun zip(srcPath: String, dstPath: String, entryNameMapper: (String) -> String = { it }) {
    val srcFile = File(srcPath)
    val dstFile = File(dstPath)
    if (!srcFile.exists()) {
        throw FileNotFoundException(srcPath + "不存在！")
    }
    val out = FileOutputStream(dstFile)
    val zipOut = ZipOutputStream(CheckedOutputStream(out, CRC32()))
    try {
        fun String.mappedName(): String {
            val name = this.replace("\\*".toRegex(), "/")
            return if (name.contains('/')) {
                "${name.substringBeforeLast('/')}/${name.substringAfterLast('/').let(entryNameMapper)}"
            } else {
                name.let(entryNameMapper)
            }
        }

        srcFile.walk().filter { it.isFile }.forEach { file ->
            zipOut.putNextEntry(ZipEntry(file.toRelativeString(srcFile).mappedName()))
            file.inputStream().use { it.copyTo(zipOut) }
        }
    } finally {
        zipOut.close()
        out.close()
    }
}

fun unzip(zipFile: String, dstPath: String, entryFilter: (String) -> Boolean = {true}, entryNameMapper: (String) -> String = { it }) {
    val pathFile = File(dstPath)
    if (!pathFile.exists()) {
        pathFile.mkdirs()
    }
    val zip = ZipFile(zipFile)
    val entries: Enumeration<*> = zip.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement() as ZipEntry
        var `in`: InputStream? = null
        var out: OutputStream? = null
        try {
            `in` = zip.getInputStream(entry)

            val name = entry.name.replace("\\*".toRegex(), "/")
            if (!entryFilter(name)) continue
            val outPath = if (name.contains('/')) {
                "$dstPath/${name.substringBeforeLast('/')}/${name.substringAfterLast('/').let(entryNameMapper)}"
            } else {
                "$dstPath/${name.let(entryNameMapper)}"
            }


            //判断路径是否存在,不存在则创建文件路径
            val file = File(outPath.substring(0, outPath.lastIndexOf('/')))
            if (!file.exists()) {
                file.mkdirs()
            }
            //判断文件全路径是否为文件夹,如果是上面已经上传,不需要解压
            if (File(outPath).isDirectory) {
                continue
            }
            out = FileOutputStream(outPath)
            val buf1 = ByteArray(DEFAULT_BUFFER_SIZE)
            var len: Int
            while (`in`.read(buf1).also { len = it } > 0) {
                out.write(buf1, 0, len)
            }
        } finally {
            `in`?.close()
            out?.close()
        }
    }
    zip.close()
}