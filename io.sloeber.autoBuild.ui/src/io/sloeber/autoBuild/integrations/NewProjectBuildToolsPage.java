package io.sloeber.autoBuild.integrations;

import javax.management.NotificationListener;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import io.sloeber.autoBuild.ui.tabs.BuildToolManagerTab;
import io.sloeber.autoBuild.ui.tabs.DialogCompleteEvent;
import io.sloeber.buildTool.api.IBuildTools;

public class NewProjectBuildToolsPage extends  WizardPage {


	private BuildToolManagerTab myBuildToolsManagerTab;

	protected NewProjectBuildToolsPage(String pageName) {
		super(pageName);
		myBuildToolsManagerTab =new BuildToolManagerTab();
	}

	@Override
	public void createControl(Composite parent) {
		Composite usercomp = new Composite(parent, SWT.NONE);
		usercomp.setLayout(new GridLayout());
		usercomp.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		setControl(usercomp);
		myBuildToolsManagerTab.internalCreateControls(usercomp, new DialogCompleteEvent(){
			@Override
			public void completeEvent(boolean isComplete) {
				setPageComplete(isComplete);
			}
		});
	}

	public IBuildTools getBuildTools() {
		return myBuildToolsManagerTab.getSelecteddBuildTool();
	}

}
