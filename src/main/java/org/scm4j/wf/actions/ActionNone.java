package org.scm4j.wf.actions;

import org.scm4j.commons.progress.IProgress;
import org.scm4j.wf.conf.Component;

import java.util.List;

public class ActionNone extends ActionAbstract {
	
	private final String reason;
	
	public ActionNone(Component comp, List<IAction> actions, String reason) {
		super(comp, actions);
		this.reason = reason;
	}

	@Override
	public Object execute(IProgress progress) {
		return null;
	}
	
	public String getReason() {
		return reason;
	}
	
	@Override
	public String toString() {
		return "Nothing should be done [" + getName() + "]. Reason: " + reason;
	}

}
