package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.ConstantCP;
import com.sun.org.apache.bcel.internal.classfile.ConstantClass;
import com.sun.org.apache.bcel.internal.classfile.ConstantInterfaceMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantMethodref;
import com.sun.org.apache.bcel.internal.classfile.ConstantNameAndType;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.EmptyVisitor;
import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.FieldOrMethod;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;

@SuppressWarnings("restriction")
class DependencyVisitor extends EmptyVisitor {

	private ConstantPool constantPool;

	private final Set<String> dependencies;

	private final Set<String> dependencyMethods;

	private static Pattern objectArrayPatten = Pattern.compile("^\\[+L(.*);");

	private static Pattern simpleArrayPatten = Pattern
			.compile("^\\[+[DFJICSBZ]");

	public DependencyVisitor() {
		dependencies = new HashSet<String>();
		dependencyMethods = new HashSet<String>();
	}

	private void addClass(String classname) {
		if (classname.startsWith("[")) {
			Matcher m = objectArrayPatten.matcher(classname);
			if (m.matches()) {
				classname = m.group(1);
			} else {
				m = simpleArrayPatten.matcher(classname);
				if (m.matches()) {
					// ignore simple type
					return;
				}
			}
		}
		dependencies.add(classname);
	}

	private void addClasses(String string) {
		StringTokenizer tokens = new StringTokenizer(string, ";");
		while (tokens.hasMoreTokens()) {
			String descriptor = tokens.nextToken();
			int pos = descriptor.indexOf('L');
			if (pos != -1) {
				addSlashClass(descriptor.substring(pos + 1));
			}
		}

	}

	private void addSlashClass(String classname) {
		addClass(classname.replace('/', '.'));
	}

	public Set<String> getDependencies() {
		return dependencies;
	}

	public Set<String> getDependencyMethods() {
		return dependencyMethods;
	}

	public void visitConstantClass(ConstantClass constantClass) {
		String classname = constantClass.getConstantValue(constantPool)
				.toString();
		addSlashClass(classname);
	}

	public void visitConstantNameAndType(ConstantNameAndType obj) {
		String name = obj.getName(constantPool);
		if (obj.getSignature(constantPool).equals("Ljava/lang/Class;")
				&& name.startsWith("class$")) {
			String classname = name.substring(6).replace('$', '.');
			int index = classname.lastIndexOf(".");
			if (index > 0) {
				int index2 = classname.lastIndexOf(".", index - 1);
				char start;
				if (index2 != -1)
					start = classname.charAt(index2 + 1);
				else
					start = classname.charAt(0);
				if (start > '@' && start < '[') {
					classname = classname.substring(0, index) + "$"
							+ classname.substring(index + 1);
					addClass(classname);
				} else {
					addClass(classname);
				}
			} else {
				addClass(classname);
			}
		}
	}

	public void visitConstantPool(ConstantPool constantPool) {
		this.constantPool = constantPool;
	}

	public void visitField(Field field) {
		addClasses(field.getSignature());
		removeWarning(field);
	}

	private void removeWarning(FieldOrMethod fm) {
		// NOTE: to remove Generic signature warning
		fm.setAttributes(new Attribute[0]);
	}

	public void visitJavaClass(JavaClass javaClass) {
		// NOTE: to remove Generic signature warning
		javaClass.setAttributes(new Attribute[0]);
		addClass(javaClass.getClassName());
	}

	public void visitMethod(Method method) {
		String signature = method.getSignature();
		int pos = signature.indexOf(")");
		addClasses(signature.substring(1, pos));
		addClasses(signature.substring(pos + 1));
		removeWarning(method);
	}

	public void visitConstantInterfaceMethodref(ConstantInterfaceMethodref obj) {
		addMethod(obj);
	}

	public void visitConstantMethodref(ConstantMethodref obj) {
		addMethod(obj);
	}

	private void addMethod(ConstantCP obj) {
		ConstantNameAndType constNameType = (ConstantNameAndType) constantPool
				.getConstant(obj.getNameAndTypeIndex(),
						Constants.CONSTANT_NameAndType);
		String name = constantPool.constantToString(obj.getClassIndex(),
				Constants.CONSTANT_Class)
				+ "."
				+ constantPool.constantToString(constNameType.getNameIndex(),
						Constants.CONSTANT_Utf8);
		dependencyMethods.add(name);
	}

}
