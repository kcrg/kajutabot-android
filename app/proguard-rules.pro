# SignalR's Gson protocol reflects over these REST DTOs at runtime.
# Keep constructors and field names used by reflection, but allow R8 to optimize
# and remove unused ordinary methods instead of pinning every member.
-keepattributes Signature
-keep,allowoptimization class com.tryniecki.kajutabot.api.model.** {
    <init>(...);
    <fields>;
}

# SignalR's Gson protocol also reflects over its own handshake and hub message
# classes. Preserve constructors/fields while still allowing method optimization.
-keep,allowoptimization class com.microsoft.signalr.** {
    <init>(...);
    <fields>;
}

# SLF4J 1.7 detects an optional binding reflectively and falls back to no-op.
-dontwarn org.slf4j.impl.StaticLoggerBinder
