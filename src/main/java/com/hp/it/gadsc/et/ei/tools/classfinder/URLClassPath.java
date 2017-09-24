package com.hp.it.gadsc.et.ei.tools.classfinder;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

public class URLClassPath {
    final Stack<URL> urls = new Stack<>();
    ArrayList<Loader> loaders = new ArrayList<>();
    HashMap<String, Loader> lmap = new HashMap<>();
    private ArrayList<URL> path = new ArrayList<>();

    public URLClassPath(URL[] urls) {
        for (int i = 0; i < urls.length; i++) {
            path.add(urls[i]);
        }
        push(urls);
    }

    public URL[] getURLs() {
        return path.toArray(new URL[path.size()]);
    }

    public Resource getResource(String name) {
        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            Resource res = loader.getResource(name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public Enumeration<Resource> getResources(final String name) {
        return new Enumeration<Resource>() {
            private int index = 0;
            private Resource res = null;

            private boolean next() {
                if (res != null) {
                    return true;
                } else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        res = loader.getResource(name);
                        if (res != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            public boolean hasMoreElements() {
                return next();
            }

            public Resource nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                Resource r = res;
                res = null;
                return r;
            }
        };
    }

    private synchronized Loader getLoader(int index) {
        while (loaders.size() < index + 1) {
            URL url;
            synchronized (urls) {
                if (urls.empty()) {
                    return null;
                } else {
                    url = urls.pop();
                }
            }
            String urlNoFragString = url.toString();
            if (lmap.containsKey(urlNoFragString)) {
                continue;
            }
            Loader loader;
            try {
                loader = getLoader(url);
                URL[] urls = loader.getClassPath();
                if (urls != null) {
                    push(urls);
                }
            } catch (IOException e) {
                continue;
            }
            loaders.add(loader);
            lmap.put(urlNoFragString, loader);
        }
        return loaders.get(index);
    }

    public Loader getLoader(final URL url) throws IOException {
        String protocol = url.getProtocol();
        String file = url.getFile();
        if (file != null && file.endsWith("/")) {
            if ("file".equals(protocol)) {
                return new FileLoader(url);
            } else if ("jar".equals(protocol) && file.endsWith("!/")) {
                URL nestedUrl = new URL(file.substring(0, file.length() - 2));
                return new JarLoader(nestedUrl);
            } else {
                return new Loader(url);
            }
        } else {
            return new JarLoader(url);
        }
    }

    private void push(URL[] us) {
        for (int i = us.length - 1; i >= 0; --i) {
            urls.push(us[i]);
        }
    }

    interface Resource {

        String getName();

        URL getURL();

        URL getCodeSourceURL();

    }

    static class Loader implements Closeable {
        final URL base;
        private JarFile jarfile;

        Loader(URL url) {
            base = url;
        }

        Resource getResource(final String name) {
            final URL url;
            try {
                url = new URL(base, name);
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("name");
            }
            final URLConnection uc;
            try {
                uc = url.openConnection();
                if (uc instanceof JarURLConnection) {
                    JarURLConnection juc = (JarURLConnection) uc;
                    jarfile = juc.getJarFile();
                }
            } catch (Exception e) {
                return null;
            }
            return new Resource() {
                public String getName() {
                    return name;
                }

                public URL getURL() {
                    return url;
                }

                public URL getCodeSourceURL() {
                    return base;
                }

            };
        }

        @Override
        public void close() throws IOException {
            if (jarfile != null) {
                jarfile.close();
            }
        }

        URL[] getClassPath() throws IOException {
            return null;
        }
    }

    static class JarLoader extends Loader {
        private final URL csu;
        private JarFile jar;
        private boolean closed = false;

        JarLoader(URL url)
                throws IOException {
            super(new URL("jar", "", -1, url + "!/"));
            csu = url;
            ensureOpen();
        }

        private static URL[] parseClassPath(URL base, String value)
                throws MalformedURLException {
            StringTokenizer st = new StringTokenizer(value);
            URL[] urls = new URL[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                String path = st.nextToken();
                urls[i] = new URL(base, path);
                i++;
            }
            return urls;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                ensureOpen();
                jar.close();
            }
        }

        private void ensureOpen() throws IOException {
            if (jar == null) {
                jar = getJarFile(csu);
            }
        }

        private JarFile getJarFile(URL url) throws IOException {
            String path = url.getPath();
            File f = new File(path);
            if (!f.exists()) {
                throw new FileNotFoundException(path);
            }
            return new JarFile(new File(path), true, ZipFile.OPEN_READ);
        }

        @Override
        Resource getResource(final String name) {
            try {
                ensureOpen();
            } catch (IOException e) {
                throw new InternalError(e);
            }
            final JarEntry entry = jar.getJarEntry(name);
            if (entry != null) {
                final URL url;
                try {
                    url = new URL(base, name);
                } catch (Exception e) {
                    return null;
                }

                return new Resource() {
                    public String getName() {
                        return name;
                    }

                    public URL getURL() {
                        return url;
                    }

                    public URL getCodeSourceURL() {
                        return csu;
                    }

                };
            }

            return null;
        }

        @Override
        URL[] getClassPath() throws IOException {
            ensureOpen();

            Manifest man = jar.getManifest();
            if (man != null) {
                Attributes attr = man.getMainAttributes();
                if (attr != null) {
                    String value = attr.getValue(Name.CLASS_PATH);
                    if (value != null) {
                        return parseClassPath(csu, value);
                    }
                }
            }
            return null;
        }
    }

    private static class FileLoader extends Loader {
        private File dir;

        FileLoader(URL url) throws IOException {
            super(url);
            String path = url.getFile().replace('/', File.separatorChar);
            dir = (new File(path)).getCanonicalFile();
        }

        @Override
        Resource getResource(final String name) {
            final URL url;
            try {
                URL normalizedBase = new URL(base, ".");
                url = new URL(base, name);

                if (!url.getFile().startsWith(normalizedBase.getFile())) {
                    return null;
                }

                final File file;
                if (name.contains("..")) {
                    file = (new File(dir, name.replace('/', File.separatorChar)))
                            .getCanonicalFile();
                    if (!((file.getPath()).startsWith(dir.getPath()))) {
                        return null;
                    }
                } else {
                    file = new File(dir, name.replace('/', File.separatorChar));
                }

                if (file.exists()) {
                    return new Resource() {
                        public String getName() {
                            return name;
                        }

                        public URL getURL() {
                            return url;
                        }

                        public URL getCodeSourceURL() {
                            return base;
                        }

                    };
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }
    }

}
