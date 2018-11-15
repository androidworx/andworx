package com.android.ddmuilib.log.event;

import com.android.ddmlib.log.EventContainer.CompareMethod;
import com.android.ddmuilib.log.event.EventDisplay.OccurrenceDisplayDescriptor;

interface DisplayDescriptor {

	int getEventTag();
	Object getFilterValue();
    int getFilterValueIndex();
    CompareMethod getFilterCompareMethod();
	
	void replaceWith(OccurrenceDisplayDescriptor descriptor);

	/**
	 * Loads the parameters from an array of strings.
	 *
	 * @param storageStrings the strings representing each parameter.
	 * @param index          the starting index in the array of strings.
	 * @return the new index in the array.
	 */
	int loadFrom(String[] storageStrings, int index);

	/**
	 * Returns the storage string for the receiver.
	 */
	String getStorageString();

}