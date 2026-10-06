# Compose + kotlinx.serialization 都带足信息，R8 未开启（isMinifyEnabled = false）。
# 如果以后开启混淆，保留序列化生成的 serializer：
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.panelone.client.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
