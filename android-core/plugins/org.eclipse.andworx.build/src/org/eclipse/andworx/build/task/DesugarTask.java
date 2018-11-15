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
package org.eclipse.andworx.build.task;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.helper.ProjectBuilder;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.process.java.JavaQueuedProcessor;
import org.eclipse.andworx.process.java.JvmParameters;
import org.eclipse.andworx.task.PipelineBuildTask;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.task.java.DesugarJavaBuilder;
import org.eclipse.andworx.transform.DirectoryInfo;
import org.eclipse.andworx.transform.JarInfo;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.andworx.transform.Transform;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.andworx.transform.TransformInfo;
import org.eclipse.andworx.transform.Transvocation;
import org.eclipse.debug.core.ILaunchConfiguration;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.LoggedProcessOutputHandler;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessResult;
import com.android.utils.PathUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Prepares Java classes for Android binary conversion, which follows in the pipeline
 */
public class DesugarTask extends PipelineBuildTask {

	public static final String TASK_NAME = "desugar classes";

    private static class InputEntry {
        @Nullable private final FileCache cache;
        @Nullable private final FileCache.Inputs inputs;
        @NonNull private final Path inputPath;
        @NonNull private final Path outputPath;

        public InputEntry(
                @Nullable FileCache cache,
                @Nullable FileCache.Inputs inputs,
                @NonNull Path inputPath,
                @NonNull Path outputPath) {
            this.cache = cache;
            this.inputs = inputs;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
        }

        @Nullable
        public FileCache getCache() {
            return cache;
        }

        @Nullable
        public FileCache.Inputs getInputs() {
            return inputs;
        }

        @NonNull
        public Path getInputPath() {
            return inputPath;
        }

        @NonNull
        public Path getOutputPath() {
            return outputPath;
        }
    }

    private static SdkLogger logger = SdkLogger.getLogger(DesugarTask.class.getName());

    private final ProjectBuilder projectBuilder;
    private final TransformAgent transformAgent;
    private final Collection<JarInput> chainJarInputs;
    private final Collection<DirectoryInput> chainDirectoryInputs;
    private File outputDir;
    private Transform transform;
    @NonNull 
    private List<Path> desugarBootclasspath;
    //@Nullable 
    //private final FileCache userCache;
    /** Minimum SDK platform level support */
    @Nullable 
    private int minSdk;
    @NonNull 
    private Path tmpDir;
    private boolean verbose;
    @NonNull 
    private Set<InputEntry> cacheMisses;
    //private final FileCache userCache;

    public DesugarTask(
    		@NonNull Pipeline pipeline,
			@NonNull ProjectBuilder projectBuilder,
	        @NonNull TransformAgent transformAgent,
	        //@Nullable FileCache userCache,
	        @NonNull TaskFactory taskFactory) {
		super(taskFactory, pipeline);
	    this.projectBuilder = projectBuilder;
	    this.transformAgent = transformAgent;
	    //this.userCache = userCache;
        cacheMisses = Sets.newConcurrentHashSet();
        chainJarInputs = new ArrayList<>();
        chainDirectoryInputs = new ArrayList<>();
	}

	public void configure(
			int minSdk,
			boolean verbose,
			@NonNull File outputDir,
			@NonNull List<File> bootClasspath,
			@NonNull Transform transform) {
	    this.minSdk = minSdk;
	    this.verbose = verbose;
	    this.outputDir = outputDir;
	    this.transform = transform;
        desugarBootclasspath =
        		bootClasspath.stream().map(File::toPath).collect(Collectors.toList());
        desugarBootclasspath.addAll(projectBuilder.getCompilationBootclasspath());
	}
	
	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction(Pipeline pipeline) {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        WatchService watchService = null;
        try {
    	    tmpDir = Files.createTempDirectory(logger.getName());
        	watchService  = FileSystems.getDefault().newWatchService();
			Transvocation invocation =
				transformAgent.createTransformInvocation(
						pipeline.getPipelineInput(), 
						outputDir, 
						transform);
        	transform(invocation, watchService);
        	for (JarInput jarInput: chainJarInputs) {
        		Path path = jarInput.getFile().toPath();
        		if (!Files.exists(path))
        			throw new FileNotFoundException(path.toString());
        	}
        	pipeline.setPipelineInput(Collections.singletonList(new TransformInfo(chainJarInputs, chainDirectoryInputs)));
        	actualResult.set(null);
        } catch (Exception e) {
        	actualResult.setException(e);
        } finally {
        	if (watchService != null)
				try {
					watchService.close();
				} catch (IOException e) {
				}
        }
		return actualResult;
	}
	
	private void transform(Transvocation transformInvocation, WatchService watchService)  throws TransformException, InterruptedException, IOException {
        try {
            processInputs(transformInvocation);
            //WatchKey watchKey = outputDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            processNonCachedOnes(getClasspath(transformInvocation));
            /*
            int count = 20; // Wait up to 10 seconds for the output files to be created
            int n = transformInvocation.getInputs().iterator().next().getJarInputs().size();
            while ((watchKey = watchService.poll(500, TimeUnit.MILLISECONDS)) != null) {
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    System.out.println(event.context().toString());
                    --n;
                }
                if ((n ==0 ) || (--count == 0))
                	break;
                watchKey.reset();
            }
            */
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransformException(e);
        } catch (Exception e) {
            throw new TransformException(e);
        }
	}

    private void processInputs(@NonNull Transvocation transformInvocation) throws Exception {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider);

        //if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll();
        //}

        for (TransformInput input : transformInvocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Path rootFolder = dirInput.getFile().toPath();
                Path output = getOutputPath(transformInvocation.getOutputProvider(), dirInput);
                if (Files.notExists(rootFolder)) {
                    PathUtils.deleteIfExists(output);
                } else {
                    //Set<Status> statuses = Sets.newHashSet(dirInput.getChangedFiles().values());
                    //boolean reRun =
                    //        !transformInvocation.isIncremental()
                    //                || !Objects.equals(
                    //                        statuses, Collections.singleton(Status.NOTCHANGED));

                    //if (reRun) {
                        //PathUtils.deleteIfExists(output);
                        processSingle(rootFolder, output, dirInput.getScopes());
                    //}
                }
            }

            for (JarInput jarInput : input.getJarInputs()) {
                //if (transformInvocation.isIncremental()
                //        && jarInput.getStatus() == Status.NOTCHANGED) {
                //    continue;
                //}

                Path output = getOutputPath(outputProvider, jarInput);
                //PathUtils.deleteIfExists(output);
                processSingle(jarInput.getFile().toPath(), output, jarInput.getScopes());
            }
        }
    }

    private void processSingle(
            @NonNull Path input, @NonNull Path output, @NonNull Set<? super Scope> scopes)
            throws Exception {
        //waitableExecutor.execute(
        //        () -> {
                    if (output.toString().endsWith(SdkConstants.DOT_JAR)) {
                        Files.createDirectories(output.getParent());
                    } else {
                        Files.createDirectories(output);
                    }

                    //FileCache cacheToUse;
                    //if (Files.isRegularFile(input)
                    //        && Objects.equals(
                    //                scopes, Collections.singleton(Scope.EXTERNAL_LIBRARIES))) {
                    //    cacheToUse = userCache;
                    //} else {
                    //    cacheToUse = null;
                    //}

                    // add it to the list of cache misses, that will be processed
                    cacheMisses.add(new InputEntry(null, null, input, output));
          //          return null;
          //      });
    }

    private void processNonCachedOnes(List<Path> classpath) throws IOException, ProcessException {
    	JavaQueuedProcessor javaQueuedProcessor = AndworxFactory.instance().getJavaQueuedProcessor();
        int parallelExecutions = javaQueuedProcessor.getParallelism();

        int index = 0;
        Multimap<Integer, InputEntry> procBuckets = ArrayListMultimap.create();
        for (InputEntry pathPathEntry : cacheMisses) {
            int bucketId = index % parallelExecutions;
            procBuckets.put(bucketId, pathPathEntry);
            index++;
        }

    	ILaunchConfiguration configuration = 
    			projectBuilder.getLaunchConfiguration(
    					ProjectBuilder.DESUGAR, 
    					DesugarJavaBuilder.getJavaProcessInfo());
    	if (configuration == null) {
    		throw new ProcessException("Launch configuration \"" + ProjectBuilder.DESUGAR + "\" not found");
    	}
        List<Future<ProcessResult>> waitList = new ArrayList<>();
        int key = javaQueuedProcessor.start();
        for (Integer bucketId : procBuckets.keySet()) {
            Map<Path, Path> inToOut = new HashMap<>();
            for (InputEntry e : procBuckets.get(bucketId)) {
                inToOut.put(e.getInputPath(), e.getOutputPath());
            }
            DesugarJavaBuilder processBuilder =
                    new DesugarJavaBuilder(
                            verbose,
                            inToOut,
                            classpath,
                            desugarBootclasspath,
                            minSdk,
                            tmpDir);
            boolean isWindows =
                    SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;
            JvmParameters jvmParameters = processBuilder.build(configuration, isWindows);
            Future<ProcessResult> futureResult = javaQueuedProcessor.run(
                    key,
            		jvmParameters, 
            		new LoggedProcessOutputHandler(logger));
            waitList.add(futureResult);
            /*
            // now copy to the cache because now we have the file
            for (InputEntry e : procBuckets.get(bucketId)) {
                if (e.getCache() != null && e.getInputs() != null) {
                    e.getCache()
                            .createFileInCacheIfAbsent(
                                    e.getInputs(),
                                    in -> Files.copy(e.getOutputPath(), in.toPath()));
                }
            }
            */
        }
        try {
			javaQueuedProcessor.end(key);
	        for (Future<ProcessResult> futureResult: waitList)
	        	futureResult.get()
	        		.rethrowFailure()
	        		.assertNormalExitValue();
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new ProcessException(ProjectBuilder.DESUGAR + " interrupted", e);
		} catch (ExecutionException e) {
			throw new ProcessException(ProjectBuilder.DESUGAR + " failed", e);
		}
    }

    @NonNull
    private List<Path> getClasspath(@NonNull Transvocation transformInvocation)
            throws IOException {
        ImmutableList.Builder<Path> classpathEntries = ImmutableList.builder();

        classpathEntries.addAll(
                getAllFiles(transformInvocation.getInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        classpathEntries.addAll(
                getAllFiles(transformInvocation.getReferencedInputs())
                        .stream()
                        .map(File::toPath)
                        .iterator());

        return classpathEntries.build();
    }

    @NonNull
    private Path getOutputPath(
            @NonNull TransformOutputProvider outputProvider, @NonNull QualifiedContent content) {
    	boolean isDirectory = content.getFile().isDirectory();
        File file = outputProvider
                .getContentLocation(
                        content.getName(),
                        content.getContentTypes(),
                        content.getScopes(),
                        isDirectory ? Format.DIRECTORY : Format.JAR);
        if (isDirectory)
        	chainDirectoryInputs.add(
                    new DirectoryInfo(
                    		transformAgent.getUniqueInputName(file),
                        file,
                        content.getContentTypes(),
                        content.getScopes()));
        else
        	chainJarInputs.add(
                    new JarInfo(
                    		transformAgent.getUniqueInputName(file),
	                    file,
	                    Status.NOTCHANGED,
	                    content.getContentTypes(),
	                    content.getScopes()));
        return file.toPath();
    }

    private static Collection<File> getAllFiles(
            Collection<TransformInput> transformInputs) {
        ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        for (TransformInput input : transformInputs) {
            for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                inputFiles.add(directoryInput.getFile());
            }
            for (JarInput jarInput : input.getJarInputs()) {
                inputFiles.add(jarInput.getFile());
            }
        }
        return inputFiles.build();
    }

}
