package org.eclipse.andworx.maven;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.andworx.log.SdkLogger;

import com.google.common.base.Throwables;

/**
 * Maven log adapter
 */
public class MavenLog implements Log, Logger {

	/** Android SDK logger */
	private final SdkLogger logger;
	/** Logger identifier */
	private String tag;

	/**
	 * Construct MavenLog object
	 * @param tag Logger identifier
	 */
	public MavenLog(String tag) {
		this.tag = tag;
		logger = SdkLogger.getLogger(tag);
	}
	
	@Override
	public String getName() {
		return tag;
	}

	@Override
	public boolean isDebugEnabled() {
		return true;
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void debug(CharSequence message) {
		logger.verbose(message.toString());
	}

	@Override
	public void debug(Throwable throwable) {
		logger.verbose(Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void debug(CharSequence message, Throwable throwable) {
		logger.verbose(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void info(CharSequence message) {
		logger.info(message.toString());
	}

	@Override
	public void info(Throwable throwable) {
		logger.info(Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void info(CharSequence message, Throwable throwable) {
		logger.info(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void warn(CharSequence message) {
		logger.warning(message.toString());
	}

	@Override
	public void warn(Throwable throwable) {
		logger.warning(Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void warn(CharSequence message, Throwable throwable) {
		logger.warning(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}
	@Override
	public void error(CharSequence message) {
		logger.error(null, message.toString());
	}

	@Override
	public void error(Throwable throwable) {
		logger.error(throwable, "");
	}

	@Override
	public void error(CharSequence message, Throwable throwable) {
		logger.error(throwable, message.toString());
	}

	@Override
	public void debug(String message) {
		logger.verbose(message.toString());
	}

	@Override
	public void debug(String message, Throwable throwable) {
		logger.verbose(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void info(String message) {
		logger.info(message.toString());
	}

	@Override
	public void info(String message, Throwable throwable) {
		logger.info(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}

	@Override
	public void warn(String message) {
		logger.warning(message.toString());
	}

	@Override
	public void warn(String message, Throwable throwable) {
		logger.warning(message + "\n" + Throwables.getStackTraceAsString(throwable));
	}
	@Override
	public void error(String message) {
		logger.error(null, message.toString());
	}

	@Override
	public void error(String message, Throwable throwable) {
		logger.error(throwable, message);
	}

	@Override
	public void fatalError(String message) {
		error(message);
	}

	@Override
	public void fatalError(String message, Throwable throwable) {
		error(message, throwable);
	}

	@Override
	public Logger getChildLogger(String arg0) {
		return null;
	}

	@Override
	public int getThreshold() {
        if (isErrorEnabled())
        {
            return Logger.LEVEL_ERROR;
        }
        else if (isWarnEnabled())
        {
            return Logger.LEVEL_WARN;
        }
        else if (isInfoEnabled())
        {
            return Logger.LEVEL_INFO;
        }
        return Logger.LEVEL_DEBUG;
	}

	@Override
	public boolean isFatalErrorEnabled() {
		return isErrorEnabled();
	}

	@Override
	public void setThreshold(int arg0) {
	}



}
