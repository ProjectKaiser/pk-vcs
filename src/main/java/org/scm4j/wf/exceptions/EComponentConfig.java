package org.scm4j.wf.exceptions;

public class EComponentConfig extends RuntimeException {

	private static final long serialVersionUID = 1L;
	
	public EComponentConfig (Exception e) {
		super(e);
	}
	
	public EComponentConfig(String message) {
		super(message);
	}

}