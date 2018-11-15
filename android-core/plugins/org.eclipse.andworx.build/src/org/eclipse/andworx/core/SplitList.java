package org.eclipse.andworx.core;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.andworx.context.MultiOutputPolicy;

import com.android.annotations.NonNull;
import com.android.build.OutputFile;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * TODO - SplitList to be fleshed out
 */
public class SplitList {
    public static final String RESOURCE_CONFIGS = "ResConfigs";
    /**
     * Internal records to save split names and types.
     */
    private static final class Record {
    	
    }
    
	public static final SplitList EMPTY = new SplitList(ImmutableList.of());

	public static SplitList load(@NonNull Collection<File> persistedList) throws IOException {
		throw new UnsupportedOperationException();
	}

	private SplitList(List<Record> records) {
	
	}
		   
    public Set<String> getFilters(OutputFile.FilterType splitType) throws IOException {
        return getFilters(splitType.name());
    }

    public Set<String> getResourcesSplit() throws IOException {
        ImmutableSet.Builder<String> allFilters = ImmutableSet.builder();
        allFilters.addAll(getFilters(OutputFile.FilterType.DENSITY));
        allFilters.addAll(getFilters(OutputFile.FilterType.LANGUAGE));
        return allFilters.build();
    }
    
    public synchronized Set<String> getFilters(String filterType) throws IOException {
        //Optional<Record> record =
        //        records.stream().filter(r -> r.splitType.equals(filterType)).findFirst();
        //return record.isPresent() ? record.get().getValues() : ImmutableSet.of();
    	return ImmutableSet.of();
    }

    @NonNull
    public static Set<String> getSplits(
            @NonNull SplitList splitList, @NonNull MultiOutputPolicy multiOutputPolicy)
            throws IOException {
        return multiOutputPolicy == MultiOutputPolicy.SPLITS
                ? splitList.getResourcesSplit()
                : ImmutableSet.of();
    }
}
