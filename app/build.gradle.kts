import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    signingConfigs {
        create("release") {
            // Configuration de signature
            // IMPORTANT: Ne jamais commiter les vraies clés !
            
            // Tenter de charger depuis variables d'environnement ou .env
            val keystoreFile = file("../keystore/assistant-release.keystore")
            val envFile = file("../keystore/.env")
            
            var keystorePass = System.getenv("KEYSTORE_PASSWORD")
            var keyPass = System.getenv("KEY_PASSWORD")
            
            // Si pas de variables d'environnement, essayer de lire .env
            if ((keystorePass == null || keyPass == null) && envFile.exists()) {
                val envProps = Properties()
                envFile.reader().use { envProps.load(it) }
                keystorePass = keystorePass ?: envProps.getProperty("KEYSTORE_PASSWORD")
                keyPass = keyPass ?: envProps.getProperty("KEY_PASSWORD")
            }
            
            storeFile = keystoreFile
            storePassword = keystorePass ?: "changeme"
            keyAlias = "assistant-release"
            keyPassword = keyPass ?: "changeme"
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
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Renommage APK avec version
            applicationVariants.all {
                val variant = this
                variant.outputs
                    .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
                    .forEach { output ->
                        output.outputFileName = "assistant-v${variant.versionName}.apk"
                    }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-opt-in=kotlin.ExperimentalUnsignedTypes"
        freeCompilerArgs += "-XXLanguage:+UnitConversionsOnArbitraryExpressions"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Theme Resource Generation Task
tasks.register("generateThemeResources") {
    description = "Generate drawable resources from theme SVG icons"
    group = "build"
    
    val themesDir = file("src/main/java/com/assistant/themes")
    val outputDir = file("src/main/res/drawable")
    val iconsListFile = file("src/main/assets/standard_icons.txt")
    
    // Gradle cache: run if themes OR icons list changed
    inputs.dir(themesDir)
    inputs.file(iconsListFile)  // ← AJOUT: surveille le fichier liste
    outputs.dir(outputDir)
    
    doFirst {
        // Phase 1: Validation avec warnings - vérifier cohérence globale
        println("🔍 Validating theme consistency...")
        
        if (!themesDir.exists()) {
            throw GradleException("Themes directory not found: ${themesDir.absolutePath}")
        }
        
        // Vérifier que le thème default existe (requis comme fallback)
        val defaultThemeDir = File(themesDir, "default")
        if (!defaultThemeDir.exists() || !File(defaultThemeDir, "icons").exists()) {
            throw GradleException("❌ Default theme directory not found: ${defaultThemeDir.absolutePath}\n" +
                "   Default theme is required as fallback for other themes")
        }
        
        themesDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
            val themeName = themeDir.name
            val iconsDir = File(themeDir, "icons")
            
            if (iconsDir.exists() && iconsDir.isDirectory) {
                // Scanner SVG disponibles dans ce thème
                val availableSvgs = iconsDir.listFiles { _, name -> 
                    name.endsWith(".svg", ignoreCase = true)
                }?.map { 
                    it.nameWithoutExtension 
                }?.toSet() ?: emptySet()
                
                val standardIcons = getStandardIcons()
                val missingSvgs = standardIcons - availableSvgs
                val orphanSvgs = availableSvgs - standardIcons
                
                if (missingSvgs.isNotEmpty() && themeName == "default") {
                    println("⚠️  Default theme missing SVG files: $missingSvgs")
                    println("   → Will generate placeholders for missing icons")
                } else if (missingSvgs.isNotEmpty()) {
                    println("ℹ️  Theme '$themeName' missing SVG files: $missingSvgs")
                    println("   → Will fallback to default theme")
                }
                
                if (orphanSvgs.isNotEmpty()) {
                    println("⚠️  Theme '$themeName': SVG files not in IconConfig.STANDARD_ICONS: $orphanSvgs")
                    println("   → Consider adding these to IconConfig.STANDARD_ICONS")
                }
                
                println("✅ Theme '$themeName': Ready (${availableSvgs.size}/${standardIcons.size} icons)")
            }
        }
    }
    
    doLast {
        println("🎨 Generating theme resources...")
        
        outputDir.mkdirs()
        
        themesDir.listFiles()?.filter { it.isDirectory }?.forEach { themeDir ->
            val themeName = themeDir.name
            val iconsDir = File(themeDir, "icons")
            
            if (iconsDir.exists() && iconsDir.isDirectory) {
                println("📁 Processing theme: $themeName")
                
                // Générer ressources pour toutes les icônes standard
                getStandardIcons().forEach { iconId ->
                    val svgFile = File(iconsDir, "$iconId.svg")
                    val outputFileName = "${themeName}_${iconId.replace("-", "_")}.xml"
                    val outputFile = File(outputDir, outputFileName)
                    
                    when {
                        // 1. SVG exists in current theme → use it
                        svgFile.exists() -> {
                            convertSvgToVectorDrawable(svgFile, outputFile, iconId)
                            println("✅ Generated from $themeName: $outputFileName")
                        }
                        
                        // 2. SVG missing but theme is default → placeholder
                        themeName == "default" -> {
                            generatePlaceholderVector(outputFile, iconId)
                            println("🔄 Generated placeholder for default: $outputFileName")
                        }
                        
                        // 3. SVG missing in other theme → fallback to default
                        else -> {
                            val defaultSvgFile = File(themesDir, "default/icons/$iconId.svg")
                            if (defaultSvgFile.exists()) {
                                convertSvgToVectorDrawable(defaultSvgFile, outputFile, iconId)
                                println("🔄 Fallback from default: $outputFileName")
                            } else {
                                generatePlaceholderVector(outputFile, iconId)
                                println("⚠️  Missing in both $themeName and default: $outputFileName")
                            }
                        }
                    }
                }
                
                // Phase 2: Nettoyage automatique - supprimer resources orphelines
                println("🧹 Cleaning orphan resources for theme: $themeName")
                
                val expectedFiles = getStandardIcons().map { iconId ->
                    "${themeName}_${iconId.replace("-", "_")}.xml"
                }.toSet()
                
                val existingFiles = outputDir.listFiles { _, name ->
                    name.startsWith("${themeName}_") && name.endsWith(".xml")
                }?.map { it.name }?.toSet() ?: emptySet()
                
                val orphanFiles = existingFiles - expectedFiles
                
                orphanFiles.forEach { orphanFile ->
                    val fileToDelete = File(outputDir, orphanFile)
                    if (fileToDelete.delete()) {
                        println("🗑️  Removed orphan resource: $orphanFile")
                    }
                }
                
                if (orphanFiles.isEmpty()) {
                    println("✨ No orphan resources found for theme: $themeName")
                }
            }
        }
        
        // Phase 3: Generate Kotlin code with direct R.drawable references
        println("📝 Generating Kotlin theme resources...")
        generateKotlinThemeResources()
        
        println("🎉 Theme resource generation complete!")
    }
}

// Auto-run before build
tasks.named("preBuild") {
    dependsOn("generateThemeResources")
}

/**
 * Convert SVG to Android Vector Drawable XML
 * Simple conversion - for complex SVGs, consider using external tools
 */
fun convertSvgToVectorDrawable(svgFile: File, outputFile: File, iconName: String) {
    try {
        val svgContent = svgFile.readText()
        
        // Extract basic SVG attributes (simplified parser)
        val widthRegex = """width=["']([^"']+)["']""".toRegex()
        val heightRegex = """height=["']([^"']+)["']""".toRegex()
        val viewBoxRegex = """viewBox=["']([^"']+)["']""".toRegex()
        val pathRegex = """<path[^>]*d=["']([^"']+)["'][^>]*/>""".toRegex()
        
        val width = widthRegex.find(svgContent)?.groupValues?.get(1) ?: "24dp"
        val height = heightRegex.find(svgContent)?.groupValues?.get(1) ?: "24dp"
        val viewBox = viewBoxRegex.find(svgContent)?.groupValues?.get(1) ?: "0 0 24 24"
        
        // Extract paths
        val paths = pathRegex.findAll(svgContent).map { 
            it.groupValues[1] 
        }.toList()
        
        if (paths.isEmpty()) {
            println("⚠️  Warning: No paths found in $svgFile")
            // Create placeholder
            generatePlaceholderVector(outputFile, iconName)
            return
        }
        
        // Generate Android Vector Drawable XML
        val vectorXml = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    android:width="24dp"""")
            appendLine("""    android:height="24dp"""")
            appendLine("""    android:viewportWidth="24"""")
            appendLine("""    android:viewportHeight="24">""")
            
            paths.forEach { pathData ->
                appendLine("""    <path""")
                appendLine("""        android:pathData="$pathData"""")
                appendLine("""        android:fillColor="#FF333333" />""")
            }
            
            appendLine("""</vector>""")
        }
        
        outputFile.writeText(vectorXml)
        
    } catch (e: Exception) {
        println("❌ Error converting $svgFile: ${e.message}")
        generatePlaceholderVector(outputFile, iconName)
    }
}

/**
 * Generate placeholder vector drawable when SVG conversion fails
 */
fun generatePlaceholderVector(outputFile: File, iconName: String) {
    val placeholderXml = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <!-- Placeholder for $iconName -->
    <path
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z"
        android:fillColor="#FF333333" />
</vector>
"""
    outputFile.writeText(placeholderXml)
}

/**
 * Get STANDARD_ICONS list from shared assets file
 * Single source of truth for both Gradle task and runtime
 */
fun getStandardIcons(): Set<String> {
    val iconsFile = file("src/main/assets/standard_icons.txt")
    if (!iconsFile.exists()) {
        throw GradleException("❌ Missing standard_icons.txt file in src/main/assets/")
    }
    
    return iconsFile.readText()
        .trim()
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .toSet()
}

/**
 * Generate Kotlin code with direct R.drawable references
 * Eliminates need for getIdentifier() by using compiled resource IDs
 */
fun generateKotlinThemeResources() {
    val standardIcons = getStandardIcons()
    val outputFile = file("src/main/java/com/assistant/core/ui/GeneratedThemeResources.kt")
    
    val kotlinCode = buildString {
        appendLine("package com.assistant.core.ui")
        appendLine("")
        appendLine("import com.assistant.R")
        appendLine("")
        appendLine("/**")
        appendLine(" * Generated theme resources with direct R.drawable references")
        appendLine(" * Auto-generated from standard_icons.txt - DO NOT EDIT MANUALLY")
        appendLine(" */")
        appendLine("object GeneratedThemeResources {")
        appendLine("    ")
        appendLine("    /**")
        appendLine("     * Get default theme icons with direct resource references")
        appendLine("     */")
        appendLine("    fun getDefaultThemeIcons(): List<AvailableIcon> {")
        appendLine("        return listOf(")
        
        standardIcons.forEach { iconId ->
            val resourceName = "default_${iconId.replace("-", "_")}"
            val displayName = iconId.split("-").joinToString(" ") { 
                it.replaceFirstChar { char -> char.uppercase() } 
            }
            appendLine("            AvailableIcon(")
            appendLine("                id = \"$iconId\",")
            appendLine("                displayName = \"$displayName\",")
            appendLine("                resourceId = R.drawable.$resourceName")
            appendLine("            ),")
        }
        
        appendLine("        )")
        appendLine("    }")
        appendLine("    ")
        appendLine("    // TODO: Add other themes here (glass, etc.) when implemented")
        appendLine("}")
    }
    
    outputFile.writeText(kotlinCode)
    println("✅ Generated: ${outputFile.name}")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}