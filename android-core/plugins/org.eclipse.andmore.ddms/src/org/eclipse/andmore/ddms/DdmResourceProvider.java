package org.eclipse.andmore.ddms;

import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.jface.resource.ImageDescriptor;

public class DdmResourceProvider implements PluginResourceProvider {

	@Override
	public ImageDescriptor descriptorFromPath(String imagePath) {
		return DdmsPlugin.getImageDescriptor("ddm/" + imagePath);
	}

}
