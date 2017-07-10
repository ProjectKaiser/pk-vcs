package org.scm4j.actions.results;

public class ActionResultVersion {
	private String name;
	private String version;
	private Boolean isNewBuild;
	private String newBranchName;
	
	public String getNewBranchName() {
		return newBranchName;
	}

	public String getName() {
		return name;
	}

	public String getVersion() {
		return version;
	}
	
	public Boolean getIsNewBuild() {
		return isNewBuild;
	}

	public ActionResultVersion(String name, String version, Boolean isNewBuild, String newBranchName) {
		this.name = name;
		this.version = version;
		this.isNewBuild = isNewBuild;
		this.newBranchName = newBranchName;
	}

	@Override
	public String toString() {
		return "ActionResultVersion [name=" + name + ", version=" + version + ", isNewBuild=" + isNewBuild
				+ ", newBranchName=" + newBranchName + "]";
	}

}
