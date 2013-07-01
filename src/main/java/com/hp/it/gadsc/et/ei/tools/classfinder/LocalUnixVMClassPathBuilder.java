package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.net.URL;

public class LocalUnixVMClassPathBuilder extends SystemClassPathBuilder
		implements ClassPathBuilder {

	private JInfo jinfo = null;

	public LocalUnixVMClassPathBuilder(String stringInLine)
			throws IllegalArgumentException {
		Jps jps;
		try {
			jps = new Jps(stringInLine);
		} catch (IOException e) {
			// cannot attach process
			throw new IllegalArgumentException(
					"cannot attache process with string: " + stringInLine, e);
		}
		initFromJps(jps);
	}

	public LocalUnixVMClassPathBuilder(int pid) throws IllegalArgumentException {
		Jps jps;
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
		try {
			jinfo = new JInfo(jps.getProcessId(), jps.getJavaHome()
					.getAbsolutePath());
		} catch (IOException e) {
			throw new IllegalArgumentException("cannot use jinfo process: "
					+ e.getMessage(), e);
		}
		useJavaHome(true);
		setJavaHome(jps.getJavaHome());
		jps.close();
		jps = null;
	}

	@Override
	protected URL[] getBootClassPath() {
		return jinfo.getBootClassPath();
	}

	@Override
	protected URL[] getEndorsedClassPath() {
		return jinfo.getEndorsedClassPath();
	}

	@Override
	protected URL[] getExtClassPath() {
		return jinfo.getExtClassPath();
	}

	@Override
	protected URL[] getUserClassPath() {
		return jinfo.getClassPath();
	}

}
