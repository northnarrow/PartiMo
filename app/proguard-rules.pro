# Regole R8 dell'app.
#
# R8 riduce solo le librerie: il codice di PartiMo resta intero e non offuscato, perché JSON
# (kotlinx.serialization), rotte di navigazione, Room, WorkManager e widget usano i nomi delle classi.
# Le librerie (Compose, Ktor, OkHttp, Room, MapLibre, ML Kit, Coil, Glance) portano le proprie regole.
-dontobfuscate
-keep class com.partimo.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
