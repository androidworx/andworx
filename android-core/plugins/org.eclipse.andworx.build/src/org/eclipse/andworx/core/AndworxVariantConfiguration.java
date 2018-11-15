package org.eclipse.andworx.core;

import org.eclipse.andworx.model.BuildTypeImpl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.ManifestAttributeSupplier;
import com.android.builder.core.VariantType;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.SourceProvider;

/**
 * Variant context of default configuration type ProductFlavor
 */
public class AndworxVariantConfiguration extends VariantConfiguration< ProductFlavor> {

	/**
	 * Construct  AndworxVariantConfiguration object
	 * @param defaultConfig Default ProductFlavor configuration
	 * @param defaultSourceProvider Default seource provider
	 * @param mainManifestAttributeSupplier Supplier of parsed main manifest
	 * @param buildType Build type
	 * @param buildTypeSourceProvider Build type source provider
	 * @param type Variant type
	 */
	AndworxVariantConfiguration(
			ProductFlavor defaultConfig, 
			SourceProvider defaultSourceProvider,
			ManifestAttributeSupplier mainManifestAttributeSupplier, 
			BuildTypeImpl buildType,
			SourceProvider buildTypeSourceProvider, 
			VariantType type) {
		super(defaultConfig, defaultSourceProvider, mainManifestAttributeSupplier, buildType, buildTypeSourceProvider, type);
	}

    /** Interface for building the {@link GradleVariantConfiguration} instances. */
    public interface Builder {
        /** Creates a variant configuration */
        @NonNull
        AndworxVariantConfiguration create(
                @NonNull ProductFlavor defaultConfig,
                @NonNull SourceProvider defaultSourceProvider,
                @Nullable ManifestAttributeSupplier mainManifestAttributeSupplier,
                @NonNull BuildTypeImpl buildType,
                @Nullable SourceProvider buildTypeSourceProvider,
                @NonNull VariantType type);
    }

    /** Builder for non-testing variant configurations */
    public static class VariantConfigurationBuilder implements Builder{
        @Override
        @NonNull
        public AndworxVariantConfiguration create(
                @NonNull ProductFlavor defaultConfig,
                @NonNull SourceProvider defaultSourceProvider,
                @Nullable ManifestAttributeSupplier mainManifestAttributeSupplier,
                @NonNull BuildTypeImpl buildType,
                @Nullable SourceProvider buildTypeSourceProvider,
                @NonNull VariantType type) {
            return new AndworxVariantConfiguration(
                    defaultConfig,
                    defaultSourceProvider,
                    mainManifestAttributeSupplier,
                    buildType,
                    buildTypeSourceProvider,
                    type);
        }
    }

	 

}
