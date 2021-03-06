package com.github.fengzh.classfinder;

import org.objectweb.asm.*;

import java.util.HashSet;
import java.util.Set;

class AsmDependencyVisitor extends ClassVisitor {

	private Set<Type> dependencies = new HashSet<>();

	private Set<String> strings = new HashSet<>();

	private Set<String> methods = new HashSet<>();

	private Set<String> fields = new HashSet<>();

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
		super(Opcodes.ASM7);
		this.reader = reader;
		this.textToFind = textToFind;
	}

	public Set<String> getDependencies() {
		Set<String> ret = new HashSet<>(dependencies.size());
		for (Type type : dependencies) {
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
		dependencies.add(type);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		addFieldType(null, name, Type.getType(desc));
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
				if (constType == CONST_FIELD) {
					addFieldType(Type.getObjectType(fmType), fmName, Type.getObjectType(fmDesc));
				}
				break;
			default:
				// System.out.println("Unknown: " + constType);
			}
		}

	}

	private void addFieldType(Type ownerType, String fmName, Type fieldType) {
		addType(fieldType);
		if (ownerType != null) {
			addType(ownerType);
			fields.add(ownerType.getClassName() + "." + fmName);
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
		for (String s : visitor.getDependencyFields()) {
			System.out.println(s);
		}
	}

	public Set<String> getDependencyFields() {
		return fields;
	}

}
