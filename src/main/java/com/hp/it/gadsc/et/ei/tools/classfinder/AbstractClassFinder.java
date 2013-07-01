package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractClassFinder implements ClassFinder {

	public URL[] findClasses(String className) {
		return findResources(Util.resolveName(className));
	}

	public URL[] findCodeSources(String className) {
		return findResourceSources(Util.resolveName(className));
	}

	public URL locateClass(String className) {
		return locateResource(Util.resolveName(className));
	}

	public URL locateCodeSource(String className) {
		return locateResourceSource(Util.resolveName(className));
	}

	public String[] findAssignableFrom(String parentClassName) {
		return findAssignableFrom(null, parentClassName);
	}

	public String[] lookupClass(final String classNamePattern) {
		return Util.listAllClassNamesByPattern(this,
				Util.createNamePatternFilter(classNamePattern)).toArray(
				new String[0]);
	}

	public Map<String, List<URL>> lookupClassAndLocation(
			final String classNamePattern) {
		return Util.locateAllClassNamesByPattern(this,
				Util.createNamePatternFilter(classNamePattern));
	}

	public Map<String, List<URL>> findDuplicates(String classNamePattern) {
		Map<String, List<URL>> map = lookupClassAndLocation(classNamePattern);
		Map<String, List<URL>> ret = new LinkedHashMap<String, List<URL>>();
		for (Map.Entry<String, List<URL>> entry : map.entrySet()) {
			if (entry.getValue().size() > 1) {
				ret.put(entry.getKey(), entry.getValue());
			}
		}
		return ret;
	}

	public Map<String, Map<URL, Long>> findConflictClasses(
			String classNamePattern, boolean duplicateInclude) {
		Map<String, Map<URL, Long>> map = Util
				.locateAllVersionedClassNamesByPattern(this,
						Util.createNamePatternFilter(classNamePattern));
		Map<String, Map<URL, Long>> ret = new LinkedHashMap<String, Map<URL, Long>>();
		for (Map.Entry<String, Map<URL, Long>> entry : map.entrySet()) {
			if (entry.getValue().size() <= 1) {
				// filter non duplicate or conflict
				continue;
			}
			if (!duplicateInclude) {
				long minVer = Long.MAX_VALUE;
				long maxVer = Long.MIN_VALUE;
				for (Long version:entry.getValue().values()) {
					minVer = Math.min(minVer, version);
					maxVer = Math.min(maxVer, version);
				}
				// only one version
				if (minVer == maxVer) {
					continue;
				}
			}
			ret.put(entry.getKey(), entry.getValue());
		}
		return ret;
	}

}
