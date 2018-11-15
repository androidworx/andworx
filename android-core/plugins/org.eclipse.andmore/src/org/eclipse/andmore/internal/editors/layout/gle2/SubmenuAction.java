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

package org.eclipse.andmore.internal.editors.layout.gle2;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

/**
 * Action which creates a submenu that is dynamically populated by subclasses
 */
public abstract class SubmenuAction extends Action implements MenuListener, IMenuCreator {
    private Menu mMenu;

    public SubmenuAction(String title) {
        super(title, IAction.AS_DROP_DOWN_MENU);
    }

    @Override
    public IMenuCreator getMenuCreator() {
        return this;
    }

    @Override
    public void dispose() {
        if (mMenu != null) {
            mMenu.dispose();
            mMenu = null;
        }
    }

    @Override
    public Menu getMenu(Control parent) {
        return null;
    }

    @Override
    public Menu getMenu(Menu parent) {
        mMenu = new Menu(parent);
        mMenu.addMenuListener(this);
        return mMenu;
    }

    @Override
    public void menuHidden(MenuEvent e) {
    }

    protected abstract void addMenuItems(Menu menu);

    @Override
    public void menuShown(MenuEvent e) {
        // TODO: Replace this stuff with manager.setRemoveAllWhenShown(true);
        MenuItem[] menuItems = mMenu.getItems();
        for (int i = 0; i < menuItems.length; i++) {
            menuItems[i].dispose();
        }
        addMenuItems(mMenu);
    }

    protected void addDisabledMessageItem(String message) {
        IAction action = new Action(message, IAction.AS_PUSH_BUTTON) {
            @Override
            public void run() {
            }
        };
        action.setEnabled(false);
        new ActionContributionItem(action).fill(mMenu, -1);

    }
}
