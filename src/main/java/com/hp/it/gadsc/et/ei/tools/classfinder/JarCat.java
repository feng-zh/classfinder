package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarCat implements Closeable {

	public static interface NameMatcher {

		public boolean match(ZipEntry entry);

		/*
		 * null: not supported
		 */
		public List<ZipEntry> refine(List<ZipEntry> entries);

	}

	public static interface MatchOutput {

		public void setCount(int count);

		public void writeResult(int index, ZipEntry entry, InputStream data)
				throws IOException;

	}

	private final ZipInputStream input;

	public JarCat(InputStream in) {
		if (in == null) {
			throw new NullPointerException("null in");
		}
		input = new ZipInputStream(in);
	}

	public boolean match(NameMatcher matcher, MatchOutput output)
			throws IOException {
		if (matcher == null) {
			throw new NullPointerException("no matcher provided");
		}
		if (output == null) {
			throw new NullPointerException("no match out provided");
		}
		Map<ZipEntry, byte[]> buffer = new LinkedHashMap<ZipEntry, byte[]>();
		ZipEntry zipEntry;
		while ((zipEntry = input.getNextEntry()) != null) {
			if (zipEntry.isDirectory())
				continue;
			if (matcher.match(zipEntry)) {
				buffer.put(zipEntry, readEntry(zipEntry, input));
			}
		}
		if (buffer.isEmpty()) {
			// not found
			return false;
		}
		Collection<ZipEntry> refined = buffer.keySet();
		if (buffer.size() > 1) {
			ArrayList<ZipEntry> entries = new ArrayList<ZipEntry>(
					buffer.keySet());
			refined = matcher.refine(entries);
			if (refined == null) {
				throw new IOException("ERROR: Find " + entries.size()
						+ " matched, but cannot refine result.");
			}
			if (refined.isEmpty()) {
				// not found after refined
				return false;
			}
			// free memory
			for (ZipEntry entry : entries) {
				if (!refined.contains(entry)) {
					buffer.remove(entry);
				}
			}
		}
		output.setCount(buffer.size());
		int i = 0;
		for (ZipEntry entry : refined) {
			output.writeResult(i++, entry,
					new ByteArrayInputStream(buffer.get(entry)));
		}
		return true;
	}

	private static byte[] readEntry(ZipEntry entry, ZipInputStream inputStream)
			throws IOException {
		byte[] buf = new byte[1024];
		int len = 0;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((len = inputStream.read(buf)) != -1) {
			output.write(buf, 0, len);
		}
		output.close();
		return output.toByteArray();
	}

	public static boolean cat(InputStream jarFile, String finding,
			InputStream input, PrintStream output) throws IOException {
		JarCat jarCat = null;
		try {
			jarCat = new JarCat(jarFile);
			if (!jarCat.match(new DefaultNameMatcher(finding,
					input == null ? null : new Scanner(input), output),
					new DefaultMatchOutput(output, "::"))) {
				return false;
			} else {
				return true;
			}
		} finally {
			if (jarCat != null) {
				try {
					jarCat.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	public void close() throws IOException {
		if (input != null) {
			input.close();
		}
	}

	public static void main(String[] args) {
		args = new String[] { "C:\\Program Files\\Java\\jdk1.5.0_16\\src.zip",
				"date" };
		String jarfile = args[0];
		String finding = args[1];
		int exitCode = 0;
		try {
			if (!cat(new FileInputStream(jarfile), finding, System.in,
					System.out)) {
				System.err.println("Not Founded.");
				exitCode = 1;
			}
		} catch (FileNotFoundException e) {
			System.err.println("ERROR: File not found - " + e.getMessage());
			exitCode = 2;
		} catch (IOException e) {
			System.err.println("ERROR: IO error - " + e.getMessage());
			e.printStackTrace();
			exitCode = 2;
		}
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

}

class DefaultNameMatcher implements JarCat.NameMatcher {

	private String findText;
	private Scanner scanner;
	private PrintStream prompt;

	private static enum NameMatchLevel {
		FullMatch, NoExtMatch {
			@Override
			protected String transform(String name) {
				int extIndex = name.lastIndexOf('.');
				String filename = name;
				if (extIndex > -1) {
					filename = name.substring(0, extIndex);
				}
				return filename;
			}
		},
		PackageMatch {
			@Override
			protected String transform(String name) {
				name = NoExtMatch.transform(name).replace('/', '.');
				return name;
			}
		},
		SimpleNameWithExtMatch {
			@Override
			protected String transform(String name) {
				int simplenameIndex = name.lastIndexOf('/');
				if (simplenameIndex > -1) {
					name = name.substring(simplenameIndex + 1);
				}
				return name;
			}
		},
		SimpleNameMatch(SimpleNameWithExtMatch, NoExtMatch), FullMatchIgnoreCase {
			@Override
			public boolean match(String name, String checkText) {
				return name.equalsIgnoreCase(checkText);
			}
		},
		NoExtMatchIgnoreCase(NoExtMatch, FullMatchIgnoreCase), PackageMatchIgnoreCase(
				PackageMatch, FullMatchIgnoreCase), SimpleNameMatchIgnoreCase(
				SimpleNameMatch, FullMatchIgnoreCase);

		private NameMatchLevel[] chains;

		private NameMatchLevel() {
		}

		private NameMatchLevel(NameMatchLevel... chains) {
			this.chains = chains;
		}

		protected String transform(String name) {
			return name;
		}

		public boolean match(String name, String checkText) {
			name = transform(name);
			if (chains == null || chains.length == 0) {
				return name.equals(checkText);
			} else {
				for (int i = 0; i < chains.length - 1; i++) {
					name = chains[i].transform(name);
				}
				return chains[chains.length - 1].match(name, checkText);
			}
		}

	}

	public DefaultNameMatcher(String findText, Scanner scanner,
			PrintStream prompt) {
		this.findText = findText;
		this.scanner = scanner;
		this.prompt = prompt;
	}

	public boolean match(ZipEntry entry) {
		return match(entry, findText);
	}

	private boolean match(ZipEntry entry, String text) {
		NameMatchLevel level = matchWithLevel(entry, text);
		return level != null;
	}

	private NameMatchLevel matchWithLevel(ZipEntry entry, String text) {
		for (NameMatchLevel level : NameMatchLevel.values()) {
			if (level.match(entry.getName(), text)) {
				return level;
			}
		}
		return null;
	}

	public List<ZipEntry> refine(List<ZipEntry> entries) {
		if (scanner == null) {
			return null;
		}
		String text = findText;
		while (true) {
			// check level
			NameMatchLevel selectedLevel = null;
			ZipEntry selectedEntry = null;
			List<ZipEntry> filterList = new ArrayList<ZipEntry>();
			for (ZipEntry entry : entries) {
				NameMatchLevel level = matchWithLevel(entry, text);
				if (level != null) {
					filterList.add(entry);
					if (selectedLevel == null
							|| level.compareTo(selectedLevel) < 0) {
						selectedEntry = entry;
						selectedLevel = level;
					}
				}
			}
			// check content type
			if (selectedLevel != null
					&& selectedLevel.compareTo(NameMatchLevel.PackageMatch) <= 0) {
				// treat it is selected
				return Collections.singletonList(selectedEntry);
			}
			if (filterList.size() == 1) {
				// only one
				return filterList;
			}
			if (filterList.size() == 0) {
				// no one;
				return Collections.emptyList();
			}
			entries = filterList;
			if (prompt != null) {
				int i = 1;
				for (ZipEntry entry : entries) {
					prompt.println((i++) + ") " + entry.getName());
				}
				prompt.print("Please select one or input refined name (Enter to see ALL): ");
				prompt.flush();
			}
			if (scanner.hasNextLine()) {
				text = scanner.nextLine();
				try {
					int selected = Integer.parseInt(text);
					if (selected >= 1 && selected <= entries.size()) {
						return Collections.singletonList(entries
								.get(selected - 1));
					}
				} catch (NumberFormatException ignored) {
				}
				if (text.trim().length() == 0) {
					// return all
					return entries;
				}
			} else {
				// no selection
				return Collections.emptyList();
			}
		}
	}

}

class DefaultMatchOutput implements JarCat.MatchOutput {

	private int count;
	private PrintStream output;
	private String prompt;
	private static Map<String, String> preDefinedTypes = new HashMap<String, String>();
	static boolean forceCat = Boolean.getBoolean("forceCat");

	static {
		preDefinedTypes.put("xml", "text/xml");
		preDefinedTypes.put("properties", "text/properties");
		preDefinedTypes.put("json", "text/json");
		preDefinedTypes.put("js", "text/javascript");
		preDefinedTypes.put("css", "text/stylesheet");
	}

	public DefaultMatchOutput(PrintStream output, String prompt) {
		this.output = output;
		this.prompt = prompt;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public void writeResult(int index, ZipEntry entry, InputStream data)
			throws IOException {
		if (prompt != null) {
			output.println(prompt + "[" + (index + 1) + "] " + entry.getName());
		} else {
			if (index != 0) {
				output.println();
			}
		}
		String contentType = guessContentTypeFromName(entry.getName());
		if (contentType == null || !isText(contentType)) {
			contentType = URLConnection.guessContentTypeFromStream(data);
		}
		if (!isText(contentType) && !forceCat) {
			output.println(prompt
					+ "Warn: \""
					+ entry.getName()
					+ "\" is not a text file"
					+ (contentType == null ? "" : ", may be content type of "
							+ contentType));
			return;
		}
		byte[] buf = new byte[512];
		int len = 0;
		while ((len = data.read(buf)) != -1) {
			output.write(buf, 0, len);
		}
		if (prompt != null) {
			if (index + 1 == count) {
				output.println();
				output.println(prompt + " -- Total Found " + count);
			}
		}
	}

	private String guessContentTypeFromName(String name) {
		String type = URLConnection.guessContentTypeFromName(name);
		if (type == null) {
			// check pre-defined ext
			int extIndex = name.lastIndexOf('.');
			if (extIndex > -1) {
				String extname = name.substring(extIndex + 1);
				return preDefinedTypes.get(extname.toLowerCase());
			}
		}
		return type;
	}

	private boolean isText(String contentType) {
		if (contentType == null) {
			return false;
		}
		if (contentType.startsWith("text/")) {
			return true;
		}
		if (contentType.endsWith("/xml")) {
			return true;
		}
		return false;
	}
}