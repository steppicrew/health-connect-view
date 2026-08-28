# Health Connect record classes are looked up reflectively by KClass, so R8 must not
# rename or strip them or reads fail at runtime with no compile-time warning.
-keep class androidx.health.connect.client.records.** { *; }
-keep class androidx.health.connect.client.units.** { *; }
-keep class androidx.health.connect.client.aggregate.** { *; }

# The registry holds KClass references to those record types.
-keepclassmembers class de.steppicrew.healthconnectview.registry.** { *; }

# Kotlin metadata is needed for KClass reflection to resolve.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Protobuf-backed IPC types used by the Health Connect client.
-dontwarn com.google.protobuf.**
-keep class androidx.health.platform.client.** { *; }

# Play Billing references Google's datatransport/Firebase telemetry uploader, which this
# build deliberately excludes (see the exclude block in app/build.gradle.kts): the app has
# no INTERNET permission, so a telemetry pipeline could never transmit anyway. The billing
# code paths that touch it are unreachable, so R8 only needs to be told not to warn.
-dontwarn com.google.android.datatransport.**
-dontwarn com.google.firebase.**
