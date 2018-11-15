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

import java.util.logging.*;

/**
 * JavaLogger
 * Logger readily interchangeable with Android android.util.Log, implemented using java.util.logging.Logger
 * Mapping Android levels to Java levels:
 * VERBOSE = FINEST
 * DEBUG = FINE
 * INFO = INFO
 * WARN = WARNING
 * ERROR = SEVERE
 * @author Andrew Bowley
 * 11/06/2014
 */
public class JavaLogger implements Log
{
    /** Use java.util.logging package for actual log implementation */
    private Logger logger;
    /** Tag Used to identify the source of a log message */
    private String name;
    
    /**
     * Create JavaLogger object. Call static getLogger() to invoke constructor.
     * @param name Tag Used to identify the source of a log message
     */
    protected JavaLogger(String name)
    {
        this.name = name;
        logger = Logger.getLogger(name);
    }

    /**
     * JavaLogger class factory
     * @param name Tag Used to identify the source of a log message
     * @return JavaLogger
     */
    public static JavaLogger getLogger(String name)
    {
        return new JavaLogger(name);
    }
    
    /**
     * Send a VERBOSE log message. Level = FINEST.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    @Override
    public void verbose(String msg) 
    {
        logger.logp(Level.FINEST, name, null, msg);
    }

    /**
     * Send a VERBOSE log message and log the exception. Level = FINEST.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    @Override
    public void verbose(String msg, Throwable tr) 
    {
        logger.logp(Level.FINEST, name, null, msg, tr);
    }

    /**
     * Send a DEBUG log message. Level = FINE.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    @Override
    public void debug(String msg) 
    {
        logger.logp(Level.FINE, name, null, msg);
    }

    /**
     * Send a DEBUG log message and log the exception. Level = FINE.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    @Override
    public void debug(String msg, Throwable tr) 
    {
        logger.logp(Level.FINE, name, null, msg, tr);
    }

    /**
     * Send an INFO log message. Level = INFO. Level = INFO.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    @Override
    public void info(String msg) 
    {
        logger.logp(Level.INFO, name, null, msg);
    }

    /**
     * Send a INFO log message and log the exception. Level = INFO.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    @Override
    public void info(String msg, Throwable tr) 
    {
        logger.logp(Level.INFO, name, null, msg, tr);
    }

    /**
     * Send a WARN log message. Level = WARNING.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    @Override
    public void warn(String msg) 
    {
        logger.logp(Level.WARNING, name, null, msg);
    }

    /**
     * Send a #WARN log message and log the exception. Level = WARNING.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    @Override
    public void warn(String msg, Throwable tr) 
    {
        logger.logp(Level.WARNING, name, null, msg, tr);
    }

    /**
     * Send an ERROR log message. Level = SEVERE.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    @Override
    public void error(String msg) 
    {
        logger.logp(Level.SEVERE, name, null, msg);
    }

    /**
     * Send an ERROR log message and log the exception. Level = SEVERE.
     * @param name Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    @Override
    public void error(String msg, Throwable tr) 
    {
        logger.logp(Level.SEVERE, name, null, msg, tr);
    }

    /**
     * Checks to see whether or not a log for the specified name is loggable at the specified level.
     * 
     * NOTE IF USING Android Log implementation:    
     * Log.isLoggable() will throw an exception if the length of the name is greater than
     * 23 characters, so trim it if necessary to avoid the exception.
     *
     * @param name The name to check.
     * @param level The level to check.
     * @return Whether or not that this is allowed to be logged.
     */
    @Override
    public boolean isLoggable(Level level) 
    {
        return logger.isLoggable(level);
    }
    
    /**
     * Set logging level. 
     * NOTE IF USING Android Log implementation, this function is not supported natively by Android.
     */
    @Override
    public void setLevel(Level level) 
    {
        logger.setLevel(level);
    }

    /**
     * Get logger referenced by name. Handle mismatch of name to this logger's name gracefully.
     * @param tag Used to identify the source of a log message. 
     * @return Logger This logger if name matches name or name is empty, otherwise logger obtained by Logger.getLogger(name).
     */
    protected Logger logger(String tag)
    {
        if (name.equals(tag) || (name == null) || (name.length() == 0))
            return logger;
        return Logger.getLogger(tag);
    }


}
