/*******************************************************************************
 * Copyright (c) 2009, 2013 Andrew Gvozdev and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Andrew Gvozdev - initial API and implementation
 *******************************************************************************/

package io.sloeber.autoBuild.Internal;

import static io.sloeber.autoBuild.api.AutoBuildConstants.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.cdt.core.AbstractExecutableExtensionBase;
import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.CommandLauncherManager;
import org.eclipse.cdt.core.ErrorParserManager;
import org.eclipse.cdt.core.ICommandLauncher;
import org.eclipse.cdt.core.IConsoleParser;
import org.eclipse.cdt.core.IMarkerGenerator;
import org.eclipse.cdt.core.ProblemMarkerInfo;
import org.eclipse.cdt.core.language.settings.providers.ICBuildOutputParser;
import org.eclipse.cdt.core.language.settings.providers.ILanguageSettingsBroadcastingProvider;
import org.eclipse.cdt.core.language.settings.providers.IWorkingDirectoryTracker;
import org.eclipse.cdt.core.language.settings.providers.LanguageSettingsStorage;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.model.LanguageManager;
import org.eclipse.cdt.core.resources.IConsole;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.cdt.core.settings.model.util.CDataUtil;
import org.eclipse.cdt.utils.CommandLineUtil;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import io.sloeber.autoBuild.api.IAutoBuildConfigurationDescription;
import io.sloeber.autoBuild.core.Activator;
import io.sloeber.autoBuild.extensionPoint.providers.AutoBuildCommon;
import io.sloeber.schema.api.IOption;

/**
 * Implementation of language settings provider for autoBuild.
 */
public class AutoBuildLanguageSettingsProvider extends AbstractExecutableExtensionBase
		implements ILanguageSettingsBroadcastingProvider {

	private static final String CDT_MANAGEDBUILDER_UI_PLUGIN_ID = "org.eclipse.cdt.managedbuilder.ui"; //$NON-NLS-1$
	private static final String SCANNER_DISCOVERY_CONSOLE = "org.eclipse.cdt.managedbuilder.ScannerDiscoveryConsole"; //$NON-NLS-1$
	private static final String SCANNER_DISCOVERY_GLOBAL_CONSOLE = "org.eclipse.cdt.managedbuilder.ScannerDiscoveryGlobalConsole"; //$NON-NLS-1$
	private static final String DEFAULT_CONSOLE_ICON = "icons/obj16/inspect_sys.gif"; //$NON-NLS-1$
	private static final String GMAKE_ERROR_PARSER_ID = "org.eclipse.cdt.core.GmakeErrorParser"; //$NON-NLS-1$



	private static final int MONITOR_SCALE = 100;
	private static final int TICKS_OUTPUT_PARSING = 1 * MONITOR_SCALE;
	private static final int TICKS_EXECUTE_COMMAND = 1 * MONITOR_SCALE;

	private Map<String, List<ICLanguageSettingEntry>> myDiscoveryCache=new HashMap<>();
	private SDMarkerGenerator markerGenerator = new SDMarkerGenerator();
	private boolean isConsoleEnabled = false;

	private class SDMarkerGenerator implements IMarkerGenerator {
		// Reuse scanner discovery markers defined in
		// org.eclipse.cdt.managedbuilder.core plugin.xml
		protected static final String SCANNER_DISCOVERY_PROBLEM_MARKER = "org.eclipse.cdt.managedbuilder.core.scanner.discovery.problem"; //$NON-NLS-1$
		protected static final String ATTR_PROVIDER = "provider"; //$NON-NLS-1$

		@Override
		public void addMarker(IResource rc, int lineNumber, String errorDesc, int severity, String errorVar) {
			ProblemMarkerInfo info = new ProblemMarkerInfo(rc, lineNumber, errorDesc, severity, errorVar);
			addMarker(info);
		}

		@Override
		public void addMarker(final ProblemMarkerInfo problemMarkerInfo) {
			final String providerId = getId();
			// Add markers in a job to avoid deadlocks
			Job markerJob = new Job(
					// ManagedMakeMessages.getResourceString(
					"AbstractBuiltinSpecsDetector.AddScannerDiscoveryMarkers") { //$NON-NLS-1$
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					// Avoid duplicates as different languages can generate identical errors
					try {
						IMarker[] markers = problemMarkerInfo.file.findMarkers(
								SDMarkerGenerator.SCANNER_DISCOVERY_PROBLEM_MARKER, false, IResource.DEPTH_ZERO);
						for (IMarker marker : markers) {
							int sev = ((Integer) marker.getAttribute(IMarker.SEVERITY)).intValue();
							if (sev == problemMarkerInfo.severity) {
								String msg = (String) marker.getAttribute(IMarker.MESSAGE);
								if (msg != null && msg.equals(problemMarkerInfo.description)) {
									return Status.OK_STATUS;
								}
							}
						}
					} catch (CoreException e) {
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Error checking markers.", //$NON-NLS-1$
								e);
					}

					try {
						IMarker marker = problemMarkerInfo.file
								.createMarker(SDMarkerGenerator.SCANNER_DISCOVERY_PROBLEM_MARKER);
						marker.setAttribute(IMarker.MESSAGE, problemMarkerInfo.description);
						marker.setAttribute(IMarker.SEVERITY, problemMarkerInfo.severity);
						marker.setAttribute(SDMarkerGenerator.ATTR_PROVIDER, providerId);

						if (problemMarkerInfo.file instanceof IWorkspaceRoot) {
							String msgPreferences = // ManagedMakeMessages.getFormattedString(
									"AbstractBuiltinSpecsDetector.ScannerDiscoveryMarkerLocationPreferences";
							// , //$NON-NLS-1$ providerName);
							marker.setAttribute(IMarker.LOCATION, msgPreferences);
						} else {
							String msgProperties = // ManagedMakeMessages.getFormattedString(
									"AbstractBuiltinSpecsDetector.ScannerDiscoveryMarkerLocationProperties";
							// //$NON-NLS-1$ providerName);
							marker.setAttribute(IMarker.LOCATION, msgProperties);
						}
					} catch (CoreException e) {
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Error adding markers.", //$NON-NLS-1$
								e);
					}

					return Status.OK_STATUS;
				}
			};

			markerJob.setRule(problemMarkerInfo.file);
			markerJob.schedule();
		}

		/**
		 * Delete markers previously set by this provider for the resource.
		 *
		 * @param rc - resource to check markers.
		 */
		public void deleteMarkers(IResource rc) {
			if (!rc.isAccessible()) {
				return; // resource might be read-only or project might be closed
			}
			String providerId = getId();
			try {
				IMarker[] markers = rc.findMarkers(SCANNER_DISCOVERY_PROBLEM_MARKER, false, IResource.DEPTH_ZERO);
				for (IMarker marker : markers) {
					if (providerId.equals(marker.getAttribute(ATTR_PROVIDER))) {
						marker.delete();
					}
				}
			} catch (CoreException e) {
				Activator.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Error deleting markers.", e)); //$NON-NLS-1$
			}
		}

	}

	@SuppressWarnings("incomplete-switch")
	@Override
	public List<ICLanguageSettingEntry> getSettingEntries(ICConfigurationDescription cfgDescription, IResource rc,
			String languageId) {
		if (cfgDescription == null || rc == null || languageId == null) {
			return null;
		}
		IAutoBuildConfigurationDescription autoConf = IAutoBuildConfigurationDescription.getConfig(cfgDescription);
		if (autoConf == null) {
			return null;
		}

		// this list is allowed to contain duplicate entries, cannot be LinkedHashSet
		List<ICLanguageSettingEntry> list = new ArrayList<>();
		int flags = ICSettingEntry.VALUE_WORKSPACE_PATH | ICSettingEntry.BUILTIN | ICSettingEntry.READONLY;

		TreeMap<IOption, String> options = autoConf.getSelectedOptions(rc);
		for (Entry<IOption, String> curOption : options.entrySet()) {
			IOption option = curOption.getKey();
			if (!option.isForLanguage(languageId)) {
				continue;
			}
			String optionValue = curOption.getValue();
			String optionValues[] = optionValue.split(SEMICOLON);
			int valueType = option.getValueType();
			switch (valueType) {
			case IOption.STRING:
			case IOption.STRING_LIST:
			case IOption.BOOLEAN:
			case IOption.ENUMERATED:
			case IOption.OBJECTS:
			case IOption.TREE:
				break;
			case IOption.INCLUDE_FILES: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCIncludeFileEntry(curName, flags));
				}
				break;
			}
			case IOption.INCLUDE_PATH: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCIncludePathEntry(curName, flags));
				}
				break;
			}
			case IOption.LIBRARIES: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCLibraryFileEntry(curName, flags));
				}
				break;
			}

			case IOption.LIBRARY_PATHS: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCLibraryPathEntry(curName, flags));
				}
				break;
			}
			case IOption.LIBRARY_FILES: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCLibraryFileEntry(curName, flags));
				}
				break;
			}

			case IOption.PREPROCESSOR_SYMBOLS: {
				for (String curName : optionValues) {
					String parts[] = curName.split(EQUAL, 2);
					if (parts.length == 2) {
						list.add(CDataUtil.createCMacroEntry(parts[0], parts[1], flags));
					}
				}
				break;
			}
			case IOption.MACRO_FILES: {
				for (String curName : optionValues) {
					list.add(CDataUtil.createCMacroFileEntry(curName, flags));
				}
				break;
			}

			case IOption.UNDEF_INCLUDE_PATH:
			case IOption.UNDEF_PREPROCESSOR_SYMBOLS:
			case IOption.UNDEF_INCLUDE_FILES:
			case IOption.UNDEF_MACRO_FILES:
			case IOption.UNDEF_LIBRARY_PATHS:
			case IOption.UNDEF_LIBRARY_FILES:

			}
		}
		String discoveryCommand = autoConf.getDiscoveryCommand(languageId);
		if (discoveryCommand == null || discoveryCommand.isBlank()) {
			return LanguageSettingsStorage.getPooledList(list);
		}
		if (myDiscoveryCache.get(discoveryCommand)==null) {
			myDiscoveryCache.put(discoveryCommand, runForLanguage(languageId, discoveryCommand, autoConf,
					autoConf.getProject(), new NullProgressMonitor()));
		}
		if (myDiscoveryCache.get(discoveryCommand) != null) {
			list.addAll(myDiscoveryCache.get(discoveryCommand));
		}
		return LanguageSettingsStorage.getPooledList(list);
	}

	@Override
	public LanguageSettingsStorage copyStorage() {
		class PretendStorage extends LanguageSettingsStorage {
			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public LanguageSettingsStorage clone() throws CloneNotSupportedException {
				return this;
			}

			@Override
			public boolean equals(Object obj) {
				// Note that this always triggers change event even if nothing changed in MBS
				return false;
			}
		}
		return new PretendStorage();
	}

	/**
	 * Run built-in specs command for one language.
	 *
	 * @param monitor - progress monitor in the initial state where
	 *                {@link IProgressMonitor#beginTask(String, int)} has not been
	 *                called yet.
	 */
	private List<ICLanguageSettingEntry> runForLanguage(String currentLanguageId, String currentCommandResolved,
			IAutoBuildConfigurationDescription autoConf, IProject currentProject, IProgressMonitor monitor) {
		List<ICLanguageSettingEntry> ret = new LinkedList<>();

		try (AutoBuildRunnerHelper buildRunnerHelper = new AutoBuildRunnerHelper(currentProject);) {
			SubMonitor subMonitor = SubMonitor.convert(monitor,
					// ManagedMakeMessages.getFormattedString(
					"AbstractBuiltinSpecsDetector.RunningScannerDiscovery", //$NON-NLS-1$
					// getName()),
					100);

			IConsole console;
			if (isConsoleEnabled) {
				console = startProviderConsole(currentProject, currentLanguageId);
			} else {
				// that looks in extension points registry and won't find the id, this console
				// is not shown
				console = CCorePlugin.getDefault().getConsole(Activator.PLUGIN_ID +DOT+ currentLanguageId + ".console.hidden"); //$NON-NLS-1$
			}
			console.start(currentProject);

			ICommandLauncher launcher = CommandLauncherManager.getInstance()
					.getCommandLauncher(autoConf.getCdtConfigurationDescription());
			launcher.setProject(currentProject);

			IPath program = new Path(""); //$NON-NLS-1$
			String[] args = new String[0];
			String[] cmdArray = CommandLineUtil.argumentsToArray(currentCommandResolved);
			if (cmdArray != null && cmdArray.length > 0) {
				program = new Path(cmdArray[0]);
				if (cmdArray.length > 1) {
					args = new String[cmdArray.length - 1];
					System.arraycopy(cmdArray, 1, args, 0, args.length);
				}
			}

			String[] envp = autoConf.getEnvironmentVariables();
			IFolder buildFolder=autoConf.getBuildFolder();
			AutoBuildCommon.createFolder(buildFolder);

			// Using GMAKE_ERROR_PARSER_ID as it can handle generated error messages
			ErrorParserManager epm = new ErrorParserManager(currentProject, buildFolder.getLocationURI(),
					markerGenerator, new String[] { GMAKE_ERROR_PARSER_ID });
			ConsoleParserAdapter consoleParser = new ConsoleParserAdapter();
			consoleParser.startup(autoConf.getCdtConfigurationDescription(), epm);
			List<IConsoleParser> parsers = new ArrayList<>();
			parsers.add(consoleParser);

			buildRunnerHelper.setLaunchParameters(launcher, program, args, buildFolder.getLocationURI(),
					envp);
			buildRunnerHelper.prepareStreams(epm, parsers, console, subMonitor.split(TICKS_OUTPUT_PARSING));

			buildRunnerHelper.greeting(// ManagedMakeMessages
					// .getFormattedString(
					"AbstractBuiltinSpecsDetector.RunningScannerDiscovery"//$NON-NLS-1$
			// , getName())
			);

			buildRunnerHelper.build(subMonitor.split(TICKS_EXECUTE_COMMAND));

			buildRunnerHelper.close();
			buildRunnerHelper.goodbye();
			ret.addAll(consoleParser.includeEntries);
			ret.addAll(consoleParser.macroEntries);

		} catch (Exception e) {
			Activator.log(new CoreException(
					new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Error running Builtin Specs Detector", e))); //$NON-NLS-1$
		} finally {
			monitor.done();
		}
		return ret;
	}

	/**
	 * Create and start the provider console.
	 *
	 * @return CDT console.
	 */
	private IConsole startProviderConsole(IProject currentProject, String currentLanguageId) {
		IConsole console = null;

		if (isConsoleEnabled && currentLanguageId != null) {
			String extConsoleId;
			if (currentProject != null) {
				extConsoleId = SCANNER_DISCOVERY_CONSOLE;
			} else {
				extConsoleId = SCANNER_DISCOVERY_GLOBAL_CONSOLE;
			}
			ILanguage ld = LanguageManager.getInstance().getLanguage(currentLanguageId);
			if (ld != null) {
				String consoleId = Activator.PLUGIN_ID + '.' + getId() + '.' + currentLanguageId;
				String consoleName = getName() + ", " + ld.getName(); //$NON-NLS-1$
				URL defaultIcon = Platform.getBundle(CDT_MANAGEDBUILDER_UI_PLUGIN_ID).getEntry(DEFAULT_CONSOLE_ICON);
				if (defaultIcon == null) {
					@SuppressWarnings("nls")
					String msg = "Unable to find icon " + DEFAULT_CONSOLE_ICON + " in plugin "
							+ CDT_MANAGEDBUILDER_UI_PLUGIN_ID;
					Activator.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg));
				}

				console = CCorePlugin.getDefault().getConsole(extConsoleId, consoleId, consoleName, defaultIcon);
			}
		}

		if (console == null) {
			// that looks in extension points registry and won't find the id, this console
			// is not shown
			console = CCorePlugin.getDefault().getConsole(Activator.PLUGIN_ID + ".console.hidden"); //$NON-NLS-1$
		}

		return console;
	}

	/**
	 * Internal ICConsoleParser to handle individual run for one language.
	 */
	private class ConsoleParserAdapter implements ICBuildOutputParser {
		List<ICLanguageSettingEntry> macroEntries = new ArrayList<>();
		List<ICLanguageSettingEntry> includeEntries = new ArrayList<>();
		int flags=ICSettingEntry.BUILTIN;
		boolean inIncludes=false;

		@Override
		public void startup(ICConfigurationDescription cfgDescription, IWorkingDirectoryTracker cwdTracker)
				throws CoreException {
			//nothing to do
		}

		@Override
		public boolean processLine(String line) {
			System.out.println(line);

			for(Entry<Pattern, outputTypes> curMatcher:outputMatchers.entrySet()) {
				Pattern pat=curMatcher.getKey();
				Matcher match= pat.matcher(line);
				if(match.find()) {
					switch(curMatcher.getValue()) {
					case INCLUDE1:
						flags=ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.LOCAL;
						inIncludes=true;
						return true;
					case INCLUDE2:
						flags=ICSettingEntry.BUILTIN | ICSettingEntry.READONLY  ;
						inIncludes=true;
						return true;
					case FRAMEWORK:{
						int localflags=ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.FRAMEWORKS_MAC ;
						includeEntries.add(CDataUtil.createCIncludeFileEntry(getCanonical(match.group(1)), localflags));
						return true;
					}
					case DEFINE1:{
						int localflags=ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.FRAMEWORKS_MAC ;
						macroEntries.add(CDataUtil.createCMacroEntry(match.group(1),match.group(2), localflags));
						return true;
					}
					case DEFINE2:{
						int localflags=ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.FRAMEWORKS_MAC ;
						macroEntries.add(CDataUtil.createCMacroEntry(match.group(1),match.group(2), localflags));
						return true;
					}
					case END_OF_INCLUDES:
						inIncludes=false;
						return true;
					default:
						break;
					}
				}
			}
			if(inIncludes) {
				includeEntries.add(CDataUtil.createCIncludePathEntry(getCanonical(line), flags));
				return true;
			}
			return false;
		}

		private static String getCanonical(String path) {
			String canonical=path;
			try {
				File path2= new File(path.trim());
				canonical = path2.getCanonicalPath();
			} catch ( IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return canonical;
		}

		@Override
		public void shutdown() {
			//nothing to do
		}
	}


//	private static final AbstractOptionParser[] optionParsers = {
//			new IncludePathOptionParser("#include \"(\\S.*)\"", "$1",
//					ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.LOCAL),
//			new IncludePathOptionParser("#include <(\\S.*)>", "$1", ICSettingEntry.BUILTIN | ICSettingEntry.READONLY),
//			new IncludePathOptionParser("#framework <(\\S.*)>", "$1",
//					ICSettingEntry.BUILTIN | ICSettingEntry.READONLY | ICSettingEntry.FRAMEWORKS_MAC),
//			new MacroOptionParser("#define\\s+(\\S*\\(.*?\\))\\s*(.*)", "$1", "$2",
//					ICSettingEntry.BUILTIN | ICSettingEntry.READONLY),
//			new MacroOptionParser("#define\\s+(\\S*)\\s*(.*)", "$1", "$2",
//					ICSettingEntry.BUILTIN | ICSettingEntry.READONLY), };
	private  enum outputTypes { INCLUDE1,  INCLUDE2 , FRAMEWORK,DEFINE1,DEFINE2,END_OF_INCLUDES}
	private static  Map<Pattern,outputTypes> outputMatchers=new HashMap<>() {{
		put(Pattern.compile("#include \"(\\S.*)\""),outputTypes.INCLUDE1); //$NON-NLS-1$
		put(Pattern.compile("#include <(\\S.*)>"), outputTypes.INCLUDE2); //$NON-NLS-1$
		put(Pattern.compile("#framework <(\\S.*)>"), outputTypes.FRAMEWORK); //$NON-NLS-1$
		put(Pattern.compile("#define\\s+(\\S*\\(.*?\\))\\s*(.*)"), outputTypes.DEFINE1); //$NON-NLS-1$
		put(Pattern.compile("#define\\s+(\\S*)\\s*(.*)"), outputTypes.DEFINE2); //$NON-NLS-1$
		put(Pattern.compile("End of search list\\."), outputTypes.END_OF_INCLUDES); //$NON-NLS-1$
	}};


//		case IOption.INCLUDE_FILES: {
//				list.add(CDataUtil.createCIncludeFileEntry(curName, flags));
//		case IOption.INCLUDE_PATH: {
//				list.add(CDataUtil.createCIncludePathEntry(curName, flags));
//		case IOption.LIBRARIES: {
//				list.add(CDataUtil.createCLibraryFileEntry(curName, flags));
//		case IOption.LIBRARY_PATHS: {
//				list.add(CDataUtil.createCLibraryPathEntry(curName, flags));
//		case IOption.LIBRARY_FILES: {
//				list.add(CDataUtil.createCLibraryFileEntry(curName, flags));
//		case IOption.PREPROCESSOR_SYMBOLS: {
//					list.add(CDataUtil.createCMacroEntry(parts[0], parts[1], flags));
//		case IOption.MACRO_FILES: {
//				list.add(CDataUtil.createCMacroFileEntry(curName, flags));

}
