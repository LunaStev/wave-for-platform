package dev.wavelang.intellij.wave.make

import com.intellij.openapi.fileTypes.LanguageFileType
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.WaveIcons
import javax.swing.Icon

class MakefileFileType : LanguageFileType(MakefileLanguage) {
  override fun getName(): String = "Wave Makefile"

  override fun getDescription(): String = WaveBundle.message("filetype.makefile.description")

  override fun getDefaultExtension(): String = "mk"

  override fun getIcon(): Icon = WaveIcons.MAKEFILE
}
