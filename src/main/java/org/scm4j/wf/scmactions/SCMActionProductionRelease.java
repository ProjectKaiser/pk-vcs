package org.scm4j.wf.scmactions;

import org.scm4j.commons.progress.IProgress;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.wf.LogTag;
import org.scm4j.wf.SCMWorkflow;
import org.scm4j.wf.Utils;
import org.scm4j.wf.actions.ActionAbstract;
import org.scm4j.wf.actions.IAction;
import org.scm4j.wf.actions.results.ActionResultVersion;
import org.scm4j.wf.branchstatus.DevelopBranch;
import org.scm4j.wf.conf.Component;
import org.scm4j.wf.conf.MDepsFile;
import org.scm4j.wf.conf.Version;

import java.util.ArrayList;
import java.util.List;

public class SCMActionProductionRelease extends ActionAbstract {
	
	private final ProductionReleaseReason reason;

	public SCMActionProductionRelease(Component dep, List<IAction> childActions, ProductionReleaseReason reason) {
		super(dep, childActions);
		this.reason = reason;
	}

	public ProductionReleaseReason getReason() {
		return reason;
	}

	@Override
	public Object execute(IProgress progress) {
		try {
			
			IVCS vcs = getVCS();
			DevelopBranch devBranch = new DevelopBranch(comp);
			
			Version currentVer = devBranch.getVersion();
			progress.reportStatus("current trunk version: " + currentVer);
			
			Object nestedResult;
			for (IAction action : childActions) {
				try (IProgress nestedProgress = progress.createNestedProgress(action.getName())) {
					nestedResult = action.execute(nestedProgress);
					if (nestedResult instanceof Throwable) {
						return nestedResult;
					}
				}
				addResult(action.getName(), nestedResult);
			}
			
			// Are we built already?
			ActionResultVersion existingResult = (ActionResultVersion) getResult(getName(), ActionResultVersion.class);
			if (existingResult != null) {
				progress.reportStatus("using already built version " + existingResult.getVersion());
				return existingResult;
			}
			
			// We have a new versions map. Will write it to mdeps on the ground
			VCSCommit newVersionStartsFromCommit;
			List<String> mDepsChanged = new ArrayList<>();
			if (vcs.fileExists(devBranch.getName(), SCMWorkflow.MDEPS_FILE_NAME)) {
				String mDepsContent = vcs.getFileContent(devBranch.getName(), SCMWorkflow.MDEPS_FILE_NAME);
				MDepsFile mDepsFile = new MDepsFile(mDepsContent, comp.getVcsRepository());
				List<String> mDepsOut = new ArrayList<>();
				String mDepOut;
				for (Component mDep : mDepsFile.getMDeps()) {
					existingResult = (ActionResultVersion) getResult(mDep.getName(), ActionResultVersion.class);
					mDepOut = "";
					if (existingResult != null) {
						if (existingResult.getIsNewBuild()) {
							mDepOut = mDep.getCoords().toString(existingResult.getVersion());
						} else {
							if (!existingResult.getVersion().equals(mDep.getCoords().getVersion().toReleaseString())) {
								mDepOut = mDep.getCoords().toString(existingResult.getVersion());
							} 
						}
					} 
					if (mDepOut.isEmpty()) {
						mDepOut = mDep.toString();
					} else {
						mDepsChanged.add(mDepOut);
					}
					mDepsOut.add(mDepOut);
				}
				progress.reportStatus("new mdeps generated");
				
				String mDepsOutContent = Utils.stringsToString(mDepsOut);
				newVersionStartsFromCommit = vcs.setFileContent(devBranch.getName(), SCMWorkflow.MDEPS_FILE_NAME, 
						mDepsOutContent, LogTag.SCM_MDEPS);
				if (newVersionStartsFromCommit == VCSCommit.EMPTY) {
					newVersionStartsFromCommit = vcs.getHeadCommit(devBranch.getName());
					progress.reportStatus("mdeps file is not changed. Going to branch from " + newVersionStartsFromCommit);
				} else {
					progress.reportStatus("mdeps updated in trunk, revision " + newVersionStartsFromCommit);
				}
			} else {
				newVersionStartsFromCommit = vcs.getHeadCommit(devBranch.getName());
				progress.reportStatus("no mdeps. Going to branch from head " + newVersionStartsFromCommit);
			}

			String newBranchName = devBranch.getReleaseBranchName();
			vcs.createBranch(devBranch.getName(), newBranchName, "release branch created");
			progress.reportStatus("branch " + newBranchName + " created");
			
			String verContent = currentVer.toNextMinorSnapshot();
			vcs.setFileContent(devBranch.getName(), SCMWorkflow.VER_FILE_NAME, 
					verContent, LogTag.SCM_VER + " " + verContent);
			progress.reportStatus("change to version " + verContent + " in trunk");

			String newVersion = currentVer.toReleaseString();
			vcs.setFileContent(newBranchName, SCMWorkflow.VER_FILE_NAME, newVersion, 
					LogTag.SCM_VER + " " + newVersion);
			progress.reportStatus("change to version " + newVersion + " in branch " + newBranchName);
			
			if (!mDepsChanged.isEmpty()) {
				vcs.setFileContent(newBranchName, SCMWorkflow.MDEPS_CHANGED_FILE_NAME, Utils.stringsToString(mDepsChanged), 
						LogTag.SCM_IGNORE);
				progress.reportStatus("mdeps-changed is written to branch " + newBranchName);
			}
			
			ActionResultVersion res = new ActionResultVersion(comp.getName(), currentVer.toReleaseString(), true,
					newBranchName);
			progress.reportStatus("new " + comp.getName() + " " 
					+ res.getVersion() + " is released in " + newBranchName);
			if (parentAction == null) {
				addResult(getName(), res); 
			}
			return res;
		} catch (Throwable t) {
			progress.reportStatus("execution error: " + t.toString() + ": " + t.getMessage());
			return t;
		} 
	}

	@Override
	public String toString() {
		return comp.getCoords().toString() + ": " + reason.toString();
	}

}
