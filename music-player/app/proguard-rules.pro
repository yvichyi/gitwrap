# Navigation routes (and later, API models) are @Serializable; navigation looks their
# serializers up reflectively from the KClass, so keep these small classes whole.
-keep @kotlinx.serialization.Serializable class io.lanyin.player.** { *; }
