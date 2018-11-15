/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.eclipse.andmore.base.resources;

import org.eclipse.swt.graphics.Image;

/**
 * Interface for Factory to generate icons for Android Editors
 * @author Andrew Bowley
 *
 */
public interface IEditorIconFactory {

    /**
     * Returns an Image for a given icon name.
     * <p/>
     * Callers should not dispose it.
     *
     * @param osName The leaf name, without the extension, of an existing icon in the
     *        editor's "icons" directory. If it doesn't exist, a default icon will be
     *        generated automatically based on the name.
     * @param color The color of the text in the automatically generated icons
     */
    Image getColorIcon(String osName, int color);
}
