# Navigation routes are @Serializable objects; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class io.lanyin.player.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
