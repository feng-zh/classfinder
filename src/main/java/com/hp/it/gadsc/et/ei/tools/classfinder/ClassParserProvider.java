package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface ClassParserProvider {

	public static class Jclass {

		private String className;

		private String superClassName;

		private String[] interfaceNames;

		private Object nativeObject;

		public Object getNativeObject() {
			return nativeObject;
		}

		public void setNativeObject(Object nativeObject) {
			this.nativeObject = nativeObject;
		}

		public String getSuperClassName() {
			return superClassName;
		}

		public void setSuperClassName(String superClassName) {
			this.superClassName = superClassName;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public void setInterfaceNames(String[] interfaceNames) {
			this.interfaceNames = interfaceNames;
		}

		public String getClassName() {
			return className;
		}

		public String[] getInterfaceNames() {
			return interfaceNames;
		}

	}

	public Jclass parse(InputStream stream, String file) throws IOException;

	public Set<String> getDependencies(Jclass javaClass);

	public Set<String> getStrings(Jclass javaClass, String text);

	public Set<String> getDependencyMethods(Jclass javaClass);

}
