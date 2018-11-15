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
package com.android.sdkuilib.ui;

import com.android.sdkuilib.internal.widgets.AvdSelector;

/**
 * The display mode of the AVD Selector.
 */
public enum AvdDisplayMode {
    /**
     * Manager mode. Invalid AVDs are displayed. Buttons to create/delete AVDs
     */
    MANAGER,

    /**
     * Non manager mode. Only valid AVDs are displayed. Cannot create/delete AVDs, but
     * there is a button to open the AVD Manager.
     * In the "check" selection mode, checkboxes are displayed on each line
     * and {@link AvdSelector#getSelected()} returns the line that is checked
     * even if it is not the currently selected line. Only one line can
     * be checked at once.
     */
    SIMPLE_CHECK,

    /**
     * Non manager mode. Only valid AVDs are displayed. Cannot create/delete AVDs, but
     * there is a button to open the AVD Manager.
     * In the "select" selection mode, there are no checkboxes and
     * {@link AvdSelector#getSelected()} returns the line currently selected.
     * Only one line can be selected at once.
     */
    SIMPLE_SELECTION,

}
