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
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import org.eclipse.andworx.entity.SigningConfigBean;
import org.eclipse.andworx.registry.ProjectState;

/**
 * Performs control functions for security configuration: validate and persist
 */
public class SecurityController {
    private static final String ENCRYPTION_ERROR = "Encryption error";
    
	public static interface ErrorHandler {
		void onVailidationFail(SigningConfigField field, String message);
	}
	
    public static final String[] KEYSTORE_TYPES =
    {
            "JKS",
            "JCEKS",
            "PKCS12"
    };

	public ConfigContext configContext(ProjectState projectState, SigningConfigBean signingConfigBean) {
		return new ConfigContext(projectState, signingConfigBean) {

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
				validateKey(bean, errorHandler) &&
				validateV1SigningEnabled(bean.isV1SigningEnabled(), errorHandler) &&
				validateV2SigningEnabled(bean.isV2SigningEnabled(), errorHandler);
	}

	private boolean validateKey(SigningConfigBean bean, ErrorHandler errorHandler) {
        char[] keypass = bean.getKeyPassword().toCharArray();
        String storeFile = bean.getStoreFileValue();
        try
        {
            KeyStore keyStore = getKeyStore(storeFile, bean.getStoreType(), keypass);
            boolean isAliasValid = false;
            Enumeration<String> aliases = keyStore.aliases(); 
            while (aliases.hasMoreElements())
            {
                String alias = aliases.nextElement();
                if (alias.equals(bean.getKeyAlias())) {
	                if (keyStore.isKeyEntry(alias)) {
	                	isAliasValid = keyStore.getCertificateChain(alias).length > 0;
	                }
	                break;
                }
            }
            if (!isAliasValid) {
    			errorHandler.onVailidationFail(
    					SigningConfigField.keyAlias, "Key alias not found or missing certificate");
    			return false;
            }

        }
        catch (NoSuchAlgorithmException e)
        {
			errorHandler.onVailidationFail(
					SigningConfigField.storeType, ENCRYPTION_ERROR + ": " + e.getMessage());
			return false;
        }
        catch (KeyStoreException e)
        {
			errorHandler.onVailidationFail(
					SigningConfigField.storeFile, 
					String.format("Security error in \"%s\": %s", storeFile, e.getMessage()));
			return false;
        }
        catch (CertificateException e)
        {
			errorHandler.onVailidationFail(
					SigningConfigField.storeFile, 
					String.format("Certificate error in \"%s\": ", storeFile, e.getMessage()));
			return false;
         }
        catch (IOException e)
        {
			errorHandler.onVailidationFail(
					SigningConfigField.storePassword, // Password incorrect most likely cause
					"Error opening keystore - check password is correct");
			return false;
        }
		return true;
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

    /**
     * Returns loaded keystore
     * @param keyStoreFile The file path 
     * @param keyStoreType The keystore type
     * @param keypass The keystore password 
     * @return KeyStore object
     * @throws KeyStoreException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws CertificateException
     */
    public KeyStore getKeyStore(String keyStoreFile, String keyStoreType, char[] keypass) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException
    {
        KeyStore keyStore =  KeyStore.getInstance(keyStoreType);
        FileInputStream fileStream = null;
        try
        {
             fileStream = new FileInputStream(keyStoreFile);
             keyStore.load(fileStream, keypass);
        }
        finally
        {
            if (fileStream != null)
                try
                {   // Close quietly 
                    fileStream.close();
                }
                catch (IOException e)
                {
                }
        }
        return keyStore;
    }

}
