package com.github.fengzh.classfinder;

import java.io.*;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class Util {

	private static final String CLASS_SUFFIX = ".class";

	private static final File[] EMPTY_FILES = new File[0];

	private static ClassLoader dummyLoader;

	private static class SuffixFilter implements FilenameFilter {

		private final String suffix;

		private boolean caseSensitive;

		public SuffixFilter(String suffix, boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
			this.suffix = caseSensitive ? suffix : suffix.toLowerCase();
		}

		public boolean accept(File dir, String name) {
			name = caseSensitive ? name : name.toLowerCase();
			return name.endsWith(suffix);
		}
	}

	private static FileFilter subFolderFilter = File::isDirectory;

	private static FilenameFilter jarFilter = new SuffixFilter(".jar", true);

	private static FilenameFilter classFileFilter = new SuffixFilter(CLASS_SUFFIX, false);

	public interface SelectFilter<T> {
		boolean accept(T name);
	}

	private static SelectFilter<?> ACCEPT_ALL = (SelectFilter<Object>) name -> true;
	
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
				return Long.compare(c1.signature, c2.signature);
			}
			// TODO USE last modified?
			return 0;
		}
		
	}
	
	private static ClassFileAttributeComparator ClassFileAttributeComparator = new ClassFileAttributeComparator();
	
	private Util() {
	}

	private static URL extractJarURL(URL fullURL) {
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
		return className.replace('.', '/') + CLASS_SUFFIX;
	}

	private static String unResolveName(String classFileName) {
		return classFileName.substring(0,
				classFileName.length() - CLASS_SUFFIX.length())
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
		List<File> container = new ArrayList<>();
		listFilesBy(folder, jarFilter, container, recursive);
		if (container.isEmpty()) {
			return EMPTY_FILES;
		} else {
			return container.toArray(new File[container.size()]);
		}
	}

	private static String[] listClassFileNames(File folder, boolean recursive) {
		List<String> container = new ArrayList<>();
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
			Collections.addAll(container, files);
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
			List<URL> urls = new ArrayList<>();
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

	static Set<String> listAllClassNames(ClassFinder finder,
			SelectFilter<String> filter) {
		return locateAllClassNames(finder, filter).keySet();
	}

	static Map<String, List<URL>> locateAllClassNames(ClassFinder finder,
			SelectFilter<String> filter) {
		List<URL> list = new ArrayList<>();
		Collections.addAll(list, finder.findResources(""));
		Collections.addAll(list, finder.findResources(JarFile.MANIFEST_NAME));
		Map<String, List<URL>> allClassNames = new HashMap<>();
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
								&& entry.getName().endsWith(CLASS_SUFFIX)) {
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
		Map<String, Map<URL, ClassFileAttribute>> container = new HashMap<>();
		getAllClassNames(container, resources, filter, false);
		for (Map.Entry<String, Map<URL, ClassFileAttribute>> entry : container.entrySet()) {
			allClassNames.put(entry.getKey(), new ArrayList<>(entry.getValue().keySet()));
		}
	}

	private static void addIntoMap(Map<String, Map<URL, ClassFileAttribute>> map,
			String className, URL url, ClassFileAttribute classFileAttr) {
		Map<URL, ClassFileAttribute> list = map.computeIfAbsent(className, k -> new LinkedHashMap<>());
		list.put(url, classFileAttr);
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
		List<URL> list = new ArrayList<>();
		Collections.addAll(list, finder.findResources(""));
		Collections.addAll(list, finder.findResources(JarFile.MANIFEST_NAME));
		Map<String, List<URL>> allClassNames = new TreeMap<>();
		getAllClassNames(allClassNames, list, filter);
		return allClassNames;
	}
	
	static Map<String, Map<URL, Long>> locateAllVersionedClassNamesByPattern(
			ClassFinder finder, SelectFilter<String> filter) {
		List<URL> list = new ArrayList<>();
		Collections.addAll(list, finder.findResources(""));
		Collections.addAll(list, finder.findResources(JarFile.MANIFEST_NAME));
		Map<String, Map<URL, ClassFileAttribute>> container = new HashMap<>();
		getAllClassNames(container, list, filter, true);
		Map<String, Map<URL, Long>> allClassNames = new TreeMap<>();
		for (Map.Entry<String, Map<URL, ClassFileAttribute>> entry : container
				.entrySet()) {
			Map<URL, Long> versions = new LinkedHashMap<>();
			Map<ClassFileAttribute, Long> verSet = new TreeMap<>(
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
		return name -> name.startsWith(string);
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

	static SelectFilter<Class> createAssignableFrom(final Class<?> parentClass) {
		return parentClass::isAssignableFrom;
	}

	static SelectFilter<String> createInPackage(final String packageName,
			final boolean directPackage) {
		return name -> {
            if (packageName == null) {
                // only class name without package if direct package
                return !directPackage || name.indexOf('.') < 0;
            } else {
                if (name.startsWith(packageName + ".")) {
                    return !directPackage || (name.indexOf('.', packageName.length() + 1) < 0);
                } else {
                    return false;
                }
            }
        };
	}

	@SuppressWarnings("unchecked")
	static <T> T[] filter(T[] array, SelectFilter<T> filter) {
		List<T> list = new ArrayList<>();
		for (T t : array) {
			if (filter.accept(t)) {
				list.add(t);
			}
		}
		return list.toArray((T[]) Array.newInstance(array.getClass().getComponentType(), 0));
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
