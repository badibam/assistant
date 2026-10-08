plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.treelune"
    compileSdk = 37
    // Pinned rather than left to the AGP default: the F-Droid build server must resolve the
    // same toolchain, or the APKs cannot be compared.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "app.treelune"
        minSdk = 26
        targetSdk = 34
        versionCode = 27
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    // Release signing. The keystore and its passwords live outside the repository and
    // reach the build through the environment: a gitignored file at the root would still
    // be swept away by `git clean -xdf`, and this key cannot be regenerated -- it is the
    // app's identity to every phone that installed it.
    //
    // No config at all when the environment is silent, rather than a placeholder password:
    // a release signed with the wrong key installs nowhere and says nothing about why.
    signingConfigs {
        val store = System.getenv("TREELUNE_KEYSTORE")
        if (store != null) {
            create("release") {
                storeFile = file(store)
                storePassword = System.getenv("TREELUNE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TREELUNE_KEY_ALIAS")
                keyPassword = System.getenv("TREELUNE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            // Off: every resource is resolved by name at runtime (strings through
            // StringsManager's getIdentifier, icons and sounds the same way), references
            // the shrinker cannot see -- it strips them all, and the release shows raw keys.
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")

            // Reproducible release: PNG crunching varies from machine to machine, and VCS info
            // would embed the state of the working tree.
            isCrunchPngs = false
            vcsInfo.include = false
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // SoundPool opens a sound through a file descriptor into the APK, which a compressed entry
    // cannot give: the themes' FLAC sounds are stored as they are (FLAC is compressed already)
    androidResources {
        noCompress += "flac"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
    }
}

// Name the release APK after the version: the release command attaches it by that name
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            (output as com.android.build.api.variant.impl.VariantOutputImpl)
                .outputFileName.set("treelune-v${output.versionName.get()}.apk")
        }
    }
}

// Strings Resource Generation Task
//
// A source carries its locale in its file name: `shared.xml` and `strings.xml` hold the
// default (English, what Android falls back to when the phone's language has no translation),
// `shared-fr.xml` and `strings-fr.xml` hold the French one. One output per locale -- `values/`
// for the default, `values-<locale>/` for the rest -- so the runtime needs no locale code of
// its own: StringsManager looks a key up through context.resources, which already picks the
// file matching the phone.
//
// A source with no translation lands in the default output alone, where every locale reaches
// it by fallback. That is what ai_prompt_chunks.xml wants: those go to the AI, not on screen.
tasks.register("generateStringResources") {
    description = "Generate string resources from tool, theme and shared XML files, one output per locale"
    group = "build"
    
    val toolsDir = file("src/main/java/app/treelune/tools")
    val themesDir = file("src/main/java/app/treelune/themes")
    val sharedStringsDir = file("src/main/java/app/treelune/core/strings/sources") // Sources strings shared
    val resDir = file("src/main/res")
    
    // Gradle cache: run if any source changed
    inputs.dir(toolsDir)
    inputs.dir(themesDir)
    if (sharedStringsDir.exists()) inputs.dir(sharedStringsDir)
    outputs.files(stringSourcesByLocale(toolsDir, themesDir, sharedStringsDir).keys.map { localeOutputFile(resDir, it) })
    
    doLast {
        println("Generating string resources...")
        
        stringSourcesByLocale(toolsDir, themesDir, sharedStringsDir).forEach { (locale, sources) ->
            val aggregatedStrings = StringBuilder()
            aggregatedStrings.append("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Auto-generated strings from tools - DO NOT EDIT MANUALLY -->
""")
            
            sources.forEach { (sourceFile, prefix) ->
                println("Processing ${if (locale.isEmpty()) "default" else locale} strings: ${sourceFile.name} -> $prefix")
                processStrings(sourceFile, prefix, aggregatedStrings)
            }
            
            aggregatedStrings.append("</resources>")
            
            val outputFile = localeOutputFile(resDir, locale)
            outputFile.parentFile.mkdirs()
            outputFile.writeText(aggregatedStrings.toString())
            println("Generated: ${outputFile.parentFile.name}/${outputFile.name}")
        }
    }
}

/**
 * Where a locale's generated file goes: values/ for the default, values-<locale>/ for the rest.
 */
fun localeOutputFile(resDir: File, locale: String): File =
    File(resDir, if (locale.isEmpty()) "values/strings_generated.xml" else "values-$locale/strings_generated.xml")

/**
 * Group the string sources by locale, in the order they must be aggregated.
 *
 * The locale is the `-xx` (or `-xx-rYY`) suffix of the file's base name, absent for the default,
 * which the map keys as "". Each source is paired with the namespace its keys take: the tool's
 * own name for a tool source, "theme_" and its name for a theme's, "shared" for everything under
 * the shared sources directory.
 */
fun stringSourcesByLocale(toolsDir: File, themesDir: File, sharedDir: File): Map<String, List<Pair<File, String>>> {
    val localePattern = Regex("""^.+?(?:-([a-zA-Z]{2}(?:-r[A-Z]{2})?))?\.xml$""")
    val byLocale = sortedMapOf<String, MutableList<Pair<File, String>>>()
    
    fun collect(sourceFile: File, prefix: String) {
        val match = localePattern.matchEntire(sourceFile.name) ?: return
        byLocale.getOrPut(match.groupValues[1]) { mutableListOf() }.add(sourceFile to prefix)
    }
    
    if (toolsDir.exists()) {
        toolsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { toolDir ->
            toolDir.listFiles()
                ?.filter { it.name.startsWith("strings") && it.extension == "xml" }
                ?.sortedBy { it.name }
                ?.forEach { collect(it, toolDir.name) }
        }
    }
    
    if (themesDir.exists()) {
        themesDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { themeDir ->
            themeDir.listFiles()
                ?.filter { it.name.startsWith("strings") && it.extension == "xml" }
                ?.sortedBy { it.name }
                ?.forEach { collect(it, "theme_${themeDir.name}") }
        }
    }

    if (sharedDir.exists()) {
        sharedDir.listFiles()
            ?.filter { it.extension == "xml" }
            ?.sortedBy { it.name }
            ?.forEach { collect(it, "shared") }
    }
    
    return byLocale
}

/**
 * Process strings.xml file and add prefixed entries to output
 */
fun processStrings(stringsFile: File, prefix: String, output: StringBuilder) {
    try {
        val xmlContent = stringsFile.readText()
        
        // Extracts <string name="key">value</string>, across several lines and through escaped characters
        val stringPattern = """<string\s+name="([^"]+)"[^>]*>(.*?)</string>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        output.appendLine("<!-- $prefix -->")
        
        stringPattern.findAll(xmlContent).forEach { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2].trim()
            val prefixedKey = "${prefix}_${key}"
            
            // Clean and validate the string content
            val cleanedValue = cleanAndEscapeXmlString(rawValue)
            
            output.appendLine("""    <string name="$prefixedKey">$cleanedValue</string>""")
        }
        
        output.appendLine()
        
    } catch (e: Exception) {
        println("Error processing $stringsFile: ${e.message}")
    }
}

/**
 * Clean and escape an XML string for Android
 * Handles apostrophes, quotes and placeholders
 * PRESERVE line breaks in CDATA sections (AI prompts need markdown formatting)
 */
fun cleanAndEscapeXmlString(value: String): String {
    // Check if this is a CDATA section (AI prompts)
    val isCDATA = value.startsWith("<![CDATA[") && value.endsWith("]]>")

    var result = if (isCDATA) {
        // For CDATA: preserve line breaks by replacing with placeholder
        // Android resources collapse whitespace even in CDATA, so we use a placeholder
        value.trim().replace("\n", "###NEWLINE###")
    } else {
        // For normal strings: collapse whitespace as before
        value.replace(Regex("\\s+"), " ").trim()
    }

    // 2. Apostrophes, without double-escaping
    if (!result.contains("\\'")) {
        // Escape apostrophes only when they are not escaped already
        result = result.replace("'", "\\'")
    }

    // 3. Quotes, without double-escaping
    if (!result.contains("\\\"")) {
        // Escape quotes only when they are not escaped already
        result = result.replace("\"", "\\\"")
    }

    // 4. Convert placeholders to the Android format, unless they already are
    if (!result.contains("%1\$")) {
        result = result.replace("%s", "%1\$s")
                       .replace("%d", "%1\$d")
    }

    // 5. Final pass: strip the invisible control characters that break Unicode decoding
    // PRESERVE newlines (\n) and tabs (\t) for CDATA
    if (isCDATA) {
        result = result.replace(Regex("[\\u0000-\\u0008\\u000B-\\u000C\\u000E-\\u001F\\u007F-\\u009F]"), "")
    } else {
        result = result.replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
    }

    return result
}

// Strings are rebuilt on every build. Icons are not: scripts/generate_icons.py writes them
// from third_party/lucide, and its output is versioned, so the build needs neither the script
// nor its inputs.
tasks.named("preBuild") {
    dependsOn("generateStringResources")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Room database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator:1.0.87")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // HTTP client for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON serialization for AI provider communication
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // WorkManager for automation scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM: the android.jar stub throws on every call, which would
    // leave the key-rename used by the database migration untestable.
    testImplementation("org.json:json:20240303")
    // A real SQLite with its JSON functions, to run the entry filters' SQL as the phone would
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
    // The bench (docs/design/local-models.md): the app plays a scenario on the emulator, run by ./run bench
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
// Some tests read files rather than classes -- the icon index and the generated drawables, the
// L1 prompt whose examples they check -- so gradle has to know about them, or a regenerated icon
// set or an edited prompt leaves the tests "up to date".
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/icons")
    inputs.dir("src/main/assets/demo")
    inputs.dir("src/main/res/drawable")
    inputs.file("src/main/java/app/treelune/core/strings/sources/ai_prompt_chunks.xml")
}
