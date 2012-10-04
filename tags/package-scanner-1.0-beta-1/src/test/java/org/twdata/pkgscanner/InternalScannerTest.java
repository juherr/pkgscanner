package org.twdata.pkgscanner;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;
import org.twdata.pkgscanner.pattern.CompiledPattern;
import org.twdata.pkgscanner.pattern.PatternFactory;
import org.twdata.pkgscanner.pattern.SimpleWildcardPatternFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

public class InternalScannerTest extends TestCase {
    private File tmpDir;
    private boolean debug = true;
    private InternalScanner scanner;

    @Override
    public void setUp() throws IOException {
        tmpDir = new File("target", "footest");
        tmpDir.mkdir();

        scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {}, debug);
        scanner.setOsgiVersionConverter(new OsgiVersionConverter()
        {
            public String getVersion(String version)
            {
                return version;
            }
        });
    }

    @Override
    public void tearDown() throws Exception {
        tmpDir.delete();
    }
    public void testDeterminePackageVersion() throws Exception {
        assertEquals("2.0", scanner.determinePackageVersion(new File(tmpDir, "sdfoo-2.0.jar"), "testpackage"));
        assertEquals("2.0_something", scanner.determinePackageVersion(new File(tmpDir, "fdsfoo-2.0_something.jar"), "testpackage"));
        assertEquals("2.0-beta", scanner.determinePackageVersion(new File(tmpDir, "fwwoo-2.0-beta.jar"), "testpackage"));
        assertEquals("2", scanner.determinePackageVersion(new File(tmpDir, "fofdso-2.jar"), "testpackage"));
        assertEquals("2", scanner.determinePackageVersion(new File(tmpDir, "foaao4-2.jar"), "testpackage"));
        assertEquals("1.2.8", scanner.determinePackageVersion(new File(tmpDir, "log4j-1.2.8.jar"), "testpackage"));
        assertEquals("2.0+xmlrpc61", scanner.determinePackageVersion(new File(tmpDir, "xmlrpc-2.0+xmlrpc61.jar"), "testpackage"));
        assertEquals(null, scanner.determinePackageVersion(new File(tmpDir, "foo-alpha.jar"), "testpackage"));
    }

    public void testDeterminePackageVersionFromManifest() throws IOException
    {
        assertEquals("1", scanner.determinePackageVersion(createJarWithManifest(singletonMap("Bundle-Version", "1")), "testpackage"));
        assertEquals("1", scanner.determinePackageVersion(createJarWithManifest(singletonMap("Specification-Version", "1")), "testpackage"));
        assertEquals("1", scanner.determinePackageVersion(createJarWithManifest(singletonMap("Implementation-Version", "1")), "testpackage"));
        assertNotNull(scanner.determinePackageVersion(createJarWithManifest(
                Collections.<String, String>emptyMap()), "testpackage"));
    }

    public void testDeterminePackageVersionFromMaven() throws Exception {
        assertEquals("1", scanner.determinePackageVersion(
                createJarWithManifestAndEntries(Collections.<String, String>emptyMap(),
                        singletonMap(
                        "/META-INF/maven/groupId/artifactId/pom.properties",
                        "foo=bar\nversion=1")), "testpackage"));
    }

    private File createJarWithManifest(Map<String, String> manifestEntries) throws
            IOException
    {
        return createJarWithManifestAndEntries(manifestEntries,
                Collections.<String, String>emptyMap());
    }
    private File createJarWithManifestAndEntries(Map<String, String> manifestEntries, Map<String,String> entries) throws
            IOException
    {
        Manifest mf = new Manifest();
        mf.getMainAttributes().putValue("Manifest-Version", "1");
        for (Map.Entry<String,String> entry : manifestEntries.entrySet()) {
            mf.getMainAttributes().putValue(entry.getKey(), entry.getValue());
        }
        File file = File.createTempFile("pkgscanner-test-", ".jar", tmpDir);
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(file));
        zout.putNextEntry(new ZipEntry("META-INF/"));
        zout.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
        mf.write(zout);

        for (Map.Entry<String,String> entry : entries.entrySet())
        {
            zout.putNextEntry(new ZipEntry(entry.getKey()));
            IOUtils.write(entry.getValue(), zout);
        }
        zout.close();
        return file;
    }

    public void testDeterminePackageVersionWithExplicitVersion() throws Exception {
        PackageScanner.VersionMapping mapping = new PackageScanner.VersionMapping("testpackage").toVersion("34");
        mapping.setPatternFactory(new PatternFactory() {
            public CompiledPattern compile(final String pattern) {
                return new CompiledPattern() {
                    public String getOriginal() {
                        return pattern;
                    }

                    public boolean matches(String value) {
                        return value.equals(pattern);
                    }
                };
            }
        });
        InternalScanner scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {mapping}, debug);

        assertEquals("34.0.0", scanner.determinePackageVersion(new File(tmpDir, "fdas-2.0.jar"), "testpackage"));
    }

    public void testDeterminePackageVersionWithExplicitNonOsgiVersion() throws Exception {
        PackageScanner.VersionMapping mapping = new PackageScanner.VersionMapping("testpackage").toVersion("34-SNAPSHOT");
        mapping.setPatternFactory(new PatternFactory() {
            public CompiledPattern compile(final String pattern) {
                return new CompiledPattern() {
                    public String getOriginal() {
                        return pattern;
                    }

                    public boolean matches(String value) {
                        return value.equals(pattern);
                    }
                };
            }
        });
        InternalScanner scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {mapping}, debug);

        assertEquals("34.0.0.SNAPSHOT", scanner.determinePackageVersion(new File(tmpDir, "asdf-2.0.jar"), "testpackage"));
    }

    public void testLoadImplementationsInDirectory() throws Exception {
        File parent = new File(tmpDir, "parent");
        File child = new File(parent, "child");
        child.mkdirs();
        File baby = new File(child, "foo.class");
        baby.createNewFile();
        File baby2 = new File(child, "bfoo");
        baby2.createNewFile();

        InternalScanner scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.loadImplementationsInDirectory(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "parent", parent);
        assertNotNull(exports);
        assertEquals(1, exports.size());
        assertEquals("parent.child", exports.iterator().next().getPackageName());
    }

    public void testFindLoadImplementationsInDirectory() throws Exception {
        File parent = new File(tmpDir, "parent");
        File child = new File(parent, "child");
        child.mkdirs();
        File baby = new File(child, "foo.class");
        baby.createNewFile();
        File baby2 = new File(child, "bfoo");
        baby2.createNewFile();

        InternalScanner scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.loadImplementationsInDirectory(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "parent", parent);
        assertNotNull(exports);
        assertEquals(1, exports.size());
        assertEquals("parent.child", exports.iterator().next().getPackageName());
    }

    public void testFindInUrls() throws Exception {

        final PackageScanner.VersionMapping mapping = new PackageScanner.VersionMapping("pkg.in.dir", "1.1");
        mapping.setPatternFactory(new SimpleWildcardPatternFactory());
        InternalScanner scanner = new InternalScanner(getClass().getClassLoader(), new PackageScanner.VersionMapping[] {
                mapping
        }, debug);
        Collection<ExportPackage> exports = scanner.findInUrls(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, getClass().getClassLoader().getResource("scanbase/lib"), getClass().getClassLoader().getResource("scanbase/classes"));
        assertNotNull(exports);
        assertEquals(2, exports.size());

        assertTrue(exports.contains(new ExportPackage("pkg.in.jar", "1.0.0", new File("somewhere/dude-1.0.jar"))));
        assertTrue(exports.contains(new ExportPackage("pkg.in.dir", "1.1.0", new File("somewhere/swan.jar"))));
    }

    public void testFindInPackagesWithUrlsAndPlusInFilename() throws Exception {

        URLClassLoader cl = new URLClassLoader(new URL[] {getClass().getResource("/foo+bar.jar")});
        InternalScanner scanner = new InternalScanner(cl, new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.findInPackage(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "foo");
        assertNotNull(exports);
        assertEquals(1, exports.size());
        assertEquals("foo", exports.iterator().next().getPackageName());
    }

    public void testFindInPackagesWithUrlsAndMultiplePlusesInFilename() throws Exception {

        URLClassLoader cl = new URLClassLoader(new URL[] {getClass().getResource("/foo+bar+baz.jar")});
        InternalScanner scanner = new InternalScanner(cl, new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.findInPackage(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "foo");
        assertNotNull(exports);
        assertEquals(1, exports.size());
        assertEquals("foo", exports.iterator().next().getPackageName());
    }

    public void testFindInPackagesWithUrlsAndSpaceInFilename() throws Exception {

        URLClassLoader cl = new URLClassLoader(new URL[] {getClass().getResource("/bar baz.jar")});
        InternalScanner scanner = new InternalScanner(cl, new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.findInPackage(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "foo");
        assertNotNull(exports);
        assertEquals(1, exports.size());
        assertEquals("foo", exports.iterator().next().getPackageName());
    }

    public void testFindInPackagesWithHttpUrl() throws Exception {

        URL[] array = {new URL("jar:http://www.atlassian.com/foo.jar!/foo")};
        Enumeration<URL> urls = new Vector<URL>(Arrays.asList(array)).elements();
        URLClassLoader cl = new URLClassLoader(array);
        InternalScanner scanner = new InternalScanner(cl, new PackageScanner.VersionMapping[] {}, debug);
        Collection<ExportPackage> exports = scanner.findInPackageWithUrls(new InternalScanner.Test() {
            public boolean matchesPackage(String pkg) { return true; }
            public boolean matchesJar(String name) { return true; }
        }, "foo", urls);
        assertNotNull(exports);
        assertEquals(0, exports.size());
    }

    public void testExtractVersion() throws Exception
    {
        InternalScanner scanner = new InternalScanner(this.getClass().getClassLoader(), new PackageScanner.VersionMapping[0], false);
        assertEquals("1.6.1", scanner.extractVersion("wsdl4j-1.6.1.jar"));
        assertEquals("1.6.0", scanner.extractVersion("wsdl4j-1.6.jar"));
        assertEquals("1.0.0", scanner.extractVersion("wsdl4j-1.jar"));
        assertEquals("1.6.1.35", scanner.extractVersion("wsdl4j-1.6.1.3.5.jar"));
        assertEquals("1.2.0.3RC3", scanner.extractVersion("wsdl4j-1.2.3RC3.jar"));
        // Test with various qualifier separators
        assertEquals("1.2.3.RC4", scanner.extractVersion("wsdl4j-1.2.3-RC4.jar"));
        assertEquals("1.2.3.RC4", scanner.extractVersion("wsdl4j-1.2.3.RC4.jar"));
        assertEquals("1.2.3.RC4", scanner.extractVersion("wsdl4j-1.2.3_RC4.jar"));
        assertEquals("1.2.3.RC4", scanner.extractVersion("wsdl4j-1.2.3/RC4.jar"));
        assertEquals("1.2.0.3RC3", scanner.extractVersion("wsdl4j-1.2.3RC3.jar"));
        // Test with a number after the first separator (this used to be a bug)
        assertEquals("2.3.4", scanner.extractVersion("stuff-foo2-2.3.4.jar"));
        assertEquals(null, scanner.extractVersion("tomcat-i18n-es.jar"));
    }
}
