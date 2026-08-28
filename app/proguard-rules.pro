# =========================
# General Android rules
# =========================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions,InnerClasses

-keep class android.** { *; }
-keep class androidx.** { *; }
-keep class com.google.android.** { *; }

# =========================
# Kotlin
# =========================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-keepclassmembers class * {
    @kotlin.jvm.JvmField <fields>;
}
-keepclassmembers class * extends kotlin.Enum {
    **[] $VALUES;
    public *;
}

# =========================
# Kotlin Serialization
# =========================
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class <1>$$serializer { *; }
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class <1> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keep,allowoptimization,allowobfuscation,allowshrinking class <1>

# =========================
# Room
# =========================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# =========================
# Hilt
# =========================
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$SupportViewFragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ViewWithFragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keep class * extends dagger.hilt.lifecycle.ViewModelInject { *; }

# Keep Hilt generated components
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep class dagger.hilt.android.internal.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.AndroidEntryPoint { *; }

# =========================
# Compose
# =========================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# =========================
# OkHttp / Retrofit
# =========================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# =========================
# Coil
# =========================
-dontwarn coil.**
-keep class coil.** { *; }
-keep class coil.decode.** { *; }
-keep class coil.fetch.** { *; }
-keep class coil.request.** { *; }
-keep class coil.util.** { *; }

# =========================
# Coroutines
# =========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# =========================
# DataStore
# =========================
-dontwarn androidx.datastore.**
-keep class androidx.datastore.** { *; }

# =========================
# Enums
# =========================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# =========================
# Parcelable
# =========================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# =========================
# R class
# =========================
-keepclassmembers class **.R$* {
    public static <fields>;
}

# =========================
# Keep Application class
# =========================
-keep class com.mindforge.app.** { *; }
