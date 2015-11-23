package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.util.HashSet;
import java.util.Set;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

@SuppressWarnings("restriction")
class AsmDependencyVisitor extends ClassVisitor {

	private Set<Type> depenencies = new HashSet<Type>();

	private Set<String> strings = new HashSet<String>();

	private Set<String> methods = new HashSet<String>();

	private ClassReader reader;

	final String textToFind;

	private static final int CONST_CLASS = 7;

	private static final int CONST_MTYPE = 16;

	private static final int CONST_HANDLE = 15;

	private static final int CONST_DOUBLE = 6;

	private static final int CONST_LONG = 5;

	private static final int CONST_STR = 8;

	private static final int CONST_FIELD = 9;

	private static final int CONST_METH = 10;

	private static final int CONST_IMETH = 11;

	AsmDependencyVisitor(ClassReader reader, String textToFind) {
		super(Opcodes.ASM5);
		this.reader = reader;
		this.textToFind = textToFind;
	}

	public Set<String> getDependencies() {
		Set<String> ret = new HashSet<String>(depenencies.size());
		for (Type type : depenencies) {
			if (type.getSort() == Type.ARRAY) {
				ret.add(type.getElementType().getClassName());
			} else {
				ret.add(type.getClassName());
			}
		}
		return ret;
	}

	public Set<String> getStrings() {
		return strings;
	}

	public Set<String> getDependencyMethods() {
		return methods;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		if (superName != null) {
			addType(Type.getObjectType(superName));
		}
		if (interfaces != null) {
			for (String intf : interfaces) {
				addType(Type.getObjectType(intf));
			}
		}
	}

	private void addType(Type type) {
		if (type == null) {
			throw new IllegalArgumentException("null type");
		}
		depenencies.add(type);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		addType(Type.getType(desc));
		return null;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		Type methodType = Type.getMethodType(desc);
		addMethodType(null, name, methodType);
		return null;
	}

	private void addMethodType(Type ownerType, String methodName, Type methodType) {
		for (Type argType : methodType.getArgumentTypes()) {
			addType(argType);
		}
		addType(methodType.getReturnType());
		if (ownerType != null) {
			addType(ownerType);
			methods.add(ownerType.getClassName() + "." + methodName);
		}
	}

	@Override
	public void visitEnd() {
		// Try to go through all constant pool
		char[] buf = new char[reader.getMaxStringLength()];
		for (int i = 1; i < reader.getItemCount(); i++) {
			int item = reader.getItem(i);
			int constType = reader.b[item - 1];
			switch (constType) {
			case CONST_DOUBLE:
			case CONST_LONG:
				i++;
				break;
			case CONST_CLASS:
				addType((Type) reader.readConst(i, buf));
				break;
			case CONST_MTYPE:
				// Type methodType = (Type) reader.readConst(i, buf);
				break;
			case CONST_HANDLE:
				Handle handle = (Handle) reader.readConst(i, buf);
				addType(Type.getObjectType(handle.getOwner()));
				break;
			case CONST_STR:
				if (textToFind != null) {
					addString((String) reader.readConst(i, buf));
				}
				break;
			case CONST_FIELD:
			case CONST_METH:
			case CONST_IMETH:
				String fmType = reader.readClass(item, buf);
				int nameType = reader.getItem(reader.readUnsignedShort(item + 2));
				String fmName = reader.readUTF8(nameType, buf);
				String fmDesc = reader.readUTF8(nameType + 2, buf);
				addType(Type.getObjectType(fmType));
				if (constType == CONST_METH || constType == CONST_IMETH) {
					addMethodType(Type.getObjectType(fmType), fmName, Type.getMethodType(fmDesc));
				}
				break;
			default:
				// System.out.println("Unknown: " + constType);
			}
		}

	}

	private void addString(String str) {
		if (str.contains(textToFind)) {
			strings.add(str);
		}
	}

	public static AsmDependencyVisitor getDependencyVisitor(ClassReader classReader, String textToFind) {
		AsmDependencyVisitor visitor = new AsmDependencyVisitor(classReader, textToFind);
		classReader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
		return visitor;
	}

	static int readUnsignedShort(byte[] b, final int index) {
		return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
	}

	public static void main(String[] args) throws Exception {
		System.out.println("hello");
		ClassReader reader = new ClassReader(AsmDependencyVisitor.class.getName());
		// parses the constant pool
		AsmDependencyVisitor visitor = AsmDependencyVisitor.getDependencyVisitor(reader, "");
		for (String s : visitor.getDependencies()) {
			System.out.println(s);
		}
		for (String s : visitor.getStrings()) {
			System.out.println(s);
		}
		for (String s : visitor.getDependencyMethods()) {
			System.out.println(s);
		}
	}

}
