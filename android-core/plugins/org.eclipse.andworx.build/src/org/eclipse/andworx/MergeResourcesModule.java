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
package org.eclipse.andworx;

import java.io.File;
import java.io.IOException;

import org.eclipse.andworx.aapt.Aapt2Executor;
import org.eclipse.andworx.aapt.MergedResourceProcessor;
import org.eclipse.andworx.build.AndworxMessageReceiver;
import org.eclipse.andworx.build.task.MergeResourcesTask;
import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.exception.AndworxException;
import org.eclipse.andworx.helper.BuildHelper;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.TaskFactory;

import com.android.ide.common.blame.MergingLog;
import com.android.ide.common.blame.MergingLogRewriter;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.blame.parser.aapt.Aapt2OutputParser;
import com.android.ide.common.process.ProcessOutputHandler;

import dagger.Module;
import dagger.Provides;

@Module
public class MergeResourcesModule {

	private static SdkLogger logger = SdkLogger.getLogger(MergeResourcesModule.class.getName());
	
	private final VariantContext variantScope;
	
	public MergeResourcesModule(VariantContext variantScope) {
		this.variantScope = variantScope;
	}

	@Provides 
	MergeResourcesTask provideMergeResourcesTask(Aapt2Executor aapt2Executor, BuildHelper buildHelper, TaskFactory taskFactory) {
		MergedResourceProcessor mergedResourceProcessor = new MergedResourceProcessor();
		// Set limit of 20 seconds for completion
		mergedResourceProcessor.setTimeoutSeconds(AndworxConstants.MERGED_RESOURCES_PROCESSOR_TIMEOUT);
		File blameLogFolder = variantScope.getResourceBlameLogDir();
		try {
			buildHelper.prepareDir(blameLogFolder);
		} 
	    catch (IOException e) {
	    	throw new AndworxException(e.getMessage(), e);
	    }
        /** Stores where file and text fragments within files came from */
		MergingLog mergingLog = new MergingLog(blameLogFolder);
		aapt2Executor.setDelegate(createProcessOutputHandler(mergingLog));
		return new MergeResourcesTask(variantScope, aapt2Executor, mergedResourceProcessor, mergingLog, buildHelper, taskFactory);
	}

    private ProcessOutputHandler createProcessOutputHandler(MergingLog blameLog) {
		return new ParsingProcessOutputHandler(
                new ToolOutputParser(new Aapt2OutputParser(), logger),
                new MergingLogRewriter(blameLog::find, new AndworxMessageReceiver("aapt2")));
	}
}
