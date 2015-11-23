package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import jdk.internal.org.objectweb.asm.ClassReader;

@SuppressWarnings("restriction")
public class AsmClassParserProvider implements ClassParserProvider {

	private static final String[] EMPTY = new String[0];

	public Jclass parse(InputStream stream, String file) throws IOException {
		ClassReader reader = new ClassReader(stream);
		return toJclass(reader);
	}

	private Jclass toJclass(ClassReader reader) {
		Jclass jclass = new Jclass();
		jclass.setClassName(toClassName(reader.getClassName()));
		String[] interfaces = reader.getInterfaces();
		if (interfaces == null) {
			jclass.setInterfaceNames(EMPTY);
		} else {
			String[] classNames = new String[interfaces.length];
			for (int i = 0; i < interfaces.length; i++) {
				classNames[i] = toClassName(interfaces[i]);
			}
			jclass.setInterfaceNames(classNames);
		}
		jclass.setSuperClassName(toClassName(reader.getSuperName()));
		Object[] nativeObj = new Object[2];
		nativeObj[0] = reader;
		jclass.setNativeObject(nativeObj);
		return jclass;
	}

	private static String toClassName(String str) {
		if (str == null) {
			return Object.class.getName();
		} else {
			return str.replace('/', '.');
		}
	}

	public Set<String> getDependencies(Jclass jclass) {
		AsmDependencyVisitor visitor = getVisitor(jclass, null);
		return visitor.getDependencies();
	}

	public Set<String> getStrings(Jclass jclass, String text) {
		AsmDependencyVisitor visitor = getVisitor(jclass, text);
		return visitor.getStrings();
	}

	public Set<String> getDependencyMethods(Jclass jclass) {
		AsmDependencyVisitor visitor = getVisitor(jclass, null);
		return visitor.getDependencyMethods();
	}

	private AsmDependencyVisitor getVisitor(Jclass jclass, String textToFind) {
		Object[] nativeObj = (Object[]) jclass.getNativeObject();
		ClassReader reader = (ClassReader) nativeObj[0];
		AsmDependencyVisitor visitor = (AsmDependencyVisitor) nativeObj[1];
		if (visitor == null || visitor.textToFind != textToFind) {
			visitor = AsmDependencyVisitor.getDependencyVisitor(reader, textToFind);
			nativeObj[1] = visitor;
		}
		return visitor;
	}

}
