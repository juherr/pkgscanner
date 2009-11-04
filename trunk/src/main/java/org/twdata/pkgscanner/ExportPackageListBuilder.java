package org.twdata.pkgscanner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Accepts ExportPackage objects and creates a sorted list with duplicates removed.
 * <p/>
 * Explicit version numbers are always preferred over "unknown".
 * When duplicate jars are found with different versions associated, a warning will be logged.
 *
 * @since 0.7.11
 */
public class ExportPackageListBuilder {
    private final static Log log = LogFactory.getLog(ExportPackageListBuilder.class);

    private Map<String, ExportPackage> packageMap = new HashMap<String, ExportPackage>();

    public void add(final ExportPackage exportPackage) {
        // Get the current ExportPackage for this package name
        String packageName = exportPackage.getPackageName();
        ExportPackage currentExportPackage = packageMap.get(packageName);
        if (currentExportPackage == null) {
            // Too Easy - this is the first copy
            packageMap.put(packageName, exportPackage);
        } else {
            // Duplicate places were found with the same package.
            // Note that this may be legitimate eg if two jars contain different different classes from the same package.
            // What is worrisome is if the different jars name different version numbers.
            // Check for conflicting versions
            if (exportPackage.getVersion() == null) {
                // The new package location has no version number, so we don't update.
                if (currentExportPackage.getVersion() != null) {
                    // The first jar has a version, and the second doesn't - log a warning.
                    logDuplicateWarning(exportPackage, currentExportPackage);
                }
            } else {
                if (currentExportPackage.getVersion() == null) {
                    // The first jar has no version, and the second one does, so we prefer the second.
                    packageMap.put(packageName, exportPackage);
                    logDuplicateWarning(exportPackage, currentExportPackage);
                } else {
                    // Check if the jars export different versions.
                    if (!currentExportPackage.getVersion().equals(exportPackage.getVersion())) {
                        // The jars both have versions, and they are not equal
                        logDuplicateWarning(exportPackage, currentExportPackage);
                    }
                    // For backward compatibility with v0.7.10, we will prefer the version of the last to be discovered.
                    // TODO: This is still non-deterministic. Should we try to export the lowest version?
                    packageMap.put(packageName, exportPackage);
                }
            }

        }
    }

    private void logDuplicateWarning(final ExportPackage exportPackage1, final ExportPackage exportPackage2) {
        log.warn("Package Scanner found duplicates for package '" + exportPackage1.getPackageName() + "' with different versions. Files: " +
                exportPackage1.getLocation().getName() + " and " + exportPackage2.getLocation().getName());
    }

    /**
     * Returns the list of packages, ordered by package name.
     *
     * @return the list of packages, ordered by package name.
     */
    public List<ExportPackage> getPackageList() {
        List<ExportPackage> packageList = new ArrayList<ExportPackage>(packageMap.values());
        Collections.sort(packageList);
        return packageList;
    }
}
