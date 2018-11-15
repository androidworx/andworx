package org.eclipse.andworx.project;

import java.util.Objects;

/**
 * Coordinates used to distingush between artifacts. This class is immutable.
 */
public class Identity {
    protected final String artifactId;
    protected final String group;
    protected final String version;

    /**
     * Construct Identity object
     * @param group
     * @param artifactId
     * @param version
     */
    public Identity(String group, String artifactId, String version) {
        this.group = group;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return group;
    }

    public String getVersion() {
        return version;
    }

	@Override
	public String toString() {
		return String.format("%s:%s:%s", group, artifactId, version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, group, version); 
	}

	@Override
	public boolean equals(Object object) {
		if ((object == null) || !(object instanceof Identity))
			return false;
		Identity other = (Identity)object;
		return artifactId.equals(other.artifactId) && group.equals(other.group) && version.equals(other.version);
	}
}
