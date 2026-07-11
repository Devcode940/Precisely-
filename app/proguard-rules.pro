# Precisly release shrinking rules.

# Preserve line numbers/source names for actionable crash reports.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Room entities/DAOs and generated implementations are annotation-driven; keep model members stable.
-keep class com.eastweblite.browser.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi/Retrofit are currently light dependencies; keep reflective adapters if introduced.
-keep class com.squareup.moshi.adapters.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okio.**

# No JavaScript interfaces are used. If one is added, explicitly keep only that interface.
# -keepclassmembers class com.eastweblite.browser.web.SafeJsBridge { public *; }
