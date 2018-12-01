/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.eclipse.andmore.internal.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.andmore.AndmoreAndroidPlugin;
import org.eclipse.andmore.internal.project.ProjectChooserHelper;
import org.eclipse.andworx.maven.Dependency;
import org.eclipse.andworx.polyglot.PolyglotAgent;
import org.eclipse.andworx.project.AndroidProjectCollection;
import org.eclipse.andworx.project.AndroidProjectCollection.IProjectChooserFilter;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.registry.ProjectState;
import org.eclipse.andworx.repo.DependencyArtifact;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;


/**
 * Self-contained UI to edit the library dependencies of a Project.
 */
final class LibraryProperties {
    /**
     * Internal struct to store library info in the table item.
     */
    private final static class ItemData {
        Dependency dependency;
        IProject project;
    }

    private Composite mTop;
    private Table mTable;
    private Image mMatchIcon;
    private Image mErrorIcon;
    private Button mAddButton;
    private Button mRemoveButton;
    private Button mUpButton;
    private Button mDownButton;
    private ProjectChooserHelper mProjectChooser;
    

    /**
     * Original ProjectState being edited. This is read-only.
     * @see #mPropertiesWorkingCopy
     */
    private ProjectState mState;
    private Set<Dependency> dependenciesWorkingCopy;

    private final List<ItemData> mItemDataList = new ArrayList<ItemData>();
    private boolean mMustSave = false;


    public Set<Dependency> getDependencies() {
    	return dependenciesWorkingCopy != null ? dependenciesWorkingCopy : Collections.emptySet();
    }
    
    /**
     * {@link IProjectChooserFilter} implementation that dynamically ignores libraries
     * that are already dependencies.
     */
    IProjectChooserFilter mFilter = new IProjectChooserFilter() {
        @Override
        public boolean accept(IProject project) {
            // first check if it's a library
            ProjectState state = AndworxFactory.instance().getProjectState(project);
            if (state != null) {
                if (state.isLibrary() == false || project == mState.getProject()) {
                    return false;
                }

                // then check if the library is not already part of the dependencies.
                for (ItemData data : mItemDataList) {
                    if (data.project == project) {
                        return false;
                    }
                }

                return true;
            }

            return false;
        }

        @Override
        public boolean useCache() {
            return false;
        }
    };

    LibraryProperties(Composite parent) {

        mMatchIcon = AndmoreAndroidPlugin.getImageDescriptor("/icons/match.png").createImage(); //$NON-NLS-1$
        mErrorIcon = AndmoreAndroidPlugin.getImageDescriptor("/icons/error.png").createImage(); //$NON-NLS-1$
        // Layout has 2 column
        mTop = new Composite(parent, SWT.NONE);
        mTop.setLayout(new GridLayout(2, false));
        mTop.setLayoutData(new GridData(GridData.FILL_BOTH));
        mTop.setFont(parent.getFont());
        mTop.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                mMatchIcon.dispose();
                mErrorIcon.dispose();
            }
        });

        mTable = new Table(mTop, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        mTable.setLayoutData(new GridData(GridData.FILL_BOTH));
        mTable.setHeaderVisible(true);
        mTable.setLinesVisible(false);
        mTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                resetEnabled();
            }
        });

        final TableColumn column0 = new TableColumn(mTable, SWT.NONE);
        column0.setText("Reference");
        final TableColumn column1 = new TableColumn(mTable, SWT.NONE);
        column1.setText("Project");

        Composite buttons = new Composite(mTop, SWT.NONE);
        buttons.setLayout(new GridLayout());
        buttons.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        AndroidProjectCollection androidProjects = new AndroidProjectCollection(mFilter);
        mProjectChooser = new ProjectChooserHelper(parent.getShell(), androidProjects);

        mAddButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mAddButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mAddButton.setText("Add...");
        mAddButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IJavaProject javaProject = mProjectChooser.chooseJavaProject(null /*projectName*/,
                        "Please select a library project");
                if (javaProject != null) {
                    IProject iProject = javaProject.getProject();
                    Dependency dependency = new DependencyArtifact(PolyglotAgent.DEFAULT_GROUP_ID, iProject.getName(), PolyglotAgent.DEFAULT_VERSION);
                    addItem(dependency, iProject, -1);
                    resetEnabled();
                    mMustSave = true;
                }
            }
        });

        mRemoveButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mRemoveButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mRemoveButton.setText("Remove");
        mRemoveButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // selection is ensured and in single mode.
                TableItem selection = mTable.getSelection()[0];
                ItemData data = (ItemData) selection.getData();
                mItemDataList.remove(data);
                mTable.remove(mTable.getSelectionIndex());
                resetEnabled();
                mMustSave = true;
            }
        });

        Label l = new Label(buttons, SWT.SEPARATOR | SWT.HORIZONTAL);
        l.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        mUpButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mUpButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mUpButton.setText("Up");
        mUpButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = mTable.getSelectionIndex();
                ItemData data = mItemDataList.remove(index);
                mTable.remove(index);

                // add at a lower index.
                addItem(data.dependency, data.project, index - 1);

                // reset the selection
                mTable.select(index - 1);
                resetEnabled();
                mMustSave = true;
            }
        });

        mDownButton = new Button(buttons, SWT.PUSH | SWT.FLAT);
        mDownButton.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        mDownButton.setText("Down");
        mDownButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = mTable.getSelectionIndex();
                ItemData data = mItemDataList.remove(index);
                mTable.remove(index);

                // add at a higher index.
                addItem(data.dependency, data.project, index + 1);

                // reset the selection
                mTable.select(index + 1);
                resetEnabled();
                mMustSave = true;
            }
        });

        adjustColumnsWidth(mTable, column0, column1);
    }

    /**
     * Sets or reset the content.
     * @param state the {@link ProjectState} to display. This is read-only.
     * @param propertiesWorkingCopy the working copy of {@link ProjectProperties} to modify.
     */
    void setContent(ProjectState state) {
        mState = state;
        dependenciesWorkingCopy = new HashSet<>();
        // reset content
        mTable.removeAll();
        mItemDataList.clear();
        // TODO - dependencies configuration
        mMustSave = false;
        resetEnabled();
    }

    /**
     * Saves the state of the UI into the {@link ProjectProperties} object that was returned by
     * {@link #setContent}.
     * <p/>This does not update the {@link ProjectState} object that was provided, nor does it save
     * the new properties on disk. Saving the properties on disk, via
     * {@link ProjectPropertiesWorkingCopy#save()}, and updating the {@link ProjectState} instance,
     * via {@link ProjectState#reloadProperties()} must be done by the caller.
     * @return <code>true</code> if there was actually new data saved in the project state, false
     * otherwise.
     */
    boolean save() {
        boolean mustSave = mMustSave;
        if (mMustSave) {
        	dependenciesWorkingCopy.clear();
            for (ItemData data : mItemDataList) {
            	dependenciesWorkingCopy.add(data.dependency);
            }
            
        }
        mMustSave = false;
        return mustSave;
    }

    /**
     * Enables or disables the whole widget.
     * @param enabled whether the widget must be enabled or not.
     */
    void setEnabled(boolean enabled) {
        if (enabled == false) {
            mTable.setEnabled(false);
            mAddButton.setEnabled(false);
            mRemoveButton.setEnabled(false);
            mUpButton.setEnabled(false);
            mDownButton.setEnabled(false);
        } else {
            mTable.setEnabled(true);
            mAddButton.setEnabled(true);
            resetEnabled();
        }
    }

    private void resetEnabled() {
        int index = mTable.getSelectionIndex();
        mRemoveButton.setEnabled(index != -1);
        mUpButton.setEnabled(index > 0);
        mDownButton.setEnabled(index != -1 && index < mTable.getItemCount() - 1);
    }

    /**
     * Adds a new item and stores a {@link Stuff} into {@link #mStuff}.
     *
     * @param dependency the library identity
     * @param project the associated IProject
     * @param index if different than -1, the index at which to insert the item.
     */
    private void addItem(Dependency dependency, IProject project, int index) {
        ItemData data = new ItemData();
        data.dependency = dependency;
        data.project = project;
        TableItem item;
        if (index == -1) {
            mItemDataList.add(data);
            item = new TableItem(mTable, SWT.NONE);
        } else {
            mItemDataList.add(index, data);
            item = new TableItem(mTable, SWT.NONE, index);
        }
        item.setData(data);
        String value = data.dependency.toString();
        item.setText(0, value);
        item.setImage(data.project != null ? mMatchIcon : mErrorIcon);
        item.setText(1, data.project != null ? data.project.getName() : "?");
    }

    /**
     * Adds a listener to adjust the columns width when the parent is resized.
     * <p/>
     * If we need something more fancy, we might want to use this:
     * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet77.java?view=co
     */
    private void adjustColumnsWidth(final Table table,
            final TableColumn column0,
            final TableColumn column1) {
        // Add a listener to resize the column to the full width of the table
        table.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                Rectangle r = table.getClientArea();
                column0.setWidth(r.width * 50 / 100); // 50%
                column1.setWidth(r.width * 50 / 100); // 50%
            }
        });
    }
}

