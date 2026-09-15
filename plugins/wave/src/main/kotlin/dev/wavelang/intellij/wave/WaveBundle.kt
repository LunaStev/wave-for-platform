package dev.wavelang.intellij.wave

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.WaveBundle"

internal object WaveBundle : DynamicBundle(BUNDLE) {
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String): String = getMessage(key)
}
