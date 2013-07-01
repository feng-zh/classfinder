package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class SystemClassPathBuilder implements ClassPathBuilder {

	private DefaultClassPathBuilder stub = new DefaultClassPathBuilder();

	private File javaHome = null;

	protected SystemClassPathBuilder() {
	}

	public SystemClassPathBuilder(boolean userClassPath) {
		useJavaHome(userClassPath);
		setJavaHome(Util.getJavaHome());
	}

	public SystemClassPathBuilder(File javaHome)
			throws IllegalArgumentException {
		if (javaHome == null) {
			throw new IllegalArgumentException("the specified javaHome is null");
		}
		File jreHome = new File(javaHome, "jre");
		if (!jreHome.exists()) {
			jreHome = javaHome;
		}
		if (!jreHome.exists()) {
			throw new IllegalArgumentException(
					"the provided java home does not exist: "
							+ jreHome.getAbsolutePath());
		}
		File libFolder = new File(jreHome, "lib");
		if (!libFolder.exists()) {
			throw new IllegalArgumentException(
					"the provided java home has no Java Runtime Environment: "
							+ javaHome.getAbsolutePath());
		}
		setJavaHome(jreHome);
		useJavaHome(false);
	}

	protected void setJavaHome(File javaHome) {
		this.javaHome = javaHome;
	}

	public File getJavaHome() {
		return javaHome;
	}

	protected void useJavaHome(boolean userClassPath)
			throws IllegalArgumentException {
		for (URL url : getEndorsedClassPath()) {
			stub.addURL(url);
		}
		for (URL url : getBootClassPath()) {
			stub.addURL(url);
		}
		for (URL url : getExtClassPath()) {
			stub.addURL(url);
		}
		if (userClassPath) {
			for (URL url : getUserClassPath()) {
				stub.addURL(url);
			}
		}
		validateJavaHome(javaHome);
	}

	protected URL[] getEndorsedClassPath() {
		if (javaHome == null) {
			return Util.getEndorsedClassPath();
		} else {
			return listJarFiles(new File(new File(javaHome, "lib"), "endorsed"));
		}
	}

	protected URL[] getBootClassPath() {
		if (javaHome == null) {
			return Util.getBootClassPath();
		} else {
			return listJarFiles(new File(javaHome, "lib"));
		}
	}

	protected URL[] getExtClassPath() {
		if (javaHome == null) {
			return Util.getExtClassPath();
		} else {
			return listJarFiles(new File(new File(javaHome, "lib"), "ext"));
		}
	}

	protected URL[] getUserClassPath() {
		return Util.getClassPath();
	}

	private URL[] listJarFiles(File folder) {
		File[] jarFiles = Util.listJarFiles(folder, false);
		URL[] urls = new URL[jarFiles.length];
		for (int i = 0; i < jarFiles.length; i++) {
			try {
				urls[i] = jarFiles[i].toURI().toURL();
			} catch (MalformedURLException ignored) {
			}
		}
		return urls;
	}

	protected void validateJavaHome(File javaHome)
			throws IllegalArgumentException {
		if (javaHome != null) {
			if (stub.getURLs().length == 0) {
				throw new IllegalArgumentException(
						"the provided java home has no Java Runtime Environment: "
								+ javaHome.getAbsolutePath());
			}
			ClassPathFinder finder = new ClassPathFinder(stub);
			if (finder.locateClass(Object.class.getName()) == null) {
				// cannot find java.lang.Object
				throw new IllegalArgumentException(
						"the provided java home has no Java Runtime Environment: "
								+ javaHome.getAbsolutePath());
			}
		}
	}

	public URL[] getURLs() {
		return stub.getURLs();
	}

	public void appendPathBuilder(ClassPathBuilder builder) {
		if (builder instanceof SystemClassPathBuilder) {
			throw new IllegalArgumentException(
					"cannot add system class path again");
		}
		for (URL url : builder.getURLs()) {
			stub.addURL(url);
		}
	}

}
