package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DefaultClassPathBuilder implements ClassPathBuilder {
	private List<URL> urlList = new ArrayList<URL>();

	public DefaultClassPathBuilder() {
	}

	public DefaultClassPathBuilder(URL[] paths) {
		for (URL path : paths) {
			addURL(path);
		}
	}

	public DefaultClassPathBuilder(File[] paths) {
		for (File path : paths) {
			addClassFolder(path);
		}
	}

	public DefaultClassPathBuilder(File rootFolder) {
		addRootFolder(rootFolder);
	}

	public DefaultClassPathBuilder addClassFolder(File folder) {
		if (folder != null) {
			try {
				addURL(Util.toCanonicalStyle(folder).toURI().toURL());
			} catch (MalformedURLException ignored) {
			}
		}
		return this;
	}

	public DefaultClassPathBuilder addJarFolder(File folder) {
		if (folder != null) {
			File[] jarFiles = Util.listJarFiles(Util.toCanonicalStyle(folder),
					false);
			for (File file : jarFiles) {
				try {
					addURL(file.toURI().toURL());
				} catch (MalformedURLException ignored) {
				}
			}
		}
		return this;
	}

	public DefaultClassPathBuilder addRootFolder(File folder) {
		if (folder != null) {
			addClassFolder(folder);
			addJarFolderRecursive(folder);
		}
		return this;
	}

	public DefaultClassPathBuilder addJarFolderRecursive(File folder) {
		if (folder != null) {
			File[] jarFiles = Util.listJarFiles(Util.toCanonicalStyle(folder),
					true);
			for (File file : jarFiles) {
				try {
					addURL(file.toURI().toURL());
				} catch (MalformedURLException ignored) {
				}
			}
		}
		return this;
	}

	public DefaultClassPathBuilder addURL(URL path) {
		if (path != null) {
			urlList.add(path);
		}
		return this;
	}

	public void appendPathBuilder(ClassPathBuilder builder) {
		if (builder != this) {
			for (URL url : builder.getURLs()) {
				addURL(url);
			}
		}
	}

	public URL[] getURLs() {
		return urlList.toArray(new URL[0]);
	}

}
