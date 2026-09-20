# SignalR's Gson protocol reflects over these REST DTOs at runtime.
-keepattributes Signature
-keep class com.tryniecki.kajutabot.api.model.** { *; }

# SignalR's Gson protocol also reflects over its own handshake and hub message
# classes. Keep their constructors and field names for release deserialization.
-keep class com.microsoft.signalr.** { *; }

# SLF4J 1.7 detects an optional binding reflectively and falls back to no-op.
-dontwarn org.slf4j.impl.StaticLoggerBinder
