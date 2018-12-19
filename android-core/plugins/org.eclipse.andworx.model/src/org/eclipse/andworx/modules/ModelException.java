package org.eclipse.andworx.modules;

import org.eclipse.andworx.exception.AndworxException;

public class ModelException extends AndworxException {

	private static final long serialVersionUID = 1L;

	public ModelException(String message, Throwable cause) {
		super(message, cause);
	}

	public ModelException(String message) {
		super(message);
	}

}
