# Shizuku / AIDL — keep the user-service entry point and AIDL stubs.
-keep class com.unlock.shizuku.** { *; }
-keep interface com.unlock.shizuku.IUserService { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Hidden-API reflection targets used via Shizuku must not be obfuscated away.
-keepnames class android.content.pm.IPackageManager
-keepnames class android.app.IActivityManager

# Kotlin metadata / coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
