# Navigation routes (and later, API models) are @Serializable; navigation looks their
# serializers up reflectively from the KClass, so keep these small classes whole.
-keep @kotlinx.serialization.Serializable class io.lanyin.player.** { *; }

# 词幕 (Lyricon)'s provider SDK ships no rules of its own. What it sends to the module crosses into
# SystemUI's process, where parcelables are found again by class name, so keep the SDK whole.
-keep class io.github.proify.lyricon.** { *; }
