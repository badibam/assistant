# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ================================================================
# JSON Schema Validation (NetworkNT + Jackson) - Keep reflection
# ================================================================

# Keep Jackson annotations and reflection
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Keep NetworkNT JSON Schema Validator  
-keep class com.networknt.** { *; }
-keepnames class com.networknt.** { *; }
-dontwarn com.networknt.**

# Keep constructors for Jackson deserialization
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonCreator <init>(...);
}

# Keep reflection attributes for JSON processing
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# Android JSON classes (fallback si utilisés)
-keep class org.json.** { *; }

# Gson (si utilisé pour autre chose)
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ================================================================
# Vosk Speech Recognition - Keep native code
# ================================================================

# Keep all Vosk classes (JNI bridge)
-keep class org.vosk.** { *; }
-keepclassmembers class org.vosk.** { *; }
-keepclasseswithmembers class org.vosk.** {
    native <methods>;
}
-dontwarn org.vosk.**

# Keep alphacephei packages (Vosk parent)
-keep class com.alphacephei.** { *; }
-keepclassmembers class com.alphacephei.** { *; }
-keepclasseswithmembers class com.alphacephei.** {
    native <methods>;
}
-dontwarn com.alphacephei.**

# Keep all native methods in all classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Prevent obfuscation of Vosk classes (JNI expects exact names)
-keepnames class org.vosk.** { *; }
-keepnames class com.alphacephei.** { *; }

# Keep JNA (Java Native Access) - Used by Vosk for native bridge
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn com.sun.jna.**