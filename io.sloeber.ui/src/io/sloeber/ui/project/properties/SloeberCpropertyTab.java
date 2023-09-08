package io.sloeber.ui.project.properties;

import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICResourceDescription;
import org.eclipse.cdt.ui.newui.AbstractCPropertyTab;
import org.eclipse.cdt.ui.newui.ICPropertyProvider;
import org.eclipse.cdt.ui.newui.ICPropertyTab;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import io.sloeber.core.api.SloeberConfiguration;

public abstract class SloeberCpropertyTab extends AbstractCPropertyTab {

	protected SloeberConfiguration mySloeberCfg = null;

	/**
	 * updte the screen based on the data stored in the properties
	 */
	protected abstract void updateScreen();

	@Override
	public void createControls(Composite parent, ICPropertyProvider provider) {
		super.createControls(parent, provider);
		mySloeberCfg = SloeberConfiguration.getConfig(getConfdesc());
	}

	/**
	 * Get the configuration we are currently working in. The configuration is null
	 * if we are in the create sketch wizard.
	 *
	 * @return the configuration to save info into
	 */
	protected ICConfigurationDescription getConfdesc() {
		if (this.page != null) {
			ICConfigurationDescription curConfDesc = getResDesc().getConfiguration();
			return curConfDesc;
		}
		return null;
	}

	public SloeberCpropertyTab() {
		super();
	}

	@Override
	public void handleTabEvent(int kind, Object data) {
		switch (kind) {
		case ICPropertyTab.OK:
			if (canBeVisible())
				performOK();
			break;
		case ICPropertyTab.APPLY:
			if (canBeVisible())
				performApply(getResDesc(), (ICResourceDescription) data);
			break;
		case ICPropertyTab.CANCEL:
			if (canBeVisible())
				performCancel();
			break;
		case ICPropertyTab.DEFAULTS:
			if (canBeVisible() /* && getResDesc() != null */) {
				updateData(getResDesc());
				performDefaults();
			}
			break;
		case ICPropertyTab.UPDATE:
//			Object description = getDescription(getConfdesc());
			updateScreen();
			break;
		case ICPropertyTab.DISPOSE:
			dispose();
			break;
		case ICPropertyTab.VISIBLE:
			if (canSupportMultiCfg() || !page.isMultiCfg()) {
				if (canBeVisible()) {
					setVisible(data != null);
					setButtonVisible(data != null);
				} else
					setVisible(false);
			} else
				setAllVisible(false, null);
			break;
		case ICPropertyTab.SET_ICON:
			icon = (Image) data;
			break;
		default:
			break;
		}
	}

	@Override
	protected void updateData(ICResourceDescription cfg) {
		// updateScreen(getDescription(cfg.getConfiguration()));
		updateScreen();
	}

	@Override
	public boolean canBeVisible() {
		return true;
	}

	@Override
	protected void updateButtons() {
		// nothing to do here

	}

}