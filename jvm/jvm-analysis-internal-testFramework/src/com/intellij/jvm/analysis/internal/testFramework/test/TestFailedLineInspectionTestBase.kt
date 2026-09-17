package com.intellij.jvm.analysis.internal.testFramework.test

import com.intellij.codeInspection.test.TestFailedLineInspection
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import java.util.Date

abstract class TestFailedLineInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: TestFailedLineInspection = TestFailedLineInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit3Library()
      model.addHamcrestLibrary()
      model.addJUnit5Library()
    }
  }

  protected fun doTest(
    lang: JvmLanguage,
    text: String,
    fileName: String = generateFileName(),
    url: String,
    stackTrace: String,
    errorMessage: String,
  ) {
    // These tests check failure highlighting, not navigation through a JVM test runner.
    val locator = SMTestLocator { _, _, _, _ -> emptyList() }
    val pair = TestStackTraceParser(url, stackTrace, errorMessage, locator, project)
    val record = TestStateStorage.Record(
      TestStateInfo.Magnitude.FAILED_INDEX.value, Date(), 0,
      pair.failedLine, pair.failedMethodName, pair.errorMessage, pair.topLocationLine
    )
    TestStateStorage.getInstance(project).writeState(url, record)
    myFixture.testHighlighting(lang, text, fileName)
    val infos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertEquals(1, infos.size)
    val attributes = infos.first().forcedTextAttributes
    assertNotNull(attributes)
    assertEquals(EffectType.BOLD_DOTTED_LINE, attributes.effectType)
  }
}
