package dev.wavelang.intellij.wave.cargo

import com.intellij.icons.AllIcons
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.toolchain.CompilerTool
import dev.wavelang.intellij.wave.toolchain.ToolExecutionOptions
import dev.wavelang.intellij.wave.toolchain.ToolRunProcessHandler
import dev.wavelang.intellij.wave.toolchain.ToolchainResolver
import dev.wavelang.intellij.wave.toolchain.ToolchainSettings
import org.jdom.Element
import java.nio.file.Path

internal data class CargoRunSpec(
  val command: String = "check",
  val manifest: String = "Cargo.toml",
  val packageId: String = "",
  val targetKind: String = "",
  val targetName: String = "",
  val profile: String = "",
  val features: String = "",
  val allFeatures: Boolean = false,
  val noDefaultFeatures: Boolean = false,
  val workingDirectory: String = "",
  val testFilter: String = "",
  val testArguments: List<String> = emptyList(),
  val environment: Map<String, String> = emptyMap(),
  val timeoutSeconds: Int = 600,
  val offline: Boolean = true,
  val log: String = "",
) {
  fun validate() {
    require(command in setOf("build", "check", "test"))
    require(manifest.isNotBlank())
    require(timeoutSeconds in 1..86400)
    require(targetKind in setOf("", "lib", "bin", "example", "test", "bench"))
    require(targetKind in setOf("", "lib") || targetName.isNotBlank())
    require(command == "test" || (testFilter.isBlank() && testArguments.isEmpty()))
    require(environment.keys.all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) })
    require(!allFeatures || features.isBlank())
  }

  fun arguments(manifestPath: Path): List<String> {
    validate()
    return buildList {
      add(command)
      addAll(listOf("--manifest-path", manifestPath.toString(), "--message-format=json", "--locked"))
      if (offline) add("--offline")
      if (packageId.isNotBlank()) addAll(listOf("--package", packageId))
      if (targetKind == "lib") add("--lib")
      else if (targetKind.isNotBlank()) addAll(listOf("--$targetKind", targetName))
      if (profile.isNotBlank()) addAll(listOf("--profile", profile))
      if (features.isNotBlank()) addAll(listOf("--features", features))
      if (allFeatures) add("--all-features")
      if (noDefaultFeatures) add("--no-default-features")
      if (command == "test") {
        if (testFilter.isNotBlank()) add(testFilter)
        if (testArguments.isNotEmpty()) { add("--"); addAll(testArguments) }
      }
    }
  }

  fun write(element: Element) {
    val node = Element("cargo")
    val fields = mapOf("command" to command, "manifest" to manifest, "packageId" to packageId,
      "targetKind" to targetKind, "targetName" to targetName, "profile" to profile, "features" to features,
      "allFeatures" to allFeatures.toString(), "noDefaultFeatures" to noDefaultFeatures.toString(),
      "workingDirectory" to workingDirectory, "testFilter" to testFilter, "timeoutSeconds" to timeoutSeconds.toString(),
      "offline" to offline.toString(), "log" to log)
    fields.forEach { (key, value) -> node.setAttribute(key, value) }
    testArguments.forEach { node.addContent(Element("testArgument").setAttribute("value", it)) }
    environment.forEach { (key, value) -> node.addContent(Element("env").setAttribute("name", key).setAttribute("value", value)) }
    element.removeChildren("cargo")
    element.addContent(node)
  }

  companion object {
    fun read(element: Element): CargoRunSpec {
      val node = element.getChild("cargo") ?: return CargoRunSpec()
      fun value(key: String, default: String = "") = node.getAttributeValue(key) ?: default
      return CargoRunSpec(value("command", "check"), value("manifest", "Cargo.toml"), value("packageId"), value("targetKind"),
        value("targetName"), value("profile"), value("features"), value("allFeatures").toBoolean(),
        value("noDefaultFeatures").toBoolean(), value("workingDirectory"), value("testFilter"),
        node.getChildren("testArgument").map { it.getAttributeValue("value").orEmpty() },
        node.getChildren("env").associate { it.getAttributeValue("name").orEmpty() to it.getAttributeValue("value").orEmpty() },
        value("timeoutSeconds", "600").toIntOrNull() ?: 600, value("offline", "true").toBoolean(), value("log"))
    }
  }
}

class CargoConfigurationType : ConfigurationTypeBase(
  "WfpCargo", WaveBundle.message("cargo.run.title"), WaveBundle.message("cargo.run.description"), AllIcons.RunConfigurations.Application,
) {
  init {
    addFactory(object : ConfigurationFactory(this) {
      override fun getId(): String = "WfpCargo"
      override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CargoRunConfiguration(project, this, WaveBundle.message("cargo.run.title"))
      override fun isEditableInDumbMode(): Boolean = true
    })
  }
}

internal class CargoRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
  RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
  var spec = CargoRunSpec()

  override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoSettingsEditor(project)

  override fun checkConfiguration() {
    try { spec.validate() }
    catch (_: IllegalArgumentException) { throw RuntimeConfigurationError(WaveBundle.message("cargo.invalid.configuration")) }
  }

  override fun readExternal(element: Element) { super.readExternal(element); spec = CargoRunSpec.read(element) }
  override fun writeExternal(element: Element) { super.writeExternal(element); spec.write(element) }

  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
    if (executor.id != DefaultRunExecutor.EXECUTOR_ID) return null
    val selected = spec.copy(testArguments = spec.testArguments.toList(), environment = spec.environment.toMap())
    return RunProfileState { _, _ ->
      val root = projectRoot(project)
      val manifest = root.resolve(selected.manifest).normalize()
      val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
      var adapter: CargoConsole? = null
      val handler = ToolRunProcessHandler(project, { indicator ->
        selected.validate()
        val workspace = project.service<CargoWorkspaceService>().refresh(manifest, indicator)
        if (selected.packageId.isNotBlank()) {
          val pkg = workspace.packages.singleOrNull { it.id == selected.packageId || it.name == selected.packageId }
          requireNotNull(pkg) { WaveBundle.message("cargo.package.missing", selected.packageId) }
          if (selected.targetKind.isNotBlank()) {
            require(pkg.targets.any { selected.targetKind in it.kinds &&
              (selected.targetKind == "lib" || it.name == selected.targetName) }) {
              WaveBundle.message("cargo.target.missing", selected.targetName)
            }
          }
        }
        val directory = if (selected.workingDirectory.isBlank()) workspace.root else root.resolve(selected.workingDirectory).normalize()
        val rust = project.service<ToolchainSettings>().state.rust
        val resolver = ToolchainResolver(root)
        val rustc = resolver.rustCommand(CompilerTool.RUSTC, rust, emptyList(), indicator, directory)
        adapter = CargoConsole(console, workspace.root)
        resolver.rustCommand(CompilerTool.CARGO, rust, selected.arguments(manifest), indicator, directory).let {
          it.copy(environment = it.environment + selected.environment + ("RUSTC" to rustc.executable.toString()))
        }
      }, ToolExecutionOptions(selected.timeoutSeconds * 1000L, log = selected.log.takeIf { it.isNotBlank() }?.let { root.resolve(it) }),
        { text, error -> adapter?.accept(text, error) }, { adapter?.flush() })
      console.attachToProcess(handler)
      DefaultExecutionResult(console, handler)
    }
  }
}

internal fun parseEnvironment(text: String): Map<String, String> = text.lineSequence().filter { it.isNotBlank() }.associate {
  val separator = it.indexOf('=')
  require(separator > 0) { WaveBundle.message("cargo.invalid.environment") }
  it.substring(0, separator).trim() to it.substring(separator + 1)
}

internal fun parseTestArguments(text: String): List<String> = ParametersListUtil.parse(text)
