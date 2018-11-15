package org.eclipse.andworx.build.task;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.andworx.context.VariantContext;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.task.PipelineBuildTask;
import org.eclipse.andworx.task.TaskFactory;
import org.eclipse.andworx.transform.DirectoryInfo;
import org.eclipse.andworx.transform.JarInfo;
import org.eclipse.andworx.transform.Pipeline;
import org.eclipse.andworx.transform.Transform;
import org.eclipse.andworx.transform.TransformAgent;
import org.eclipse.andworx.transform.TransformInfo;
import org.eclipse.andworx.transform.Transvocation;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.builder.dexing.ClassFileInput;
import com.android.builder.dexing.ClassFileInputs;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderException;
import com.android.builder.dexing.DexArchiveMerger;
import com.android.builder.dexing.DexingType;
import com.android.builder.dexing.r8.ClassFileProviderFactory;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.MessageReceiver;
import com.android.ide.common.blame.ParsingProcessOutputHandler;
import com.android.ide.common.blame.parser.DexParser;
import com.android.ide.common.blame.parser.ToolOutputParser;
import com.android.ide.common.internal.WaitableExecutor;
import com.android.ide.common.process.ProcessException;
import com.android.ide.common.process.ProcessOutput;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.SettableFuture;

/**
 * Convert Java classes to Android binary format
 */
public class D8Task  extends PipelineBuildTask {

	public static final String TASK_NAME = "d8 classes";

	/**
	 * Parameters for dex archive builder. Includes filter predicate for allocating classes to buckets.
	 */
    public static class DexConversionParameters implements Serializable {
	    private static final long serialVersionUID = 1L;
	    private final QualifiedContent input;
	    private final List<Path> bootClasspath;
	    private final List<Path> classpath;
	    private final File preDexOutputFile;
        private final int numberOfBuckets;
        private final int bucketId;
        private final int minSdkVersion;
        private final boolean isDebuggable;
        private final ClassFileProviderFactory classFileProviderFactory;
	    private final MessageReceiver messageReceiver;
      
        public DexConversionParameters(
                @NonNull QualifiedContent input,
                @NonNull List<Path> bootClasspath,
                @NonNull List<Path> classpath,
                File preDexOutputFile,
                int numberOfBuckets,
                int bucketId,
                int minSdkVersion,
                boolean isDebuggable,
                @NonNull ClassFileProviderFactory classFileProviderFactory,
                @NonNull MessageReceiver messageReceiver) {
            this.input = input;
            this.bootClasspath = bootClasspath;
            this.classpath = classpath;
            this.preDexOutputFile = preDexOutputFile;
            this.numberOfBuckets = numberOfBuckets;
            this.bucketId = bucketId;
            this.minSdkVersion = minSdkVersion;
            this.isDebuggable = isDebuggable;
            this.classFileProviderFactory = classFileProviderFactory;
            this.messageReceiver = messageReceiver;
        }
       
        public boolean belongsToThisBucket(String path) {
            return (Math.abs(path.hashCode()) % numberOfBuckets) == bucketId;
        }

        public boolean isDirectoryBased() {
            return input instanceof DirectoryInput;
        }
    }

    /**
     * Callable to be executed in a ForkJoinTask. Callable implementation cannot be anonymous.
     */
	private static class D8Merger implements Callable<Void> {
	    @NonNull 
	    private final MessageReceiver messageReceiver;
	    @NonNull 
	    private final DexingType dexingType;
	    @NonNull 
	    private final ProcessOutput processOutput;
	    @NonNull 
	    private final File dexOutputDir;
	    @NonNull 
	    private final Iterable<Path> dexArchives;
	    @Nullable 
	    private final Path mainDexList;
	    private final int minSdkVersion;
	    private final boolean isDebuggable;

	    public D8Merger(
	            @NonNull MessageReceiver messageReceiver,
	            @NonNull DexingType dexingType,
	            @NonNull ProcessOutput processOutput,
	            @NonNull File dexOutputDir,
	            @NonNull Iterable<Path> dexArchives,
	            @Nullable Path mainDexList,
	            int minSdkVersion,
	            boolean isDebuggable) {
	        this.messageReceiver = messageReceiver;
	        this.dexingType = dexingType;
	        this.processOutput = processOutput;
	        this.dexOutputDir = dexOutputDir;
	        this.dexArchives = dexArchives;
	        this.mainDexList = mainDexList;
	        this.minSdkVersion = minSdkVersion;
	        this.isDebuggable = isDebuggable;
	    }

    @Override
	    public Void call() throws Exception {
	        DexArchiveMerger merger;
            int d8MinSdkVersion = minSdkVersion;
            if (d8MinSdkVersion < 21 && dexingType == DexingType.NATIVE_MULTIDEX) {
                // D8 has baked-in logic that does not allow multiple dex files without
                // main dex list if min sdk < 21. When we deploy the app to a device with api
                // level 21+, we will promote legacy multidex to native multidex, but the min
                // sdk version will be less than 21, which will cause D8 failure as we do not
                // supply the main dex list. In order to prevent that, it is safe to set min
                // sdk version to 21.
                d8MinSdkVersion = 21;
            }
            merger =
                DexArchiveMerger.createD8DexMerger(
                     messageReceiver, d8MinSdkVersion, isDebuggable);
	        merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
	        return null;
	    }

	}

    /** Number partitions into which of jar file content is divided for parallel processing */
	private static final int NUMBER_OF_BUCKETS = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
    /** Minimum Size of jar file to undergo partitioning of content. 
     * Small files are more efficiently processed in one unit of work. */
	private static final long BUCKET_THRESHOLD = 20000;

    private static ILogger logger = SdkLogger.getLogger(D8Task.class.getName());

    private final int minSdkVersion;
    private final TransformAgent transformAgent;
    private final Collection<JarInput> chainJarInputs;
    private final Collection<DirectoryInput> chainDirectoryInputs;
	private final ForkJoinPool forkJoinPool;
    private File[] outputDirs;
    @NonNull 
    private Transform transform;
    private boolean isDebuggable;
    @NonNull 
    private MessageReceiver messageReceiver;
    @NonNull 
    final WaitableExecutor executor;
    @NonNull 
    List<Path> bootClasspath;

	public D8Task (
			@NonNull Pipeline pipeline,
			@NonNull VariantContext variantScope,
            @NonNull TransformAgent transformAgent,
            @NonNull WaitableExecutor executor,
			TaskFactory taskFactory) {
		super(taskFactory, pipeline);
        this.transformAgent = transformAgent;
	    this.forkJoinPool = ForkJoinPool.commonPool();
		minSdkVersion = variantScope.getVariantConfiguration().getMinSdkVersionValue();
		this.executor = executor;
        chainJarInputs = new ArrayList<>();
        chainDirectoryInputs = new ArrayList<>();
	}

	public void configure(
			@NonNull File[] outputDirs,
			@NonNull List<File> bootClasspath,
			@NonNull Transform transform,
			@NonNull MessageReceiver messageReceiver,
			boolean isDebuggable) {
		this.outputDirs = new File[2];
	    System.arraycopy(outputDirs, 0, this.outputDirs, 0, 2);
	    this.transform = transform;
		this.messageReceiver = messageReceiver;
	    this.isDebuggable = isDebuggable;
        this.bootClasspath = 
        		bootClasspath
			.stream()
			.map(file -> file.toPath())
			.collect(Collectors.toList());
	}
	
	@Override
	public String getTaskName() {
		return TASK_NAME;
	}

	@Override
	public Future<Void> doFullTaskAction(Pipeline pipeline) {
        final SettableFuture<Void> actualResult = SettableFuture.create();
        try {
			Transvocation invocation =
					transformAgent.createTransformInvocation(
						pipeline.getPipelineInput(), outputDirs[0], transform);
			archiveTransform(invocation);
			// The bucket strategy may result in one or more output jars not being created due to empty content
			List<JarInput> filteredChainJarInputs = new ArrayList<>();
			for (JarInput jarInput: chainJarInputs)
				if (jarInput.getFile().exists())
					filteredChainJarInputs.add(jarInput);
			invocation =
					transformAgent.createTransformInvocation(
						Collections.singletonList(
							new TransformInfo(filteredChainJarInputs, chainDirectoryInputs)), outputDirs[1], transform);
			chainJarInputs.clear();
			chainDirectoryInputs.clear();
			mergeTransform(invocation);
       	    actualResult.set(null);
        	pipeline.setPipelineInput(Collections.singletonList(new TransformInfo(chainJarInputs, chainDirectoryInputs)));
        } catch (Exception e) {
        	actualResult.setException(e);
        }
		return actualResult;
	}

	private void archiveTransform(Transvocation invocation) throws IOException, InterruptedException {
        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider, "Missing output provider.");
        ClassFileProviderFactory classFileProviderFactory = new ClassFileProviderFactory();
        List<Path> classpath =
                getClasspath(invocation)
                        .stream()
                        .map(file -> file.toPath())
                        .collect(Collectors.toList());
        for (TransformInput input : invocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                logger.verbose("Dir input %s", dirInput.getFile().toString());
                convertToDexArchive(
                    dirInput,
                    outputProvider,
                    classpath,
                    classFileProviderFactory);
            }
            for (JarInput jarInput : input.getJarInputs()) {
                logger.verbose("Jar input %s", jarInput.getFile().toString());
                Preconditions.checkState(
                    jarInput.getFile().exists(),
                    "File %s does not exist, yet it is reported as input. Try \n"
                            + "cleaning the build directory.",
                    jarInput.getFile().toString());
                convertToDexArchive(
                    jarInput,
                    outputProvider,
                    classpath,
                    classFileProviderFactory);
            }
        }
        executor.waitForTasksWithQuickFail(true);
	}

    private void convertToDexArchive(
            @NonNull QualifiedContent input,
            @NonNull TransformOutputProvider outputProvider,
            @NonNull List<Path> classpath,
            @NonNull ClassFileProviderFactory classFileProviderFactory) throws IOException
        {
        File inputFile = input.getFile();
        logger.verbose("Dexing %s", inputFile.getAbsolutePath());
        if (inputFile.isFile() && (inputFile.length() < BUCKET_THRESHOLD)) {
            File preDexOutputFile = getPreDexFile(outputProvider, input, -1);
            launchD8(input, preDexOutputFile, classpath, classFileProviderFactory, 0, 0);
        } else
	        for (int bucketId = 0; bucketId < NUMBER_OF_BUCKETS; bucketId++) {
	            File preDexOutputFile = getPreDexFile(outputProvider, input, bucketId);
	            launchD8(input, preDexOutputFile, classpath, classFileProviderFactory, NUMBER_OF_BUCKETS, bucketId);
	        }
    }

    private void launchD8(
    		QualifiedContent input, 
    		File preDexOutputFile, 
    		List<Path> classpath, 
    		ClassFileProviderFactory classFileProviderFactory, 
    		int buckets, 
    		int bucketId) throws IOException {
        if (preDexOutputFile.isDirectory())
        	prepareDexOutputDir(preDexOutputFile);
        else 
        	prepareDexOutputDir(preDexOutputFile.getParentFile());
        DexConversionParameters parameters =
                new DexConversionParameters(
                        input,
                        bootClasspath,
                        classpath,
                        preDexOutputFile,
                        buckets,
                        bucketId,
                        minSdkVersion,
                        isDebuggable,
                        classFileProviderFactory,
                        messageReceiver);
        executor.execute(
            () -> {
                ProcessOutputHandler outputHandler =
                        new ParsingProcessOutputHandler(
                                new ToolOutputParser(
                                        new DexParser(), Message.Kind.ERROR, logger),
                                new ToolOutputParser(new DexParser(), logger),
                                messageReceiver);
                ProcessOutput output = null;
                try (Closeable ignored = output = outputHandler.createOutput()) {
                    launchProcessing(
                            parameters,
                            messageReceiver);
                } finally {
                    if (output != null) {
                        try {
                            outputHandler.handleOutput(output);
                        } catch (ProcessException e) {
                            // ignore this one
                        }
                    }
                }
                return null;
            });
    }
 
    private void prepareDexOutputDir(File path) {
    	if (path.isDirectory() && !path.exists())
    		FileUtils.mkdirs(path);
    }
    
    private static void launchProcessing(
            @NonNull DexConversionParameters dexConversionParameters,
            @NonNull MessageReceiver receiver)
            throws IOException {
        DexArchiveBuilder dexArchiveBuilder =
                DexArchiveBuilder.createD8DexBuilder(
                		dexConversionParameters.minSdkVersion,
                		dexConversionParameters.isDebuggable,
                		dexConversionParameters.bootClasspath,
                		dexConversionParameters.classpath,
                		dexConversionParameters.classFileProviderFactory,
                        false,
                        dexConversionParameters.messageReceiver);
        Path inputPath = dexConversionParameters.input.getFile().toPath();
        Predicate<String> bucketFilter = 
        		dexConversionParameters.numberOfBuckets > 0 ?
        				dexConversionParameters::belongsToThisBucket :
        				new Predicate<String>() {
							@Override
							public boolean test(String t) {
								return true;
							}};
        logger.verbose("Dexing '" + inputPath + "' to '" + dexConversionParameters.preDexOutputFile + "'");

        try (ClassFileInput input = ClassFileInputs.fromPath(inputPath)) {
            dexArchiveBuilder.convert(
                    input.entries(bucketFilter),
                    dexConversionParameters.preDexOutputFile.toPath(),
                    dexConversionParameters.isDirectoryBased());
        } catch (DexArchiveBuilderException ex) {
            throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
        }
    }

	private void mergeTransform(Transvocation invocation) throws IOException, TransformException {
        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        ProcessOutputHandler outputHandler =
                new ParsingProcessOutputHandler(
                        new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                        new ToolOutputParser(new DexParser(), logger),
                        messageReceiver);

        outputProvider.deleteAll();
 
        ProcessOutput output = null;
        List<ForkJoinTask<Void>> mergeTasks;
        try (Closeable ignored = output = outputHandler.createOutput()) {
        	/*
            if (dexingType == DexingType.NATIVE_MULTIDEX && isDebuggable) {
                mergeTasks =
                        handleNativeMultiDexDebug(
                                transformInvocation.getInputs(),
                                output,
                                outputProvider,
                                transformInvocation.isIncremental());
            } else {*/
            File outputDir =
                    getDexOutputLocation(outputProvider, "main", Collections.emptySet()); //, TransformManager.SCOPE_FULL_PROJECT);
            mergeTasks = mergeDex(invocation.getInputs(), output, outputDir);

            // now wait for all merge tasks completion
            mergeTasks.forEach(ForkJoinTask::join);
        	chainDirectoryInputs.add(
                    new DirectoryInfo(
                		transformAgent.getUniqueInputName(outputDir),
                		outputDir,
                		transform.getInputTypes(),
                		transform.getReferencedScopes()));

            
        } catch (Exception e) {
            // Print the error always, even without --stacktrace
            logger.error(null, Throwables.getStackTraceAsString(e));
            throw new TransformException(e);
        } finally {
            if (output != null) {
                try {
                    outputHandler.handleOutput(output);
                } catch (ProcessException e) {
                    // ignore this one
                }
            }
        }
	}

    /**
     * For legacy and mono-dex we always merge all dex archives, non-incrementally. For release
     * native multidex we do the same, to get the smallest possible dex files.
     */
    @NonNull
    private List<ForkJoinTask<Void>> mergeDex(
            @NonNull Collection<TransformInput> inputs,
            @NonNull ProcessOutput output,
            @NonNull File outputDir)
            throws IOException {
        ImmutableList.Builder<Path> dexArchiveBuilder = ImmutableList.builder();
        getDirectories(inputs)
                .stream()
                .map(File::toPath)
                .forEach(dexArchiveBuilder::add);
        inputs.stream()
                .flatMap(transformInput -> transformInput.getJarInputs().stream())
                .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                .map(jarInput -> jarInput.getFile().toPath())
                .forEach(dexArchiveBuilder::add);

        ImmutableList<Path> dexesToMerge = dexArchiveBuilder.build();
        if (dexesToMerge.isEmpty()) {
            return ImmutableList.of();
        }

        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses = null;
        return ImmutableList.of(submitForMerging(output, outputDir, dexesToMerge, mainDexClasses));
    }

    /**
     * Add a merging task to the queue of tasks.
     *
     * @param output the process output that dx will output to.
     * @param dexOutputDir the directory to output dexes to
     * @param dexArchives the dex archive inputs
     * @param mainDexList the list of classes to keep in the main dex. Must be set <em>if and
     *     only</em> legacy multidex mode is used.
     * @return the {@link ForkJoinTask} instance for the submission.
     */
    @NonNull
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList) {
    	/*
                new DexMergerTransformCallable(
                        messageReceiver,
                        dexingType,
                        output,
                        dexOutputDir,
                        dexArchives,
                        mainDexList,
                        forkJoinPool,
                        dexMerger,
                        minSdkVersion,
                        isDebuggable); */
    	D8Merger callable = new D8Merger(
                messageReceiver,
                DexingType.MONO_DEX,
                output,
                dexOutputDir,
                dexArchives,
                mainDexList,
                minSdkVersion,
                isDebuggable);
        return forkJoinPool.submit(callable);
    }

	@NonNull
    private File getPreDexFile(
            @NonNull TransformOutputProvider output,
            @NonNull QualifiedContent content,
            int bucketId) {

    	boolean isDirectory = content.getFile().isDirectory();
        File file = isDirectory ?
                getPreDexFolder(output, (DirectoryInfo) content) :
                getPreDexJar(output, (JarInfo) content, bucketId == -1 ? null : bucketId);
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
        return file;
    }

    @NonNull
    private File getDexOutputLocation(
            @NonNull TransformOutputProvider outputProvider,
            @NonNull String name,
            @NonNull Set<? super Scope> scopes) {
        return outputProvider.getContentLocation(name, Collections.emptySet(), scopes, Format.DIRECTORY);
    }
    
    @NonNull
    private static File getPreDexJar(
            @NonNull TransformOutputProvider output,
            @NonNull JarInfo qualifiedContent,
            @Nullable Integer bucketId) {

        return output.getContentLocation(
            qualifiedContent.getName() + (bucketId == null ? "" : ("-" + bucketId)),
            Collections.emptySet(),
            //ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
            qualifiedContent.getScopes(),
            Format.JAR);
    }

    @NonNull
    private static File getPreDexFolder(
            @NonNull TransformOutputProvider output, @NonNull DirectoryInfo directoryInput) {

        return FileUtils.mkdirs(
            output.getContentLocation(
                directoryInput.getName(),
                Collections.emptySet(),
                //ImmutableSet.of(ExtendedContentType.DEX_ARCHIVE),
                directoryInput.getScopes(),
                Format.DIRECTORY));
    }

    @NonNull
    private static List<File> getClasspath(
            @NonNull Transvocation transformInvocation)
            throws IOException {
        ImmutableList.Builder<File> classpathEntries = ImmutableList.builder();

        Iterable<TransformInput> dependencies =
                Iterables.concat(
                        transformInvocation.getInputs(), transformInvocation.getReferencedInputs());
        classpathEntries.addAll(
                getDirectories(dependencies)
                        .stream()
                        .distinct()
                        .iterator());

        classpathEntries.addAll(
                Streams.stream(dependencies)
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        //.filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                        .map(jarInput -> jarInput.getFile())
                        .distinct()
                        .iterator());

        return classpathEntries.build();
    }

    /** Return existing directories from the inputs. Deleted ones are omitted. */
    private static Collection<File> getDirectories(Iterable<TransformInput> transformInputs) {
        return getAllFiles(transformInputs, true, false);
    }

    private static Collection<File> getAllFiles(
            Iterable<TransformInput> transformInputs,
            boolean includeDirectoryInput,
            boolean includeJarInput) {
        ImmutableList.Builder<File> inputFiles = ImmutableList.builder();
        for (TransformInput input : transformInputs) {
            if (includeDirectoryInput) {
                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    if (directoryInput.getFile().isDirectory()) {
                        inputFiles.add(directoryInput.getFile());
                    }
                }
            }
            if (includeJarInput) {
                for (JarInput jarInput : input.getJarInputs()) {
                    if (jarInput.getFile().isFile()) {
                        inputFiles.add(jarInput.getFile());
                    }
                }
            }
        }
        return inputFiles.build();
    }

}
