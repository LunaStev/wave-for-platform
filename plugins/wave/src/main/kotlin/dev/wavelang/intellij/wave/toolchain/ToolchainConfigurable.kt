package dev.wavelang.intellij.wave.toolchain

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import dev.wavelang.intellij.wave.WaveBundle
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.nio.file.Path
import java.util.UUID
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class ToolchainConfigurable(private val project: Project) : Configurable {
  private val settings get() = project.service<ToolchainSettings>()
  @Volatile private var editor: Editor? = null
  @Volatile private var inspection: ProgressIndicator? = null

  override fun getDisplayName(): String = WaveBundle.message("toolchains.title")

  override fun createComponent(): JComponent {
    return Editor().also { editor = it; it.load(settings.state) }.panel
  }

  override fun isModified(): Boolean = editor?.snapshot()?.let { it != settings.state } ?: false

  override fun apply() {
    val state = editor?.snapshot() ?: return
    if (state.profiles.any { it.name.isBlank() } || state.profiles.map { it.name }.distinct().size != state.profiles.size) {
      throw ConfigurationException(WaveBundle.message("toolchains.profile.unique"))
    }
    settings.update(state)
  }

  override fun reset() {
    inspection?.cancel()
    editor?.load(settings.state)
  }

  override fun disposeUIResources() {
    inspection?.cancel()
    editor = null
  }

  private inner class Editor {
    val panel = JPanel(BorderLayout(JBUI.scale(8), JBUI.scale(8)))
    private val form = JPanel(GridBagLayout())
    private var row = 0
    private val rustFields = listOf("toolchain", "cargo", "rustc", "rustAnalyzer", "rustfmt").associateWith { JBTextField(36) }
    private val profileFields = listOf("name", "wavec", "waveRoot", "whale", "whaleRoot", "vex", "vexRoot", "workingDirectory", "target")
      .associateWith { JBTextField(36) }
    private val profiles = ArrayList<EcosystemProfile>()
    private val selector = JComboBox<String>()
    private var selectedIndex = -1
    private var loading = false
    private val result = JBTextArea(8, 60).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
    }
    private val check = JButton(WaveBundle.message("toolchains.check"))

    init {
      addRow(null, JBLabel(WaveBundle.message("toolchains.local.only")))
      addRow(null, JBLabel(WaveBundle.message("toolchains.rust.hint")))
      for ((key, field) in rustFields) addRow(WaveBundle.message("toolchains.rust.$key"), field)
      val profileControls = JPanel().apply {
        add(selector)
        add(JButton(WaveBundle.message("toolchains.add")).apply {
          addActionListener {
            saveProfile()
            val profile = EcosystemProfile(
              id = UUID.randomUUID().toString(), name = WaveBundle.message("toolchains.profile.new", profiles.size + 1),
            )
            profiles.add(profile)
            refreshSelector(profiles.lastIndex)
          }
        })
        add(JButton(WaveBundle.message("toolchains.remove")).apply {
          addActionListener {
            if (profiles.size > 1) {
              profiles.removeAt(selectedIndex)
              refreshSelector(0)
            }
          }
        })
      }
      val profileLabel = JBLabel(WaveBundle.message("toolchains.profile"))
      profileLabel.labelFor = selector
      addRow(null, profileLabel)
      addRow(null, profileControls)
      addRow(null, JBLabel(WaveBundle.message("toolchains.profile.hint")))
      for ((key, field) in profileFields) addRow(WaveBundle.message("toolchains.profile.$key"), field)
      profileFields.getValue("name").addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent) {
          if (!loading && selectedIndex >= 0) {
            saveProfile()
            refreshSelector(selectedIndex)
          }
        }
      })
      selector.addActionListener {
        if (!loading) {
          saveProfile()
          selectedIndex = selector.selectedIndex
          loadProfile()
        }
      }
      check.addActionListener { inspect() }
      addRow(null, check)
      val resultLabel = JBLabel(WaveBundle.message("toolchains.results"))
      resultLabel.labelFor = result
      addRow(null, resultLabel)
      addRow(null, JBScrollPane(result))
      panel.add(JBScrollPane(form), BorderLayout.CENTER)
    }

    private fun addRow(label: String?, component: JComponent) {
      val constraints = GridBagConstraints().apply {
        gridy = row++
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(3, 6)
      }
      if (label != null) {
        constraints.gridx = 0
        form.add(JBLabel(label).apply { labelFor = component }, constraints)
      }
      constraints.gridx = if (label == null) 0 else 1
      constraints.gridwidth = if (label == null) 2 else 1
      constraints.weightx = 1.0
      constraints.fill = GridBagConstraints.HORIZONTAL
      form.add(component, constraints)
    }

    fun load(state: ToolchainState) {
      val rust = state.rust
      val values = listOf(rust.toolchain, rust.cargo, rust.rustc, rust.rustAnalyzer, rust.rustfmt)
      rustFields.values.zip(values).forEach { (field, value) -> field.text = value }
      profiles.clear()
      profiles.addAll(state.profiles.ifEmpty { listOf(EcosystemProfile()) })
      refreshSelector(profiles.indexOfFirst { it.id == state.activeProfileId }.coerceAtLeast(0))
      result.text = ""
    }

    private fun refreshSelector(index: Int) {
      loading = true
      selector.removeAllItems()
      profiles.forEach { selector.addItem(it.name) }
      selectedIndex = index
      selector.selectedIndex = index
      loading = false
      loadProfile()
    }

    private fun loadProfile() {
      val p = profiles.getOrNull(selectedIndex) ?: return
      val values = listOf(p.name, p.wavec, p.waveRoot, p.whale, p.whaleRoot, p.vex, p.vexRoot, p.workingDirectory, p.target)
      profileFields.values.zip(values).forEach { (field, value) -> field.text = value }
    }

    private fun saveProfile() {
      val old = profiles.getOrNull(selectedIndex) ?: return
      fun value(key: String) = profileFields.getValue(key).text
      profiles[selectedIndex] = old.copy(
        name = value("name"), wavec = value("wavec"), waveRoot = value("waveRoot"), whale = value("whale"),
        whaleRoot = value("whaleRoot"), vex = value("vex"), vexRoot = value("vexRoot"),
        workingDirectory = value("workingDirectory"), target = value("target"),
      )
    }

    fun snapshot(): ToolchainState {
      saveProfile()
      fun value(key: String) = rustFields.getValue(key).text
      return ToolchainState(
        RustToolchain(value("toolchain"), value("cargo"), value("rustc"), value("rustAnalyzer"), value("rustfmt")),
        profiles.toList(), profiles[selectedIndex].id,
      )
    }

    private fun inspect() {
      val state = snapshot()
      check.isEnabled = false
      result.text = WaveBundle.message("toolchains.checking")
      object : Task.Backgroundable(project, WaveBundle.message("toolchains.check"), true) {
        private var report = ""
        override fun run(indicator: ProgressIndicator) {
          inspection = indicator
          if (editor !== this@Editor) {
            indicator.cancel()
            return
          }
          val root = project.basePath ?: throw IllegalArgumentException(WaveBundle.message("toolchains.no.project.root"))
          report = ToolchainResolver(Path.of(root)).inspect(state, indicator)
        }

        override fun onSuccess() {
          if (editor === this@Editor) {
            result.text = if (snapshot() == state) report else WaveBundle.message("toolchains.changed")
            result.caretPosition = 0
          }
        }

        override fun onThrowable(error: Throwable) {
          if (editor === this@Editor) result.text = error.message ?: WaveBundle.message("toolchains.check.failed")
        }

        override fun onCancel() {
          if (editor === this@Editor) result.text = WaveBundle.message("toolchains.cancelled")
        }

        override fun onFinished() {
          inspection = null
          if (editor === this@Editor) check.isEnabled = true
        }
      }.queue()
    }
  }
}
