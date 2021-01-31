import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private object AutoApk2Jar

suspend fun main(args: Array<String>) {
    runCatching {
        impl(args)
    }.exceptionOrNull()?.printStackTrace()

    println("Type anything to exit.")
    readLine()
}

private val isWindows by lazy {
    System.getProperty("os.name").contains("windows", ignoreCase = true)
}

private const val DEX2JAR_THREAD_COUNT = 5

suspend fun impl(args: Array<String>) {
    val apkFile = args.getOrElse(0) {
        println("path to the apk?")
        readLine() ?: error("Please provide apk path")
    }

    val defaultOutputDir = File(".").absoluteFile.parentFile.resolve("output")
    val output = args.getOrElse(1) {
        println("output dir? default: ${defaultOutputDir.absolutePath}")
        readLine()
    }.let { dir ->
        (if (dir.isNullOrBlank()) defaultOutputDir else File(dir)).apply { mkdirs() }
    }

    println("Output dir: ${output.absolutePath}")

    prepareDex2Jar(output)

    val apkUnzip = output.resolve("apkUnzip")
    println("Unzipping apk to $apkUnzip")
    runInterruptible { unzip(apkFile, apkUnzip.absolutePath, entryFilter = { it.substringAfterLast(".") == "dex" }) }

    println("Starting dex2jar, thread count=$DEX2JAR_THREAD_COUNT, this may occupy 100% CPU")
    val jars = output.resolve("jars")

    dex2Jar(
        output.resolve(
            if (isWindows) "dex2jar/d2j-dex2jar.bat"
            else "dex2jar/d2j-dex2jar"
        ),
        apkUnzip,
        jars
    )

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

    println("Preparing project template")

    val projectTemplateZip = output.resolve("ProjectTemplate.zip")
    projectTemplateZip.outputStream().use { out ->
        AutoApk2Jar::class.java.getResourceAsStream("ProjectTemplate.zip").use { it.copyTo(out) }
    }

    val project = output.resolve(File(apkFile).nameWithoutExtension)
    project.mkdir()
    unzip(projectTemplateZip.absolutePath, project.absolutePath)


    val decompiled = project.resolve("app/src/main/java").apply { mkdirs() }
    println("Decompiled classes output: ${decompiled.absolutePath}")
    println("Starting FernFlower. This may take a long time, please wait until 'Decompile finished.'")

    runInterruptible {
        ProcessBuilder().command("""
        "java" -jar "${fernFlower.absolutePath}" 
        -dgs=1 
        -log=ERROR 
        -lit=1 
        -mpm=1800 
        "${classes.absolutePath}" 
        "${decompiled.absolutePath}"
    """.trimIndent().replace('\n', ' ')).inheritIO().start().waitFor()
    }

    println("Decompile finished. ")
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
    val semaphore = Semaphore(DEX2JAR_THREAD_COUNT)
    val total = input.walk().filter { it.extension == "dex" }.toList()
    val finished = AtomicInteger(0)
    total.forEach { file ->
        val cmd = """
            "${dex2jar.absolutePath}"
             --force
             -o "${output.resolve(file.relativeTo(input)).apply { parentFile.mkdirs() }.run { resolveSibling("$nameWithoutExtension.jar") }.absolutePath}"
             "${file.absolutePath}"
             """.trimIndent().replace('\n', ' ')

        launch {
            semaphore.withPermit {
                runInterruptible { ProcessBuilder().command(cmd).start().waitFor() }
                println("${file.name} done. ${finished.incrementAndGet().toDouble().div(total.size).times(100).toInt()}%")
            }
        }
    }
}