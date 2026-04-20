# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities
-keep class com.chrisirlam.snorenudge.data.** { *; }

# Keep DataStore generated classes
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }
