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
# ---- NavCert Offline release rules ----
# Native ML and vision runtimes call back into Java/Kotlin classes through JNI by name.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class ai.onnxruntime.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.opencv.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.canhub.cropper.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.auto.value.**
-dontwarn org.opencv.**
