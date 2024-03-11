package io.sloeber.ui;

import static io.sloeber.ui.Activator.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.dialogs.WizardResourceImportPage;

import io.sloeber.core.api.Const;
import io.sloeber.core.api.IArduinoLibraryVersion;
import io.sloeber.core.api.ISloeberConfiguration;
import io.sloeber.core.api.LibraryManager;

public class Import_Libraries_Page extends WizardResourceImportPage {

	protected Tree myLibrarySelector;

	private IProject myProject = null;

	protected Import_Libraries_Page(IProject project, String name, IStructuredSelection selection) {

		super(name, selection);
		setTitle(Messages.ui_import_arduino_libraries_in_project);
		if (project != null) {
			this.myProject = project;
			setContainerFieldValue(project.getName());
			setDescription(Messages.ui_import_arduino_libraries_in_project_help + this.myProject.getName());
		} else {
			setDescription(Messages.ui_error_select_arduino_project);
		}

	}

	@Override
	public void createControl(Composite parent) {

		initializeDialogUnits(parent);

		Composite composite = new Composite(parent, SWT.NULL);
		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
		composite.setSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		composite.setFont(parent.getFont());

		createSourceGroup(composite);

		setPageComplete(true);
		setErrorMessage(null); // should not initially have error message

		setControl(composite);
	}

	class ItemSorter {
		public static ISloeberConfiguration sloeberCfg;
		public TreeMap<String, ItemSorter> myItems = new TreeMap<>();
		public IArduinoLibraryVersion myLib = null;

		ItemSorter() {
		}

		public void createChildren(TreeItem curItem) {
			for (Entry<String, ItemSorter> curentry : myItems.entrySet()) {
				String key = curentry.getKey();
				ItemSorter curSorter = curentry.getValue();
				TreeItem newItem = new TreeItem(curItem, SWT.NONE);
				newItem.setText(key);
				curSorter.createChildren(newItem);
				newItem.setExpanded(true);
			}
			if (myLib == null) {
				curItem.setGrayed(true);
			}else {
				curItem.setChecked(sloeberCfg.getUsedLibraries().get(myLib.getName()) != null);
				curItem.setData(myLib);
			}

		}
	}

	@Override
	protected void createSourceGroup(Composite parent) {
		if (myProject == null)
			return;
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout theGridLayout = new GridLayout();
		theGridLayout.numColumns = 1;
		composite.setLayout(theGridLayout);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		composite.setFont(parent.getFont());

		GridData theGriddata;

		myLibrarySelector = new Tree(composite, SWT.CHECK | SWT.BORDER);
		theGriddata = new GridData(SWT.FILL, SWT.FILL, true, true);
		theGriddata.horizontalSpan = 1;
		myLibrarySelector.setLayoutData(theGriddata);
		myLibrarySelector.setSortDirection(SWT.UP);

		ISloeberConfiguration sloeberCfg = ISloeberConfiguration.getActiveConfig(myProject);
		// find the items to add to the list
		Map<String, IArduinoLibraryVersion> allLibraries = LibraryManager
				.getLibrariesAll(sloeberCfg.getBoardDescription());

		// sort the items
		ItemSorter sortedItems = new ItemSorter();
		ItemSorter.sloeberCfg = sloeberCfg;

		for (IArduinoLibraryVersion curlib : allLibraries.values()) {
			String keys[] = curlib.getBreadCrumbs();
			ItemSorter curParent = sortedItems;
			for (String curKey : keys) {
				ItemSorter curSorter = curParent.myItems.get(curKey);
				if (curSorter == null) {
					curSorter = new ItemSorter();
					curParent.myItems.put(curKey, curSorter);
				}
				curParent = curSorter;
			}
			curParent.myLib = curlib;
		}
		myLibrarySelector.setRedraw(false);

		for (Entry<String, ItemSorter> curentry : sortedItems.myItems.entrySet()) {
			String key = curentry.getKey();
			ItemSorter curSorter = curentry.getValue();

			TreeItem curItem = new TreeItem(myLibrarySelector, SWT.NONE);
			curItem.setText(key);
			curSorter.createChildren(curItem);
			curItem.setExpanded(true);
		}
		myLibrarySelector.setRedraw(true);

	}


	@Override
	protected ITreeContentProvider getFileProvider() {
		return null;
	}

	@Override
	protected ITreeContentProvider getFolderProvider() {
		return null;
	}

	public boolean PerformFinish() {
		// check if there is a incompatibility in the library folder name
		// windows only
		if (Platform.getOS().equals(Platform.OS_WIN32)) {
			IFolder folder = this.myProject.getFolder(Const.ARDUINO_LIBRARY_FOLDER_NAME);
			if (!folder.exists()) {
				try {
					folder.create(false, true, null);
				} catch (CoreException e) {
					log(new Status(IStatus.ERROR, PLUGIN_ID,
							"Failed to create \"libraries\" folder.\nThis is probably a windows case insensetivity problem", //$NON-NLS-1$
							e));
					return true;
				}

			}
		}
		TreeItem selectedTreeItems[] = this.myLibrarySelector.getItems();
		Set<IArduinoLibraryVersion> selectedLibraries = getSelectedLibraries();
		for (TreeItem CurItem : selectedTreeItems) {
			if (CurItem.getChecked())
				selectedLibraries.add((IArduinoLibraryVersion) CurItem.getData());
		}
		CoreModel coreModel = CoreModel.getDefault();
		ICProjectDescription projDesc = coreModel.getProjectDescription(myProject, true);
		ISloeberConfiguration sloeberCfg = ISloeberConfiguration.getActiveConfig(projDesc);
		sloeberCfg.setLibraries(selectedLibraries);
		try {
			coreModel.setProjectDescription(myProject, projDesc, true, null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return true;
	}

	public Set<IArduinoLibraryVersion> getSelectedLibraries() {
		Set<IArduinoLibraryVersion> ret = new HashSet<>();
		for (TreeItem curTreeItem : myLibrarySelector.getItems()) {
			ret.addAll(internalGetSelectedLibraries(curTreeItem));
		}
		return ret;
	}

	private List<IArduinoLibraryVersion> internalGetSelectedLibraries(TreeItem TreeItem) {
		List<IArduinoLibraryVersion> ret = new ArrayList<>();
		for (TreeItem curchildTreeItem : TreeItem.getItems()) {
			if (curchildTreeItem.getChecked() && (curchildTreeItem.getData() != null)) {
				IArduinoLibraryVersion location = (IArduinoLibraryVersion) curchildTreeItem.getData();
				ret.add(location);
			}
			ret.addAll(internalGetSelectedLibraries(curchildTreeItem));
		}
		return ret;
	}

}
