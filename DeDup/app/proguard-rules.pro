# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- Aggressive Obfuscation & Optimization ---
# Flatten the package hierarchy by moving all obfuscated classes into the root package
-repackageclasses ''
# Allow R8 to change the access modifiers of classes and members (e.g. make public classes private if possible)
-allowaccessmodification
# Overload method names with the same name if signatures differ
-overloadaggressively

# Remove debug and verbose log calls in release builds to prevent leaking info
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# --- General Kotlin & Compose ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.jetbrains.annotations.**

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- SQLCipher ---
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- ML Kit ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Project Specific Models ---
# Keep your data classes to prevent issues with JSON serialization or Room
-keep class com.rp.dedup.core.data.** { *; }

# --- Google Identity & Credential Manager ---
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Preserve line numbers for better crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile