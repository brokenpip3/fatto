# Tink / Google annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# JNA / UniFFI
-dontwarn java.awt.**
-keep class com.sun.jna.** { *; }
-keep class uniffi.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }

# Keep UniFFI generated bindings
-keep class com.brokenpip3.fatto.** { *; }
-keep interface com.brokenpip3.fatto.** { *; }
-keep class uniffi.taskchampion_android.** { *; }
-keep interface uniffi.taskchampion_android.** { *; }

# Keep native library loading
-keepclassmembers class * {
    native <methods>;
}
