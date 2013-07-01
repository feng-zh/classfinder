package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassPathFinderMain {

	private static boolean verbose = false;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ClassPathBuilder builder = null;
		int action = 0;
		List<String> names = new ArrayList<String>();
		for (AtomicInteger i = new AtomicInteger(0); i.get() < args.length; i
				.incrementAndGet()) {
			String arg = args[i.get()];
			if (arg.equals("-pid")) {
				String pid = mandatory(args, i, "-pid");
				builder = buildCpById(pid);
			} else if (arg.equals("-cmd")) {
				String cmd = mandatory(args, i, "-cmd");
				builder = buildCpByCmd(cmd);
			} else if (arg.equals("-classpath")) {
				String cp = mandatory(args, i, "-classpath");
				String[] classPaths = cp.split(File.pathSeparator);
				String javaHome = optional(args, i, "-jre");
				builder = buildCp(classPaths, javaHome);
			} else if (arg.equals("-group")) {
				action = 1;// group by source
			} else if (arg.equals("-package")) {
				action = 2;// package find
			} else if (arg.equals("-super")) {
				action = 3;// find super classes
			} else if (arg.equals("-sub")) {
				action = 4;// find sub classes
			} else if (arg.equals("-duplicate")) {
				action = 5;// find duplicated classes
			} else if (arg.equals("-ref")) {
				action = 6;// find referring classes
			} else if (arg.equals("-depend")) {
				action = 7;// find dependence classes
			} else if (arg.equals("-strings")) {
				action = 8;// find strings
			} else if (arg.equals("-mref")) {
				action = 9;// find referring methods
			} else if (arg.equals("-cat")) {
				action = 10;// cat file from jar file
			} else if (arg.equals("-duplicateset")) {
				action = 11;// find duplicated classes by source
			} else if (arg.equals("-conflict")) {
				action = 12;// find conflict classes by source
			} else if (arg.equals("-conflictset")) {
				action = 13;// find conflict classes by source
			} else if (arg.equals("-verbose")) {
				verbose = true;
			} else if (arg.equals("-current")) {
				if (builder == null) {
					builder = new DefaultClassPathBuilder();
				}
				if (builder instanceof DefaultClassPathBuilder) {
					((DefaultClassPathBuilder) builder)
							.appendPathBuilder(new SystemClassPathBuilder(false));
				} else {
					throw new IllegalArgumentException(
							"-current cannot be used in non-classpath option.");
				}
			} else if (arg.startsWith("-")) {
				System.err.println("Unknown option: " + arg);
				usage();
				System.exit(1);
			} else {
				names.add(arg);
			}
		}
		if (!names.isEmpty() && builder == null) {
			builder = buildCp(extractClassPath(new BufferedReader(
					new InputStreamReader(System.in))), null);
		}
		if (builder != null) {
			findByBuilder(builder, names, action);
		} else {
			usage();
			System.exit(1);
		}
	}

	private static String optional(String[] args, AtomicInteger index,
			String argName) {
		if (args.length <= index.get() + 1) {
			return null;
		}
		String nextArgName = args[index.get() + 1];
		if (nextArgName.equals(argName)) {
			index.incrementAndGet();
			return mandatory(args, index, nextArgName);
		} else {
			// throw new IllegalArgumentException("Not support option: "
			// + nextArgName + ". The expected is: " + argName);
			return null;
		}
	}

	private static String mandatory(String[] args, AtomicInteger index,
			String argName) {
		if (args.length <= index.get() + 1) {
			throw new IllegalArgumentException("No value after option: "
					+ argName);
		}
		String value = args[index.incrementAndGet()];
		if (value.startsWith("-")) {
			throw new IllegalArgumentException("No value after option: "
					+ argName);
		}
		return value;
	}

	private static String[] extractClassPath(BufferedReader in) {
		List<String> list = new ArrayList<String>();
		try {
			String line;
			while ((line = in.readLine()) != null) {
				String[] paths = line.split(File.pathSeparator);
				list.addAll(Arrays.asList(paths));
			}
		} catch (IOException ignored) {
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			}
		}
		return list.toArray(new String[list.size()]);
	}

	private static boolean isWindows() {
		return File.pathSeparatorChar == ';';
	}

	private static ClassPathBuilder buildCpById(String pid) {
		try {
			int id = Integer.parseInt(pid);
			ClassPathBuilder builder = isWindows() ? new LocalVMClassPathBuilder(
					id) : new LocalUnixVMClassPathBuilder(id);
			return builder;
		} catch (Exception e) {
			throw new IllegalArgumentException("Cannot attach to process id ["
					+ pid + "] with error: " + e.getMessage(), e);
		}
	}

	private static ClassPathBuilder buildCpByCmd(String cmd) {
		try {
			ClassPathBuilder builder = isWindows() ? new LocalVMClassPathBuilder(
					cmd) : new LocalUnixVMClassPathBuilder(cmd);
			return builder;
		} catch (Exception e) {
			throw new IllegalArgumentException(
					"Cannot attach to process by string [" + cmd
							+ "] with error: " + e.getMessage(), e);
		}
	}

	private static ClassPathBuilder buildCp(String[] classPaths, String javaHome) {
		ClassPathBuilder sysbuilder = null;
		if (javaHome != null) {
			sysbuilder = new SystemClassPathBuilder(new File(javaHome));
		}
		DefaultClassPathBuilder builder = new DefaultClassPathBuilder();
		for (String f : classPaths) {
			File file = new File(f.trim());
			if (file.exists()) {
				builder.addClassFolder(file);
			}
		}
		if (sysbuilder != null) {
			builder.appendPathBuilder(sysbuilder);
		}
		return builder;
	}

	private static void findByBuilder(ClassPathBuilder builder,
			List<String> classNamePatterns, int action) {
		ClassPathFinder finder = new ClassPathFinder(builder);
		ClassFinderAgent agent = new ClassFinderAgent(finder);
		if (verbose) {
			System.err.println("***** START CLASSPATH *****");
			for (URL url : builder.getURLs()) {
				try {
					System.err.println(new File(url.toURI()).getAbsolutePath());
				} catch (URISyntaxException e) {
				}
			}
			System.err.println("***** END CLASSPATH *****");
			System.err.println();
			System.err.println("***** START ARGUMENTS *****");
			for (String cnp : classNamePatterns) {
				System.err.println(cnp);
			}
			System.err.println("***** END ARGUMENTS *****");
		}
		if (action == 0 || action == 1) {
			Set<String> classNames = new TreeSet<String>();
			for (String name : classNamePatterns) {
				if (name.indexOf('.') < 0 || name.indexOf('*') >= 0
						|| name.indexOf('?') >= 0) {
					classNames.addAll(Arrays.asList(finder.lookupClass(name)));
				} else {
					classNames.add(name);
				}
			}
			boolean found = false;
			if (action == 0) {
				for (String className : classNames) {
					int i = 0;
					for (String s : agent.findClasses(className)) {
						if (i++ == 0) {
							System.out.println("[" + className + "]");
						}
						System.out.println(s);
						found = true;
					}
				}
			} else {
				Map<String, List<String>> map = new TreeMap<String, List<String>>();
				for (String className : classNames) {
					for (String s : agent.findClasses(className)) {
						List<String> list = map.get(s);
						if (list == null) {
							list = new ArrayList<String>();
							map.put(s, list);
						}
						list.add(className);
						found = true;
					}
				}
				for (String s : map.keySet()) {
					System.out.println("-- From [" + s + "]");
					for (String ss : map.get(s)) {
						System.out.println(ss);
					}
				}
			}
			if (!found) {
				System.err.println("The class/pattern " + classNamePatterns
						+ " cannot be found from class paths.");
				System.exit(1);
			}
		} else {
			for (String name : classNamePatterns) {
				if (action == 2) {
					String[] classNames = finder
							.findPackageClasses(name, false);
					if (classNames.length > 0) {
						System.out
								.println("-- Class in package [" + name + "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No class found in package ["
								+ name + "]");
					}
				} else if (action == 3) {
					String[] classNames = finder.findSuperTypes(name);
					if (classNames.length > 0) {
						System.out.println("-- Super class for [" + name + "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No super class found for ["
								+ name + "]");
					}
				} else if (action == 4) {
					List<String> classNames = new ArrayList<String>(
							Arrays.asList(finder.findAssignableFrom(name)));
					classNames.remove(name);
					Collections.sort(classNames);
					if (classNames.size() > 0) {
						System.out.println("-- Sub class for [" + name + "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No sub class found for [" + name
								+ "]");
					}
				} else if (action == 5) {
					Map<String, List<URL>> map = finder.findDuplicates(name);
					Map<URL, String> urlCache = new HashMap<URL, String>();
					for (Map.Entry<String, List<URL>> entry : map.entrySet()) {
						String className = entry.getKey();
						int i = 0;
						for (URL url : entry.getValue()) {
							if (i++ == 0) {
								System.out.println("[" + className + "]");
							}
							String file = urlCache.get(url);
							if (file == null) {
								file = Util.toAbsolutePath(url, null);
								urlCache.put(url, file);
							}
							System.out.println(file);
						}
					}
					if (map.isEmpty()) {
						System.err.println("-- No duplicated class found");
					}
				} else if (action == 6) {
					String[] classNames = finder.findReferencedBy(name, null);
					Arrays.sort(classNames);
					if (classNames.length > 0) {
						System.out.println("-- Reference class for [" + name
								+ "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No reference class found for ["
								+ name + "]");
					}
				} else if (action == 7) {
					String[] classNames = finder.findDepedencies(name);
					Arrays.sort(classNames);
					if (classNames.length > 0) {
						System.out.println("-- Dependence class for [" + name
								+ "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No dependence class found for ["
								+ name + "]");
					}
					classNames = finder.findNotFoundedDepedencies(name);
					Arrays.sort(classNames);
					if (classNames.length > 0) {
						System.out
								.println("-- Non-founded dependence class for ["
										+ name + "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					}
				} else if (action == 8) {
					Map<String, Set<String>> classNames = finder.findConstants(
							null, name);
					if (!classNames.isEmpty()) {
						System.out.println("-- Class contains text \"" + name
								+ "\"");
						for (Map.Entry<String, Set<String>> entry : classNames
								.entrySet()) {
							System.out.println("[" + entry.getKey() + "]");
							for (String line : entry.getValue()) {
								System.out.println("\t" + line);
							}
						}
					} else {
						System.err.println("-- No class found for text \""
								+ name + "\"");
					}
				} else if (action == 9) {
					String[] classNames = finder.findReferencedByMethod(name,
							null);
					Arrays.sort(classNames);
					if (classNames.length > 0) {
						System.out.println("-- Reference class for Method ["
								+ name + "]");
						for (String className : classNames) {
							System.out.println(className);
						}
					} else {
						System.err.println("-- No reference class found for ["
								+ name + "]");
					}
				} else if (action == 10) {
					if (!finder.cat(name, System.out)) {
						System.err.println("-- No text file found for [" + name
								+ "]");
					}
				} else if (action == 11) {
					Map<String, List<URL>> map = finder.findDuplicates(name);
					if (map.isEmpty()) {
						System.err.println("-- No duplicated class found");
					} else {
						Map<URL, List<String>> setList = new HashMap<URL, List<String>>();
						for (Map.Entry<String, List<URL>> entry : map
								.entrySet()) {
							String className = entry.getKey();
							for (URL url : entry.getValue()) {
								List<String> classes = setList.get(url);
								if (classes == null) {
									classes = new ArrayList<String>();
									setList.put(url, classes);
								}
								classes.add(className);
							}
						}
						for (Map.Entry<URL, List<String>> entry : setList
								.entrySet()) {
							System.out.println("["
									+ Util.toAbsolutePath(entry.getKey(), null)
									+ "]");
							for (String line : entry.getValue()) {
								System.out.println(line);
							}
						}
					}
				} else if (action == 12) {
					Map<String, Map<URL, Long>> map = finder
							.findConflictClasses(name, false);
					Map<URL, String> urlCache = new HashMap<URL, String>();
					for (Map.Entry<String, Map<URL, Long>> entry : map
							.entrySet()) {
						String className = entry.getKey();
						int i = 0;
						for (Map.Entry<URL, Long> urlEntry : entry
								.getValue().entrySet()) {
							if (i++ == 0) {
								System.out.println("[" + className + "]");
							}
							URL url = urlEntry.getKey();
							String file = urlCache.get(url);
							if (file == null) {
								file = Util.toAbsolutePath(url, null);
								urlCache.put(url, file);
							}
							System.out.println(file + " ("
									+ new Date(urlEntry.getValue()) + ")");
						}
					}
					if (map.isEmpty()) {
						System.err.println("-- No conflict class found");
					}
				} else if (action == 13) {
					Map<String, Map<URL, Long>> map = finder
							.findConflictClasses(name, false);
					if (map.isEmpty()) {
						System.err.println("-- No conflict class found");
					} else {
						Map<URL, Map<String, Long>> setList = new HashMap<URL, Map<String, Long>>();
						for (Map.Entry<String, Map<URL, Long>> entry : map
								.entrySet()) {
							String className = entry.getKey();
							for (Map.Entry<URL, Long> urlEntry : entry
									.getValue().entrySet()) {
								URL url = urlEntry.getKey();
								Map<String, Long> classes = setList.get(url);
								if (classes == null) {
									classes = new LinkedHashMap<String, Long>();
									setList.put(url, classes);
								}
								classes.put(className, urlEntry.getValue());
							}
						}
						for (Map.Entry<URL, Map<String, Long>> entry : setList
								.entrySet()) {
							System.out.println("["
									+ Util.toAbsolutePath(entry.getKey(), null)
									+ "]");
							for (Map.Entry<String, Long> lineEntry : entry
									.getValue().entrySet()) {
								System.out.println(lineEntry.getKey() + " ("
										+ new Date(lineEntry.getValue()) + ")");
							}
						}
					}
				}
			}
		}
	}

	private static void usage() {
		PrintStream out = System.err;
		out.println("Usage: classfinder [SCOPE] [OPTION] NAME...");
		out.println("Find class name from classpath.");
		out.println();
		out.println("SCOPE:");
		out.println("  -pid <PID>");
		out.println("\t\tLookup class from the specified local process. (NOTE: Classpath longer than 1024 chars, may cause missing path item.)");
		out.println("  -cmd <String in CMD>");
		out.println("\t\tLookup class from the local process by string in command. (NOTE: Classpath longer than 1024 chars, may cause missing path item.)");
		out.println("  -classpath <PATH LIST> [-jre <JAVA HOME>]");
		out.println("\t\tLookup class from the provided class path");
		out.println("  (EMPTY)");
		out.println("\t\tFind class from the class path loaded from std in. It accept path seperator or new line seperator");
		out.println();
		out.println("OPTION:");
		out.println("  (EMPTY)");
		out.println("\t\tList class names and group by name");
		out.println("  -group ");
		out.println("\t\tGroup class by its source");
		out.println("  -package ");
		out.println("\t\tList class names by package name, which is specified in NAME");
		out.println("  -super ");
		out.println("\t\tList all super class or interface names");
		out.println("  -sub ");
		out.println("\t\tList all children class or interface names");
		out.println("  -duplicate ");
		out.println("\t\tList all dupliated class or interface names");
		out.println("  -duplicateset ");
		out.println("\t\tList set of all dupliated class or interface names per source");
		out.println("  -conflict ");
		out.println("\t\tList all conflict class or interface names");
		out.println("  -conflictset ");
		out.println("\t\tList set of all conflict class or interface names per source");
		out.println("  -ref ");
		out.println("\t\tList all incoming referring class or interface names");
		out.println("  -mref ");
		out.println("\t\tList all incoming referring class or interface names for the full method name");
		out.println("  -depend ");
		out.println("\t\tList all outgoing depending class or interface names");
		out.println("  -strings ");
		out.println("\t\tList class or interface contains the string text");
		out.println("  -cat ");
		out.println("\t\tPrint text file with name matching the string text");
		out.println("  -verbose ");
		out.println("\t\tList class path will be searched");
		out.println("  -current ");
		out.println("\t\tAdd current java home into classpath if no explict java home specified");
		out.println();
		out.println("NAME:");
		out.println("  java.lang.Object\tThe full class name");
		out.println("  'Object*'\t\tThe class name with wildcards, but without package name. This is only support for class name finding.");
		out.println("  'java.util.*List'\tThe full class name with wildcards(like '*', or '?'). This is only support for class name finding.");
		out.println("  'java.util.Collection.toArray'\tThe full method name. This is only support for method name based finding.");
	}

}
