package org.scm4j.wf.conf;

import java.util.ArrayList;
import java.util.List;

public class MDepsFile extends ConfFile {
	
	private List<String> mDeps;
	
	public List<String> getMDeps() {
		return this.mDeps;
	}
	
	@Override
	protected void parseLine(String line) {
		if (mDeps == null) {
			mDeps = new ArrayList<>();
		}
		mDeps.add(line);
	}
	
	public MDepsFile(String content) {
		super(content);
	}
	
	public MDepsFile() {
		
	}
	
	@Override
	public String toFileContent() {
		StringBuilder sb = new StringBuilder();
		for (String str : mDeps) {
			sb.append(str + SEP);
		}
		return sb.toString();
	}
	
}
