package org.scm4j.wf;

import java.util.ArrayList;
import java.util.List;

import org.scm4j.actions.ActionAbstract;
import org.scm4j.actions.IAction;
import org.scm4j.actions.results.ActionResultVersion;
import org.scm4j.progress.IProgress;
import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.exceptions.EVCSFileNotFound;
import org.scm4j.wf.conf.MDepsFile;
import org.scm4j.wf.conf.VerFile;

public class SCMActionProductionRelease extends ActionAbstract {
	
	public static final String VCS_TAG_SCM_VER = "#scm-ver";
	public static final String VCS_TAG_SCM_MDEPS = "#scm-mdeps";
	public static final String VCS_TAG_SCM_IGNORE = "#scm-ignore";
	public static final String[] VCS_TAGS = new String[] {VCS_TAG_SCM_VER, VCS_TAG_SCM_MDEPS, VCS_TAG_SCM_IGNORE};
	public static final String BRANCH_DEVELOP = "develop";
	public static final String BRANCH_RELEASE = "release";

	public SCMActionProductionRelease(VCSRepository repo, List<IAction> childActions, String masterBranchName) {
		super(repo, childActions, masterBranchName);
	}

	@Override
	public Object execute(IProgress progress) {
		progress.reportStatus(getName() + " execution started");
		Object result;
		Object nestedResult;
		try {
			
			IVCS vcs = getVCS();
			VerFile verFile = getVerFile();
			progress.reportStatus("current trunk version: " + verFile);
			
			/**
			 * �������� ��� ������ � ������� ����������.
			 * �������� ������� ����� ������ (������� ���������� � �����)
			 */
			
			for (IAction action : childActions) {
				try (IProgress nestedProgress = progress.createNestedProgress(action.getName())) {
					nestedResult = action.execute(nestedProgress);
					if (nestedResult instanceof Throwable) {
						return nestedResult;
					}
				}
				getResults().put(action.getName(), nestedResult);
			}
			
			// � �� ��������� �� �� ���?
			if (getResults().get(getName()) != null) {
				Object existingResult = getResults().get(getName());
				if (existingResult instanceof ActionResultVersion) {
					progress.reportStatus("using already built version " + ((ActionResultVersion) existingResult).getVersion()); 
					return existingResult;
				}
			}
			
			// �������� �������� ������
			Integer minor = verFile.getMinor();
			Integer newMinor = minor + 1;
			
			// ��� � ��� ���� � ������ ��������. ����� ����������� �� � mdeps ��� ������.
			VCSCommit newVersionStartsFromCommit;
			try {
				String mDepsContent = vcs.getFileContent(masterBranchName, SCMWorkflow.MDEPS_FILE_NAME);
				MDepsFile mdepsFile = new MDepsFile(mDepsContent);
				List<String> mDepsOut = new ArrayList<>();
				for (String mDep : mdepsFile.getMDeps()) {
					String mDepName = getMDepName(mDep);
					nestedResult = getResults().get(mDepName);
					if (nestedResult != null && nestedResult instanceof ActionResultVersion) {
						ActionResultVersion res = (ActionResultVersion) nestedResult;
						mDepsOut.add(mDepName + ":" + res.getVersion());
					} else {
						mDepsOut.add(mDep);
					}
				}
				progress.reportStatus("new mdeps generated");
				
				String mDepsOutContent = mdepsFile.toFileContent();
				newVersionStartsFromCommit = vcs.setFileContent(masterBranchName, SCMWorkflow.MDEPS_FILE_NAME, 
						mDepsOutContent, VCS_TAG_SCM_MDEPS);
				if (newVersionStartsFromCommit == VCSCommit.EMPTY) {
					// ����������� �� ����������, �� ��� ��� ����� ���� ������� �����
					newVersionStartsFromCommit = vcs.getHeadCommit(masterBranchName);
					progress.reportStatus("mdeps file is not changed. Going to branch from " + newVersionStartsFromCommit);
				} else {
					progress.reportStatus("mdeps updated in trunk, revision " + newVersionStartsFromCommit);
				}
			} catch (EVCSFileNotFound e) {
				newVersionStartsFromCommit = vcs.getHeadCommit(masterBranchName);
				progress.reportStatus("no mdeps. Going to branch from " + newVersionStartsFromCommit);
			}
			
			// ������� �����
			String newBranchName = verFile.getReleaseBranchPrefix() + verFile.getVer() + ".0"; 
			vcs.createBranch(masterBranchName, newBranchName, "branch created");
			progress.reportStatus("branch " + newBranchName + " created");
			
			// �������� lastVerCommit � ver � ������
			verFile.setRelease(verFile.getVer() + ".0");				// release = 1.0,
			verFile.setNumberGroupValueFromEnd(2, newMinor);	// ver = 2 
			 
			String verContent = verFile.toFileContent();
			vcs.setFileContent(masterBranchName, SCMWorkflow.VER_FILE_NAME, 
					verContent, VCS_TAG_SCM_VER + " " + verFile.getRelease());
			progress.reportStatus("change to version " + verFile.getRelease() + " in trunk");
			
			// �������� verCommit � �����
			verFile.setVer(verFile.getRelease()); 	// ver=1.0
			verFile.setRelease(null);				// no release
			verContent = verFile.toFileContent();
			vcs.setFileContent(newBranchName, SCMWorkflow.VER_FILE_NAME, verContent, 
					VCS_TAG_SCM_VER);
			progress.reportStatus(verFile.toString() + " is written to " + newBranchName);
			
			ActionResultVersion res = new ActionResultVersion();
			res.setName(repo.getName());
			res.setVersion(verFile.getVer());		// 1.0
			result = res;
			progress.reportStatus("new " + repo.getName() + " " 
					+ res.getVersion() + " is released in " + newBranchName);
		} catch (Throwable t) {
			result = t;
			progress.reportStatus("execution error: " + t.getMessage());
		}
		progress.reportStatus(getName() + " execution finished");
		return result;
	}

	private String getMDepName(String mDep) {
		String[] parts = mDep.split(":");
		if (parts.length < 3) {
			throw new RuntimeException("wrong coords: " + mDep);
		}
		return parts[0] + ":" + parts[1];
	}
}
