package org.eclipse.andworx.functions;

import java.io.File;

import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.AndworxParserContext.Function;
import org.eclipse.andworx.project.AndworxParserContext.Variable;

public class Builtin {

	private final AndworxParserContext context;
	
	public Builtin(AndworxParserContext context) {
		this.context = context;
	}

	public Object invoke(Function function) {
		Variable variable = function.variable;
		switch (variable.name) {
		case "rootProject": 
			return new File(context.getRootProject(), function.arguments[0].toString()); 
	    default:
	    	throw new AndworxException("Unknown function: " + variable.name);
		}
	}
}
