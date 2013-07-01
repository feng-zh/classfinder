package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassFinderAgent implements ClassFinderAgentMBean {
	private final ClassFinder finder;

	public ClassFinderAgent(ClassFinder finder) {
		if (finder == null) {
			throw new IllegalArgumentException("null finder");
		}
		this.finder = finder;
	}

	public List<String> findClasses(String className) {
		URL[] urls = finder.findClasses(className);
		List<String> paths = new ArrayList<String>();
		for (URL url : urls) {
			paths.add(Util.toAbsolutePath(url, Util.resolveName(className)));
		}
		return paths;
	}

	public List<String> findCodeSources(String className) {
		URL[] urls = finder.findCodeSources(className);
		List<String> paths = new ArrayList<String>();
		for (URL url : urls) {
			paths.add(Util.toAbsolutePath(url, null));
		}
		return paths;
	}

	public List<String> findResourceSources(String resourceName) {
		URL[] urls = finder.findResourceSources(resourceName);
		List<String> paths = new ArrayList<String>();
		for (URL url : urls) {
			paths.add(Util.toAbsolutePath(url, resourceName));
		}
		return paths;
	}

	public List<String> findResources(String resourceName) {
		URL[] urls = finder.findResources(resourceName);
		List<String> paths = new ArrayList<String>();
		for (URL url : urls) {
			paths.add(Util.toAbsolutePath(url, resourceName));
		}
		return paths;
	}

	public String locateClass(String className) {
		URL url = finder.locateClass(className);
		if (url != null) {
			return Util.toAbsolutePath(url, Util.resolveName(className));
		} else {
			return null;
		}
	}

	public String locateCodeSource(String className) {
		URL url = finder.locateCodeSource(className);
		if (url != null) {
			return Util.toAbsolutePath(url, null);
		} else {
			return null;
		}
	}

	public String locateResource(String resourceName) {
		URL url = finder.locateResource(resourceName);
		if (url != null) {
			return Util.toAbsolutePath(url, resourceName);
		} else {
			return null;
		}
	}

	public String locateResourceSource(String resourceName) {
		URL url = finder.locateResourceSource(resourceName);
		if (url != null) {
			return Util.toAbsolutePath(url, null);
		} else {
			return null;
		}
	}

	public List<String> findSuperClasses(String subTypeClassName) {
		String[] superTypes = finder.findSuperTypes(subTypeClassName);
		List<String> list = new ArrayList<String>();
		for (String s : superTypes) {
			if (!s.equals(subTypeClassName)) {
				list.add(s);
			}
		}
		return list;
	}

	public List<String> findPackageClasses(String packageName,
			boolean directPackage) {
		return Arrays.asList(finder.findPackageClasses(packageName,
				directPackage));
	}

}
