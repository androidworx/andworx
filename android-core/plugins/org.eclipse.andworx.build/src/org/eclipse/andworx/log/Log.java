/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not FilenameFilter filter this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
  http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.log;

import java.util.logging.Level;

/**
 * Log
 * Provides a Java util logging interface similar to that of Android log.
 * This is to ease replacement of Java logging with Android logging if desired. See android.util.Log
 * @author Andrew Bowley
 * 11/06/2014
 */
public interface Log
{
    /**
     * Send a VERBOSE log message. Level = FINEST.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    void verbose(String msg);

    /**
     * Send a VERBOSE log message and log the exception. Level = FINEST.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    void verbose(String msg, Throwable tr);

    /**
     * Send a DEBUG log message. Level = FINE.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    void debug(String msg);

    /**
     * Send a DEBUG log message and log the exception. Level = FINE.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    void debug(String msg, Throwable tr);

    /**
     * Send an INFO log message. Level = INFO. Level = INFO.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    void info(String msg);

    /**
     * Send a INFO log message and log the exception. Level = INFO.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    void info(String msg, Throwable tr);

    /**
     * Send a WARN log message. Level = WARNING.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    void warn(String msg);

    /**
     * Send a #WARN log message and log the exception. Level = WARNING.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    void warn(String msg, Throwable tr);
    
    /**
     * Send an ERROR log message. Level = SEVERE.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    void error(String msg);

    /**
     * Send an ERROR log message and log the exception. Level = SEVERE.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    void error(String msg, Throwable tr);
    
    /**
     * Checks to see whether or not a log for the specified tag is loggable at the specified level.
     * 
     * NOTE IF USING Android Log implementation:    
     * Log.isLoggable() will throw an exception if the length of the tag is greater than
     * 23 characters, so trim it if necessary to avoid the exception.
     *
     * @param tag The tag to check.
     * @param level The level to check.
     * @return Whether or not that this is allowed to be logged.
     */
    boolean isLoggable(Level level);
    
    /**
     * Set logging level. 
     * NOTE IF USING Android Log implementation, this function is not supported natively by Android.
     */
    void setLevel(Level level);
}
