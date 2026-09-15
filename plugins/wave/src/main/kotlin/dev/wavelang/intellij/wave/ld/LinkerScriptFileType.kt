package dev.wavelang.intellij.wave.ld

import com.intellij.openapi.fileTypes.LanguageFileType
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.WaveIcons
import javax.swing.Icon

class LinkerScriptFileType : LanguageFileType(LinkerScriptLanguage) {
  override fun getName(): String = "Wave Linker Script"

  override fun getDescription(): String = WaveBundle.message("filetype.linker.description")

  override fun getDefaultExtension(): String = "ld"

  override fun getIcon(): Icon = WaveIcons.LD
}
