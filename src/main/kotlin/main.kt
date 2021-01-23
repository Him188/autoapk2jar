import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

private object AutoApk2Jar

suspend fun main(args: Array<String>) {
    runCatching {
        impl(args)
    }.exceptionOrNull()?.printStackTrace()
    readLine()
}

suspend fun impl(args: Array<String>) {
    val apkFile = args.getOrElse(0) {
        println("path to the apk?")
        readLine() ?: error("Please provide apk path")
    }

    val output = File("../output").apply { mkdir() }
    prepareDex2Jar(output)

    val apkUnzip = output.resolve("apkUnzip")
    println("Unzipping apk to $apkUnzip")
    runInterruptible { unzip(apkFile, apkUnzip.absolutePath, entryFilter = { it.substringAfterLast(".") == "dex" }) }

    println("Starting dex2jar")
    val jars = output.resolve("jars")
    dex2Jar(output.resolve("dex2jar/d2j-dex2jar.bat"), apkUnzip, jars)

    println("Jars dumped to $jars")

    val dex2jarErrors = output.resolve("dex2jarErrors")
    output.listFiles().orEmpty().filter { it.name.endsWith("-error.zip") }.forEach { file ->
        runCatching {
            file.copyTo(dex2jarErrors.resolve(file.relativeTo(output)), overwrite = true)
            file.delete()
        }.exceptionOrNull()?.printStackTrace()
    }

    println("Starting unzipping jars")

    val classes = output.resolve("classes")
    jars.walk().filter { it.extension == "jar" }.forEach { jar ->
        unzip(jar.absolutePath, classes.absolutePath)
    }

    val fernFlower = output.resolve("fernflower.jar")
    fernFlower.outputStream().use { out ->
        AutoApk2Jar::class.java.getResourceAsStream("fernflower.jar").use { it.copyTo(out) }
    }

    val decompiled = output.resolve("decompiled").apply { mkdir() }
    println("Decompiled classes: ${decompiled.absolutePath}")
    println("Start FernFlower")

    runInterruptible {
        ProcessBuilder().command("""
        "java" -jar "${fernFlower.absolutePath}" 
        -dgs=1 
        -log=ERROR 
        -lit=1 
        -mpm=120 
        "${classes.absolutePath}" 
        "${decompiled.absolutePath}"
    """.trimIndent().replace('\n', ' ')).inheritIO().start().waitFor()
    }
}

fun prepareDex2Jar(output: File) {
    val outFile = output.resolve("dex2jar.zip")
    outFile.outputStream().use { out ->
        AutoApk2Jar::class.java.getResourceAsStream("dex2jar.zip").use { it.copyTo(out) }
    }

    outFile.resolveSibling("dex2jar").mkdir()
    unzip(outFile.absolutePath, outFile.resolveSibling("dex2jar").absolutePath)
}

suspend fun dex2Jar(dex2jar: File, input: File, output: File) = coroutineScope {
    val semaphore = Semaphore(5)
    input.walk().filter { it.extension == "dex" }.forEach { file ->
        val cmd = """
            "${dex2jar.absolutePath}"
             --force
             -o "${output.resolve(file.relativeTo(input)).apply { parentFile.mkdirs() }.run { resolveSibling("$nameWithoutExtension.jar") }.absolutePath}"
             "${file.absolutePath}"
             """.trimIndent().replace('\n', ' ')

        launch {
            semaphore.withPermit {
                runInterruptible { ProcessBuilder().command(cmd).start().waitFor() }
            }
        }
    }
}