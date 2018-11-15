package org.eclipse.andmore.internal.wizards.newproject;

import org.eclipse.andworx.polyglot.PolyglotAgent;
import org.eclipse.andworx.project.Identity;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;

public class ProjectCellLabelProvider extends CellLabelProvider {
	public static final int DIR_COLUMN = 0;
	public static final int NAME_COLUMN = 1;

    private Table table;
    private CheckboxTableViewer checkboxTableViewer;

    public ProjectCellLabelProvider(Table table, CheckboxTableViewer checkboxTableViewer) {
    	this.table = table;
    	this.checkboxTableViewer = checkboxTableViewer;
    }
    
    @Override
    public void update(ViewerCell cell) {
        Object element = cell.getElement();
        int index = cell.getColumnIndex();
        ImportedProject project = (ImportedProject) element;

        Display display = table.getDisplay();
        Color fg;
        if (checkboxTableViewer.getGrayed(element)) {
            fg = display.getSystemColor(SWT.COLOR_DARK_GRAY);
        } else {
            fg = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
        }
        cell.setForeground(fg);
        cell.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));

        switch (index) {
            case DIR_COLUMN: {
                // Directory name
                cell.setText(project.getRelativePath());
                return;
            }

            case NAME_COLUMN: {
                // New name
            	Identity identity = project.getProjectProfile().getIdentity();
            	if (PolyglotAgent.DEFAULT_GROUP_ID.equals(identity.getGroupId()))
            		cell.setText(identity.getArtifactId());
            	else
            		cell.setText(identity.toString());
                return;
            }
            default:
                assert false : index;
        }
        cell.setText("");
    }

}
