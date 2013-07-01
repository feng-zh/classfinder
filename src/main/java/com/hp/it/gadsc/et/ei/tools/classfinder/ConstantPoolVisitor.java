package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.util.HashSet;
import java.util.Set;

import com.sun.org.apache.bcel.internal.classfile.Attribute;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.ConstantString;
import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.EmptyVisitor;
import com.sun.org.apache.bcel.internal.classfile.Field;
import com.sun.org.apache.bcel.internal.classfile.FieldOrMethod;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;

@SuppressWarnings("restriction")
class ConstantPoolVisitor extends EmptyVisitor {

	private ConstantPool constantPool;

	private final Set<String> constants;

	private final String text;

	public ConstantPoolVisitor(String text) {
		this.text = text;
		constants = new HashSet<String>();
	}

	public Set<String> getStrings() {
		return constants;
	}

	@Override
	public void visitConstantString(ConstantString obj) {
		String string = obj.getBytes(constantPool);
		if (string.contains(text)) {
			constants.add(string);
		}
	}

	@Override
	public void visitConstantUtf8(ConstantUtf8 obj) {
		String bytes = obj.getBytes();
		if (bytes.contains(text)) {
			constants.add(bytes);
		}
	}

	public void visitConstantPool(ConstantPool constantPool) {
		this.constantPool = constantPool;
	}

	public void visitField(Field field) {
		removeWarning(field);
	}

	private void removeWarning(FieldOrMethod fm) {
		// NOTE: to remove Generic signature warning
		fm.setAttributes(new Attribute[0]);
	}

	public void visitJavaClass(JavaClass javaClass) {
		// NOTE: to remove Generic signature warning
		javaClass.setAttributes(new Attribute[0]);
	}

	public void visitMethod(Method method) {
		removeWarning(method);
	}
}
