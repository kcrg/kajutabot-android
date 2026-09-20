# SignalR's Gson protocol reflects over these REST DTOs at runtime.
-keepattributes Signature
-keep class com.tryniecki.kajutabot.api.model.** { *; }

# SLF4J 1.7 detects an optional binding reflectively and falls back to no-op.
-dontwarn org.slf4j.impl.StaticLoggerBinder
