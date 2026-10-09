import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stockfishUrl = "https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8.tar"
val stockfishSha256 = "e2eca54b0e3189ec7de338133c2b34fa8f5cdec3d2473519b414a5cb6815e768"
val stockfishTar = layout.buildDirectory.file("stockfish/stockfish-android-armv8.tar")
fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

val downloadStockfish by tasks.registering {
    description = "Downloads the official Stockfish 18 Android arm64 build and checks its SHA-256."
    val target = stockfishTar
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        if (file.exists() && sha256(file) == stockfishSha256) return@doLast
        file.parentFile.mkdirs()
        URI(stockfishUrl).toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        val actual = sha256(file)
        check(actual == stockfishSha256) { "Stockfish download hash $actual does not match $stockfishSha256" }
    }
}

abstract class StockfishLibraryTask : DefaultTask() {
    @get:InputFile
    abstract val archive: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun extract() {
        files.sync {
            from(archives.tarTree(archive)) {
                include("stockfish/stockfish-android-armv8")
                eachFile { path = "arm64-v8a/libstockfish.so" }
            }
            includeEmptyDirs = false
            into(outputDir)
        }
    }
}

val stockfishLibrary by tasks.registering(StockfishLibraryTask::class) {
    dependsOn(downloadStockfish)
    archive.set(stockfishTar)
}

val sourceUrl = providers.gradleProperty("otb.sourceUrl").getOrElse("")
val privacyUrl = providers.gradleProperty("otb.privacyUrl").getOrElse("")

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.chessnut.otb"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.chessnut.otb"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "SOURCE_URL", "\"$sourceUrl\"")
        buildConfigField("String", "PRIVACY_URL", "\"$privacyUrl\"")
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("upload")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libstockfish.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(stockfishLibrary, StockfishLibraryTask::outputDir)
    }
}

val checkReleaseLinks by tasks.registering {
    description = "A GPL-3 release has to point users at its source code."
    doLast {
        check(sourceUrl.isNotBlank()) { "Set otb.sourceUrl in gradle.properties to the public repository before a release build." }
        check(privacyUrl.isNotBlank()) { "Set otb.privacyUrl in gradle.properties to the hosted privacy policy before a release build." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseLinks) }

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    debugImplementation("com.github.engelon:BLEForEmulator:v0.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
