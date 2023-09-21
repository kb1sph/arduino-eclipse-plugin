package io.sloeber.core.common;

import static io.sloeber.core.api.Const.*;

import java.io.File;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

import io.sloeber.core.api.Common;
import io.sloeber.core.api.Defaults;

/**
 * Items on the Configuration level are linked to the ConfigurationScope
 * (=eclipse install base).
 *
 * @author jan
 *
 */
public class ConfigurationPreferences {

    private static final String EXAMPLE_FOLDER_NAME = "examples"; //$NON-NLS-1$
    private static final String DOWNLOADS_FOLDER = "downloads"; //$NON-NLS-1$
    private static final String PRE_PROCESSING_PLATFORM_TXT = "pre_processing_platform.txt"; //$NON-NLS-1$
    private static final String POST_PROCESSING_PLATFORM_TXT = "post_processing_platform.txt"; //$NON-NLS-1$
    private static final String PRE_PROCESSING_BOARDS_TXT = "pre_processing_boards.txt"; //$NON-NLS-1$
    private static final String POST_PROCESSING_BOARDS_TXT = "post_processing_boards.txt"; //$NON-NLS-1$

    private static final String KEY_UPDATE_JASONS = "Update jsons files"; //$NON-NLS-1$

    // preference nodes
    private static final String PACKAGES_FOLDER_NAME = "packages"; //$NON-NLS-1$

    public static void removeKey(String key) {
        IEclipsePreferences myScope = ConfigurationScope.INSTANCE.getNode(NODE_ARDUINO);
        myScope.remove(key);
    }

    public static String getString(String key, String defaultValue) {
        IEclipsePreferences myScope = ConfigurationScope.INSTANCE.getNode(NODE_ARDUINO);
        return myScope.get(key, defaultValue);
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        IEclipsePreferences myScope = ConfigurationScope.INSTANCE.getNode(NODE_ARDUINO);
        return myScope.getBoolean(key, defaultValue);
    }

    private static void setBoolean(String key, boolean value) {
        IEclipsePreferences myScope = ConfigurationScope.INSTANCE.getNode(NODE_ARDUINO);
        myScope.putBoolean(key, value);
        try {
            myScope.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    public static void setString(String key, String value) {
        IEclipsePreferences myScope = ConfigurationScope.INSTANCE.getNode(NODE_ARDUINO);
        myScope.put(key, value);
        try {
            myScope.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    public static IPath getInstallationPath() {
        return Common.sloeberHomePath.append("arduinoPlugin"); //$NON-NLS-1$
    }

    public static IPath getInstallationPathLibraries() {
        return getInstallationPath().append(ARDUINO_LIBRARY_FOLDER_NAME);
    }

    public static IPath getInstallationPathExamples() {
        return getInstallationPath().append(EXAMPLE_FOLDER_NAME);
    }

    public static IPath getInstallationPathDownload() {
        return getInstallationPath().append(DOWNLOADS_FOLDER);
    }

    public static IPath getInstallationPathPackages() {
        return getInstallationPath().append(PACKAGES_FOLDER_NAME);
    }

    /**
     * Get the file that contains the preprocessing platform content
     *
     * @return
     */
    public static File getPreProcessingPlatformFile() {
        return getInstallationPath().append(PRE_PROCESSING_PLATFORM_TXT).toFile();
    }

    /**
     * Get the file that contains the post processing platform content
     *
     * @return
     */
    public static File getPostProcessingPlatformFile() {
        return getInstallationPath().append(POST_PROCESSING_PLATFORM_TXT).toFile();
    }

    public static File getPreProcessingBoardsFile() {
        return getInstallationPath().append(PRE_PROCESSING_BOARDS_TXT).toFile();
    }

    public static File getPostProcessingBoardsFile() {
        return getInstallationPath().append(POST_PROCESSING_BOARDS_TXT).toFile();
    }

    public static Path getMakePath() {
        return new Path(getInstallationPath().append("tools/make").toString()); //$NON-NLS-1$

    }

    public static IPath getAwkPath() {
        return new Path(getInstallationPath().append("tools/awk").toString()); //$NON-NLS-1$
    }

    public static boolean getUpdateJasonFilesFlag() {
        return getBoolean(KEY_UPDATE_JASONS, Defaults.updateJsonFiles);
    }

    public static void setUpdateJasonFilesFlag(boolean newFlag) {
        setBoolean(KEY_UPDATE_JASONS, newFlag);
    }

}
