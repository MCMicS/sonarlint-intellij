/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.excludeFile
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.removeFileExclusion
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.toggleRule

@DisabledIf("isCLionOrGoLand")
class StandaloneIdeaTests : BaseUiTest() {

    @Test
    fun should_exclude_rule_() = uiTest {
        openExistingProject("sample-java-issues")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        toggleRule("java:S139", "Comments should not be located at the end of lines of code")
        verifyCurrentFileTabContainsMessages("Move this trailing comment on the previous empty line.")
        toggleRule("java:S139", "Comments should not be located at the end of lines of code")
        verifyCurrentFileTabContainsMessages("No issues to display")
    }

    @Test
    fun should_exclude_file_and_analyze_file_and_no_issues_found() = uiTest {
        openExistingProject("sample-java-issues")
        excludeFile("Foo.java")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        verifyCurrentFileTabContainsMessages("No issues to display")
        removeFileExclusion("Foo.java")
    }

}
