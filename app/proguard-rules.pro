# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.wrtctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.wrtctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.wrtctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-dontwarn javax.annotation.**

# Release builds drop verbose, debug and info log calls (WrtCtrl itself logs nothing; this also covers libraries).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# WorkManager keeps its job list in a Room 2.6.1 database, and Room creates the generated
# WorkDatabase_Impl by reflection through its no-argument constructor. Room 2.6.1's own rule
# (-keep class * extends androidx.room.RoomDatabase) names no members, and the R8 in Android
# Gradle Plugin 9 no longer keeps a constructor for such a rule, so without this line the app
# would crash at start (NoSuchMethodException: WorkDatabase_Impl.<init>). Same rule Room 2.7 ships.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
