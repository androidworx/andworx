/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.andworx.aapt;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.andworx.log.SdkLogger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.AbstractAapt;
import com.android.builder.internal.aapt.BlockingResourceLinker;
import com.android.builder.internal.aapt.v2.Aapt2Exception;
import com.android.builder.internal.aapt.v2.Aapt2QueuedResourceProcessor;
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions;
import com.android.ide.common.internal.ResourceCompilationException;
import com.android.ide.common.internal.ResourceProcessor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.res2.CompileResourceRequest;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Creates a new entry point to the original {@code aapt2}.
 * Implementation of {@link com.android.builder.internal.aapt.Aapt} that uses out-of-process
 * execution of {@code aapt2}. It queues request and uses a pool of AAPT2 server/daemon processes to
 * serve them. The reason for re-creating the class is that the original only has package scope.
 */
public class Aapt2Executor extends AbstractAapt {
	
	/** Maximum time that excess idle threads will wait for new tasks before terminating */
    private static final long AUTO_THREAD_SHUTDOWN_MS = 250;
    private static SdkLogger logger = SdkLogger.getLogger(Aapt2Executor.class.getName());

	private static class ProxyProcessOutputHandler implements ProcessOutputHandler {

		private ProcessOutputHandler delegate;

		public ProxyProcessOutputHandler(ProcessOutputHandler delegate) {
			this.delegate = delegate;
		}
		
		public void setDelegate(ProcessOutputHandler delegate) {
			this.delegate = delegate;
		}

		@Override
		public ProcessOutput createOutput() {
			if (delegate != null)
				return delegate.createOutput();
			return new ProcessOutput() {

				@Override
				public void close() throws IOException {
				}

				@Override
				public OutputStream getStandardOutput() {
					return System.out;
				}

				@Override
				public OutputStream getErrorOutput() {
					return System.err;
				}};
		}

		@Override
		public void handleOutput(ProcessOutput processOutput) throws ProcessException {
			if (delegate != null)
				delegate.handleOutput(processOutput);
		}
		

	}

	public static class Builder {
        private final BuildToolInfo buildToolInfo; 
 
    	public Builder(BuildToolInfo buildToolInfo) {
    		this.buildToolInfo = buildToolInfo;
    	}
    	
	    public Aapt2Executor build() {
	         return new Aapt2Executor(
	                        	null, // Use default output process handler until a delegate is assigned
	                            buildToolInfo,
	                            logger,
	                            0 /* use default */);
	    }

}

	/** {@link ResourceProcessor} that queues requests and serves them using a pool of aapt2 server processes */
    @NonNull 
    private final Aapt2QueuedResourceProcessor aapt;
    /** An object that executes submitted {@link Runnable} tasks */
    @NonNull 
    private final Executor executor;
    /** Identity assigned to an AAPT processing session */
    @NonNull 
    private final Integer requestKey;
    /**  Handler for the Process output */
    @Nullable 
    private final ProxyProcessOutputHandler processOutputHandler;

    /**
     * Construct an Aapt2Executor object. 
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo Build tools information
     * @param logger Android SDK logger
     * @param numberOfProcesses Number of concurrent AAPT processes. 0 = default.
      */
	public Aapt2Executor(
			ProcessOutputHandler processOutputHandler, 
			BuildToolInfo buildToolInfo, 
			ILogger logger,
			int numberOfProcesses) {
        this.processOutputHandler = new ProxyProcessOutputHandler(processOutputHandler);
        this.executor =
                new ThreadPoolExecutor(
                        0, // Core threads
                        1, // Maximum threads
                        AUTO_THREAD_SHUTDOWN_MS,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>());
        this.aapt =
                Aapt2QueuedResourceProcessor.builder()
                        .executablePath(getAapt2ExecutablePath(buildToolInfo))
                        .logger(logger)
                        .numberOfProcesses(numberOfProcesses)
                        .build();

        requestKey = aapt.start();
	}

	public void setDelegate(ProcessOutputHandler delegate) {
		processOutputHandler.setDelegate(delegate);
	}
	
    /**
     * Produces an optional output file for an input file. Not all files are compilable. An
     * individual resource compiler will know if a file is compilable or not.
     *
     * @return a future for the output file, which may be produced asynchronously; if the future is
     *     computed as {@code null}, then the file is not compilable; this future may hol an
     *     exception if compilation fails
     * @throws Exception failed to process the compilation request
     */
    @NonNull
    @Override
    public Future<File> compile(@NonNull CompileResourceRequest request) throws Exception {
        // TODO(imorlowska): move verification to CompileResourceRequest.
        Preconditions.checkArgument(
                request.getInputFile().isFile(),
                "Input file needs to be a normal file.\nInput file: %s",
                request.getInputFile().getAbsolutePath());
        Preconditions.checkArgument(
                request.getOutputDirectory().isDirectory(),
                "Output for resource compilation needs to be a directory.\nOutput: %s",
                request.getOutputDirectory().getAbsolutePath());

        SettableFuture<File> actualResult = SettableFuture.create();
        ListenableFuture<File> futureResult;

        try {
            futureResult = aapt.compile(requestKey, request, processOutputHandler);
        } catch (ResourceCompilationException e) {
            throw new Aapt2Exception(
                    String.format("Failed to compile file %s", request.getInputFile()), e);
        }

        futureResult.addListener(
                () -> {
                    try {
                        actualResult.set(futureResult.get());
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                        actualResult.setException(e);
                    } catch (ExecutionException e) {
                        actualResult.setException(e);
                    }
                },
                executor);

        return actualResult;
    }

    /**
     * Same as {@link BlockingResourceLinker#link(AaptPackageConfig, ILogger)} but invoked only
     * after validation has been performed.
     *
     * @param config same as in {@link BlockingResourceLinker#link(AaptPackageConfig, ILogger)}
     * @return same as in {@link BlockingResourceLinker#link(AaptPackageConfig, ILogger)}
     * @throws AaptException same as in {@link BlockingResourceLinker#link(AaptPackageConfig,
     *     ILogger)}
     */
    @NonNull
    @Override
    public ListenableFuture<Void> makeValidatedPackage(@NonNull AaptPackageConfig config)
            throws AaptException {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        ListenableFuture<File> futureResult;

        try {
            futureResult = aapt.link(requestKey, config, processOutputHandler);
        } catch (Exception e) {
            throw new AaptException("Failed to link", e);
        }

        futureResult.addListener(
                () -> {
                    try {
                        // Just wait for the job to finish, result is Void
                        futureResult.get();
                        actualResult.set(null);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                        actualResult.setException(e);
                    } catch (ExecutionException e) {
                        actualResult.setException(e);
                    }
                },
                executor);

        return actualResult;
    }

    @Override
    @NonNull
    public File compileOutputFor(@NonNull CompileResourceRequest request) {
        return new File(
                request.getOutputDirectory(),
                Aapt2RenamingConventions.compilationRename(request.getInputFile()));
    }

    @Override
    public void close() throws IOException {
        try {
            aapt.end(requestKey);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static String getAapt2ExecutablePath(BuildToolInfo buildToolInfo) {
        Preconditions.checkArgument(
                BuildToolInfo.PathId.DAEMON_AAPT2.isPresentIn(buildToolInfo.getRevision()),
                "Aapt2 with daemon mode requires newer build tools.\n"
                        + "Current version %s, minimum required %s.",
                buildToolInfo.getRevision(),
                BuildToolInfo.PathId.DAEMON_AAPT2.getMinRevision());
        String aapt2 = buildToolInfo.getPath(BuildToolInfo.PathId.DAEMON_AAPT2);
        if (aapt2 == null || !new File(aapt2).isFile()) {
            throw new IllegalStateException("aapt2 is missing on '" + aapt2 + "'");
        }
        return aapt2;
    }
}
