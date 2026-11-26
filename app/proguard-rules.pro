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

# Preserve Kotlin metadata & annotations for Moshi KotlinJsonAdapterFactory & Room
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# Domain & Drive DTOs serialized via Moshi reflection
-keep class com.example.domain.model.** { *; }
-keep class com.example.data.drive.** { *; }
-keep class com.example.data.local.*Entity { *; }
-keep class com.example.data.local.LocalAccountProfile { *; }
-keep class com.example.data.local.DriveLinkState { *; }
-keep class com.example.data.local.DriveFolderIds { *; }
-keep class com.example.data.local.DriveSyncStatus { *; }
-keep class com.example.data.local.ConflictAuditRecord { *; }

# Moshi reflection adapter
-keep class com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory { *; }

# Credential Manager & Play Services Auth
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.errorprone.annotations.**

