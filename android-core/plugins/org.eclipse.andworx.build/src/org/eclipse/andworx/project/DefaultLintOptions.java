/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.project;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.android.builder.model.LintOptions;

/**
 *  Lint options implementation - used as default
 */
public class DefaultLintOptions implements LintOptions {

	@Override
	public Set<String> getDisable() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> getEnable() {
		return Collections.emptySet();
	}

	@Override
	public Set<String> getCheck() {
		return Collections.emptySet();
	}

	@Override
	public boolean isAbortOnError() {
		return false;
	}

	@Override
	public boolean isAbsolutePaths() {
		return false;
	}

	@Override
	public boolean isNoLines() {
		return false;
	}

	@Override
	public boolean isQuiet() {
		return true;
	}

	@Override
	public boolean isCheckAllWarnings() {
		return false;
	}

	@Override
	public boolean isIgnoreWarnings() {
		return true;
	}

	@Override
	public boolean isWarningsAsErrors() {
		return false;
	}

	@Override
	public boolean isCheckTestSources() {
		return false;
	}

	@Override
	public boolean isCheckGeneratedSources() {
		return false;
	}

	@Override
	public boolean isExplainIssues() {
		return false;
	}

	@Override
	public boolean isShowAll() {
		return false;
	}

	@Override
	public File getLintConfig() {
		return null;
	}

	@Override
	public boolean getTextReport() {
		return false;
	}

	@Override
	public File getTextOutput() {
		return null;
	}

	@Override
	public boolean getHtmlReport() {
		return false;
	}

	@Override
	public File getHtmlOutput() {
		return null;
	}

	@Override
	public boolean getXmlReport() {
		return false;
	}

	@Override
	public File getXmlOutput() {
		return null;
	}

	@Override
	public boolean isCheckReleaseBuilds() {
		return false;
	}

	@Override
	public boolean isCheckDependencies() {
		return false;
	}

	@Override
	public File getBaselineFile() {
		return null;
	}

	@Override
	public Map<String, Integer> getSeverityOverrides() {
		return Collections.emptyMap();
	}
	
}
