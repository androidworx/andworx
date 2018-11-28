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

import java.io.File;

import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.project.ProjectProfile;

/**
 * Performs control functions for security configuration: validate and persist
 */
public class SecurityController {
	public static interface ErrorHandler {
		void onVailidationFail(SigningConfigField field, String message);
	}
	
	public ConfigContext<SigningConfigBean> configContext(ProjectProfile projectProfile, SigningConfigBean signingConfigBean) {
		return new ConfigContext<SigningConfigBean>(projectProfile, signingConfigBean) {

			@Override
			public synchronized void update(SigningConfigBean updateBean) {
	    		bean.setKeyAlias(updateBean.getKeyAlias());
	    		bean.setKeyPassword(updateBean.getKeyPassword());
	    		bean.setStoreFile(updateBean.getStoreFileValue());
	    		bean.setStorePassword(updateBean.getStorePassword());
	    		bean.setStoreType(updateBean.getStoreType());
	    		bean.setV1SigningEnabled(updateBean.isV1SigningEnabled());
	    		bean.setV2SigningEnabled(updateBean.isV2SigningEnabled());
			}

			@Override
			public synchronized void persist() {
				SigningConfigBean mail = new SigningConfigBean(bean);
				post(mail);
			}};
	}
	
	public boolean validate(SigningConfigBean bean, ErrorHandler errorHandler) {
		return  validateStoreFile(bean.getStoreFileValue(), errorHandler) &&
				validateStorePassword(bean.getStorePassword(), errorHandler) &&
				validateKeyAlias(bean.getKeyAlias(), errorHandler) &&
				validateKeyPassword(bean.getKeyPassword(), errorHandler) &&
				validateStoreType(bean.getStoreType(), errorHandler) &&
				validateV1SigningEnabled(bean.isV1SigningEnabled(), errorHandler) &&
				validateV2SigningEnabled(bean.isV2SigningEnabled(), errorHandler);
	}

	private boolean validateV2SigningEnabled(boolean v2SigningEnabled, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateV1SigningEnabled(boolean v1SigningEnabled, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateStoreType(String storeType, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateKeyPassword(String keyPassword, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateKeyAlias(String keyAlias, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateStorePassword(String storePassword, ErrorHandler errorHandler) {
		return true;
	}

	private boolean validateStoreFile(String storeFileValue, ErrorHandler errorHandler) {
		boolean isValid = false;
		if (!storeFileValue.isEmpty()) {
			File storeFile = new File(storeFileValue);
			if (!storeFile.exists())
				errorHandler.onVailidationFail(
						SigningConfigField.storeFile, 
						String.format("Keystore \"%s\" does not exist", storeFile.getPath()));
			else if (!storeFile.isFile())
				errorHandler.onVailidationFail(
						SigningConfigField.storeFile, 
						String.format("Keystore \"%s\" is not a file", storeFile.getPath()));
			else
				isValid = true;
		} else {
			errorHandler.onVailidationFail(
					SigningConfigField.storeFile, 
					"Please enter keystore file");
		}
		return isValid;
	}
}
