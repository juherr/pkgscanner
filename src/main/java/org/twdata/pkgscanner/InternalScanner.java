package org.twdata.pkgscanner;

import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.IOException;
import java.io.File;

/**
 * Does the actual work of scanning the classloader
 */
class InternalScanner {
    private ClassLoader classloader;
    private PackageScanner.VersionMapping[] versionMappings;
    private OsgiVersionConverter versionConverter = new DefaultOsgiVersionConverter();

    static interface Test {
        boolean matchesPackage(String pkg);

        boolean matchesJar(String name);
    }

    InternalScanner(ClassLoader cl, PackageScanner.VersionMapping[] versionMappings) {
        this.classloader = cl;
        this.versionMappings = versionMappings;
    }

    void setOsgiVersionConverter(OsgiVersionConverter converter) {
        this.versionConverter = converter;
    }

    Collection<ExportPackage> findInPackages(Test test, String... roots) {
        // weans out duplicates by choosing the winner as the last one to be discovered
        Map<String, ExportPackage> map = new HashMap<String,ExportPackage>();
        for (String pkg : roots) {
            for (ExportPackage export : findInPackage(test, pkg)) {
                map.put(export.getPackageName(), export);
            }
        }

        // Let's be nice and sort the results by package
        return new TreeSet(map.values());
    }

    Collection<ExportPackage> findInUrls(Test test, URL... urls) {
        // weans out duplicates by choosing the winner as the last one to be discovered
        Map<String, ExportPackage> map = new HashMap<String,ExportPackage>();
        Vector<URL> list = new Vector<URL>(Arrays.asList(urls));
        for (ExportPackage export : findInPackageWithUrls(test, "", list.elements())) {
            map.put(export.getPackageName(), export);
        }

        // Let's be nice and sort the results by package
        return new TreeSet(map.values());
    }

    /**
     * Scans for classes starting at the package provided and descending into subpackages.
     * Each class is offered up to the Test as it is discovered, and if the Test returns
     * true the class is retained.
     *
     * @param test        an instance of {@link Test} that will be used to filter classes
     * @param packageName the name of the package from which to start scanning for
     *                    classes, e.g. {@code net.sourceforge.stripes}
     */
    List<ExportPackage> findInPackage(Test test, String packageName) {
        List<ExportPackage> localExports = new ArrayList<ExportPackage>();

        packageName = packageName.replace('.', '/');
        Enumeration<URL> urls;

        try {
            urls = classloader.getResources(packageName);
        }
        catch (IOException ioe) {
            System.err.println("Could not read package: " + packageName);
            return localExports;
        }

        return findInPackageWithUrls(test, packageName, urls);
    }

    List<ExportPackage> findInPackageWithUrls(Test test, String packageName, Enumeration<URL> urls)
    {
        List<ExportPackage> localExports = new ArrayList<ExportPackage>();
        while (urls.hasMoreElements()) {
            try {
	        URL url = urls.nextElement();
                String urlPath = url.getPath();

                // it's in a JAR, grab the path to the jar
                if (urlPath.lastIndexOf('!') > 0) {
                    urlPath = urlPath.substring(0, urlPath.lastIndexOf('!'));
                } else if (!urlPath.startsWith("file:")) {
                    urlPath = "file:"+urlPath;
		}    

                //System.out.println("Scanning for classes in [" + urlPath + "] matching criteria: " + test);
                File file = new File(new URL(urlPath).toURI());
                if (file.isDirectory()) {
                    localExports.addAll(loadImplementationsInDirectory(test, packageName, file));
                } else {
                    if (test.matchesJar(file.getName())) {
                        localExports.addAll(loadImplementationsInJar(test, packageName, file));
                    }
                }
            }
            catch (Exception ioe) {
                System.err.println("could not read entries: " + ioe);
            }
        }
        return localExports;
    }


    /**
     * Finds matches in a physical directory on a filesystem.  Examines all
     * files within a directory - if the File object is not a directory, and ends with <i>.class</i>
     * the file is loaded and tested to see if it is acceptable according to the Test.  Operates
     * recursively to find classes within a folder structure matching the package structure.
     *
     * @param test     a Test used to filter the classes that are discovered
     * @param parent   the package name up to this directory in the package hierarchy.  E.g. if
     *                 /classes is in the classpath and we wish to examine files in /classes/org/apache then
     *                 the values of <i>parent</i> would be <i>org/apache</i>
     * @param location a File object representing a directory
     */
    List<ExportPackage> loadImplementationsInDirectory(Test test, String parent, File location) {
        File[] files = location.listFiles();
        StringBuilder builder = null;
        List<ExportPackage> localExports = new ArrayList<ExportPackage>();
        Set scanned = new HashSet<String>();

        for (File file : files) {
            builder = new StringBuilder(100);
            builder.append(parent).append("/").append(file.getName());
            String packageOrClass = (parent == null ? file.getName() : builder.toString());


            if (file.isDirectory()) {
                localExports.addAll(loadImplementationsInDirectory(test, packageOrClass, file));

            // If the parent is empty, then assume the directory's jars should be searched
            } else if ("".equals(parent) && file.getName().endsWith(".jar") && test.matchesJar(file.getName())) {
                localExports.addAll(loadImplementationsInJar(test, "", file));
            } else {
                String pkg = packageOrClass;
                int lastSlash = pkg.lastIndexOf('/');
                if (lastSlash > 0) {
                    pkg = pkg.substring(0, lastSlash);
                }
                pkg = pkg.replace("/", ".");
                if (!scanned.contains(pkg)) {
                    if (test.matchesPackage(pkg)) {
                        localExports.add(new ExportPackage(pkg, determinePackageVersion(null, pkg)));
                    }
                    scanned.add(pkg);
                }
            }
        }
        return localExports;
    }

    /**
     * Finds matching classes within a jar files that contains a folder structure
     * matching the package structure.  If the File is not a JarFile or does not exist a warning
     * will be logged, but no error will be raised.
     *
     * @param test    a Test used to filter the classes that are discovered
     * @param parent  the parent package under which classes must be in order to be considered
     * @param file the jar file to be examined for classes
     */
    List<ExportPackage> loadImplementationsInJar(Test test, String parent, File file) {

        List<ExportPackage> localExports = new ArrayList<ExportPackage>();
        try {
            JarFile jarFile = new JarFile(file);
            Set scanned = new HashSet<String>();

            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); ) {
                JarEntry entry = e.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(parent)) {
                    String pkg = name;
                    int pos = pkg.lastIndexOf('/');
                    if (pos > -1) {
                        pkg = pkg.substring(0, pos);
                    }
                    pkg = pkg.replace('/', '.');
                    if (!scanned.contains(pkg)) {
                        if (test.matchesPackage(pkg)) {
                            localExports.add(new ExportPackage(pkg, determinePackageVersion(file, pkg)));
                        }
                        scanned.add(pkg);
                    }
                 }
            }
        }
        catch (IOException ioe) {
            System.err.println("Could not search jar file '" + file + "' for classes matching criteria: " +
                    test + " due to an IOException" + ioe);
        }
        return localExports;
    }

    String determinePackageVersion(File jar, String pkg) {
        // Look for an explicit mapping
        String version = null;
        for (PackageScanner.VersionMapping mapping : versionMappings) {
            if (mapping.matches(pkg)) {
                version = mapping.getVersion();
            }
        }
        if (version == null && jar != null) {
            // TODO: Look for osgi headers

            if (version == null) {
                // Try to guess the version from the jar name
                String name = jar.getName();
                return extractVersion(name);
            }
        }

        return version;
    }

    /**
     * Tries to guess the version by assuming it starts as the first number after a '-' or '_' sign, then converts
     * the version into an OSGi-compatible one.
     */
    String extractVersion(String filename)
    {
        StringBuilder version = null;
        boolean lastWasSeparator = false;
        for (int x=0; x<filename.length(); x++)
        {
            char c = filename.charAt(x);
            if (c == '-' || c == '_')
                lastWasSeparator = true;
            else if (Character.isDigit(c) && lastWasSeparator && version == null)
                version = new StringBuilder();

            if (version != null)
                version.append(c);
        }

        if (version != null)
        {
            if (".jar".equals(version.substring(version.length() - 4)))
                version.delete(version.length() - 4, version.length());
            return versionConverter.getVersion(version.toString());
        } else
            return null;
    }
}
