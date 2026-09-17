// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.customization.base

import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.Urls

internal class IntelliJIdeaExternalResourceUrls : ExternalProductResourceUrls {
  // WfP does not publish an update feed or patches yet; use the interface's null defaults.
  override val bugReportUrl: (String) -> Url
    get() = { Urls.newFromEncoded("https://github.com/wavefnd/wave-for-platform/issues/new") }

  override val technicalSupportUrl: (String) -> Url
    get() = { Urls.newFromEncoded("https://github.com/wavefnd/wave-for-platform/issues") }

  override val downloadPageUrl: Url
    get() = Urls.newFromEncoded("https://github.com/wavefnd/wave-for-platform/releases")

  override val whatIsNewPageUrl: Url
    get() = downloadPageUrl

  override val gettingStartedPageUrl: Url
    get() = Urls.newFromEncoded("https://github.com/wavefnd/wave-for-platform#readme")

  override val helpPageUrl: (String) -> Url
    get() = { gettingStartedPageUrl }
}
