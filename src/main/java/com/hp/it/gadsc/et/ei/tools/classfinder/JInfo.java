package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

class JInfo {
	private Properties sysProp;

	public JInfo(int pid, String javaHome) throws IOException {
		File jinfoExec = new File(getJdkHome(new File(javaHome)), "bin/jinfo");
		if (!jinfoExec.exists()) {
			throw new IllegalArgumentException(
					"no jinfo execuable file found: "
							+ jinfoExec.getAbsolutePath());
		}
		Process jinfo = Runtime.getRuntime().exec(
				jinfoExec.getAbsolutePath() + " -sysprops " + pid);
		InputStream input = jinfo.getInputStream();
		try {
			Properties prop = new Properties();
			prop.load(input);
			if (jinfo.waitFor() != 0) {
				input = jinfo.getErrorStream();
				StringBuffer error = new StringBuffer();
				byte[] buffer = new byte[1024];
				int len;
				while ((len = input.read(buffer)) != -1) {
					error.append(new String(buffer, 0, len));
				}
				throw new IOException("Execute jinfo error:\n"
						+ error.toString());
			} else {
				sysProp = prop;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			close(jinfo.getInputStream());
			close(jinfo.getErrorStream());
			close(jinfo.getOutputStream());
			jinfo.destroy();
		}
	}

	private File getJdkHome(File javaHome) {
		File toolsJarFile = new File(new File(javaHome, "lib"), "tools.jar");
		if (!toolsJarFile.exists()) {
			toolsJarFile = new File(new File(javaHome.getParentFile(), "lib"),
					"tools.jar");
		}
		return toolsJarFile.getParentFile().getParentFile();
	}

	private void close(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException ignored) {
			}
		}
	}

	public File getJavaHome() {
		return new File(getSystemProperty("java.home"));
	}

	public URL[] getBootClassPath() {
		return Util.parsePath(getSystemProperty("sun.boot.class.path"), false,
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

	public URL[] getClassPath() {
		return Util.parsePath(getSystemProperty("java.class.path"), false,
				getSystemProperty("user.dir"));
	}

	public String getSystemProperty(String propName) {
		return sysProp.getProperty(propName);
	}

}
