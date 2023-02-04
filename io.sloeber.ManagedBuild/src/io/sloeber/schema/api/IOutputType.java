/*******************************************************************************
 * Copyright (c) 2005, 2010 Intel Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package io.sloeber.schema.api;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.content.IContentType;

import io.sloeber.autoBuild.integration.AutoBuildConfigurationData;

/**
 * This interface represents an outputType instance in the managed build system.
 * It describes one category of output files created by a Tool. A tool can
 * have multiple outputType children.
 *
 * @since 3.0
 * @noextend This class is not intended to be subclassed by clients.
 * @noimplement This interface is not intended to be implemented by clients.
 */
public interface IOutputType extends ISchemaObject {
    public static final String OUTPUT_TYPE_ELEMENT_NAME = "outputType"; //$NON-NLS-1$

    public static final String OUTPUT_CONTENT_TYPE = "outputContentType"; //$NON-NLS-1$
    public static final String OPTION = "option"; //$NON-NLS-1$
    public static final String OUTPUT_PREFIX = "outputPrefix"; //$NON-NLS-1$
    public static final String OUTPUT_EXTENSION = "outputExtension"; //$NON-NLS-1$
    public static final String OUTPUT_NAME = "outputName"; //$NON-NLS-1$
    public static final String NAME_PATTERN = "namePattern"; //$NON-NLS-1$
    public static final String NAME_PROVIDER = "nameProvider"; //$NON-NLS-1$
    public static final String BUILD_VARIABLE = "buildVariable"; //$NON-NLS-1$

    /**
     * Answers <code>true</code> if the output type considers the file extension to
     * be
     * one associated with an output file.
     *
     * @param tool
     *            the tool that contains the output-type
     * @param ext
     *            file extension
     * @return boolean
     */
    public boolean isOutputExtension(ITool tool, String ext);

    /**
     * Returns the id of the option that is associated with this
     * output type on the command line. The default is to use the Tool
     * "outputFlag" attribute if primaryOutput is True. If option is not
     * specified, and primaryOutput is False, then the output file(s) of
     * this outputType are not added to the command line.
     * When specified, the namePattern, nameProvider and outputNames are ignored.
     *
     * @return String
     */
    public String getOptionId();

    /**
     * Returns the prefix that the tool should prepend to the name of the build
     * artifact.
     * For example, a librarian usually prepends 'lib' to the target.a
     * 
     * @return String
     */
    public String getOutputPrefix();

    /**
     * Returns the name of the build variable associated this this output type's
     * resources
     * The variable is used in the build file to represent the list of output files.
     * The same variable name can be used by an inputType to identify a set of
     * output
     * files that contribute to the tool's input (i.e., those using the same
     * buildVariable
     * name). The default name is chosen by MBS.
     *
     * @return String
     */
    public String getBuildVariable();

    /**
     * Fiven a file, configurationdescription, and inputtype
     * provide the filename that will be created during the build.
     * <p>
     * Note that configurationdescription, a inputtype are provided as information
     * for advanced name provider functionality
     * Therefore these can be null for convenience reasons.
     * <p>
     * 
     * @param config
     *            The configuration this is asked for or null
     * @param inputType
     *            The input type that leads to this name provider or null
     * @param inputFile
     * @return The file that will be created by the build.
     */
    IContentType getOutputContentType();

    public String getOutputExtension();

    public IFile getOutputName(IFile inputFile, AutoBuildConfigurationData autoBuildConfData, IInputType inputType);

}
