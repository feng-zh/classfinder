package com.hp.it.gadsc.et.ei.tools.classfinder;

import com.hp.it.gadsc.et.ei.tools.classfinder.ClassParserProvider.Jclass;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;

public class ClassPathFinder extends AbstractClassFinder implements ClassFinder {

    private static ClassParserProvider parserProvider = detectParseProvider();

    private final URLClassPath urlClassPath;

    public ClassPathFinder(ClassPathBuilder builder) {
        urlClassPath = new URLClassPath(builder.getURLs());
    }

    private static ClassParserProvider detectParseProvider() {
        return new AsmClassParserProvider();
    }

    private static Jclass parseJavaClass(URL url) {
        if (url == null)
            return null;
        try (InputStream stream = url.openStream()) {
            return parserProvider.parse(stream, url.getFile());
        } catch (ClassFormatError | Exception e) {
            return null;
        }
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
            Stack<URL> urlStack = new Stack<>();
            for (URL url : classPath.getURLs()) {
                urlStack.add(0, url);
            }
            Stack<URL> foundedStack = new Stack<>();
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
                    URLClassPath.Loader loader = classPath.getLoader(url);
                    if (loader instanceof URLClassPath.JarLoader) {
                        URL[] urls = loader.getClassPath();
                        if (urls != null) {
                            foundedStack.push(url);
                            for (int i = urls.length - 1; i >= 0; i--) {
                                urlStack.push(urls[i]);
                            }
                            continue;
                        }
                    }
                } catch (Exception e) {
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
        URLClassPath.Resource resource = getURLClassPath().getResource(resourceName);
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
            URLClassPath.Resource res = (URLClassPath.Resource) resources.nextElement();
            list.add(res.getURL());
        }
        return list.toArray(new URL[list.size()]);
    }

    public URL locateResourceSource(String resourceName) {
        URLClassPath.Resource resource = getURLClassPath().getResource(resourceName);
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
            URLClassPath.Resource res = (URLClassPath.Resource) resources.nextElement();
            list.add(res.getCodeSourceURL());
        }
        return list.toArray(new URL[list.size()]);
    }

    public String[] findDependencies(String className) {
        Jclass theClass = parseJavaClass(locateClass(className));
        if (theClass == null) {
            return new String[0];
        }

        Set<String> foundedClass = new HashSet<>();
        Set<String> notFoundedClass = new HashSet<>();
        processDependency(className, foundedClass, notFoundedClass);

        return foundedClass.toArray(new String[foundedClass.size()]);
    }

    public String[] findNotFoundedDependencies(String className) {
        Jclass theClass = parseJavaClass(locateClass(className));
        if (theClass == null) {
            return new String[0];
        }

        Set<String> foundedClass = new HashSet<>();
        Set<String> notFoundedClass = new HashSet<>();
        processDependency(className, foundedClass, notFoundedClass);

        return notFoundedClass.toArray(new String[notFoundedClass.size()]);
    }

    public boolean cat(String finding, PrintStream output) {
        boolean find = false;
        for (URL url : getURLClassPath().getURLs()) {
            JarCat jarCat = null;
            try {
                jarCat = new JarCat(url.openStream());
                find |= jarCat.match(new DefaultNameMatcher(finding, new Scanner(new InputStream() {

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
        Set<String> allClassNames = Util.listAllClassNames(this, Util.createInPackage(packageName, false));
        Set<String> foundedClass = new HashSet<>();

        for (String className : allClassNames) {
            Jclass javaClass = parseJavaClass(locateClass(className));

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

    public String[] findReferencedByMethod(String fullMethodName, String packageName) {
        Set<String> allClassNames = Util.listAllClassNames(this, Util.createInPackage(packageName, false));
        Set<String> foundedClass = new HashSet<>();
        if (fullMethodName.lastIndexOf('.') < 0) {
            throw new IllegalArgumentException("not full method name: " + fullMethodName);
        }

        for (String className : allClassNames) {
            Jclass javaClass = parseJavaClass(locateClass(className));

            if (javaClass == null) {
                continue;
            }
            if (parserProvider.getDependencyMethods(javaClass).contains(fullMethodName)) {
                foundedClass.add(javaClass.getClassName());
            }
        }
        return foundedClass.toArray(new String[foundedClass.size()]);
    }

    private URLClassPath getURLClassPath() {
        return urlClassPath;
    }

    private void processDependency(String className, Set<String> foundedClass, Set<String> notFoundedClass) {
        Stack<String> processedClass = new Stack<>();
        processedClass.add(className);

        while (!processedClass.isEmpty()) {
            String name = processedClass.pop();
            if (name == null || foundedClass.contains(name) || notFoundedClass.contains(name)) {
                // founded or not founded
                continue;
            }
            Jclass javaClass = parseJavaClass(locateClass(name));
            if (javaClass == null) {
                notFoundedClass.add(name);
                continue;
            }
            processedClass.addAll(getDependenciesFor(javaClass));
            foundedClass.add(javaClass.getClassName());
        }
    }

    private Set<String> getDependenciesFor(Jclass javaClass) {
        if (javaClass == null) {
            throw new NullPointerException("java class is null");
        }
        return parserProvider.getDependencies(javaClass);
    }

    private boolean isAssignableFrom(String className, Set<String> foundedClass, Set<String> processedClass) {
        if (processedClass.contains(className)) {
            // has processed
            return foundedClass.contains(className);
        }
        processedClass.add(className);
        Jclass javaClass = parseJavaClass(locateClass(className));
        if (javaClass == null) {
            // not in the classpath
            return false;
        }
        if (isAssignableFrom(javaClass.getSuperClassName(), foundedClass, processedClass)) {
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

    public String[] findAssignableFrom(String topPackageName, String parentClassName) {
        Jclass theClass = parseJavaClass(locateClass(parentClassName));
        if (theClass == null) {
            return new String[0];
        }

        Set<String> allClassNames = Util.listAllClassNames(this,
                topPackageName == null ? Util.acceptAll(String.class) : Util.createStartsWith(topPackageName));

        Set<String> foundedClass = new HashSet<>();
        foundedClass.add(theClass.getClassName());
        Set<String> processedClass = new HashSet<>();
        processedClass.add(parentClassName);

        for (String className : allClassNames) {
            isAssignableFrom(className, foundedClass, processedClass);
        }
        return foundedClass.toArray(new String[foundedClass.size()]);
    }

    public String[] findPackageClasses(String packageName, boolean directPackage) {
        Set<String> allClassNames = Util.listAllClassNames(this, Util.createInPackage(packageName, directPackage));

        return allClassNames.toArray(new String[allClassNames.size()]);
    }

    public String[] findSuperTypes(String topPackageName, String subTypeClassName) {
        return Util.filter(findSuperTypes(subTypeClassName), Util.createInPackage(topPackageName, false));
    }

    public String[] findSuperTypes(String subTypeClassName) {
        Jclass theClass = parseJavaClass(locateClass(subTypeClassName));
        if (theClass == null) {
            return new String[0];
        }

        List<String> list = new ArrayList<>();
        Stack<Jclass> stack = new Stack<>();
        stack.push(theClass);
        while (!stack.isEmpty()) {
            Jclass javaClass = stack.pop();
            if (javaClass != null && !list.contains(javaClass.getClassName())) {
                list.add(javaClass.getClassName());
            } else {
                continue;
            }
            for (String inf : javaClass.getInterfaceNames()) {
                stack.push(parseJavaClass(locateClass(inf)));
            }
            if (javaClass.getSuperClassName() != null) {
                stack.push(parseJavaClass(locateClass(javaClass.getSuperClassName())));
            }
        }
        list.remove(theClass.getClassName());
        return list.toArray(new String[list.size()]);
    }

    public Map<String, Set<String>> findConstants(String packageName, String text) {
        Set<String> allClassNames = Util.listAllClassNames(this, Util.createInPackage(packageName, false));

        Map<String, Set<String>> ret = new TreeMap<>();
        for (String className : allClassNames) {
            Jclass javaClass = parseJavaClass(locateClass(className));

            if (javaClass == null) {
                continue;
            }
            Set<String> strings = parserProvider.getStrings(javaClass, text);
            if (!strings.isEmpty()) {
                ret.put(className, strings);
            }

        }
        return ret;
    }

    public String[] findReferencedByField(String fullFieldName, String packageName) {
        Set<String> allClassNames = Util.listAllClassNames(this, Util.createInPackage(packageName, false));
        Set<String> foundedClass = new HashSet<>();
        if (fullFieldName.lastIndexOf('.') < 0) {
            throw new IllegalArgumentException("not full field name: " + fullFieldName);
        }

        for (String className : allClassNames) {
            Jclass javaClass = parseJavaClass(locateClass(className));

            if (javaClass == null) {
                continue;
            }
            if (parserProvider.getDependencyFields(javaClass).contains(fullFieldName)) {
                foundedClass.add(javaClass.getClassName());
            }
        }
        return foundedClass.toArray(new String[foundedClass.size()]);
    }

}
