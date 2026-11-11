# kotlinx.serialization (type-safe navigation routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Glance action callbacks are instantiated reflectively by class name
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# WorkManager workers are created reflectively
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
