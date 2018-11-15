/*
 * Copyright (C) 2007 The Android Open Source Project
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
package org.eclipse.andworx.sdk;

/**
 * An error handler for checkSdkLocationAndId() that will handle the generated error
 * or warning message. Each method must return a boolean that will in turn be returned by
 * checkSdkLocationAndId.
 */
public abstract class CheckSdkErrorHandler {

    public enum Solution {
        NONE,
        OPEN_SDK_MANAGER,
        OPEN_ANDROID_PREFS,
        OPEN_P2_UPDATE
    }

    /**
     * Handle an error message during sdk location check. Returns whatever
     * checkSdkLocationAndId() should returns.
     */
    public abstract boolean handleError(Solution solution, String message);

    /**
     * Handle a warning message during sdk location check. Returns whatever
     * checkSdkLocationAndId() should returns.
     */
    public abstract boolean handleWarning(Solution solution, String message);
}
