package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class Util {

	private static final String CLASS_SUFIX = ".class";

	private static final File[] EMPTY_FILES = new File[0];

	private static ClassLoader dummyLoader;

	private static class SufixFilter implements FilenameFilter {

		private final String sufix;

		private boolean casesensitive;

		public SufixFilter(String sufix, boolean casesensitive) {
			this.casesensitive = casesensitive;
			this.sufix = casesensitive ? sufix : sufix.toLowerCase();
		}

		public boolean accept(File dir, String name) {
			name = casesensitive ? name : name.toLowerCase();
			if (name.endsWith(sufix)) {
				return true;
			} else {
				return false;
			}
		}
	}

	private static FileFilter subFolderFilter = new FileFilter() {

		public boolean accept(File pathname) {
			return pathname.isDirectory();
		}
	};

	private static FilenameFilter jarFilter = new SufixFilter(".jar", true);

	private static FilenameFilter classFileFilter = new SufixFilter(
			CLASS_SUFIX, false);

	public static interface SelectFilter<T> {
		public boolean accept(T name);
	}

	private static SelectFilter<?> ACCEPT_ALL = new SelectFilter<Object>() {
		public boolean accept(Object name) {
			return true;
		}
	};
	
	public static class ClassFileAttribute implements Serializable {
		private static final long serialVersionUID = 1465344870523345849L;
		private long lastModified;
		private long size;
		private long signature;
		
		public long getLastModified() {
			return lastModified;
		}
	}
	
	// to keep compareTo out of class file attribute to elimiate break comparable contact (equals)
	private static class ClassFileAttributeComparator implements Comparator<ClassFileAttribute> {

		public int compare(ClassFileAttribute c1, ClassFileAttribute c2) {
			// use size if it is OK
			if (c1.size!=c2.size) {
				return c1.size<c2.size ? -1 : 1;
			}
			// use signature if exists
			if (c1.signature!=0 && c2.signature!=0) {
				// if signature exists
				return (c1.signature<c2.signature ? -1 : (c1.signature==c2.signature ? 0 : 1));
			}
			// TODO USE last modified?
			return 0;
		}
		
	}
	
	private static ClassFileAttributeComparator ClassFileAttributeComparator = new ClassFileAttributeComparator();
	
	private Util() {
	}

	static URL extractJarURL(URL fullURL) {
		if (isJarUrl(fullURL)) {
			String fullString = fullURL.getFile();
			if (fullString != null && fullString.endsWith("!/")) {
				try {
					return new URL(fullString.substring(0,
							fullString.length() - 2));
				} catch (MalformedURLException ignored) {
				}
			}
		}
		return fullURL;
	}

	private static URL extractParentURL(URL fullURL, String name) {
		String fullFilePath = fullURL.getFile();
		if (fullFilePath.endsWith(name)) {
			boolean absolutePath = name.startsWith("/");
			try {
				return new URL(fullURL.getProtocol(), fullURL.getHost(),
						fullURL.getPort(), fullFilePath.substring(0,
								fullFilePath.length() - name.length()
										+ (absolutePath ? 1 : 0)));
			} catch (MalformedURLException ignored) {
			}
		}
		return fullURL;
	}

	static URL extractBaseURL(URL url, String name) {
		URL resourceBaseURL = extractParentURL(url, name);
		if (isJarUrl(url)) {
			resourceBaseURL = extractJarURL(resourceBaseURL);
		}
		return resourceBaseURL;
	}

	static String resolveName(String className) {
		return className.replace('.', '/') + CLASS_SUFIX;
	}

	static String unResolveName(String classFileName) {
		return classFileName.substring(0,
				classFileName.length() - CLASS_SUFIX.length())
				.replace('/', '.');
	}

	static ClassLoader resolveLoader(Class<?> clz) {
		if (clz.getClassLoader() == null) {
			return getDummyLoader();
		} else {
			return clz.getClassLoader();
		}
	}

	synchronized static ClassLoader getDummyLoader() {
		if (dummyLoader == null) {
			dummyLoader = new ClassLoader(null) {
			};
		}
		return dummyLoader;
	}

	static ClassLoader getDefaultLoader() {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = ClassLoaderFinder.class.getClassLoader();
		}
		return cl;
	}

	static File[] listJarFiles(File folder, boolean recursive) {
		List<File> container = new ArrayList<File>();
		listFilesBy(folder, jarFilter, container, recursive);
		if (container.isEmpty()) {
			return EMPTY_FILES;
		} else {
			return container.toArray(new File[container.size()]);
		}
	}

	static String[] listClassFileNames(File folder, boolean recursive) {
		List<String> container = new ArrayList<String>();
		listNamesBy(folder, classFileFilter, null, container, recursive);
		if (container.isEmpty()) {
			return new String[0];
		} else {
			return container.toArray(new String[container.size()]);
		}
	}

	private static void listFilesBy(File folder, FilenameFilter filter,
			List<File> container, boolean recursive) {
		File[] files = folder.listFiles(filter);
		if (files != null) {
			for (File f : files) {
				container.add(f);
			}
		}
		if (recursive) {
			for (File f : listSubFolders(folder)) {
				listFilesBy(f, filter, container, recursive);
			}
		}
	}

	private static void listNamesBy(File folder, FilenameFilter filter,
			String parentName, List<String> container, boolean recursive) {
		String[] files = folder.list(filter);
		if (files != null) {
			for (String f : files) {
				container.add(parentName == null ? f : (parentName + "/" + f));
			}
		}
		if (recursive) {
			for (File f : listSubFolders(folder)) {
				listNamesBy(f, filter, parentName == null ? f.getName()
						: (parentName + "/" + f.getName()), container,
						recursive);
			}
		}
	}

	private static File[] listSubFolders(File folder) {
		File[] files = folder.listFiles(subFolderFilter);
		if (files == null) {
			return EMPTY_FILES;
		} else {
			return files;
		}
	}

	static File toCanonicalStyle(File file) {
		try {
			return file.getCanonicalFile();
		} catch (IOException e) {
			return file;
		}
	}

	synchronized static File getJavaHome() {
		return new File(System.getProperty("java.home"));
	}

	static URL[] getBootClassPath() {
		return parsePath(System.getProperty("sun.boot.class.path"), false,
				System.getProperty("user.dir"));
	}

	static URL[] getExtClassPath() {
		return parsePath(System.getProperty("java.ext.dirs"), true,
				System.getProperty("user.dir"));
	}

	static URL[] getEndorsedClassPath() {
		return parsePath(System.getProperty("java.endorsed.dirs"), true,
				System.getProperty("user.dir"));
	}

	static URL[] getClassPath() {
		return parsePath(System.getProperty("java.class.path"), false,
				System.getProperty("user.dir"));
	}

	static URL[] parsePath(String path, boolean expand, String baseDir) {
		if (path != null) {
			String[] paths = path.split(File.pathSeparator);
			List<URL> urls = new ArrayList<URL>();
			for (String p : paths) {
				try {
					File file = new File(p);
					if (!file.isAbsolute()) {
						file = new File(baseDir, p);
					}
					if (file.exists()) {
						if (file.isDirectory() && expand) {
							for (File f : listJarFiles(file, false)) {
								urls.add(f.toURI().toURL());
							}
						} else {
							urls.add(file.toURI().toURL());
						}
					}
				} catch (MalformedURLException ignored) {
				}
			}
			return urls.toArray(new URL[urls.size()]);
		} else {
			return new URL[0];
		}
	}

	static <T> T getField(Object obj, Class<?> declaredClass, String fieldName,
			Class<T> clz) throws RuntimeException {
		Field field;
		try {
			field = declaredClass.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("field name [" + fieldName
					+ "]  cannot be found for class: " + declaredClass);
		}
		field.setAccessible(true);
		try {
			return clz.cast(field.get(obj));
		} catch (IllegalAccessException e) {
			throw new SecurityException(e);
		}
	}

	static <T> T invokeMethod(Object obj, Class<?> declaredClass,
			String methodName, Class<?>[] paraTypes, Object[] args, Class<T> clz)
			throws RuntimeException, InvocationTargetException {
		Method method;
		try {
			method = declaredClass.getDeclaredMethod(methodName, paraTypes);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("method name [" + methodName
					+ "]  cannot be found for class: " + declaredClass);
		}
		method.setAccessible(true);
		try {
			return clz.cast(method.invoke(obj, args));
		} catch (IllegalAccessException e) {
			throw new SecurityException(e);
		}
	}

	static Object newInstance(Class<?> declaredClass, Class<?>[] paraTypes,
			Object[] args) throws RuntimeException, InvocationTargetException {
		Constructor<?> constructor;
		try {
			constructor = declaredClass.getConstructor(paraTypes);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(
					"constructure cannot be found for class: " + declaredClass);
		}
		constructor.setAccessible(true);
		try {
			return declaredClass.cast(constructor.newInstance(args));
		} catch (IllegalAccessException e) {
			throw new SecurityException(e);
		} catch (InstantiationException e) {
			throw new SecurityException(e);
		}
	}

	static Set<String> listAllClassNames(ClassFinder finder,
			SelectFilter<String> filter) {
		return locateAllClassNames(finder, filter).keySet();
	}

	static Map<String, List<URL>> locateAllClassNames(ClassFinder finder,
			SelectFilter<String> filter) {
		List<URL> list = new ArrayList<URL>();
		for (URL url : finder.findResources("")) {
			list.add(url);
		}
		for (URL url : finder.findResources(JarFile.MANIFEST_NAME)) {
			list.add(url);
		}
		Map<String, List<URL>> allClassNames = new HashMap<String, List<URL>>();
		getAllClassNames(allClassNames, list, filter);
		return allClassNames;
	}

	private static void getAllClassNames(Map<String, Map<URL, ClassFileAttribute>> allClassNames,
			List<URL> resources, SelectFilter<String> filter, boolean withAttributes) {
		for (URL url : resources) {
			if (isJarUrl(url)) {
				// Jar File
				URL jarFileUrl = extractBaseURL(url, JarFile.MANIFEST_NAME);
				JarFile jarFile = null;
				try {
					try {
						jarFile = new JarFile(new File(jarFileUrl.toURI()));
					} catch (IOException e) {
						continue;
					}
					Enumeration<JarEntry> entries = jarFile.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						if (!entry.isDirectory()
								&& entry.getName().endsWith(CLASS_SUFIX)) {
							// class file entry
							String className = unResolveName(entry.getName());
							if (filter.accept(className)) {
								ClassFileAttribute classFileAttr = null;
								if (withAttributes){
									classFileAttr = new ClassFileAttribute();
									classFileAttr.lastModified = entry.getTime();
									classFileAttr.size = entry.getSize();
									classFileAttr.signature = entry.getCrc();
								}
								addIntoMap(allClassNames, className, jarFileUrl, classFileAttr);
							}
						}
					}
				} catch (URISyntaxException e) {
					throw new IllegalStateException(e);
				} finally {
					if (jarFile != null) {
						try {
							jarFile.close();
						} catch (IOException ignored) {
						}
					}
				}
			} else {
				// Class Folder
				try {
					File classFolder = new File(url.toURI());
					for (String classFileName : listClassFileNames(classFolder,
							true)) {
						String className = unResolveName(classFileName);
						if (filter.accept(className)) {
							ClassFileAttribute classFileAttr = null;
							if (withAttributes){
								File classFile = new File(classFolder, classFileName);
								classFileAttr = new ClassFileAttribute();
								classFileAttr.lastModified = classFile.lastModified();
								classFileAttr.size = classFile.length();
							}
							addIntoMap(allClassNames, className, url, classFileAttr);
						}
					}
				} catch (URISyntaxException e) {
					throw new IllegalStateException(e);
				} catch (IllegalArgumentException e) {
					throw new IllegalStateException(
							"handle such url as folder", e);
				}
			}
		}
	}

	private static void getAllClassNames(Map<String, List<URL>> allClassNames,
			List<URL> resources, SelectFilter<String> filter) {
		Map<String, Map<URL, ClassFileAttribute>> container = new HashMap<String, Map<URL, ClassFileAttribute>>();
		getAllClassNames(container, resources, filter, false);
		for (Map.Entry<String, Map<URL, ClassFileAttribute>> entry : container
				.entrySet()) {
			allClassNames.put(entry.getKey(), new ArrayList<URL>(entry
					.getValue().keySet()));
		}
	}

	private static void addIntoMap(Map<String, Map<URL, ClassFileAttribute>> map,
			String className, URL url, ClassFileAttribute classFileAttr) {
		Map<URL, ClassFileAttribute> list = map.get(className);
		if (list == null) {
			list = new LinkedHashMap<URL, ClassFileAttribute>();
			map.put(className, list);
		}
		list.put(url, classFileAttr);
	}

	static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
			}
		}
	}

	static boolean isJarUrl(URL url) {
		return "jar".equals(url.getProtocol());
	}

	static Set<String> listAllClassNamesByPattern(ClassFinder finder,
			SelectFilter<String> filter) {
		return locateAllClassNamesByPattern(finder, filter).keySet();
	}

	static Map<String, List<URL>> locateAllClassNamesByPattern(
			ClassFinder finder, SelectFilter<String> filter) {
		List<URL> list = new ArrayList<URL>();
		for (URL url : finder.findResources("")) {
			list.add(url);
		}
		for (URL url : finder.findResources(JarFile.MANIFEST_NAME)) {
			list.add(url);
		}
		Map<String, List<URL>> allClassNames = new TreeMap<String, List<URL>>();
		getAllClassNames(allClassNames, list, filter);
		return allClassNames;
	}
	
	static Map<String, Map<URL, Long>> locateAllVersionedClassNamesByPattern(
			ClassFinder finder, SelectFilter<String> filter) {
		List<URL> list = new ArrayList<URL>();
		for (URL url : finder.findResources("")) {
			list.add(url);
		}
		for (URL url : finder.findResources(JarFile.MANIFEST_NAME)) {
			list.add(url);
		}
		Map<String, Map<URL, ClassFileAttribute>> container = new HashMap<String, Map<URL, ClassFileAttribute>>();
		getAllClassNames(container, list, filter, true);
		Map<String, Map<URL, Long>> allClassNames = new TreeMap<String, Map<URL, Long>>();
		for (Map.Entry<String, Map<URL, ClassFileAttribute>> entry : container
				.entrySet()) {
			Map<URL, Long> versions = new LinkedHashMap<URL, Long>();
			Map<ClassFileAttribute, Long> verSet = new TreeMap<ClassFileAttribute, Long>(
					ClassFileAttributeComparator);
			for (Map.Entry<URL, ClassFileAttribute> urlEntry : entry.getValue()
					.entrySet()) {
				verSet.put(urlEntry.getValue(), urlEntry.getValue()
						.getLastModified());
				versions.put(urlEntry.getKey(), verSet.get(urlEntry.getValue()));
			}
			allClassNames.put(entry.getKey(), versions);
		}
		return allClassNames;
	}

	static SelectFilter<String> createStartsWith(final String string) {
		return new SelectFilter<String>() {

			public boolean accept(String name) {
				return name.startsWith(string);
			}

		};
	}

	static SelectFilter<String> createNamePatternFilter(
			final String classNamePattern) {
		return new SelectFilter<String>() {

			private final boolean packageBasedSearch = classNamePattern
					.indexOf('.') >= 0;

			public boolean accept(String name) {
				if (!packageBasedSearch) {
					int index = name.lastIndexOf('.');
					if (index > 0) {
						name = name.substring(index + 1);
					}
				}
				return Util.wildmatch(name, classNamePattern);
			}

		};
	}

	static boolean wildmatch(String str1, String str2) {
		return wildmatch(str1, str2, 0, 0);
	}

	private static boolean wildmatch(String str1, String str2, int offset1,
			int offset2) {
		char c;
		final int len1 = str1.length();
		final int len2 = str2.length();

		while (offset2 < len2) {
			c = str2.charAt(offset2++);
			if (c == '?') {
				if (++offset1 > len1)
					return false;
			} else if (c == '*') {
				if (offset2 >= len2)
					return true;
				do {
					if (wildmatch(str1, str2, offset1, offset2))
						return true;
				} while (++offset1 < len1);
				return false;
			} else {
				if (offset1 >= len1 || c != str1.charAt(offset1++))
					return false;
			}
		}
		return (offset1 == len1);
	}

	@SuppressWarnings("unchecked")
	static <T> SelectFilter<T> acceptAll(Class<T> clz) {
		return (SelectFilter<T>) ACCEPT_ALL;
	}

	@SuppressWarnings("rawtypes")
	static SelectFilter<Class> createAssignableFrom(final Class<?> parentClass) {
		return new SelectFilter<Class>() {
			public boolean accept(Class clz) {
				return parentClass.isAssignableFrom(clz);
			}
		};
	}

	static SelectFilter<String> createInPackage(final String packageName,
			final boolean directPackage) {
		return new SelectFilter<String>() {

			public boolean accept(String name) {
				if (packageName == null) {
					// only class name without package if direct package
					return directPackage ? name.indexOf('.') < 0 : true;
				} else {
					if (name.startsWith(packageName + ".")) {
						return directPackage ? name.indexOf('.',
								packageName.length() + 1) < 0 : true;
					} else {
						return false;
					}
				}
			}

		};
	}

	@SuppressWarnings("unchecked")
	static <T> T[] filter(T[] array, SelectFilter<T> filter) {
		List<T> list = new ArrayList<T>();
		for (T t : array) {
			if (filter.accept(t)) {
				list.add(t);
			}
		}
		return (T[]) list.toArray((T[]) Array.newInstance(array.getClass()
				.getComponentType(), 0));
	}

	static String toAbsolutePath(URL url, String name) {
		if (name != null && isJarUrl(url)) {
			url = extractBaseURL(url, name);
		}
		try {
			return new File(url.toURI()).getAbsolutePath();
		} catch (URISyntaxException e) {
			return url.getPath();
		} catch (RuntimeException e) {
			e.printStackTrace();
			return url.getPath();
		}
	}

}
