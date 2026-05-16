# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in Android SDK tools/proguard/proguard-android-optimize.txt

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.lanrhyme.clipypse.**$$serializer { *; }
-keepclassmembers class com.lanrhyme.clipypse.** {
    *** Companion;
}
-keepclasseswithmembers class com.lanrhyme.clipypse.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep protobuf classes
-keep class kotlinx.serialization.protobuf.** { *; }
-keep class com.lanrhyme.clipypse.Protocol** { *; }

# Keep network classes
-keep class com.lanrhyme.clipypse.network.** { *; }

# Keep clipboard engine
-keep class com.lanrhyme.clipypse.ClipboardEngine { *; }
-keep class com.lanrhyme.clipypse.ClipboardItem { *; }
-keep class com.lanrhyme.clipypse.ClipboardType { *; }

# General optimization
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

-keep public class * extends android.app.Activity
-keep public class * extends androidx.lifecycle.ViewModel
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}
