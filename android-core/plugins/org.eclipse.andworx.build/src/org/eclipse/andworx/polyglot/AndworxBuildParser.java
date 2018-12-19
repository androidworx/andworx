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
package org.eclipse.andworx.polyglot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.eclipse.andworx.helper.CommentStrip;
import org.eclipse.andworx.project.AndroidDigest;
import org.eclipse.andworx.project.AndworxParserContext;
import org.eclipse.andworx.project.AndworxParserContext.Variable;
import org.eclipse.andworx.project.SyntaxItemReceiver;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Parses Andworx configuration file using Groovy AST parser
 * TODO - Complete support for Groovy scripting
 */
public class AndworxBuildParser {

	/** Bind to Groovy parser using visitor pattern */
	private class CodeVisitor extends CodeVisitorSupport {
		/** Analysis is founded on tracking method calls */
        private final List<MethodCallExpression> methodCallStack = new ArrayList<>();

        @Override
        public void visitMethodCallExpression(MethodCallExpression expression) {
            methodCallStack.add(expression);
            super.visitMethodCallExpression(expression);
            assert !methodCallStack.isEmpty();
            assert methodCallStack.get(methodCallStack.size() - 1) == expression;
            methodCallStack.remove(methodCallStack.size() - 1);
        }

        @Override
        public void visitTupleExpression(TupleExpression tupleExpression) {
            if (!methodCallStack.isEmpty()) {
        	    actionTuple(tupleExpression, methodCallStack);
            }
            super.visitTupleExpression(tupleExpression);
        }
        
        @Override
        public void visitDeclarationExpression(DeclarationExpression expression) {
        	//System.out.println(expression.getText());
        	actionDeclaration(expression);
        	super.visitDeclarationExpression(expression);
        }

	}

	/** Handles content extracted from a configuration file by a parser */
	private final AndworxBuildReceiver receiver;
	/** Assembles declarations embedded in the build file */
	private final AndworxBuildAssembler assembler;
	private final AndworxParserContext context;
	private SyntaxItemReceiver chainSyntaxItemReceiver;

	/**
	 * Construct AndworxBuildParser object
	 * @param buildFile Input file
	 * @param receiver Handles content extracted from a configuration file by a parser
	 */
	public AndworxBuildParser(AndworxParserContext context, AndworxBuildReceiver receiver) {
		this.context = context;
		this.receiver = receiver;
		assembler = new AndworxBuildAssembler();
	}

	/**
	 * Parser entry point
	 * @throws IOException
	 */
	public void parse(File buildFile) throws IOException {
		receiver.setProjectLocation(buildFile.getParentFile());
		// Read input file into String object, stripping out comments in the process
        CommentStrip commentStrip = new CommentStrip();
        String fileContents = commentStrip.contentCommentStrip(buildFile);
        // Generate list of AST nodes
        List<ASTNode> astNodes = new AstBuilder().buildFromString(fileContents);
        // Walk nodes
	    final GroovyCodeVisitor visitor = new CodeVisitor();
        for (ASTNode node : astNodes) {
            node.visit(visitor);
        }
	}

	public AndroidDigest getAndroidDigest() {
		return receiver;
	}

	public void setChainSyntaxItemReceiver(SyntaxItemReceiver chainSyntaxItemReceiver) {
		this.chainSyntaxItemReceiver = chainSyntaxItemReceiver;
	}

	/**
	 * Action tuple
	 * @param tupleExpression TupleExpression obje3ct
	 * @param methodCallStack MethodCallExpression list
	 */
    private void actionTuple(
    		TupleExpression tupleExpression, 
    		List<MethodCallExpression> methodCallStack) {
        MethodCallExpression call = methodCallStack.get(methodCallStack.size() - 1);
        if (call.getArguments() == tupleExpression) {
            String parent = call.getMethodAsString();
            String parentParent = getParentParent(methodCallStack);
            if (tupleExpression instanceof ArgumentListExpression) {
            	boolean isInterestingblock = isInterestingBlock(parent, parentParent);
	            if (isInterestingblock) {
		            if (tupleExpression instanceof ArgumentListExpression) {
		            	actionArgumentList(tupleExpression, parent, parentParent);
		            }
	            }
            } else {
                if (isInterestingStatement(parent, parentParent)) {
                	/*
                    Map<String, String> namedArguments = new HashMap<>();
                    List<String> unnamedArguments = new ArrayList<>();
                    for (Expression subExpr : tupleExpression.getExpressions()) {
                        if (subExpr instanceof NamedArgumentListExpression) {
                            NamedArgumentListExpression nale = (NamedArgumentListExpression) subExpr;
                            for (MapEntryExpression mae : nale.getMapEntryExpressions()) {
                                namedArguments.put(mae.getKeyExpression().getText(),
                                        mae.getValueExpression().getText());
                            }
                        }
                    }
                    checkMethodCall(context, parent, parentParent, namedArguments, unnamedArguments, call);
                    */
                }
            } 
        } else if (call.getArguments() instanceof ArgumentListExpression) 
         	invokeCall(call);
        	//System.out.println(call.getArguments().getClass().getSimpleName());
	}

    protected static boolean isInterestingStatement(
            @NonNull String statement,
            @Nullable String parent) {
        return parent == null && statement.equals("apply");
    }

	/**
	 * Action argument list
	 * @param tupleExpression TupleExpression object
	 * @param parent Parent name
	 * @param parentParent Grandparent name
	 */
	private void actionArgumentList(
    		TupleExpression tupleExpression, 
    		String parent, 
    		String parentParent) {
        ArgumentListExpression ale = (ArgumentListExpression)tupleExpression;
        List<Expression> expressions = ale.getExpressions();
        if ((expressions.size() == 1) &&
            (expressions.get(0) instanceof ClosureExpression))
        {
            ClosureExpression closureExpression =
                    (ClosureExpression)expressions.get(0);
            Statement block = closureExpression.getCode();
            if (block instanceof BlockStatement) {
                BlockStatement bs = (BlockStatement)block;
                for (Statement statement : bs.getStatements()) {
                    if (statement instanceof ExpressionStatement) {
                        ExpressionStatement e = (ExpressionStatement)statement;
                        if (e.getExpression() instanceof MethodCallExpression) {
                        	analyseMethodCall(parent, (MethodCallExpression) e.getExpression());
                        } else 
                        	System.out.println(e.getExpression().getClass().getSimpleName());
                    } else if (statement instanceof ReturnStatement) {
                        // Single item in block
                        ReturnStatement e = (ReturnStatement)statement;
                        if (e.getExpression() instanceof MethodCallExpression) {
                        	//System.out.println(getText(fileContents, e));
                        	analyseMethodCall(parent, (MethodCallExpression) e.getExpression());
                        } else 
                        	System.out.println(e.getExpression().getClass().getSimpleName());
                    } else 
                    	System.out.println(statement.getClass().getSimpleName());
                }
            }
        }  else if ((expressions.size() == 1) &&
                    (expressions.get(0) instanceof ConstructorCallExpression))
            {
        	    ConstructorCallExpression constructorCall = (ConstructorCallExpression)expressions.get(0);
        	    invokeConstructor(constructorCall, parent);
             }

    }

    /**
     * Invoke method call
     * @param call MethodCallExpression object
     */
	private void invokeCall(MethodCallExpression call) {
	    Class<?> clazz = call.getType().getTypeClass();
	    String type = call.getType().getName();
	    context.setType(type, clazz);
	    ArgumentListExpression argList = (ArgumentListExpression)call.getArguments();
	    Object[] arguments = evaluate(argList.getExpressions());
	    assembler.invokeMethod(context, type, call.getObjectExpression().getText(), call.getMethodAsString(), arguments);
    }

	/**
	 * Analyse constructor call
	 * @param constructorCall ConstructorCallExpression object
	 * @param parent Parent name
	 */
    private void invokeConstructor(ConstructorCallExpression constructorCall, String parent) {
	    if (constructorCall.isSuperCall() || constructorCall.isThisCall())
	    	return; // Not new 
	    Class<?> clazz = constructorCall.getType().getTypeClass();
	    String type = constructorCall.getType().getName();
	    context.setType(type, clazz);
	    ArgumentListExpression argList = (ArgumentListExpression)constructorCall.getArguments();
	    Object[] arguments = evaluate(argList.getExpressions());
	    assembler.invokeMethod(context, type, "", constructorCall.getMethodAsString(), arguments);
	}

    /**
     * Action variable method
     * @param variable VariableExpression object to receive result
     * @param call MethodCallExpression object
     */
	private void actionVariableMethod(VariableExpression variable, MethodCallExpression call) {
	    String variableName = variable.getName();
	    Variable newVariable = context.variableInstance(variableName);
	    newVariable.value = evaluateMethodCall(call);
	}

	/**
	 * Return arguments in an array
	 * @param expressions Argument list
	 * @return Object array
	 */
    private Object[] evaluate(List<Expression> expressions) {
    	if (expressions.size() == 0)
    		return new Object[] {};
    	List<Object> returnList = new ArrayList<Object>();
    	for (Expression expression: expressions) {
    		if (expression instanceof VariableExpression) {
    			VariableExpression varExpr = (VariableExpression)expression;
    			Variable variable = context.getVariable(varExpr.getName());
    			if (variable != null)
    				returnList.add(variable.value);
    			else
    				returnList.add(null);
    		}
    		else if (expression instanceof MethodCallExpression) 
    	    	returnList.add(evaluateMethodCall((MethodCallExpression)expression));
    	    else if (expression instanceof ConstructorCallExpression) 
    	    	returnList.add(evaluateConstructorCall((ConstructorCallExpression)expression));
    	    else
    	    	returnList.add(expressions.get(0).getText());
    	}
		return returnList.toArray(new Object[returnList.size()]);
	}

    /**
     * Invoke method call
     * @param call MethodCallExpression object
     */
	private Object evaluateMethodCall(MethodCallExpression call) {
	    Class<?> clazz = call.getType().getTypeClass();
	    String type = call.getType().getName();
	    context.setType(type, clazz);
	    ArgumentListExpression argList = (ArgumentListExpression)call.getArguments();
	    Object[] arguments = evaluate(argList.getExpressions());
	    return assembler.invokeMethod(context, type, call.getObjectExpression().getText(), call.getMethodAsString(), arguments);
	}
	
	/**
     * Action variable method
     * @param call ConstructorCallExpression object
     * @param variable VariableExpression object
     */
	private void actionVariableMethod(ConstructorCallExpression call, VariableExpression variable) {
	    String variableName = variable.getName();
	    Variable newVariable = context.variableInstance(variableName);
	    newVariable.value = evaluateConstructorCall(call);
	}

	/**
     * Invoke constructor
     * @param call ConstructorCallExpression object
      */
	private Object evaluateConstructorCall(ConstructorCallExpression call) {
	    Class<?> clazz = call.getType().getTypeClass();
	    String type = call.getType().getName();
	    context.setType(type, clazz);
	    ArgumentListExpression argList = (ArgumentListExpression)call.getArguments();
	    Object[] arguments = evaluate(argList.getExpressions());
	    return assembler.invokeMethod(context, type, "", call.getMethodAsString(), arguments);
	}

	/**
	 * Action declaration
	 * @param expression DeclarationExpression object
	 */
	private void actionDeclaration(DeclarationExpression expression) {
		if (expression.isMultipleAssignmentDeclaration())
			return; // eg. def (x, y) = ..." not supported 
	    VariableExpression variable = expression.getVariableExpression();
	    String variableName = variable.getName();
	    Expression right = expression.getRightExpression();
		context.variableInstance(variableName);
	    if (right instanceof MethodCallExpression) {
	    	MethodCallExpression methodCall = (MethodCallExpression)right;
	    	actionVariableMethod(variable, methodCall);
	    } else if (right instanceof ConstructorCallExpression) {
	    	ConstructorCallExpression methodCall = (ConstructorCallExpression)right;
	    	actionVariableMethod(methodCall, variable);
	    } else {
	    	analyseExpression(expression.getRightExpression(), variableName);
	    }
	}
 
	/**
	 * Analyse method call
	 * @param parent Parent name
	 * @param e MethodCallExpression object
	 */
	private void analyseMethodCall(String parent, MethodCallExpression e) {
		String method = e.getMethodAsString();
		if (method == null) // Null method means it is dynamically calculated. This is not expected
			return;
		Expression arguments = e.getArguments();
        if (arguments instanceof ClosureExpression) {
            ClosureExpression closureExpression = (ClosureExpression)arguments;
            analyseClosure(closureExpression, parent);
        } else if (arguments instanceof ArgumentListExpression) {
        	ArgumentListExpression argListExpression = (ArgumentListExpression)arguments;
        	List<Expression> expressions = argListExpression.getExpressions();
        	if (expressions.isEmpty())
             	receiveItem(parent + "/" + method, "");
        	else for (Expression expression : expressions)
        		analyseExpression(expression, parent + "/" + method);
         } else if (arguments instanceof TupleExpression) {
        	List<Expression> expressions = ((TupleExpression) arguments).getExpressions();
        	for (Expression expression : expressions)
        		analyseExpression(expression, parent + "/" + method);
        } else if (arguments instanceof  BinaryExpression) {
        	analyseBinary(parent, (BinaryExpression)arguments);
        } else if (arguments instanceof  NamedArgumentListExpression) {
        	analyseNamedArgumentList(parent, (NamedArgumentListExpression)arguments);
        } else 
        	System.out.println(arguments.getClass().getSimpleName());
		//String argsText = arguments.getText();
        //if (!argsText.isEmpty())
        //	System.out.println(parent + "/" + method + "(" + argsText.substring(1, argsText.length() -1) + ")");
		
	}

	/**
	 * Analyse expression
	 * @param expression Expression object
	 * @param parent Parent name
	 */
	private void analyseExpression(Expression expression, String parent) {
        if (expression instanceof ClosureExpression) {
            ClosureExpression closureExpression = (ClosureExpression)expression;
            analyseClosure(closureExpression, parent);
        } else if (expression instanceof MethodCallExpression) {
        	analyseMethodCall(parent, (MethodCallExpression) expression);
        } else if (expression instanceof  BinaryExpression) {
        	analyseBinary(parent, (BinaryExpression)expression);
        } else if (expression instanceof  NamedArgumentListExpression) {
        	analyseNamedArgumentList(parent, (NamedArgumentListExpression)expression);
        } else if (expression instanceof ArgumentListExpression) {
        	ArgumentListExpression argListExpression = (ArgumentListExpression)expression;
            for (Expression arg : argListExpression.getExpressions())
        		analyseExpression(arg, parent);
         }  else if (expression instanceof ConstructorCallExpression) {
        	 invokeConstructor((ConstructorCallExpression)expression, parent);
         } else {
    		String argsText = expression.getText();
            if (!argsText.isEmpty())
             	receiveItem(parent, argsText);
       }
	}

	/**
	 * Analyse closure
	 * @param closure ClosureExpression object
	 * @param parent parent name
	 */
	private void analyseClosure(ClosureExpression closure, String parent) {
        Statement block = closure.getCode();
        if (block instanceof BlockStatement) {
            BlockStatement bs = (BlockStatement)block;
            for (Statement statement : bs.getStatements()) {
                if (statement instanceof ExpressionStatement) {
                    ExpressionStatement s = (ExpressionStatement)statement;
                    if (s.getExpression() instanceof MethodCallExpression) {
                    	analyseMethodCall(parent, (MethodCallExpression) s.getExpression());
                    } else if (s.getExpression() instanceof BinaryExpression) {
                    	analyseBinary(parent, (BinaryExpression) s.getExpression());
                    } else {
                		String argsText = s.getExpression().getText();
                        if (!argsText.isEmpty())
                        	receiveItem(parent, argsText);
                    }
                } else if (statement instanceof ReturnStatement) {
                    // Single item in block
                    ReturnStatement s = (ReturnStatement)statement;
                    if (s.getExpression() instanceof MethodCallExpression) {
                    	analyseMethodCall(parent, (MethodCallExpression) s.getExpression());
                    } else if (s.getExpression() instanceof BinaryExpression) {
                    	analyseBinary(parent, (BinaryExpression) s.getExpression());
                    } else {
                		String argsText = s.getExpression().getText();
                        if (!argsText.isEmpty())
                        	receiveItem(parent, argsText);
                    }
                } else 
                	System.out.println(statement.getClass().getSimpleName());
            }
        }
	}

	/**
	 * Analyse named argument list
	 * @param parent Parent name
	 * @param namedArguments NamedArgumentListExpression object
	 */
	private void analyseNamedArgumentList(String parent, NamedArgumentListExpression namedArguments) {
		List<MapEntryExpression> mapEntryExpressions = namedArguments.getMapEntryExpressions();
        for (MapEntryExpression expression : mapEntryExpressions) {
           	receiveItem(parent, expression.getKeyExpression().getText(), expression.getValueExpression().getText());
        }
	}

	/**
	 * Analyse binary expression
	 * @param parent Parent name
	 * @param expression BinaryExpression object
	 */
	private void analyseBinary(String parent, BinaryExpression expression) {
		Token operation = expression.getOperation();
		Expression leftExpression = expression.getLeftExpression();
		Expression rightExpression = expression.getRightExpression();
        if (operation.getType() == Types.LEFT_SQUARE_BRACKET) 
        	receiveItem(parent, assembler.receiveMap(context, leftExpression.getText(), rightExpression.getText()));
        	//analyseExpression(rightExpression, parent + "/" + leftExpression.getText());
        	//receiveItem(parent, leftExpression.getText(), rightExpression.getText());
            //System.out.println(parent + "(" + leftExpression.getText() + "[" + rightExpression.getText() + "]" + ")");
        else
        	receiver.receiveItem(context, parent, leftExpression.getText(), operation.getText(), rightExpression.getText());
            //System.out.println(parent + "(" + leftExpression.getText() + " " + operation.getText() + " " + rightExpression.getText() + ")");
 	}

	/**
	 * Handle value
	 * @param path
	 * @param value
	 */
	private void receiveItem(String path, String value) {
		receiver.receiveItem(context, path, value);
		if (chainSyntaxItemReceiver != null)
			chainSyntaxItemReceiver.receiveItem(context, path, value);
	}

	/**
	 * Handle property
	 * @param path
	 * @param key
	 * @param value
	 */
	private void receiveItem(String path, String key, String value) {
		receiver.receiveItem(context, path, key, value);
		if (chainSyntaxItemReceiver != null)
			chainSyntaxItemReceiver.receiveItem(context, path, key, value);
	}

	/**
	 * Determine if top-level configuration block should be analysed
	 * @param parent Parent name
	 * @param parentParent Garndparent name
	 * @return boolean
	 */
	private static boolean isInterestingBlock(
            @NonNull String parent,
            @Nullable String parentParent) {
        switch (parent) {
            case "defaultConfig":
                return parentParent == null;
            case "android":
            case "project":
            case "buildscript":
            case "allprojects":
                return true;
            case "identity":
            	return "project".equals(parentParent);
            case "dependencies":
            case "repositories":
                return !"buildscript".equals(parentParent);
            case "dev":
                return "productFlavors".equals(parentParent);
            default:
            	return false;
        }
    }

	/**
	 * Returns grandparent name for given method call stack
	 * @param methodCallStack MethodCallExpression list
	 * @return name
	 */
	private static String getParentParent(List<MethodCallExpression> methodCallStack) {
        for (int i = methodCallStack.size() - 2; i >= 0; i--) {
            MethodCallExpression expression = methodCallStack.get(i);
            Expression arguments = expression.getArguments();
            if (arguments instanceof ArgumentListExpression) {
                ArgumentListExpression ale = (ArgumentListExpression)arguments;
                List<Expression> expressions = ale.getExpressions();
                if (expressions.size() == 1 &&
                        expressions.get(0) instanceof ClosureExpression) {
                    return expression.getMethodAsString();
                }
            }
        }
        return null;
    }
}
