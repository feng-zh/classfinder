package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.util.List;

public interface ClassFinderAgentMBean {
	public String locateClass(String className);

	public List<String> findClasses(String className);

	public String locateCodeSource(String className);

	public List<String> findCodeSources(String className);

	public String locateResource(String resourceName);

	public List<String> findResources(String resourceName);

	public String locateResourceSource(String resourceName);

	public List<String> findResourceSources(String resourceName);

	public List<String> findSuperClasses(String subTypeClassName);

	public List<String> findPackageClasses(String packageName,
			boolean directPackage);

}
