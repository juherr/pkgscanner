Small Java library that scans the classloader and tries to determine what packages are available as well as what version they may be.  The intended use is to generate the Export-Package OSGi header value to automatically expose the current classpath to an embedded OSGi container.

## Downloads ##
You can download the jars, source, and javadocs from the Maven 2 repository:

> http://twdata-m2-repository.googlecode.com/svn/org/twdata/pkgscanner/package-scanner/

## Example Usage ##
```
         Collection<ExportPackage> exports = new PackageScanner()
                .select(
                    jars(
                            include(
                                    "*.jar",
                                    "bar-*.jar"),
                            exclude(
                                    "*dira*.jar")),
                    packages(
                            include(
                                    "org.*",
                                    "com.*",
                                    "javax.*",
                                    "org.twdata.pkgscanner.*"),

                            exclude(
                                    "com.intellij.*")))
                .withMappings(
                        mapPackage("org.twdata.pkgscanner.foo").toVersion("2.0.4"))
                .scan();
```

## Maven Repository ##
Add this to your pom.xml:
```
    <repositories>
        <repository>
            <id>twdata-repository</id>
            <name>twdata Repository for Maven</name>
            <url>http://twdata-m2-repository.googlecode.com/svn/</url>
        </repository>
    </repositories>
```