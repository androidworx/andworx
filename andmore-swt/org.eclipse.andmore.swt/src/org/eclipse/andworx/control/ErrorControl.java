package org.eclipse.andworx.control;

import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

public class ErrorControl {
    private final static String IMG_MATCH = "match"; 
    private final static String IMG_ERROR = "error"; 
    private final static String IMG_WARNING = "warning"; 

    private final Composite composite;
    private final PluginResourceProvider resourceProvider;
	private final Color backColor;
    /** Make icon usage subject to all images available */
    private boolean useImages;
    private Image okImage;
    private Image errorImage;
    private Image warnImage;
	
	public ErrorControl(Composite parent, PluginResourceProvider resourceProvide) {
		this.resourceProvider = resourceProvide;
    	createImages(parent.getShell());
		composite = new Composite(parent, SWT.NONE);
        composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        backColor = parent.getBackground();
        GridLayout gl = new GridLayout(1, false);
        gl.marginHeight = gl.marginWidth = 0;
        gl.verticalSpacing *= 3; // more spacing than normal.
        composite.setLayout(gl);
	}
	
	public void displyNoErrors() {
        CLabel label = new CLabel(composite, SWT.NONE);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        label.setLayoutData(gd);
        label.setText("No errors found - Click Finish.");
        label.setImage(okImage);
        label.setBackground(backColor);
	}
	
	public void setError(String message) {
        CLabel label = new CLabel(composite, SWT.NONE);
        label.setText(message);
        label.setImage(errorImage);
        label.setBackground(backColor);
        GridData gd = new GridData(SWT.FILL, GridData.VERTICAL_ALIGN_BEGINNING, true, true);
        StatusItemLayoutData layoutData = new StatusItemLayoutData(label, SWT.DEFAULT);
        gd.widthHint = layoutData.widthHint;
        gd.heightHint = layoutData.heightHint;
         label.setLayoutData(gd);
	}
	
	public void setWarning(String message) {
        CLabel label = new CLabel(composite, SWT.NONE);
        label.setText(message);
        label.setImage(warnImage);
        label.setBackground(backColor);
        GridData gd = new GridData(SWT.FILL, GridData.VERTICAL_ALIGN_BEGINNING, true, true);
        StatusItemLayoutData layoutData = new StatusItemLayoutData(label, SWT.DEFAULT);
        gd.widthHint = layoutData.widthHint;
        gd.heightHint = layoutData.heightHint;
        gd.grabExcessHorizontalSpace = true;
        label.setLayoutData(gd);
	}
	
	public void dispose() {
    	disposeImages();
		composite.dispose();
	}

    /**
     * Create all the images and ensure their disposal
     */
    private void createImages(Shell shell) {
    	useImages = false;
        ImageDescriptor descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_WARNING + ".png");
        if (descriptor != null)
        	warnImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_ERROR + ".png");
        if (descriptor != null)
        	errorImage = descriptor.createImage();
        else
        	return;
        descriptor = resourceProvider.descriptorFromPath("icons/" + IMG_MATCH + ".png"); 
        if (descriptor != null) {
        	okImage = descriptor.createImage();
        	useImages = (okImage != null) && (errorImage != null) && (warnImage != null);
        }
    }
 
    /**
     * Dispose images
     */
    private void disposeImages() {
    	if (warnImage != null)
    		warnImage.dispose();
    	if (errorImage != null)
    		errorImage.dispose();
    	if (okImage != null)
    		okImage.dispose();
   }
}
