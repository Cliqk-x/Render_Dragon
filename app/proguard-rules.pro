# Aniyomi extensions must NOT be obfuscated.
# The app loads extension classes by name via reflection.
-dontobfuscate
-keep class eu.kanade.tachiyomi.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, Signature, Exception
