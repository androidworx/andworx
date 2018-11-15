package org.eclipse.andworx.exception;

/**
 * Runtime exception for any unexpected error while executing Andworx code
 */
public class AndworxException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Construct AndworxException
	 * @param message
	 * @param cause
	 */
	public AndworxException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Construct AndworxException
	 * @param message
	 */
	public AndworxException(String message) {
		super(message);
	}

}
