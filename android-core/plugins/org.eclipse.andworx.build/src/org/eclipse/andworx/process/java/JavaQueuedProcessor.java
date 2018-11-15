package org.eclipse.andworx.process.java;

import java.io.IOException;
import java.util.concurrent.Future;

import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.process.QueuedJob;
import org.eclipse.andworx.process.QueuedJobProcessor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.tasks.Job;
import com.android.builder.tasks.JobContext;
import com.android.builder.tasks.Task;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.process.ProcessResult;
import com.google.common.base.MoreObjects;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Process queue to allow multiple JVMs to run concurrently.
 * Call super.start() to kick off queue and super.end() to wait for all processes to complete.
 */
public class JavaQueuedProcessor extends QueuedJobProcessor<JavaProcess, ProcessResult> {
	
	/** Logger */
    private static SdkLogger logger = SdkLogger.getLogger(JavaQueuedProcessor.class.getName());

    /**
     * Construct JavaQueuedProcessor object
     * @param logger Logger
     * @param processesNumber Maximum number of processes or 0 for default
     */
	public JavaQueuedProcessor(int processesNumber) {
		super(logger, processesNumber);
	}

	/**
	 * Launch JVM with given parameters, attach giver output handler and return a future object
	 * @param key Session id
	 * @param jvmParameters
	 * @param processOutputHandler
	 * @return a future ProcessResult object
	 * @throws ProcessException
	 */
    public Future<ProcessResult> run(
            int key,
    		@NonNull  JvmParameters jvmParameters, 
            @Nullable ProcessOutputHandler processOutputHandler) throws ProcessException {
    	// Future to signal job completion to work queue
        final SettableFuture<ProcessResult> jobResult = SettableFuture.create();
        // Future to return result to caller
        final SettableFuture<ProcessResult> actualResult = SettableFuture.create();
        // Process to launch JVM and set both above futures upon completion
        final JavaProcess javaProcess = new JavaProcess(jvmParameters, processOutputHandler, jobResult, actualResult);
        // Identify the process by it's launch configuration name
       	final String name = jvmParameters.getConfiguration().getName();
        try {
        	// Construct job to place on work request queue
            final Job<JavaProcess> javaProcessJob =
                new QueuedJob<JavaProcess>(
                    key,
                    "Executing " + name,
                    // Construct task to launch process using #run
                    new Task<JavaProcess>() {
                        @Override
                        public void run(
                                @NonNull Job<JavaProcess> job,
                                @NonNull JobContext<JavaProcess> context)
                               throws IOException {
                            javaProcess.start();
                            logger.verbose("Started %1$d", javaProcess.hashCode());
                        }

                        @Override
                        public void finished() {
                        }

                        @Override
                        public void error(Throwable e) {
                        	jobResult.setException(e);
                            actualResult.setException(e);
                        }

                        @Override
                        public String toString() {
                            return MoreObjects.toStringHelper(this)
                                    .add("name", name)
                                    .toString();
                        }
                    },
                    jobResult);
            // Asssure outstanding jobs list exists for current session.
            threadContext.assureOutstandingJobsList(key);
            // Push job on work request queue
            processingRequests.push(javaProcessJob);
        } catch (InterruptedException e) {
            // Restore the interrupted status
            Thread.currentThread().interrupt();
            throw new ProcessException(e);
        }
        return actualResult;
    }
}
