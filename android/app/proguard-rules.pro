# Retrofit/Gson model classes must keep their fields for reflection-based
# (de)serialization — without this, release builds silently send/parse
# empty JSON bodies after R8 renames fields.
-keep class id.whitesecurity.antivirus.data.remote.dto.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Room
-keep class * extends androidx.room.RoomDatabase

# androidx.security:security-crypto (Google Tink) references compile-time-only
# error-prone annotations that aren't on the runtime classpath — harmless to
# drop, but R8 fails the build over them unless told to ignore.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
