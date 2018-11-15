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
package org.eclipse.andmore.ddms;

import com.android.ddmlib.Client;

/**
 * Handler to act on HPROF dumps.
 */
public interface IHprofDumpHandler {
    /**
     * Called when a HPROF dump succeeded.
     * @param remoteFilePath the device-side path of the HPROF file.
     * @param client the client for which the HPROF file was.
     */
    void onSuccess(String remoteFilePath, Client client);

    /**
     * Called when a HPROF dump was successful.
     * @param data the data containing the HPROF file, streamed from the VM
     * @param client the client that was profiled.
     */
    void onSuccess(byte[] data, Client client);

    /**
     * Called when a hprof dump failed to end on the VM side
     * @param client the client that was profiled.
     * @param message an optional (<code>null</code> ok) error message to be displayed.
     */
    void onEndFailure(Client client, String message);

}
