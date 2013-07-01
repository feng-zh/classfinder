package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import sun.misc.Launcher;
import sun.misc.Resource;

import com.hp.it.gadsc.et.ei.tools.classfinder.Util.SelectFilter;

@SuppressWarnings("restriction")
public class ClassLoaderFinder extends AbstractClassFinder implements
		ClassFinder {

	final private ClassLoader loader;

	private boolean reportErrorNoPermission = false;

	public ClassLoaderFinder() {
		// try to use thread context loader when finding
		this.loader = Util.getDefaultLoader();
	}

	public ClassLoaderFinder(ClassLoader loader) {
		this.loader = loader;
	}

	public ClassLoaderFinder(String refClassName) throws ClassNotFoundException {
		this(Class.forName(refClassName, false, Util.getDefaultLoader()));
	}

	public ClassLoaderFinder(Class<?> refClass) {
		this(Util.resolveLoader(refClass));
	}

	public boolean getReportErrorNoPermission() {
		return reportErrorNoPermission;
	}

	public void setReportErrorNoPermission(boolean errorIfSecurityIssue) {
		this.reportErrorNoPermission = errorIfSecurityIssue;
	}

	public URL locateClass(Class<?> clz) {
		return getClassLoader().getResource(Util.resolveName(clz.getName()));
	}

	public URL[] findClasses(Class<?> clz) {
		return findResources(Util.resolveLoader(clz),
				Util.resolveName(clz.getName()));
	}

	public URL locateCodeSource(String className) {
		URL[] list = findCodeSources(className);
		if (list.length == 0) {
			try {
				Class<?> clz = getClassLoader().loadClass(className);
				return locateCodeSource(clz);
			} catch (ClassNotFoundException e) {
				// not found
				return null;
			}
		} else {
			return list[0];
		}
	}

	public URL locateCodeSource(Class<?> clz) {
		if (clz == null) {
			throw new IllegalArgumentException("null class");
		}
		ProtectionDomain protectionDomain = null;
		try {
			protectionDomain = clz.getProtectionDomain();
		} catch (SecurityException e) {
			// got security error
			if (reportErrorNoPermission) {
				throw e;
			} else {
				return null;
			}
		}
		if (protectionDomain != null
				&& protectionDomain.getCodeSource() != null) {
			return protectionDomain.getCodeSource().getLocation();
		}
		if (clz.getClassLoader() == null) {
			// bootstrap loader
			String name = Util.resolveName(clz.getName());
			URL resourceURL = Util.getDummyLoader().getResource(name);
			return Util.extractBaseURL(resourceURL, name);
		}
		return null;
	}

	public URL[] findCodeSources(Class<?> clz) {
		return findResourceSources(clz.getClassLoader(),
				Util.resolveName(clz.getName()));
	}

	public ClassLoader locateClassLoader(String className,
			boolean errorIfNotFound) throws NullPointerException {
		try {
			return getClassLoader().loadClass(className).getClassLoader();
		} catch (ClassNotFoundException e) {
			if (errorIfNotFound) {
				throw new NullPointerException(e.toString());
			} else {
				return null;
			}
		} catch (NoClassDefFoundError e) {
			if (errorIfNotFound) {
				throw new NullPointerException(e.toString());
			} else {
				return null;
			}
		}
	}

	public ClassLoader[] findClassLoaders(String className) {
		List<ClassLoader> list = new ArrayList<ClassLoader>();
		try {
			ClassLoader cl = getClassLoader();
			while (cl != null) {
				Vector<?> loadedClasses = Util.getField(cl, ClassLoader.class,
						"classes", Vector.class);
				boolean founded = false;
				for (Object object : loadedClasses) {
					Class<?> clz = (Class<?>) object;
					if (clz.getName().equals(className)) {
						list.add(clz.getClassLoader());
						founded = true;
						break;
					}
				}
				if (!founded) {
					Class<?> clz;
					try {
						clz = Util.invokeMethod(cl, ClassLoader.class,
								"findClass", new Class[] { String.class },
								new Object[] { className }, Class.class);
						list.add(clz.getClassLoader());
					} catch (InvocationTargetException e) {
						if (!(e.getCause() instanceof ClassNotFoundException)) {
							// ignore class not found
							if (e.getCause() instanceof SecurityException) {
								if (reportErrorNoPermission) {
									throw (SecurityException) e.getCause();
								}
							}
						}
					}
				}
				cl = cl.getParent();
			}
			// check bootstrap classes
			Resource resource = Launcher.getBootstrapClassPath().getResource(
					Util.resolveName(className));
			if (resource != null) {
				// bootstrap classloader in first
				list.add(0, null);
			}
		} catch (RuntimeException e) {
			if (reportErrorNoPermission) {
				throw e;
			}
		}
		return list.toArray(new ClassLoader[list.size()]);
	}

	public URL locateResource(String resourceName) {
		return getClassLoader().getResource(resourceName);
	}

	public URL[] findResources(String resourceName) {
		return findResources(getClassLoader(), resourceName);
	}

	public URL locateResourceSource(String resourceName) {
		URL resourceURL = locateResource(resourceName);
		if (resourceURL != null) {
			return Util.extractBaseURL(resourceURL, resourceName);
		} else {
			return null;
		}
	}

	public URL[] findResourceSources(String resourceName) {
		return findResourceSources(getClassLoader(), resourceName);
	}

	public Class<?>[] findAssignableFrom(Class<?> parentClass) {
		return findAssignableFrom(null, parentClass);
	}

	public static ClassPathFinder createPathFinder() {
		SystemClassPathBuilder builder = new SystemClassPathBuilder(true);
		ClassPathFinder pathFinder = new ClassPathFinder(builder);
		return pathFinder;
	}

	public boolean isClassLoaded(String className) {
		try {
			ClassLoader cl = getClassLoader();
			while (cl != null) {
				// get loaded classes
				Vector<?> loadedClasses = Util.getField(cl, ClassLoader.class,
						"classes", Vector.class);
				for (Object object : loadedClasses) {
					Class<?> clz = (Class<?>) object;
					if (clz.getName().equals(className)) {
						return true;
					}
				}
				cl = cl.getParent();
			}
			// check bootstrap classes
			Resource resource = Launcher.getBootstrapClassPath().getResource(
					Util.resolveName(className));
			return resource != null;
		} catch (RuntimeException e) {
			if (reportErrorNoPermission) {
				throw e;
			} else {
				return false;
			}
		}
	}

	private ClassLoader getClassLoader() {
		return loader == null ? Util.getDummyLoader() : loader;
	}

	private static Enumeration<URL> getResources(ClassLoader classLoader,
			String name) {
		Enumeration<URL> enumer;
		try {
			enumer = classLoader.getResources(name);
		} catch (IOException e) {
			List<URL> emptyList = Collections.emptyList();
			enumer = Collections.enumeration(emptyList);
		}
		return enumer;
	}

	private static URL[] findResourceSources(ClassLoader classLoader,
			String name) {
		Enumeration<URL> enumer = getResources(classLoader, name);
		List<URL> list = new ArrayList<URL>();
		while (enumer.hasMoreElements()) {
			URL resourceURL = enumer.nextElement();
			list.add(Util.extractBaseURL(resourceURL, name));
		}
		return list.toArray(new URL[list.size()]);
	}

	private static URL[] findResources(ClassLoader classLoader,
			String resourceName) {
		Enumeration<URL> enumer = getResources(classLoader, resourceName);
		List<URL> list = new ArrayList<URL>();
		while (enumer.hasMoreElements()) {
			list.add(enumer.nextElement());
		}
		return list.toArray(new URL[list.size()]);
	}

	public String[] findAssignableFrom(String topPackageName,
			String parentClassName) {
		List<String> list = new ArrayList<String>();
		Class<?> clz;
		try {
			clz = getClassLoader().loadClass(parentClassName);
		} catch (ClassNotFoundException e) {
			return new String[0];
		} catch (NoClassDefFoundError e) {
			return new String[0];
		}
		for (Class<?> c : findAssignableFrom(topPackageName, clz)) {
			list.add(c.getName());
		}
		return list.toArray(new String[list.size()]);
	}

	public Class<?>[] findAssignableFrom(String topPackage,
			final Class<?> parentClass) {
		return findClasses(topPackage == null ? Util.acceptAll(String.class)
				: Util.createStartsWith(topPackage),
				Util.createAssignableFrom(parentClass));
	}

	public String[] findPackageClasses(String packageName, boolean directPackage) {
		List<String> list = new ArrayList<String>();
		for (Class<?> c : findClassesInPackage(packageName, directPackage)) {
			list.add(c.getName());
		}
		return list.toArray(new String[list.size()]);
	}

	public Class<?>[] findClassesInPackage(final String packageName,
			final boolean directPackage) {
		return findClasses(Util.createInPackage(packageName, directPackage),
				Util.acceptAll(Class.class));
	}

	protected Class<?>[] findClasses(SelectFilter<String> nameFilter,
			SelectFilter<? super Class<?>> classFilter) {
		Set<String> allClassNames = Util.listAllClassNames(this, nameFilter);
		List<Class<?>> subClasses = new ArrayList<Class<?>>();
		ClassLoader classLoader = getClassLoader();
		for (String className : allClassNames) {
			Class<?> clz;
			try {
				clz = classLoader.loadClass(className);
				if (classFilter.accept(clz)) {
					subClasses.add(clz);
				}
			} catch (ClassNotFoundException ignored) {
			} catch (NoClassDefFoundError ignored) {
			}
		}
		return subClasses.toArray(new Class[0]);
	}

	public String[] findSuperTypes(String subTypeClassName) {
		return findSuperTypes(null, subTypeClassName);
	}

	public Class<?>[] findSuperTypes(Class<?> subTypeClass) {
		List<Class<?>> list = new ArrayList<Class<?>>();
		Stack<Class<?>> stack = new Stack<Class<?>>();
		stack.push(subTypeClass);
		while (!stack.isEmpty()) {
			Class<?> clz = stack.pop();
			if (clz != null && !list.contains(clz)) {
				list.add(clz);
			} else {
				continue;
			}
			for (Class<?> inf : clz.getInterfaces()) {
				stack.push(inf);
			}
			if (clz.getSuperclass() != null) {
				stack.push(clz.getSuperclass());
			}
		}
		return list.toArray(new Class[list.size()]);
	}

	public String[] findSuperTypes(String topPackageName,
			String subTypeClassName) {
		List<String> list = new ArrayList<String>();
		Class<?> clz;
		try {
			clz = getClassLoader().loadClass(subTypeClassName);
		} catch (ClassNotFoundException e) {
			return new String[0];
		} catch (NoClassDefFoundError e) {
			return new String[0];
		}
		for (Class<?> c : findSuperTypes(topPackageName, clz)) {
			list.add(c.getName());
		}
		return list.toArray(new String[list.size()]);
	}

	public Class<?>[] findSuperTypes(final String topPackageName,
			Class<?> subTypeClass) {
		Class<?>[] array = findSuperTypes(subTypeClass);
		return Util.filter(array, new Util.SelectFilter<Class<?>>() {

			public boolean accept(Class<?> name) {
				return topPackageName == null
						|| name.getName().startsWith(topPackageName + ".");
			}

		});
	}

}
