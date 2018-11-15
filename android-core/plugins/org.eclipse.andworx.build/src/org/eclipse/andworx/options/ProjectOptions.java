/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.andworx.options;

//import android.databinding.tool.util.Preconditions;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.DataBindingOptions;
import com.android.builder.model.OptionalCompilationStep;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/** Determines if various options, triggered from the command line or environment, are set. */
@Immutable
public final class ProjectOptions {

    public static final String PROPERTY_TEST_RUNNER_ARGS =
            "android.testInstrumentationRunnerArguments.";
    public static final String USE_SUPPORT_LIBRARY_KEY = "android.useSupportLibrary";
    public static final Object GENERATED_DENSITIES_KEY = "android.generatedDensities";
	public static final String DATABIND_VERSION = "3.1.1";

    private final ImmutableMap<RemovedOptions, String> removedOptions;
    private final ImmutableMap<BooleanOption, Boolean> booleanOptions;
    private final ImmutableMap<OptionalBooleanOption, Boolean> optionalBooleanOptions;
    private final ImmutableMap<IntegerOption, Integer> integerOptions;
    private final ImmutableMap<LongOption, Long> longOptions;
    private final ImmutableMap<StringOption, String> stringOptions;
    private final ImmutableMap<String, String> testRunnerArgs;
    private final EnumOptions enumOptions;
    // VectorDrawablesOption
    @Nullable
    private final Set<String> generatedDensities;
    // TODO - Add DataBindingOptions attributes to Options set
    private AndworxDataBindingOptions dataBindingOptions;

    @SuppressWarnings("unchecked")
	public ProjectOptions(@NonNull ImmutableMap<String, Object> properties) {

        removedOptions = readOptions(RemovedOptions.values(), properties);
        booleanOptions = readOptions(BooleanOption.values(), properties);
        optionalBooleanOptions =
                readOptions(OptionalBooleanOption.values(), properties);
        integerOptions = readOptions(IntegerOption.values(), properties);
        longOptions = readOptions(LongOption.values(), properties);
        stringOptions = readOptions(StringOption.values(), properties);
        enumOptions =
                EnumOptions.load(
                        readOptions(
                                EnumOptions.EnumOption.values(),
                                properties));
        testRunnerArgs = readTestRunnerArgs(properties);
        generatedDensities = (Set<String>)properties.get(GENERATED_DENSITIES_KEY);
        dataBindingOptions = new AndworxDataBindingOptions();
        dataBindingOptions.setVersion(DATABIND_VERSION);
    }

    /**
     * Option to configure the build-time support for {@code vector} drawables.
     */
    @Nullable
        public Set<String>  getGeneratedDensities() {
        return generatedDensities;
    }

    /**
     * Specifies options for the <a
     * href="https://developer.android.com/topic/libraries/data-binding/index.html">Data Binding
     * Library</a>.
     *
     * <p>Data binding helps you write declarative layouts and minimize the glue code necessary to
     * bind your application logic and layouts.
     */
	public DataBindingOptions getDataBinding() {
		return dataBindingOptions;
	}

	public boolean get(BooleanOption option) {
        return booleanOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public Boolean get(OptionalBooleanOption option) {
        return optionalBooleanOptions.get(option);
    }

    @Nullable
    public Integer get(IntegerOption option) {
        return integerOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public Long get(LongOption option) {
        return longOptions.getOrDefault(option, option.getDefaultValue());
    }

    @Nullable
    public String get(StringOption option) {
        return stringOptions.getOrDefault(option, option.getDefaultValue());
    }

    @NonNull
    public Map<String, String> getExtraInstrumentationTestRunnerArgs() {
        return testRunnerArgs;
    }

    @NonNull
    public Set<OptionalCompilationStep> getOptionalCompilationSteps() {
        String values = get(StringOption.IDE_OPTIONAL_COMPILATION_STEPS);
        if (values != null) {
            List<OptionalCompilationStep> optionalCompilationSteps = new ArrayList<>();
            StringTokenizer st = new StringTokenizer(values, ",");
            while (st.hasMoreElements()) {
                optionalCompilationSteps.add(OptionalCompilationStep.valueOf(st.nextToken()));
            }
            return EnumSet.copyOf(optionalCompilationSteps);
        }
        return EnumSet.noneOf(OptionalCompilationStep.class);
    }

    public EnumOptions getEnumOptions() {
        return enumOptions;
    }


    public boolean hasRemovedOptions() {
        return !removedOptions.isEmpty();
    }

    @NonNull
    public String getRemovedOptionsErrorMessage() {
        //Preconditions.check(
        //        hasRemovedOptions(),
        //        "Has removed options should be checked before calling this method.");
        StringBuilder builder =
                new StringBuilder(
                        "The following project options are deprecated and have been removed: \n");
        removedOptions.forEach(
                (option, errorMessage) -> {
                    builder.append(option.getPropertyName())
                            .append("\n")
                            .append(errorMessage)
                            .append("\n\n");
                });
        return builder.toString();
    }

    public ImmutableMap<BooleanOption, Boolean> getExplicitlySetBooleanOptions() {
        return booleanOptions;
    }

    public ImmutableMap<OptionalBooleanOption, Boolean> getExplicitlySetOptionalBooleanOptions() {
        return optionalBooleanOptions;
    }

    public ImmutableMap<IntegerOption, Integer> getExplicitlySetIntegerOptions() {
        return integerOptions;
    }

    public ImmutableMap<LongOption, Long> getExplicitlySetLongOptions() {
        return longOptions;
    }

    public ImmutableMap<StringOption, String> getExplicitlySetStringOptions() {
        return stringOptions;
    }

    @NonNull
    private static ImmutableMap<String, Object> copyProperties(@NonNull ImmutableMap<String, Object> properties) {
        ImmutableMap.Builder<String, Object> optionsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry :properties.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                optionsBuilder.put(entry.getKey(), value);
            }
        }
        return optionsBuilder.build();
    }

    @NonNull
    private static <OptionT extends Option<ValueT>, ValueT>
            ImmutableMap<OptionT, ValueT> readOptions(
                    @NonNull OptionT[] values,
                    @NonNull Map<String, ?> properties) {
        Map<String, OptionT> optionLookup =
                Arrays.stream(values).collect(Collectors.toMap(Option::getPropertyName, v -> v));
        ImmutableMap.Builder<OptionT, ValueT> valuesBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> property : properties.entrySet()) {
            OptionT option = optionLookup.get(property.getKey());
            if (option != null) {
                ValueT value = option.parse(property.getValue());
                valuesBuilder.put(option, value);
            }
        }
        return valuesBuilder.build();
    }

    @NonNull
    private static ImmutableMap<String, String> readTestRunnerArgs(
            @NonNull Map<String, ?> properties) {
        ImmutableMap.Builder<String, String> testRunnerArgsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(PROPERTY_TEST_RUNNER_ARGS)) {
                String argName = name.substring(PROPERTY_TEST_RUNNER_ARGS.length());
                String argValue = entry.getValue().toString();
                testRunnerArgsBuilder.put(argName, argValue);
            }
        }
        return testRunnerArgsBuilder.build();
    }

}
