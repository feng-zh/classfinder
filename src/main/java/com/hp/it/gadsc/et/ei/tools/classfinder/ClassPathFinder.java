package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import sun.misc.Resource;
import sun.misc.URLClassPath;

import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.DescendingVisitor;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;

@SuppressWarnings("restriction")
public class ClassPathFinder extends AbstractClassFinder implements ClassFinder {

	private final URLClassPath urlClassPath;

	public ClassPathFinder(ClassPathBuilder builder) {
		urlClassPath = new URLClassPath(builder.getURLs());
	}

	public URL[] findCodeSourceStack(String className) {
		URL source = locateCodeSource(className);
		return getResourceBaseStack(source);
	}

	public URL[] findResourceSourceStack(String resourceName) {
		URL source = locateResourceSource(resourceName);
		return getResourceBaseStack(source);
	}

	private URL[] getResourceBaseStack(URL source) {
		if (source != null) {
			URLClassPath classPath = getURLClassPath();
			Class<?> jarLoaderClz;
			try {
				jarLoaderClz = Util.getDefaultLoader().loadClass(
						"sun.misc.URLClassPath$JarLoader");
			} catch (ClassNotFoundException ignored) {
				return new URL[] { source };
			}
			Stack<URL> urlStack = new Stack<URL>();
			for (URL url : classPath.getURLs()) {
				urlStack.add(0, url);
			}
			Stack<URL> foundedStack = new Stack<URL>();
			while (!urlStack.isEmpty()) {
				URL url = urlStack.peek();
				if (!foundedStack.isEmpty() && foundedStack.peek() == url) {
					foundedStack.pop();
					urlStack.pop();
					continue;
				}
				if (url.equals(source)) {
					// founded
					foundedStack.push(url);
					break;
				}
				try {
					Object loader = Util.invokeMethod(classPath,
							URLClassPath.class, "getLoader",
							new Class[] { URL.class }, new Object[] { url },
							Object.class);
					if (jarLoaderClz.isInstance(loader)) {
						URL[] urls = Util.invokeMethod(loader, jarLoaderClz,
								"getClassPath", new Class[0], new Object[0],
								URL[].class);
						if (urls != null) {
							foundedStack.push(url);
							for (int i = urls.length - 1; i >= 0; i--) {
								urlStack.push(urls[i]);
							}
							continue;
						}
					}
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e.getCause());
				}
				urlStack.pop();
			}
			Collections.reverse(foundedStack);
			return foundedStack.toArray(new URL[foundedStack.size()]);
		} else {
			return new URL[0];
		}
	}

	public URL locateResource(String resourceName) {
		Resource resource = getURLClassPath().getResource(resourceName);
		if (resource != null) {
			return resource.getURL();
		} else {
			return null;
		}
	}

	public URL[] findResources(String resourceName) {
		Enumeration<?> resources = getURLClassPath().getResources(resourceName);
		List<URL> list = new ArrayList<URL>();
		while (resources.hasMoreElements()) {
			Resource res = (Resource) resources.nextElement();
			list.add(res.getURL());
		}
		return list.toArray(new URL[list.size()]);
	}

	public URL locateResourceSource(String resourceName) {
		Resource resource = getURLClassPath().getResource(resourceName);
		if (resource != null) {
			return resource.getCodeSourceURL();
		} else {
			return null;
		}
	}

	public URL[] findResourceSources(String resourceName) {
		Enumeration<?> resources = getURLClassPath().getResources(resourceName);
		List<URL> list = new ArrayList<URL>();
		while (resources.hasMoreElements()) {
			Resource res = (Resource) resources.nextElement();
			list.add(res.getCodeSourceURL());
		}
		return list.toArray(new URL[list.size()]);
	}

	public String[] findDepedencies(String className) {
		JavaClass theClass = parseJavaClass(locateClass(className));
		if (theClass == null) {
			return new String[0];
		}

		Set<String> foundedClass = new HashSet<String>();
		Set<String> notFoundedClass = new HashSet<String>();
		processDependency(className, foundedClass, notFoundedClass);

		return foundedClass.toArray(new String[foundedClass.size()]);
	}

	public String[] findNotFoundedDepedencies(String className) {
		JavaClass theClass = parseJavaClass(locateClass(className));
		if (theClass == null) {
			return new String[0];
		}

		Set<String> foundedClass = new HashSet<String>();
		Set<String> notFoundedClass = new HashSet<String>();
		processDependency(className, foundedClass, notFoundedClass);

		return notFoundedClass.toArray(new String[notFoundedClass.size()]);
	}

	public boolean cat(String finding, PrintStream output) {
		boolean find = false;
		for (URL url : getURLClassPath().getURLs()) {
			JarCat jarCat = null;
			try {
				jarCat = new JarCat(url.openStream());
				find |= jarCat.match(new DefaultNameMatcher(finding,
						new Scanner(new InputStream() {

							// always input Enter
							@Override
							public int read() throws IOException {
								return '\n';
							}

						}), null), new DefaultMatchOutput(output, "::"));
			} catch (IOException e) {
				throw new IllegalArgumentException("cannot process " + url, e);
			} finally {
				if (jarCat != null) {
					try {
						jarCat.close();
					} catch (IOException ignored) {
					}
				}
			}
		}
		return find;
	}

	public String[] findReferencedBy(String refClassName, String packageName) {
		Set<String> allClassNames = Util.listAllClassNames(this,
				Util.createInPackage(packageName, false));
		Set<String> foundedClass = new HashSet<String>();

		for (String className : allClassNames) {
			JavaClass javaClass = parseJavaClass(locateClass(className));

			if (javaClass == null) {
				continue;
			}
			Set<String> deps = getDependenciesFor(javaClass);
			if (deps.contains(refClassName)) {
				foundedClass.add(javaClass.getClassName());
			}
		}
		return foundedClass.toArray(new String[foundedClass.size()]);
	}

	public String[] findReferencedByMethod(String fullMethodName,
			String packageName) {
		Set<String> allClassNames = Util.listAllClassNames(this,
				Util.createInPackage(packageName, false));
		Set<String> foundedClass = new HashSet<String>();
		if (fullMethodName.lastIndexOf('.') < 0) {
			throw new IllegalArgumentException("not full method name: "
					+ fullMethodName);
		}
		String refClassName = fullMethodName.substring(0,
				fullMethodName.lastIndexOf('.'));

		for (String className : allClassNames) {
			JavaClass javaClass = parseJavaClass(locateClass(className));

			if (javaClass == null) {
				continue;
			}
			DependencyVisitor dependencyVisitor = getDependencyVisitor(javaClass);
			if (dependencyVisitor.getDependencies().contains(refClassName)) {
				if (dependencyVisitor.getDependencyMethods().contains(
						fullMethodName)) {
					foundedClass.add(javaClass.getClassName());
				}
			}
		}
		return foundedClass.toArray(new String[foundedClass.size()]);
	}

	private URLClassPath getURLClassPath() {
		return urlClassPath;
	}

	private void processDependency(String className, Set<String> foundedClass,
			Set<String> notFoundedClass) {
		Stack<String> processedClass = new Stack<String>();
		processedClass.add(className);

		while (!processedClass.isEmpty()) {
			String name = processedClass.pop();
			if (name == null || foundedClass.contains(name)
					|| notFoundedClass.contains(name)) {
				// founded or not founded
				continue;
			}
			JavaClass javaClass = parseJavaClass(locateClass(name));
			if (javaClass == null) {
				notFoundedClass.add(name);
				continue;
			}
			processedClass.addAll(getDependenciesFor(javaClass));
			foundedClass.add(javaClass.getClassName());
		}
	}

	private Set<String> getDependenciesFor(JavaClass javaClass) {
		if (javaClass == null) {
			throw new NullPointerException("java class is null");
		}
		DependencyVisitor dependencyVisitor = new DependencyVisitor();
		DescendingVisitor traverser = new DescendingVisitor(javaClass,
				dependencyVisitor);
		traverser.visit();
		return dependencyVisitor.getDependencies();
	}

	private DependencyVisitor getDependencyVisitor(JavaClass javaClass) {
		if (javaClass == null) {
			throw new NullPointerException("java class is null");
		}
		DependencyVisitor dependencyVisitor = new DependencyVisitor();
		DescendingVisitor traverser = new DescendingVisitor(javaClass,
				dependencyVisitor);
		traverser.visit();
		return dependencyVisitor;
	}

	private boolean isAssignableFrom(String className,
			Set<String> foundedClass, Set<String> processedClass) {
		if (processedClass.contains(className)) {
			// has processed
			return foundedClass.contains(className);
		}
		processedClass.add(className);
		JavaClass javaClass = parseJavaClass(locateClass(className));
		if (javaClass == null) {
			// not in the classpath
			return false;
		}
		if (isAssignableFrom(javaClass.getSuperclassName(), foundedClass,
				processedClass)) {
			foundedClass.add(className);
			return true;
		}
		for (String infName : javaClass.getInterfaceNames()) {
			if (isAssignableFrom(infName, foundedClass, processedClass)) {
				foundedClass.add(className);
				return true;
			}
		}
		return false;
	}

	private static JavaClass parseJavaClass(URL url) {
		if (url == null)
			return null;
		InputStream stream = null;
		try {
			stream = url.openStream();
			return new ClassParser(stream, url.getFile()).parse();
		} catch (ClassFormatError e) {
			return null;
		} catch (IOException ignored) {
			return null;
		} finally {
			Util.close(stream);
		}
	}

	public String[] findAssignableFrom(String topPackageName,
			String parentClassName) {
		JavaClass theClass = parseJavaClass(locateClass(parentClassName));
		if (theClass == null) {
			return new String[0];
		}

		Set<String> allClassNames = Util.listAllClassNames(
				this,
				topPackageName == null ? Util.acceptAll(String.class) : Util
						.createStartsWith(topPackageName));

		Set<String> foundedClass = new HashSet<String>();
		foundedClass.add(theClass.getClassName());
		Set<String> processedClass = new HashSet<String>();
		processedClass.add(parentClassName);

		for (String className : allClassNames) {
			isAssignableFrom(className, foundedClass, processedClass);
		}
		return foundedClass.toArray(new String[foundedClass.size()]);
	}

	public String[] findPackageClasses(String packageName, boolean directPackage) {
		Set<String> allClassNames = Util.listAllClassNames(this,
				Util.createInPackage(packageName, directPackage));

		return allClassNames.toArray(new String[allClassNames.size()]);
	}

	public String[] findSuperTypes(String topPackageName,
			String subTypeClassName) {
		return Util.filter(findSuperTypes(subTypeClassName),
				Util.createInPackage(topPackageName, false));
	}

	public String[] findSuperTypes(String subTypeClassName) {
		JavaClass theClass = parseJavaClass(locateClass(subTypeClassName));
		if (theClass == null) {
			return new String[0];
		}

		List<String> list = new ArrayList<String>();
		Stack<JavaClass> stack = new Stack<JavaClass>();
		stack.push(theClass);
		while (!stack.isEmpty()) {
			JavaClass javaClass = stack.pop();
			if (javaClass != null && !list.contains(javaClass.getClassName())) {
				list.add(javaClass.getClassName());
			} else {
				continue;
			}
			for (String inf : javaClass.getInterfaceNames()) {
				stack.push(parseJavaClass(locateClass(inf)));
			}
			if (javaClass.getSuperclassName() != null) {
				stack.push(parseJavaClass(locateClass(javaClass
						.getSuperclassName())));
			}
		}
		list.remove(theClass.getClassName());
		return list.toArray(new String[list.size()]);
	}

	public Map<String, Set<String>> findConstants(String packageName,
			String text) {
		Set<String> allClassNames = Util.listAllClassNames(this,
				Util.createInPackage(packageName, false));

		Map<String, Set<String>> ret = new TreeMap<String, Set<String>>();
		for (String className : allClassNames) {
			JavaClass javaClass = parseJavaClass(locateClass(className));

			if (javaClass == null) {
				continue;
			}
			ConstantPoolVisitor visitor = new ConstantPoolVisitor(text);
			DescendingVisitor traverser = new DescendingVisitor(javaClass,
					visitor);
			traverser.visit();
			if (!visitor.getStrings().isEmpty()) {
				ret.put(className, visitor.getStrings());
			}

		}
		return ret;
	}

}
