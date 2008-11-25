package org.twdata.pkgscanner;

/**
 * Represents an export consisting of a package name and version
 */
public class ExportPackage implements Comparable<ExportPackage> {
    private String packageName;
    private String version;

    public ExportPackage(String packageName, String version) {
        this.version = version;
        if (packageName != null && packageName.startsWith(".")) {
            packageName = packageName.substring(1);
        }
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersion() {
        return version;
    }

    public int compareTo(ExportPackage exportPackage) {
        return packageName.compareTo(exportPackage.getPackageName());
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExportPackage that = (ExportPackage) o;

        if (!packageName.equals(that.packageName)) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = packageName.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }
}
