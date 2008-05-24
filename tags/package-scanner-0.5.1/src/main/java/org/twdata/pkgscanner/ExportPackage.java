package org.twdata.pkgscanner;

/**
 * Created by IntelliJ IDEA.
 * User: mrdon
 * Date: 24/05/2008
 * Time: 10:45:17 AM
 * To change this template use File | Settings | File Templates.
 */
public class ExportPackage implements Comparable<ExportPackage> {
    private String packageName;
    private String version;

    public ExportPackage(String packageName, String version) {
        this.version = version;
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
}
