package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.DescendingVisitor;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;

@SuppressWarnings("restriction")
public class BcelClassParserProvider implements ClassParserProvider {

	public Jclass parse(InputStream stream, String file) throws IOException {
		return toJclass(new ClassParser(stream, file).parse());
	}

	private Jclass toJclass(JavaClass javaClass) {
		Jclass jclass = new Jclass();
		jclass.setClassName(javaClass.getClassName());
		jclass.setInterfaceNames(javaClass.getInterfaceNames());
		jclass.setSuperClassName(javaClass.getSuperclassName());
		jclass.setNativeObject(javaClass);
		return jclass;
	}

	public Set<String> getDependencies(Jclass jClass) {
		DependencyVisitor dependencyVisitor = new DependencyVisitor();
		DescendingVisitor traverser = new DescendingVisitor((JavaClass) jClass.getNativeObject(), dependencyVisitor);
		traverser.visit();
		return dependencyVisitor.getDependencies();
	}

	public Set<String> getStrings(Jclass jClass, String text) {
		ConstantPoolVisitor visitor = new ConstantPoolVisitor(text);
		DescendingVisitor traverser = new DescendingVisitor((JavaClass) jClass.getNativeObject(), visitor);
		traverser.visit();
		return visitor.getStrings();
	}

	public Set<String> getDependencyMethods(Jclass jClass) {
		DependencyVisitor dependencyVisitor = new DependencyVisitor();
		DescendingVisitor traverser = new DescendingVisitor((JavaClass) jClass.getNativeObject(), dependencyVisitor);
		traverser.visit();
		return dependencyVisitor.getDependencyMethods();
	}

}
