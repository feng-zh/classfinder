package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.net.URL;

public class LocalVMClassPathBuilder extends SystemClassPathBuilder implements
		ClassPathBuilder {

	private Jps jps = null;

	public LocalVMClassPathBuilder(String stringInLine)
			throws IllegalArgumentException {
		try {
			jps = new Jps(stringInLine);
		} catch (IOException e) {
			// cannot attach process
			throw new IllegalArgumentException(
					"cannot attache process with string: " + stringInLine, e);
		}
		initFromJps(jps);
	}

	public LocalVMClassPathBuilder(int pid) throws IllegalArgumentException {
		try {
			jps = new Jps(pid);
		} catch (IOException e) {
			// cannot attach process
			throw new IllegalArgumentException(
					"cannot attache process: " + pid, e);
		}
		initFromJps(jps);
	}

	private void initFromJps(Jps jps) {
		useJavaHome(true);
		setJavaHome(jps.getJavaHome());
		jps.close();
		jps = null;
	}

	@Override
	protected URL[] getBootClassPath() {
		return jps.getBootClassPath();
	}

	@Override
	protected URL[] getEndorsedClassPath() {
		return jps.getEndorsedClassPath();
	}

	@Override
	protected URL[] getExtClassPath() {
		return jps.getExtClassPath();
	}

	@Override
	protected URL[] getUserClassPath() {
		return jps.getClassPath();
	}

}
