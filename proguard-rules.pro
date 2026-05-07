# ProGuard rules for a simple agenda app
# Keep Room classes.
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keep class androidx.room.RoomDatabase { *; }
-keep class com.example.agenda.** { *; }
