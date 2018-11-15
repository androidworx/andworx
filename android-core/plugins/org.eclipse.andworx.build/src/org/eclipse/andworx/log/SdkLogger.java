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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.base.Throwables;

import java.util.Formatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

/**
 * SdkLogger
 * Logger to display warnings/errors while parsing the SDK content, implemented using java.util.logging.Logger
 * Mapping Android levels to Java levels:
 * VERBOSE = FINEST
 * INFO = INFO
 * WARNING = WARNING
 * ERROR = SEVERE
 * @author Andrew Bowley
 * 11/06/2014
 */
public class SdkLogger implements ILogger {

	private static class SdkLogManager {

		public SdkLogManager() {
			logLevelMap = new ConcurrentHashMap<>();
		}
		
	    /** Logging levels */
	    private ConcurrentHashMap<String,Level> logLevelMap;

	    /**
	     * SdkLogger class factory
	     * @param name Tag Used to identify the source of a log message
	     * @return SdkLogger
	     */
	    public SdkLogger getLogger(String name)
	    {
	        return new SdkLogger(name);
	    }

	    public void setLevel(String name, Level level) {
	    	logLevelMap.put(name, level);
	    	Logger javaLogger = LogManager.getLogManager().getLogger(name);
	    	if (javaLogger !=  null) {
	    		javaLogger.setLevel(level);
            }
	    }

		public void setLevel(Logger logger) {
			Level level = logLevelMap.get(logger.getName());
			if (level != null)
				logger.setLevel(level);
		}
	}

	private static class LogManagerHolder {

		private volatile SdkLogManager sdkLogManager = null;

		public SdkLogManager getSdkLogManager() {
	        if (sdkLogManager == null) {
	            synchronized(this) {
	                if (sdkLogManager == null) {
	                	sdkLogManager = new SdkLogManager();
	                }
	            }
	        }
	        return sdkLogManager;
		}
	}

	private static LogManagerHolder logManagerHolder;
    /** Use java.util.logging package for actual log implementation */
    private Logger logger;
    /** Tag Used to identify the source of a log message */
    private final String name;

    static {
    	logManagerHolder = new LogManagerHolder();
    }
    
    
    /**
     * Create SdkLogger object. Call static getLogger() to invoke constructor.
     * @param name Tag Used to identify the source of a log message
     */
    public SdkLogger(String name) {
        this.name = name;
        logger = Logger.getLogger(name);
        logManagerHolder.getSdkLogManager().setLevel(logger);
	}

    /**
     * SdkLogger class factory
     * @param name Tag Used to identify the source of a log message
     * @return SdkLogger
     */
    public static SdkLogger getLogger(String name)
    {
    	return logManagerHolder.getSdkLogManager().getLogger(name);
    }

    public static void setLevel(String name, Level level) {
    	logManagerHolder.getSdkLogManager().setLevel(name, level);
    }
    
    public String getName() {
    	return name;
    }
    
    /**
     * Prints an error message. Level = SEVERE.
     *
     * @param t is an optional {@link Throwable} or {@link Exception}. If non-null, its
     *          message will be printed out.
     * @param msgFormat is an optional error format. If non-null, it will be printed
     *          using a {@link Formatter} with the provided arguments.
     * @param args provides the arguments for errorFormat.
     */
	@Override
	public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
		String msg = String.format(msgFormat, args);
		if (msgFormat != null)
			System.err.println(String.format(msgFormat, args));
		if (t != null)
			System.err.println(Throwables.getStackTraceAsString(t));
        logger.logp(Level.SEVERE, name, null, msg, t);
	}

    /**
     * Prints a warning message.
     *
     * @param msgFormat is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for warningFormat.
     */
	@Override
	public void warning(@NonNull String msgFormat, Object... args) {
		String msg = String.format(msgFormat, args);
        logger.logp(Level.WARNING, name, null, msg);
	}

    /**
     * Prints an information message.
     *
     * @param msgFormat is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for msgFormat.
     */
	@Override
	public void info(String msgFormat, Object... args) {
		String msg = String.format(msgFormat, args);
        logger.logp(Level.INFO, name, null, msg);
	}

    /**
     * Prints a verbose message.
     *
     * @param msgFormat is a string format to be used with a {@link Formatter}. Cannot be null.
     * @param args provides the arguments for msgFormat.
     */
	@Override
	public void verbose(@NonNull String msgFormat, Object... args) {
		String msg = String.format(msgFormat, args);
        logger.logp(Level.FINEST, name, null, msg);
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
     public boolean isLoggable(Level level) 
    {
        return logger.isLoggable(level);
    }
    
    /**
     * Set logging level. 
     */
     public void setLevel(Level level) 
    {
        logger.setLevel(level);
    }

}
