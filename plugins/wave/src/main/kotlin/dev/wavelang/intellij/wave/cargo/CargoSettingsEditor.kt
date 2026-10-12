package dev.wavelang.intellij.wave.cargo

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.ui.JBUI
import dev.wavelang.intellij.wave.WaveBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class CargoSettingsEditor(private val project: Project) : SettingsEditor<CargoRunConfiguration>() {
  private val form = RustForm()
  private val command = JComboBox(arrayOf("check", "build", "test"))
  private val manifest = JBTextField("Cargo.toml")
  private val packageId = JComboBox<String>().apply { isEditable = true }
  private val targetKind = JComboBox(arrayOf("", "lib", "bin", "example", "test", "bench"))
  private val targetName = JComboBox<String>().apply { isEditable = true }
  private val profile = JBTextField()
  private val features = JBTextField()
  private val allFeatures = JCheckBox(WaveBundle.message("cargo.all.features"))
  private val noDefaultFeatures = JCheckBox(WaveBundle.message("cargo.no.default.features"))
  private val workingDirectory = JBTextField()
  private val testFilter = JBTextField()
  private val testArguments = JBTextField()
  private val environment = JBTextArea(4, 40)
  private val timeout = JBTextField("600")
  private val offline = JCheckBox(WaveBundle.message("cargo.offline"), true)
  private val log = JBTextField()
  private val report = JBTextArea(5, 40).apply { isEditable = false }
  private val refresh = JButton(WaveBundle.message("cargo.refresh"))
  @Volatile private var refreshing: ProgressIndicator? = null
  @Volatile private var disposed = false
  private var workspace: CargoWorkspace? = null

  init {
    form.add("cargo.command", command)
    form.add("cargo.manifest", manifest)
    form.add("cargo.package", packageId)
    form.add("cargo.target.kind", targetKind)
    form.add("cargo.target.name", targetName)
    form.add("cargo.profile", profile)
    form.add("cargo.features", features)
    form.add(null, allFeatures)
    form.add(null, noDefaultFeatures)
    form.add("cargo.working.directory", workingDirectory)
    form.add("cargo.test.filter", testFilter)
    form.add("cargo.test.arguments", testArguments)
    form.add("cargo.environment", JBScrollPane(environment), environment)
    form.add("cargo.timeout", timeout)
    form.add(null, offline)
    form.add("cargo.log", log)
    form.add(null, JBLabel(WaveBundle.message("cargo.refresh.hint")))
    form.add(null, refresh)
    form.add("cargo.refresh.result", JBScrollPane(report), report)
    form.add(null, JButton(WaveBundle.message("cargo.open.target")).apply {
      addActionListener {
        val pkg = workspace?.packages?.find { it.name == packageValue() || it.id == packageValue() }
        val target = pkg?.targets?.find { it.name == targetValue() && targetKind.selectedItem in it.kinds }
        val file = target?.source?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        if (file != null) OpenFileDescriptor(project, file).navigate(true)
      }
    })
    packageId.addActionListener { updateTargets() }
    targetKind.addActionListener { updateTargets() }
    refresh.addActionListener { refreshWorkspace() }
  }

  private fun packageValue(): String = packageId.editor.item?.toString().orEmpty()
  private fun targetValue(): String = targetName.editor.item?.toString().orEmpty()

  private fun updateTargets() {
    val previous = targetValue()
    targetName.removeAllItems()
    targetName.addItem("")
    workspace?.packages?.filter { it.id == packageValue() || it.name == packageValue() }?.flatMap { it.targets }
      ?.filter { targetKind.selectedItem in it.kinds }?.forEach { targetName.addItem(it.name) }
    targetName.editor.item = previous
  }

  private fun refreshWorkspace() {
    val selected = manifest.text
    refresh.isEnabled = false
    report.text = WaveBundle.message("toolchains.checking")
    object : Task.Backgroundable(project, WaveBundle.message("cargo.refresh"), true) {
      private var model: CargoWorkspace? = null
      override fun run(indicator: ProgressIndicator) {
        refreshing = indicator
        if (disposed) { indicator.cancel(); return }
        model = runBlockingMaybeCancellable {
          project.service<CargoWorkspaceService>().refresh(projectRoot(project).resolve(selected).normalize(), indicator)
        }
      }

      override fun onSuccess() {
        if (disposed || manifest.text != selected) return
        workspace = model
        val previous = packageValue()
        packageId.removeAllItems()
        packageId.addItem("")
        model?.packages?.forEach { packageId.addItem(it.name) }
        packageId.editor.item = previous
        updateTargets()
        report.text = model?.let { workspaceSummary(it) }.orEmpty()
        report.caretPosition = 0
      }

      override fun onThrowable(error: Throwable) {
        if (!disposed) report.text = WaveBundle.message("cargo.refresh.failed", error.message.orEmpty())
      }

      override fun onCancel() { if (!disposed) report.text = WaveBundle.message("toolchains.cancelled") }
      override fun onFinished() { refreshing = null; if (!disposed) refresh.isEnabled = true }
    }.queue()
  }

  override fun resetEditorFrom(configuration: CargoRunConfiguration) {
    val value = configuration.spec
    command.selectedItem = value.command
    manifest.text = value.manifest
    packageId.editor.item = value.packageId
    targetKind.selectedItem = value.targetKind
    targetName.editor.item = value.targetName
    profile.text = value.profile
    features.text = value.features
    allFeatures.isSelected = value.allFeatures
    noDefaultFeatures.isSelected = value.noDefaultFeatures
    workingDirectory.text = value.workingDirectory
    testFilter.text = value.testFilter
    testArguments.text = ParametersListUtil.join(value.testArguments)
    environment.text = value.environment.entries.joinToString("\n") { "${it.key}=${it.value}" }
    timeout.text = value.timeoutSeconds.toString()
    offline.isSelected = value.offline
    log.text = value.log
  }

  override fun applyEditorTo(configuration: CargoRunConfiguration) {
    try {
      val value = CargoRunSpec(command.selectedItem.toString(), manifest.text, packageValue(), targetKind.selectedItem.toString(),
        targetValue(), profile.text, features.text, allFeatures.isSelected, noDefaultFeatures.isSelected, workingDirectory.text,
        testFilter.text, parseTestArguments(testArguments.text), parseEnvironment(environment.text), timeout.text.toInt(),
        offline.isSelected, log.text)
      value.validate()
      configuration.spec = value
    }
    catch (e: IllegalArgumentException) {
      throw ConfigurationException(WaveBundle.message("cargo.invalid.configuration"))
    }
  }

  override fun createEditor(): JComponent = JBScrollPane(form.panel)
  override fun disposeEditor() { disposed = true; refreshing?.cancel() }
}

internal class RustForm {
  val panel = JPanel(GridBagLayout())
  private var row = 0
  fun add(key: String?, component: JComponent, labelFor: JComponent = component) {
    val constraints = GridBagConstraints().apply {
      gridy = row++
      insets = JBUI.insets(4, 6)
      anchor = GridBagConstraints.WEST
    }
    if (key != null) panel.add(JBLabel(WaveBundle.message(key)).apply { this.labelFor = labelFor }, constraints)
    constraints.gridx = if (key == null) 0 else 1
    constraints.gridwidth = if (key == null) 2 else 1
    constraints.fill = GridBagConstraints.HORIZONTAL
    constraints.weightx = 1.0
    panel.add(component, constraints)
  }
}

internal fun workspaceSummary(model: CargoWorkspace): String = buildString {
  appendLine(WaveBundle.message("cargo.workspace.summary", model.root, model.targetDirectory))
  for (pkg in model.packages) {
    appendLine("${pkg.name}: ${pkg.manifest}")
    for (target in pkg.targets) appendLine("  ${target.kinds.joinToString(",")}: ${target.name} (${target.edition}) — ${target.source}")
  }
}
