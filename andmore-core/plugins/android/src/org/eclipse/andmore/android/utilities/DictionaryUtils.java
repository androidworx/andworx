package org.eclipse.andmore.android.utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.andmore.android.common.IAndroidConstants;
import org.eclipse.andmore.android.common.utilities.i18n.UtilitiesNLS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Class that contains methods to do common tasks with string.xml files.
 */
public class DictionaryUtils {

	class StringNode {}
	class StringArrayNode {}
	
	private static final String LOCALIZATION_FILE_TYPE = "org.eclipse.sequoyah.localization.android.datamodel.AndroidStringLocalizationFile"; //$NON-NLS-1$

	/**
	 * Add strings and array to string.xml file
	 * 
	 * @param project
	 * @param strings
	 *            string entries to add
	 * @param arrays
	 *            array entries to add
	 * @param monitor
	 *            array entries to add
	 * @throws CoreException
	 * @throws IOException
	 */
	public static void createOrUpdateDictionaryFile(IProject project, Map<String, String> strings,
			Map<String, List<String>> arrays, IProgressMonitor monitor) throws IOException, CoreException {
		/*
		List<StringNode> stringNodes = new ArrayList<StringNode>();
		List<StringArrayNode> arrayNodes = new ArrayList<StringArrayNode>();

		if (strings != null) {
			Set<String> stringSet = strings.keySet();
			for (String key : stringSet) {
				String strValue = strings.get(key);
				stringNodes.add(new StringNode(key, strValue));
			}
		}
		if (arrays != null) {
			Set<String> arraySet = arrays.keySet();
			for (String key : arraySet) {
				List<String> arrayValues = arrays.get(key);
				StringArrayNode strArray = new StringArrayNode(key);
				for (String value : arrayValues) {
					strArray.addValue(value);
				}
				arrayNodes.add(strArray);
			}
		}
		createOrUpdateDictionaryFile(project, stringNodes, arrayNodes, monitor);
		*/
	}

	/**
	 * Add strings and array to string.xml file
	 * 
	 * @param project
	 * @param strings
	 *            string entries to add
	 * @param arrays
	 *            array entries to add
	 * @param monitor
	 *            array entries to add
	 * @throws CoreException
	 * @throws IOException
	 */
	public static void createOrUpdateDictionaryFile(IProject project, List<StringNode> strings,
			List<StringArrayNode> arrays, IProgressMonitor monitor) throws IOException, CoreException {
		/*
		int taskSize = strings != null ? strings.size() : 0;
		taskSize += arrays != null ? arrays.size() : 0;

		monitor.beginTask(UtilitiesNLS.UI_ProjectCreationSupport_Creating_Strings_Task, (taskSize * 100) + 100);

		IFile projectStringXmlFile = project.getFile(IAndroidConstants.RES_DIR + IAndroidConstants.VALUES_DIR
				+ IAndroidConstants.STRINGS_FILE);

		LocalizationFile locFile = null;

		if (projectStringXmlFile.exists()) {

			ProjectLocalizationManager projManager = LocalizationManager.getInstance().getProjectLocalizationManager(
					project, true);

			// load localization file
			locFile = projManager.getProjectLocalizationSchema().loadFile(LOCALIZATION_FILE_TYPE, projectStringXmlFile);
			if (locFile.getLocalizationProject() == null) {
				locFile.setLocalizationProject(projManager.getLocalizationProject());
			}

			// add new string nodes
			for (StringNode strNode : strings) {
				((StringLocalizationFile) locFile).addStringNode(strNode);
			}
			List<StringArrayNode> currentArrays = ((StringLocalizationFile) locFile).getStringArrays();
			List<StringArrayNode> newArrays = new ArrayList<StringArrayNode>();
			newArrays.addAll(currentArrays);

			// add new array nodes
			for (StringArrayNode strArray : arrays) {
				newArrays.add(strArray);
			}
			((StringLocalizationFile) locFile).setStringArrayNodes(newArrays);

			// update file
			LocalizationManager.getInstance().getLocalizationSchema(project).updateFile(locFile);
		} else {
			ILocalizationSchema locSchema = LocalizationManager.getInstance().getLocalizationSchema(project);

			LocalizationFileBean bean = new LocalizationFileBean(LOCALIZATION_FILE_TYPE, projectStringXmlFile,
					new LocaleInfo(), strings, arrays);

			locFile = locSchema.createLocalizationFile(bean);

			locSchema.createLocalizationFile(locFile);
		}
		*/
	}
	
}
