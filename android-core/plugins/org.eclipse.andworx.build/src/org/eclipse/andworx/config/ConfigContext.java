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
package org.eclipse.andworx.config;

import static com.android.builder.core.BuilderConstants.RELEASE;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.event.AndworxEvents;
import org.eclipse.andworx.project.ProjectProfile;
import org.eclipse.andworx.registry.ProjectState;

/**
 * Context for persisting an entity bean
 * @param <T>
 */
public abstract class ConfigContext {
	protected ProjectState projectState;
	protected SigningConfigBean bean;
	private IEventBroker eventBroker;
	
	public ConfigContext(ProjectState projectState, SigningConfigBean bean) {
		this.projectState = projectState;
		this.bean = bean;
	}

	public ProjectProfile getProjectProfile() {
		return projectState.getProfile();
	}

	public SigningConfigBean getBean() {
		return bean;
	}

	protected void post(SigningConfigBean updateBean) {
		if (eventBroker == null) {
	        IEclipseContext eclipseContext = AndworxFactory.instance().getEclipseContext(); 
	    	eventBroker = (IEventBroker) eclipseContext.get(IEventBroker.class.getName());
		}
		projectState.getContext().getVariantConfiguration(RELEASE).getBuildType().setSigningConfig(updateBean);
		eventBroker.post(AndworxEvents.UPDATE_ENTITY_BEAN, updateBean);
	}
	
	abstract public void update(SigningConfigBean updateBean);
	
	abstract public void persist();
}
