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

import static com.android.builder.core.BuilderConstants.MAIN;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.andworx.config.AndroidConfig;
import org.eclipse.andworx.config.ProguardFile;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.andworx.entity.AndroidBean;
import org.eclipse.andworx.entity.AndroidSourceBean;
import org.eclipse.andworx.entity.BaseConfigBean;
import org.eclipse.andworx.entity.BaseString;
import org.eclipse.andworx.entity.BuildTypeBean;
import org.eclipse.andworx.entity.ProductFlavorBean;
import org.eclipse.andworx.entity.ProjectBean;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.entity.SourceSetBean;
import org.eclipse.andworx.file.AndroidSourceSet;
import org.eclipse.andworx.file.FileManager;
import org.eclipse.andworx.log.SdkLogger;
import org.eclipse.andworx.model.CodeSource;
import org.eclipse.andworx.model.FieldName;
import org.eclipse.andworx.model.ProductFlavorImpl;
import org.eclipse.andworx.model.SourceSet;

import com.android.SdkConstants;
import com.android.builder.core.BuilderConstants;
import com.android.builder.model.SigningConfig;

import au.com.cybersearch2.classyjpa.EntityManagerLite;

/**
 * Assembles configuration content extracted by a Groovy AST parser into JPA entity beans and then persists them
 */
public class AndroidConfigurationBuilder implements AndworxBuildReceiver {
    public static final String CONFIG_NAME_COMPILE = "compile";
    public static final String CONFIG_NAME_PUBLISH = "publish";
    public static final String CONFIG_NAME_APK = "apk";
    public static final String CONFIG_NAME_PROVIDED = "provided";
    public static final String CONFIG_NAME_API = "api";
    public static final String CONFIG_NAME_COMPILE_ONLY = "compileOnly";
    public static final String CONFIG_NAME_IMPLEMENTATION = "implementation";
    public static final String CONFIG_NAME_RUNTIME_ONLY = "runtimeOnly";
    public static final String CONFIG_NAME_RUNTIME = "runtime";
    public static final String CONFIG_NAME_APPLICATION = "application";

	private static final String DEPENDENCIES = "dependencies";
	private static final String ANDROID = "android";
	private static final String PROJECT = "project";
	private static final String LOG = "log";
	private static final String ERROR = "error";
	private static final String WARNING = "warning";
	private static final String INFO = "info";
	private static final String DEBUG = "debug";
	private static final String DEFAULT_CONFIG = "defaultConfig";
	private static final String SIGNING_CONFIGS = "signingConfigs";
	private static final String BUILD_TYPES = "buildTypes";
	private static final String SOURCE_SETS = "sourceSets";

    /** BaseConfig slice for JPA class inheritance */
	private static class BaseComposite {
		public BaseConfigBean base;
		public Set<BaseString> baseStringSet;
		
		protected BaseComposite(String name) {
			base = new BaseConfigBean(name);
			baseStringSet = new HashSet<>();
		}

		protected void persist(ProjectBean projectBean, EntityManagerLite entityManager) {
			base.setProjectBean(projectBean);
			entityManager.persist(base);
			for (BaseString baseString: baseStringSet) {
				baseString.setBaseConfigBean(base);
				entityManager.persist(baseString);
			}
		}
    }

	/** ProductFlavor slice for JPA class inheritance */
	private static class ProductFlavorComposite extends BaseComposite {
		public ProductFlavorBean atom;
		
		public ProductFlavorComposite(String name) {
			super(name);
			atom = new ProductFlavorBean(name);
		}
		
		public void persist(ProjectBean projectBean, EntityManagerLite entityManager) {
			atom.setProjectBean(projectBean);
			entityManager.persist(atom);
			super.persist(projectBean, entityManager);
		}
	}
	
	/** BuildType slice for JPA class inheritance */
	private static class BuildTypeComposite extends BaseComposite {
		public BuildTypeBean atom;
		
		public BuildTypeComposite(String name) {
			super(name);
			atom = new BuildTypeBean(name);
		}
		
		public void persist(ProjectBean projectBean, EntityManagerLite entityManager, Map<String, SigningConfigBean> signingConfigBeanMap) {
			atom.setProjectBean(projectBean);
			// SigningConfig is found by name and joined
			SigningConfig signingConfig = atom.getSigningConfig();
			if (signingConfig != null) {
				SigningConfigBean bean = signingConfigBeanMap.get(signingConfig.getName());
				if (bean != null)
					atom.setSigningConfigId(bean.getId());
			}
			entityManager.persist(atom);
			super.persist(projectBean, entityManager);
		}
    }
    
	/** SourceSet whole-part association */
	private static class SourceSetComposite {
		private String name;
		public final Map<CodeSource, SourceSetBean> sourceSetBeanMap;
		
		public SourceSetComposite(String name) {
			this.name = name;
			sourceSetBeanMap = new HashMap<>();
		}
		
		public String getName() {
			return name;
		}

		public void persist(AndroidSourceBean androidSourceBean, EntityManagerLite entityManager) {
			for (SourceSetBean sourceSetBean: sourceSetBeanMap.values()) {
				sourceSetBean.setAndroidSourceBean(androidSourceBean);
				entityManager.persist(sourceSetBean);
			}
		}
	}
	
	/** Manager of file cache and bundle files */
	private final FileManager fileManager;
	/** Project absolute directory */
	private final File projectLocation;
	/** Provides debug signing configuration */
	private final AndroidEnvironment androidEnvironment;
	/** Maven model is a POM configuration used to resolve external dependencies */
	private final Model mavenModel;
	/** Entity bean to persist configuration in "android" block */ 
	private final AndroidBean androidBean;
	
	// Map structural components by name 
	private final Map<String, ProductFlavorComposite> productFlavorMap;
	private final Map<String, BuildTypeComposite> buildTypeMap;
	private final Map<String, SigningConfigBean> signingConfigBeanMap;
	private final Map<String, SourceSetComposite> sourceSetMap;

	/**
	 * Construct AndroidConfigurationBuilder object
	 * @param fileManager Manager of file cache and bundle files
	 * @param projectLocation Project absolute directory 
	 * @param androidEnvironment Provides debug signing configuration
	 */
	public AndroidConfigurationBuilder(FileManager fileManager, File projectLocation, AndroidEnvironment androidEnvironment) {
		this.fileManager = fileManager;
		this.projectLocation = projectLocation;
		this.androidEnvironment = androidEnvironment;
		mavenModel = new Model();
		// Model version is mandatory
		mavenModel.setVersion("4.0.0");
		androidBean = new AndroidBean();
		productFlavorMap = new HashMap<>();
		buildTypeMap = new HashMap<>();
		sourceSetMap = new HashMap<>();
		signingConfigBeanMap = new HashMap<>(); 
		setDefaultSigningConfig();
		// Add product flavor named "main" to use as default config
		ProductFlavorComposite defaultConfig = new ProductFlavorComposite(MAIN);
		productFlavorMap.put(MAIN, defaultConfig);
		androidBean.setDefaultConfig(new ProductFlavorImpl(defaultConfig.atom, defaultConfig.base));
	}

	/**
	 * Returns user configuration settings within the "android" block, excluding those shared by BuildType
	 * @return AndroidConfig object
	 */
	public AndroidConfig getAndroidConfig() {
		return androidBean;
	}

	/**
	 * Returns Maven model containing project dependendencies
	 * @return Model object
	 */
	public Model getMavenModel() {
		return mavenModel;
	}

	/**
	 * Returns debug Signing Configuration
	 * @return SigningConfig object
	 */
	public SigningConfig getDefaultSigningConfig() {
		return androidEnvironment.getDefaultDebugSigningConfig();
	}

	/**
	 * Returns source path for given source set bype
	 * @param codeSource Source set type
	 * @return path
	 */
	public String getSourceFolder(CodeSource codeSource) {
		SourceSetComposite mainSourceSet = sourceSetMap.get(SourceSet.MAIN_SOURCE_SET_NAME);
		if (mainSourceSet != null) {
			SourceSetBean bean = mainSourceSet.sourceSetBeanMap.get(codeSource);
			if (bean != null)
				return bean.getPath();
		}
		return AndroidSourceSet.DEFAULT_PROJECT_ROOT + codeSource.defaultPath;
	}

	/**
	 * Persist entire configuration
	 * @param entityManager Entity manager operating in persistence context
	 * @param projectBean Project entity object which has already been persisted
	 */
	public void persist(EntityManagerLite entityManager, ProjectBean projectBean) {
		androidBean.setProjectBean(projectBean);
		entityManager.persist(androidBean);
		for (SigningConfigBean bean: signingConfigBeanMap.values()) {
			bean.setAndroidBean(androidBean);
			entityManager.persist(bean);
		}
		for (SourceSetComposite sourceSetComposite: sourceSetMap.values()) {
			AndroidSourceBean androidSourceBean = new AndroidSourceBean(projectBean, sourceSetComposite.getName());
			entityManager.persist(androidSourceBean);
			sourceSetComposite.persist(androidSourceBean, entityManager);
		}
		for (ProductFlavorComposite composite: productFlavorMap.values()) {
			composite.persist(projectBean, entityManager);
		}
		for (BuildTypeComposite composite: buildTypeMap.values()) {
			composite.persist(projectBean, entityManager, signingConfigBeanMap);
		}
	}

	/**
	 * Handle value item 
	 * @param path Abstract Syntax Tree path
	 * @param value Value at indicated path
	 */
	@Override
	public void receiveItem(String path, String value) {
		if (path.startsWith(ANDROID)) {
		    configureAndroid(path.substring(ANDROID.length() + 1), value);
		} else if (path.startsWith(DEPENDENCIES)) {
			path = path.substring(DEPENDENCIES.length() + 1);
			if (path.indexOf('/') != -1)
				return; // TODO - Handle child elements
		    String[] items = value.split(":");
		    if ((items.length > 1) && (items.length < 5))
		    	configureDependency(path, items);
		    // TODO - report invalid configuration
		}
		else if (path.startsWith(PROJECT)) {
			    configureProject(path.substring(PROJECT.length() + 1), value);
		}
	}

	/**
	 * Handle property item 
	 * @param path Abstract Syntax Tree path
	 * @param key Property key
	 * @param value Property value
	 */
	@Override
	public void receiveItem(String path, String key, String value) {
	}

	/**
	 * Handle binary item 
	 * @param path Abstract Syntax Tree path
	 * @param lhs Left hand side expression
	 * @param op Binary operator
	 * @param rhs Right hand side expression
	 */
	@Override
	public void receiveItem(String path, String lhs, String op, String rhs) {
		if ("=".equals(op)) {
			receiveItem(path + "/" + lhs, rhs);
			//System.out.println(path + "/" + lhs + " = " + rhs);
		}
	}

	/**
	 * Path = "android"
	 * Terminals = compileSdkVersion, buildToolsVersion
	 * @param key 
	 * @param value
	 */
	private void configureAndroid(String key, String value) {
    	switch (key) {
    	case "compileSdkVersion": androidBean.setCompileSdkVersion(value); break;
    	case "buildToolsVersion": androidBean.setBuildToolsVersion(value); break;
        default:
    		if (key.startsWith(DEFAULT_CONFIG)) {
    		    configureDefaultConfig(key.substring(DEFAULT_CONFIG.length() + 1), value);
    		} else if (key.startsWith(SIGNING_CONFIGS)) {
    			configureSigningConfig(key.substring(SIGNING_CONFIGS.length() + 1), value);
    		} else if (key.startsWith(BUILD_TYPES)) {
			configureBuildType(key.substring(BUILD_TYPES.length() + 1), value);
    		} else if (key.startsWith(SOURCE_SETS)) {
			configureSourceSet(key.substring(SOURCE_SETS.length() + 1), value);
		}
        	// TODOD - Generate error
     	}
	}

	/**
	 * Path = "android/defaultConfig"
	 * Terminals = minSdkVersion, targetSdkVersion, maxSdkVersion, applicationId, versionCode, versionName, testInstrumentationRunner
	 * @param key
	 * @param value
	 */
	private void configureDefaultConfig(String key, String value) {
		// Default config name is "main"
		ProductFlavorComposite defaultConfig = productFlavorMap.get(MAIN);
		switch (key) {
		case "minSdkVersion": defaultConfig.atom.setMinSdkVersion(Integer.parseInt(value)); break;
		case "targetSdkVersion": defaultConfig.atom.setTargetSdkVersion(Integer.parseInt(value)); break;
		case "maxSdkVersion": defaultConfig.atom.setMaxSdkVersion(Integer.parseInt(value)); break;
		case "applicationId": defaultConfig.atom.setApplicationId(value); break;
		case "versionCode": defaultConfig.atom.setVersionCode(Integer.parseInt(value)); break;
		case "versionName": defaultConfig.atom.setVersionName(value); break;
		case "testInstrumentationRunner": defaultConfig.atom.setTestInstrumentationRunner(value); break;
        default:
    	// TODOD - Generate error
		}
	}

	/**
	 * Path = "android/signingConfigs"
	 * Next path element is the name of a config
	 * Terminals = storeFile, storePassword, keyAlias, keyPassword, storeType, v1SigningEnabled, v2SigningEnabled
	 * @param key
	 * @param value
	 */
	private void configureSigningConfig(String key, String value) {
		int pos = key.indexOf('/');
		if (pos > 0) {
			String name = key.substring(0, pos);
			SigningConfigBean bean = signingConfigBeanMap.get(name);
			if (bean == null) {
				bean = new SigningConfigBean(name);
				signingConfigBeanMap.put(name, bean);
			}
			key = key.substring(pos + 1);
			switch(key) {
	        case "storeFile": bean.setStoreFile(value); break;
	        case "storePassword": bean.setStorePassword(value); break;
	        case "keyAlias": bean.setKeyAlias(value); break;
	        case "keyPassword": bean.setKeyPassword(value); break;
	        case "storeType": bean.setStoreType(value); break;
	        case "v1SigningEnabled": bean.setV1SigningEnabled(Boolean.valueOf(value)); break;
	        case "v2SigningEnabled": bean.setV2SigningEnabled(Boolean.valueOf(value)); break;			
	        default:
	    	// TODOD - Generate error
		    }
		} // TODO - Report invalid config
	}

	/**
	 * Path = "android/buildTypes"
	 * Next path element is the name of a build type
	 * Terminals = debuggable, pseudoLocalesEnabled, testCoverageEnabled, jniDebuggable, renderscriptDebuggable,
	 *             renderscriptOptimLevel, minifyEnabled, signingConfig, embedMicroApp, zipAlignEnabled
	 * @param key
	 * @param value
	 */
	private void configureBuildType(String key, String value) {
		int pos = key.indexOf('/');
		if (pos > 0) {
			String name = key.substring(0, pos);
			BuildTypeComposite buildType = buildTypeMap.get(name);
			if (buildType == null) {
				buildType = new BuildTypeComposite(name);
				buildTypeMap.put(name, buildType);
			}
			key = key.substring(pos + 1);
			pos = key.indexOf('/');
			// Additional path element in case of Proguard default file: "getDefaultProguardFile" method
			String method = null;
			if (pos > 0) {
				method = key.substring(pos + 1);
				key = key.substring(0, pos);
			}
			switch(key) {
		    case "debuggable": buildType.atom.setDebuggable(Boolean.valueOf(value)); break;
		    case "pseudoLocalesEnabled": buildType.atom.setPseudoLocalesEnabled(Boolean.valueOf(value)); break;
		    case "testCoverageEnabled": buildType.atom.setTestCoverageEnabled(Boolean.valueOf(value)); break;
		    case "jniDebuggable": buildType.atom.setJniDebuggable(Boolean.valueOf(value)); break;
		    case "renderscriptDebuggable": buildType.atom.setRenderscriptDebuggable(Boolean.valueOf(value)); break;
		    case "renderscriptOptimLevel": buildType.atom.setRenderscriptOptimLevel(Integer.valueOf(value)); break;
		    case "minifyEnabled": buildType.atom.setMinifyEnabled(Boolean.valueOf(value)); break;
		    case "signingConfig": { 
		    	// References config defined in path "android/signingConfigs"
		    	if (value.startsWith(SIGNING_CONFIGS)) {
		    		name = value.substring(SIGNING_CONFIGS.length() + 1);
		    		if (signingConfigBeanMap.containsKey(name))
		    			buildType.atom.setSigningConfig(signingConfigBeanMap.get(name)); 
		    		else { // Use default config as place-holder
		    			SigningConfig defaultConfig = androidEnvironment.getDefaultDebugSigningConfig();
		    			SigningConfigBean bean = new SigningConfigBean(defaultConfig);
		    			bean.setName(name);
		    			signingConfigBeanMap.put(name, bean);
		    		}
		    	}
		    	break;
		    }
		    case "embedMicroApp": buildType.atom.setEmbedMicroApp(Boolean.valueOf(value)); break;
		    case "zipAlignEnabled": buildType.atom.setZipAlignEnabled(Boolean.valueOf(value)); break;
		    default:
		    	configureBaseConfig(buildType, key, value, method);
		    }
		}
	}

	// TODO - Configure filter patterns. Support multiple entries for some types.
	/**
	 * Path = "android/sourceSets"
	 * Next path elements are source set name followed by source set type keyword
	 * @param key
	 * @param value "[" path "]"
	 */
	private void configureSourceSet(String key, String value) {
		int pos = key.indexOf('/');
		if (pos > 0) {
			String name = key.substring(0, pos);
			key = key.substring(pos + 1);
			pos = key.indexOf('.');
			if (pos != -1) {
				key = key.substring(0, pos);
				CodeSource codeSource = CodeSource.KEYWORD_MAP.get(key);
				if (codeSource != null) {
					SourceSetComposite sourceSet = sourceSetMap.get(name);
					if (sourceSet == null) {
						sourceSet = new SourceSetComposite(name);
						sourceSetMap.put(name, sourceSet);
					}
					// Remove enclosing brackets from value
					sourceSet.sourceSetBeanMap.put(codeSource, new SourceSetBean(codeSource, value.substring(1, value.length() - 1)));
				}
			}
		}
	}

	/**
	 * Path = "android/buildTypes" name
	 * Terminals = applicationIdSuffix, versionNameSuffix, multiDexEnabled, multiDexKeepProguard, multiDexKeepFile, proguardFiles, consumerProguardFiles
	 *             testProguardFiles
	 * @param bean BaseConfig entity bean composite
	 * @param key
	 * @param value
	 * @param method "getDefaultProguardFile" or null
	 */
	private void configureBaseConfig(BaseComposite bean, String key, String value, String method) {
		switch(key) {
		case "applicationIdSuffix": bean.base.setApplicationIdSuffix(value); break;
		case "versionNameSuffix": bean.base.setVersionNameSuffix(value); break;
		case "multiDexEnabled": bean.base.setMultiDexEnabled(Boolean.valueOf(value)); break;
		case "multiDexKeepProguard": bean.base.setMultiDexKeepProguard(value);
		case "multiDexKeepFile": bean.base.setMultiDexKeepFile(value); break;
		case "proguardFiles": {
			String file = value;
			if ("getDefaultProguardFile".equals(method)) {
				ProguardFile proguardFile = new ProguardFile(fileManager);
				proguardFile.initialize();
				File projectFile = proguardFile.getDefaultProguardFile(file, projectLocation);
				if (fileManager.containsFile(projectFile.getName())) {
					fileManager.copyFile(file, projectFile);
				} // TODO - Report missing proguard file
				file = projectFile.getAbsolutePath();
			} else {
				file = projectLocation.getAbsolutePath() + File.separator + file;
			}
			bean.baseStringSet.add(new BaseString(FieldName.proguardFile, file));
			break;
		} 
		case "consumerProguardFiles": bean.baseStringSet.add(new BaseString(FieldName.consumerProguardFile, value)); break;
		case "testProguardFiles": bean.baseStringSet.add(new BaseString(FieldName.testProguardFile, value)); break;
		default: // TODO - ManifestPlaceholders, BuildConfigFields, ResValues and unknown option error
		}
	}

	/**
	 * Path = "dependencies"
	 * @param scope Scope eg. "implementation"
	 * @param items Array of up to 5 coordinates in set order
	 */
	private void configureDependency(String scope, String[] items) {
    	// Convert Gradle scope names to Maven
    	if (CONFIG_NAME_IMPLEMENTATION.equals(scope))
    		scope = CONFIG_NAME_COMPILE;
    	else if (CONFIG_NAME_API.equals(scope))
    		scope = CONFIG_NAME_COMPILE;
    	else if (CONFIG_NAME_COMPILE_ONLY.equals(scope))
    		scope = CONFIG_NAME_PROVIDED;
    	else if (CONFIG_NAME_RUNTIME_ONLY.equals(scope))
    		scope = CONFIG_NAME_RUNTIME;
    	else if (CONFIG_NAME_APK.equals(scope))
    		scope = CONFIG_NAME_RUNTIME;
    	else // TODO - test and androidTest dependencies
    		return;
	    Dependency node = new Dependency();
	    // Unset type "jar" default as the default should be "aar"
	    node.setType(null);
    	node.setScope(scope);
	    for (int i = 0; i < items.length; ++i) {
	    	switch(i) {
	    	case 0: node.setGroupId(items[0]); break;
	    	case 1: node.setArtifactId(items[1]); break;
	    	case 2: node.setVersion(items[2]); break;
	    	case 3: node.setType(items[3]);break;
	    	case 4: node.setClassifier(items[4]); break;
	    	default:
	    		return; // TODO - handle unexpected items
	    	}
	    }
	    // Default type to "aar"
	    if (node.getType() == null)
	    	node.setType(SdkConstants.EXT_AAR);
	    mavenModel.addDependency(node);
    }

	/**
	 * Path = "project"
	 * Only identity currently supported
	 * @param key
	 * @param value
	 */
    private void configureProject(String key, String value) {
    	switch (key) {
    	case "identity": configureIdentity(value); break;
        default:
        	if (key.startsWith(LOG)) 
    			configureLog(key.substring(LOG.length() + 1), value);
    		// TODOD - Generate error
     	}
    }

    private void configureLog(String key, String name) {
    	switch (key) {
    	case ERROR: SdkLogger.setLevel(name, Level.SEVERE); break;
    	case WARNING: SdkLogger.setLevel(name, Level.WARNING); break;
    	case INFO: SdkLogger.setLevel(name, Level.INFO); break;
    	case DEBUG: SdkLogger.setLevel(name, Level.FINEST); break;
        default:
    		// TODOD - Generate error
     	}
	}

	/**
     * Configure identity
     * @param value Identity colon-separated coordinates
     */
    private void configureIdentity(String value) {
	    String[] items = value.split(":");
	    if ((items.length > 1) && (items.length < 4)) {
    		setProjectIdentity(items);
	    }
	    // TODO - report invalid configuration
    }

    /**
     * Set project identity
     * @param items
     */
	private void setProjectIdentity(String[] items) {
	    for (int i = 0; i < items.length; ++i) {
	    	switch(i) {
	    	case 0: mavenModel.setGroupId(items[0]); break;
	    	case 1: mavenModel.setArtifactId(items[1]); break;
	    	case 2: mavenModel.setVersion(items[2]); break;
	    	case 3: mavenModel.setPackaging(items[3]);break;
	    	}
	    }
	    // Default packaging to "apk"
	    if (mavenModel.getPackaging() == null)
	    	 mavenModel.setPackaging(SdkConstants.EXT_ANDROID_PACKAGE);
    	// TODO - Generate error for items.length != 3 
	}

	/**
	 * Set debug signing config as default
	 */
	private void setDefaultSigningConfig() {
		SigningConfigBean bean = new SigningConfigBean(getDefaultSigningConfig());
		signingConfigBeanMap.put(BuilderConstants.DEBUG, bean);
	}

}
