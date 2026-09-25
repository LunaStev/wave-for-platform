package dev.wavelang.intellij.wave.toolchain

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import dev.wavelang.intellij.wave.WaveBundle

/** Machine-specific paths must not be shared with the project's sources. */
@Service(Service.Level.PROJECT)
@State(name = "WfpToolchains", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ToolchainSettings : SerializablePersistentStateComponent<ToolchainState>(ToolchainState()) {
  fun update(value: ToolchainState) {
    updateState { value }
  }
}

data class ToolchainState(
  val rust: RustToolchain = RustToolchain(),
  val profiles: List<EcosystemProfile> = listOf(EcosystemProfile()),
  val activeProfileId: String = "default",
) {
  fun activeProfile(): EcosystemProfile? = profiles.find { it.id == activeProfileId }
}

data class RustToolchain(
  val toolchain: String = "",
  val cargo: String = "",
  val rustc: String = "",
  val rustAnalyzer: String = "",
  val rustfmt: String = "",
)

data class EcosystemProfile(
  val id: String = "default",
  val name: String = WaveBundle.message("toolchains.profile.default"),
  val wavec: String = "",
  val whale: String = "",
  val vex: String = "",
  val waveRoot: String = "",
  val whaleRoot: String = "",
  val vexRoot: String = "",
  val workingDirectory: String = "",
  val target: String = "",
)

enum class CompilerTool(val executableName: String, val rust: Boolean = false) {
  CARGO("cargo", true),
  RUSTC("rustc", true),
  RUST_ANALYZER("rust-analyzer", true),
  RUSTFMT("rustfmt", true),
  WAVEC("wavec"),
  WHALE("whale"),
  VEX("vex"),
}
