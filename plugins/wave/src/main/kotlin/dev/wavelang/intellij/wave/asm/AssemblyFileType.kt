package dev.wavelang.intellij.wave.asm

import com.intellij.openapi.fileTypes.LanguageFileType
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.WaveIcons
import javax.swing.Icon

class AssemblyFileType : LanguageFileType(AssemblyLanguage) {
  override fun getName(): String = "Wave Assembly"

  override fun getDescription(): String = WaveBundle.message("filetype.assembly.description")

  override fun getDefaultExtension(): String = "s"

  override fun getIcon(): Icon = WaveIcons.ASSEMBLY
}
