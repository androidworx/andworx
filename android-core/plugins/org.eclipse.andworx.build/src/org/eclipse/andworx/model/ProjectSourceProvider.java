/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.eclipse.andworx.model;

import java.util.List;

import com.android.builder.model.SourceProvider;

/**
 * SourceProvider which also provides relative paths needed for Eclipse file system
 */
public interface ProjectSourceProvider  extends SourceProvider{
	/**
	 * Returns paths relative to project root for given CodeSource enum.
	 * l
	 * @return list of SourcePath objects
	 */
	List<SourcePath> getSourcePathList(CodeSource codeSource);
}
