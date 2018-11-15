/*
 * Copyright (C) 2007 The Android Open Source Project
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
package org.eclipse.andmore.internal.build.builders;

import java.io.IOException;

import org.eclipse.andmore.internal.build.SourceProcessor;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.build.task.RenderscriptCompileTask;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.core.runtime.CoreException;

public class RenderScriptOp implements BuildOp<PreCompilerContext> {

    public RenderScriptOp() {
    }
    
	@Override
	public boolean execute(PreCompilerContext context) throws CoreException, InterruptedException {
		AndworxFactory.instance().getRenderscriptCompileTask(context.getVariantContext()).schedule();
        TaskFactory taskFactory = context.getTaskFactory();
    	synchronized(taskFactory) {
    		taskFactory.wait();
    	}
		return true;
	}
	
	@Override
	public void commit(PreCompilerContext context) throws IOException {
		context.addSourceProcessorStatus(SourceProcessor.COMPILE_STATUS_CODE | SourceProcessor.COMPILE_STATUS_RES);
	}

	@Override
	public String getDescription() {
		return RenderscriptCompileTask.TASK_NAME;
	}
	
	//private int compileRs(
    //		PreCompilerContext contex) {
        //if (!renderScriptSourceChangeHandler.mustCompile()) {
        //    return SourceProcessor.COMPILE_STATUS_NONE;
        //}

        //RenderScriptChecker checker = renderScriptSourceChangeHandler.getChecker();

        //List<File> inputs = checker.findInputFiles();
        //List<File> importFolders = checker.getSourceFolders();
        //File buildFolder = androidOutputFolder.getLocation().toFile();

        // clean old dependency files fiest
        //checker.cleanDependencies();

        // then clean old output files
        //processor.cleanOldOutput(checker.getOldOutputs());
    //    return SourceProcessor.COMPILE_STATUS_CODE | SourceProcessor.COMPILE_STATUS_RES;
    //}

}
