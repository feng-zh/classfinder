package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.net.URL;
import java.util.List;
import java.util.Map;

public interface ClassFinder {

	public URL locateClass(String className);

	public URL[] findClasses(String className);

	public URL locateCodeSource(String className);

	public URL[] findCodeSources(String className);

	public URL locateResource(String resourceName);

	public URL[] findResources(String resourceName);

	public URL locateResourceSource(String resourceName);

	public URL[] findResourceSources(String resourceName);

	public String[] findAssignableFrom(String parentClassName);

	public String[] findAssignableFrom(String topPackageName,
			String parentClassName);

	public String[] findSuperTypes(String subTypeClassName);

	public String[] findSuperTypes(String topPackageName,
			String subTypeClassName);

	/*
	 * Support '*', '?', no package name (like Eclipse, but append '*' if starts
	 * with)
	 */
	public String[] lookupClass(String classNamePattern);

	public Map<String, List<URL>> lookupClassAndLocation(String classNamePattern);

	public String[] findPackageClasses(String packageName, boolean directPackage);

	public Map<String, List<URL>> findDuplicates(String classNamePattern);
	
	public Map<String, Map<URL, Long>> findConflictClasses(String classNamePattern, boolean duplicateInclude);

}
