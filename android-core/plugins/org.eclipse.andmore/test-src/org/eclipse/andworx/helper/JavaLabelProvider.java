package org.eclipse.andworx.helper;

import java.net.URL;

import org.eclipse.andmore.base.resources.JFaceImageLoader;
import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

public class JavaLabelProvider  extends LabelProvider {

	private static final Image projectOpen;
	private static final Image projectClosed;
	
	static {
		JFaceImageLoader imageLoader = new JFaceImageLoader(new PluginResourceProvider() {

			@Override
			public ImageDescriptor descriptorFromPath(String imagePath) {
				return imageDescriptorFromPlugin(imagePath);
			}});
		projectOpen = imageLoader.getImage("icons/prj_obj.png");
		projectClosed =  imageLoader.getImage("icons/folder.png");
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return true;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}

	@Override
	public Image getImage(Object element) {
		boolean isOpen = false;
		if (element instanceof IJavaProject) {
			IJavaProject jp = (IJavaProject)element;
			isOpen = jp.getProject().isOpen();
		} 
		else if (element instanceof IProject) {
			IProject project = (IProject)element;
			isOpen =project.isOpen();
		}
		return isOpen ? projectOpen : projectClosed;
	}

	@Override
	public String getText(Object element) {
		if (element instanceof IJavaProject) {
			IJavaProject jp = (IJavaProject)element;
			return jp.getElementName();
		}
		else if (element instanceof IProject) {
			IProject project = (IProject)element;
			return project.getName();
		}
		else 
			return null;
	}

    private static ImageDescriptor imageDescriptorFromPlugin(String imagePath) {
    	String path = imagePath;
    	ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    	URL url = classLoader.getResource(path);
    	if (url == null)
    		throw new IllegalArgumentException("Image path " + imagePath + " not found");
    	return ImageDescriptor.createFromURL(url);
    }
}
