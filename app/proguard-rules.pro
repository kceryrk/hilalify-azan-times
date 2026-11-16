# Keep app components referenced by the manifest (activities, services, receivers, widgets, workers).
-keep class com.kceryrk.prayertimes.** { *; }

# Keep Play Core app update classes used via reflection.
-keep class com.google.android.play.core.** { *; }

# Keep WorkManager and RoomDatabase *_Impl classes instantiated via reflection.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep class *_Impl { *; }
-keep class androidx.work.** { *; }
-keep class androidx.room.** { *; }
