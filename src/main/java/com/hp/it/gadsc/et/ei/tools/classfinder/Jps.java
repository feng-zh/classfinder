package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.concurrent.Callable;

class Jps implements Closeable {

	private Callable<Properties> vmAdapter;

	private Properties allProperites;

	public Jps(int pid) throws IOException {
		vmAdapter = createVMAdapter(pid);
	}

	public Jps(String stringInLine) throws IOException {
		vmAdapter = createVMAdapter(stringInLine);
	}

	public int getProcessId() {
		return Integer.parseInt(getAllProperites().getProperty(
				"classfinder.pid"));
	}

	@SuppressWarnings("unchecked")
	private Callable<Properties> createVMAdapter(int pid) throws IOException {
		try {
			File toolsJarFile = getToolsJarFile();
			if (toolsJarFile.exists()) {
				Class<?> clz = loadVMAdapterClass(toolsJarFile);
				try {
					return (Callable<Properties>) Util.newInstance(clz,
							new Class[] { Integer.TYPE },
							new Object[] { new Integer(pid) });
				} catch (InvocationTargetException e) {
					throw e.getCause();
				}
			} else {
				return new VMAdapter(pid);
			}
		} catch (Throwable e) {
			throw new IOException(e.toString());
		}
	}

	@SuppressWarnings("unchecked")
	private Callable<Properties> createVMAdapter(String stringInLine)
			throws IOException {
		try {
			File toolsJarFile = getToolsJarFile();
			if (toolsJarFile.exists()) {
				Class<?> clz = loadVMAdapterClass(toolsJarFile);
				try {
					return (Callable<Properties>) Util.newInstance(clz,
							new Class[] { String.class },
							new Object[] { stringInLine });
				} catch (InvocationTargetException e) {
					throw e.getCause();
				}
			} else {
				return new VMAdapter(stringInLine);
			}
		} catch (Throwable e) {
			throw new IOException(e.toString());
		}
	}

	private Class<?> loadVMAdapterClass(File toolsJarFile)
			throws MalformedURLException, ClassNotFoundException {
		ClassLoader loader = new URLClassLoader(new URL[] {
				toolsJarFile.toURI().toURL(),
				new ClassLoaderFinder().locateCodeSource(VMAdapter.class
						.getName()) }, null);
		Class<?> clz = loader.loadClass(VMAdapter.class.getName());
		return clz;
	}

	private File getToolsJarFile() {
		File javaHome = Util.getJavaHome();
		File toolsJarFile = new File(new File(javaHome, "lib"), "tools.jar");
		if (!toolsJarFile.exists()) {
			toolsJarFile = new File(new File(javaHome.getParentFile(), "lib"),
					"tools.jar");
		}
		return toolsJarFile;
	}

	public File getJavaHome() {
		return new File(getSystemProperty("java.home"));
	}

	public URL[] getBootClassPath() {
		return Util.parsePath(
				getAllProperites().getProperty(
						"sun.property.sun.boot.class.path"), false,
				getSystemProperty("user.dir"));
	}

	public URL[] getExtClassPath() {
		return Util.parsePath(getSystemProperty("java.ext.dirs"), true,
				getSystemProperty("user.dir"));
	}

	public URL[] getEndorsedClassPath() {
		return Util.parsePath(getSystemProperty("java.endorsed.dirs"), true,
				getSystemProperty("user.dir"));
	}

	// NOTE: It may get 1024 chars based on default JVM setting
	// TO break this limit, the attached JVM need to be started with
	// -XX:PerfMaxStringConstLength=<SIZE>
	// Coming from http://www.md.pp.ru/~eu/jdk6options.html
	public URL[] getClassPath() {
		return Util.parsePath(getSystemProperty("java.class.path"), false,
				getSystemProperty("user.dir"));
	}

	public String getSystemProperty(String propName) {
		return getAllProperites().getProperty("java.property." + propName);
	}

	public Properties getAllProperites() {
		if (allProperites == null) {
			try {
				allProperites = vmAdapter.call();
			} catch (Exception e) {
				return new Properties();
			}
		}
		return allProperites;
	}

	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}

	public void close() {
		try {
			((Closeable) vmAdapter).close();
		} catch (IOException ignored) {
		}
	}

}
