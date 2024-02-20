package io.sloeber.autoBuild.Internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.osgi.framework.Bundle;

import io.sloeber.autoBuild.api.ICodeProvider;
import static io.sloeber.autoBuild.integration.AutoBuildConstants.*;

public class AutoBuildTemplateCodeProvider implements ICodeProvider {
	private String myID = null;
	private String myName = null;
	private String myDescription = null;
	private Set<String> myBuildArtifactTypes = new HashSet<>();
	private IPath myTemplateFolder;

	@SuppressWarnings("nls")
	public AutoBuildTemplateCodeProvider(Bundle bundle, IConfigurationElement element)
			throws IOException, URISyntaxException {
		myID = element.getAttribute(ID);
		myName = element.getAttribute(NAME);
		myDescription = element.getAttribute(DESCRIPTION);
		String buildArtifacts = element.getAttribute("SupportedArtifactTypes");
		if (buildArtifacts != null) {
			myBuildArtifactTypes.addAll(Arrays.asList(buildArtifacts.split(";")));
		}
		String providedPath = element.getAttribute("CodeLocation");
		Path path = new Path(providedPath);
		URL fileURL = FileLocator.find(bundle, path, null);
		if (fileURL == null) {
			System.err.println("For template code with name " + myName + " and ID " + myID + " the path is not found "
					+ providedPath);
		}
		URL resolvedFileURL = FileLocator.toFileURL(fileURL);
		myTemplateFolder = new Path(resolvedFileURL.toURI().getPath());
	}

	@Override
	public boolean createFiles(IFolder targetFolder, IProgressMonitor monitor) {
		try {
			File templateFolder = myTemplateFolder.toFile();
			return recursiveCreateFiles(templateFolder, targetFolder, monitor);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean recursiveCreateFiles(File templateFolder, IFolder targetFolder, IProgressMonitor monitor) {
		try {
			for (File curMember : templateFolder.listFiles()) {
				if (curMember.isFile()) {
					File sourceFile = curMember;
					IFile targetFile = targetFolder.getFile(sourceFile.getName());

					try (InputStream theFileStream = new FileInputStream(sourceFile.toString())) {
						targetFile.create(theFileStream, true, monitor);
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					// curmember is a folder
					IFolder newtargetFolder = targetFolder.getFolder(curMember.getName());
					newtargetFolder.create(true, true, monitor);
					recursiveCreateFiles(curMember, newtargetFolder, monitor);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public boolean supportsBuildArticactType(String buildArtifactType) {
		return myBuildArtifactTypes.contains(buildArtifactType);
	}

	@Override
	public String getName() {
		return myName;
	}

	@Override
	public String getID() {
		return myID;
	}

	@Override
	public String getDescription() {
		return myDescription;
	}
}
