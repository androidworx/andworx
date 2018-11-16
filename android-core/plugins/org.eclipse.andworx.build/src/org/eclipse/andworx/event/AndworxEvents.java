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
package org.eclipse.andworx.event;

/**
 * Message types applied to Eclipse Event Broker.
 */
public class AndworxEvents {
    /** Use package name base for global uniqueness */
    public static final String  TOPIC_BASE = "org/eclipse/andworx";
    /** Andworx started */
    public static final String  ANDWORX_STARTED = TOPIC_BASE + "/andworx-started";
    /** ADB Started */
    public static final String  ADB_STARTED = TOPIC_BASE + "/adb-started";
    /** Andworx started with no SDK configured */
    public static final String  INSTALL_SDK_REQUEST = TOPIC_BASE + "/install-sdk";
    /** SDK loaded */
    public static final String  SDK_LOADED = TOPIC_BASE + "/sdk-loaded";
    /** Device Debugger Connector request */
    public static final String  DEVICE_DEBUG_REQUEST = TOPIC_BASE + "/device-debug-request";
    /** Device Debugger Connector request */
    public static final String  DEBUG_CONNECTOR_HANDLER = TOPIC_BASE + "/debug-connector-handler";
    /** Device connected event */
    public static final String  DEVICE_CONNECTED = TOPIC_BASE + "/device-connected";
    /** Device disconnected event */
    public static final String  DEVICE_DISCONNECTED = TOPIC_BASE + "/device-disconnected";
    /** Device state change event */
    public static final String  DEVICE_STATE_CHANGE = TOPIC_BASE + "/device-state-change";
    /** Device client debug change event */
    public static final String  CHANGE_DEBUGGER_STATUS = TOPIC_BASE + "/client-debug-change";
    /** Device client debug change event */
    public static final String  CHANGE_CLIENT_NAME = TOPIC_BASE + "/client-name-change";
}
