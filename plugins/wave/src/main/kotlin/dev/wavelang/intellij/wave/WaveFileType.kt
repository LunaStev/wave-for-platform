package dev.wavelang.intellij.wave

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class WaveFileType : LanguageFileType(WaveLanguage) {
  override fun getName() = "Wave"
  override fun getDescription() = WaveBundle.message("filetype.wave.description")
  override fun getDefaultExtension() = "wave"
  override fun getIcon(): Icon = WaveIcons.FILE
}
