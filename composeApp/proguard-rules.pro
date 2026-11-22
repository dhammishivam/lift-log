# Ktor references JVM-only classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
# kotlinx.serialization ships its own R8 rules; keep our models' generated serializers just in case.
-keepclassmembers @kotlinx.serialization.Serializable class app.liftlog.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class app.liftlog.**$$serializer { *; }
