# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class org.wishy.**$$serializer { *; }
-keepclassmembers class org.wishy.** {
    *** Companion;
}
-keepclasseswithmembers class org.wishy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
