/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.file;

import java.io.IOException;
import java.net.URL;

/**
 * Utility interface for opening files using platform
 */
public interface FileResource {
	/** 
	 * Returns name of file 
	 * @return name
	 */
	String getFileName();
	/** 
	 * Returns file location as URL 
	 * @return URL object
	 */
	URL asUrl() throws IOException;
}
