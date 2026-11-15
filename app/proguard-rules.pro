# CHARGEVERSE ProGuard/R8 rules

# Keep crash-relevant line numbers for release builds
-keepattributes SourceFile,LineNumberTable

# Room generated code
-keep class * extends androidx.room.RoomDatabase { <init>(); }
