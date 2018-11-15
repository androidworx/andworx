/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.eclipse.andmore.internal.preferences;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.andmore.internal.preferences.messages"; //$NON-NLS-1$

    public static String AndroidPreferencePage_ERROR_Reserved_Char;

    public static String AndroidPreferencePage_SDK_Location_;

    public static String AndroidPreferencePage_Title;

    public static String BuildPreferencePage_Auto_Refresh_Resources_on_Build;

    public static String BuildPreferencePage_Build_Output;

    public static String BuildPreferencePage_Custom_Keystore;

    public static String BuildPreferencePage_Custom_Certificate_Fingerprint_MD5;

    public static String BuildPreferencePage_Custom_Certificate_Fingerprint_SHA1;

    public static String BuildPreferencePage_Default_KeyStore;

    public static String BuildPreferencePage_Default_Certificate_Fingerprint_MD5;

    public static String BuildPreferencePage_Default_Certificate_Fingerprint_SHA1;

    public static String BuildPreferencePage_Normal;

    public static String BuildPreferencePage_Silent;

    public static String BuildPreferencePage_Title;

    public static String BuildPreferencePage_Verbose;

    public static String LaunchPreferencePage_Default_Emu_Options;

    public static String LaunchPreferencePage_Default_HOME_Package;

    public static String LaunchPreferencePage_Title;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
