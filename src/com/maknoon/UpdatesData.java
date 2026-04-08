package com.maknoon;

class UpdatesData
{
	public final String version, description, size;
    public boolean installStatus, disabled; // Used to disable the checkbox when it is not allowed to select it e.g. duplicate ACU files, not compatible DB ... etc
    
	UpdatesData(boolean updateInstallStatus, String updateDescription, String updateVersion, String updateSize, boolean disableIt)
	{
		installStatus = updateInstallStatus;
		description = updateDescription;
		version = updateVersion;
		size = updateSize;
		disabled = disableIt;
	}
}