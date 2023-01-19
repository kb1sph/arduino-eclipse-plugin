package io.sloeber.schema.internal;


import static io.sloeber.autoBuild.core.Messages.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.Vector;

import org.eclipse.cdt.core.cdtvariables.CdtVariableException;
import org.eclipse.cdt.core.settings.model.ICStorageElement;
import org.eclipse.cdt.core.settings.model.extension.CLanguageData;
import org.eclipse.cdt.utils.cdtvariables.CdtVariableResolver;
import org.eclipse.cdt.utils.cdtvariables.SupplierBasedCdtVariableSubstitutor;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeSettings;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.framework.Version;

import io.sloeber.autoBuild.Internal.BooleanExpressionApplicabilityCalculator;
import io.sloeber.autoBuild.Internal.BuildLanguageData;
import io.sloeber.autoBuild.Internal.BuildMacroProvider;
import io.sloeber.autoBuild.Internal.BuildfileMacroSubstitutor;
import io.sloeber.autoBuild.Internal.FileContextData;
import io.sloeber.autoBuild.Internal.IMacroContextInfo;
import io.sloeber.autoBuild.Internal.IMacroContextInfoProvider;
import io.sloeber.autoBuild.Internal.ManagedBuildManager;
import io.sloeber.autoBuild.Internal.OptionContextData;
import io.sloeber.autoBuild.Internal.OptionEnablementExpression;
import io.sloeber.autoBuild.api.BuildException;
import io.sloeber.autoBuild.api.BuildMacroException;
import io.sloeber.autoBuild.api.IBuildMacroProvider;
import io.sloeber.autoBuild.api.IEnvVarBuildPath;
import io.sloeber.autoBuild.api.IOptionPathConverter;
import io.sloeber.autoBuild.core.Activator;
import io.sloeber.autoBuild.extensionPoint.IManagedCommandLineGenerator;
import io.sloeber.autoBuild.extensionPoint.IOptionApplicability;
import io.sloeber.autoBuild.extensionPoint.IOptionCategoryApplicability;
import io.sloeber.autoBuild.extensionPoint.IOptionCommandGenerator;
import io.sloeber.buildProperties.PropertyManager;
import io.sloeber.schema.api.IConfiguration;
import io.sloeber.schema.api.IFileInfo;
import io.sloeber.schema.api.IFolderInfo;
import io.sloeber.schema.api.IHoldsOptions;
import io.sloeber.schema.api.IInputType;
import io.sloeber.schema.api.IManagedProject;
import io.sloeber.schema.api.IOption;
import io.sloeber.schema.api.IOptionCategory;
import io.sloeber.schema.api.IOutputType;
import io.sloeber.schema.api.IProjectType;
import io.sloeber.schema.api.IResourceConfiguration;
import io.sloeber.schema.api.IResourceInfo;
import io.sloeber.schema.api.ITool;
import io.sloeber.schema.api.IToolChain;
import io.sloeber.schema.internal.HoldsOptions;
import io.sloeber.schema.internal.IBuildObject;
import io.sloeber.schema.internal.InputType;
import io.sloeber.schema.internal.Option;
import io.sloeber.schema.internal.OutputType;
import io.sloeber.schema.internal.ToolChain;

/**
 * Represents a tool that can be invoked during a build.
 * Note that this class implements IOptionCategory to represent the top
 * category.
 */
public class Tool extends HoldsOptions implements ITool, IOptionCategory {

    public static final String DEFAULT_PATTERN = "${COMMAND} ${FLAGS} ${OUTPUT_FLAG} ${OUTPUT_PREFIX}${OUTPUT} ${INPUTS} ${EXTRA_FLAGS}"; //$NON-NLS-1$
    public static final String DEFAULT_CBS_PATTERN = "${COMMAND}"; //$NON-NLS-1$

    //property name for holding the rebuild state
//    private static final String REBUILD_STATE = "rebuildState"; //$NON-NLS-1$

    private static final String DEFAULT_SEPARATOR = ","; //$NON-NLS-1$
    private static final String EMPTY_STRING = ""; //$NON-NLS-1$
    private static final String EMPTY_QUOTED_STRING = "\"\""; //$NON-NLS-1$
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String WHITESPACE = " "; //$NON-NLS-1$

    private static final boolean resolvedDefault = true;

    public static final String DEFAULT_TOOL_ID = "org.eclipse.cdt.build.core.default.tool"; //$NON-NLS-1$

    //  Superclass
    //  Note that superClass itself is defined in the base and that the methods
    //  getSuperClass() and setSuperClassInternal(), defined in Tool must be used to
    //  access it. This avoids widespread casts from IHoldsOptions to ITool.
//    private String superClassId;
    //  Parent and children
    private IBuildObject parent;
    private Vector<InputType> inputTypeList;
    private Map<String, InputType> inputTypeMap;
    private Vector<OutputType> outputTypeList;
    private Map<String, OutputType> outputTypeMap;
    private List<IEnvVarBuildPath> envVarBuildPathList;
    //  Managed Build model attributes
    private String unusedChildren;
    private Boolean isAbstract;
    private String command;
    private List<String> inputExtensions;
    private List<String> interfaceExtensions;
    private Integer natureFilter;
    private String outputExtensions;
    private String outputFlag;
    private String outputPrefix;
    private String errorParserIds;
    private String commandLinePattern;
    private String versionsSupported;
    private String convertToId;
    private Boolean advancedInputCategory;
    private Boolean customBuildStep;
    private String announcement;
    private IConfigurationElement commandLineGeneratorElement = null;
    private IManagedCommandLineGenerator commandLineGenerator = null;
    private IConfigurationElement dependencyGeneratorElement = null;
    //    private IManagedDependencyGeneratorType dependencyGenerator = null;
    private URL iconPathURL;
    private IConfigurationElement pathconverterElement = null;
    private IOptionPathConverter optionPathConverter = null;
    private SupportedProperties supportedProperties;
    private Boolean supportsManagedBuild;
    private Boolean isHidden;
    private boolean isTest;
    //  Miscellaneous
    private boolean isExtensionTool = false;
//    private boolean isDirty = false;
//    private boolean resolved = resolvedDefault;
    private IConfigurationElement previousMbsVersionConversionElement = null;
    private IConfigurationElement currentMbsVersionConversionElement = null;
//    private boolean rebuildState;
    private BooleanExpressionApplicabilityCalculator booleanExpressionCalculator;

    private HashMap<IInputType, CLanguageData> typeToDataMap = new HashMap<>(2);
    //private HashMap<String, PathInfoCache> discoveredInfoMap = new HashMap<>(2);
    private String scannerConfigDiscoveryProfileId;
    private List<OptionEnablementExpression> myOptionEnablementExpression = new ArrayList<>();;

    /*
     *  C O N S T R U C T O R S
     */

    /**
     * Constructor to create a tool based on an element from the plugin
     * manifest.
     *
     * @param element
     *            The element containing the information about the tool.
     * @param managedBuildRevision
     *            the fileVersion of Managed Build System
     */
    public Tool(IExtensionPoint root, IConfigurationElement element) {
        // setup for resolving
//        resolved = false;
//
//        isExtensionTool = true;
//
//        // Set the managedBuildRevision
//        setManagedBuildRevision(managedBuildRevision);
//
//        loadNameAndID(root, element);
//
//        // version
//        setVersion(getVersionFromId());
//
//        // superClass
//        superClassId = element.getAttribute(IProjectType.SUPERCLASS);
//
//        // Get the 'versionsSupported' attribute
//        versionsSupported = element.getAttribute(VERSIONS_SUPPORTED);
//
//        // Get the 'convertToId' attribute
//        convertToId = element.getAttribute(CONVERT_TO_ID);
//
//        // isAbstract
//        String isAbs = element.getAttribute(IS_ABSTRACT);
//        if (isAbs != null) {
//            isAbstract = Boolean.parseBoolean(isAbs);
//        }
//
//        // Get the semicolon separated list of IDs of the error parsers
//        errorParserIds = element.getAttribute(IToolChain.ERROR_PARSERS);
//
//        // Get the nature filter
//        String nature = element.getAttribute(NATURE);
//        if (nature != null) {
//            if ("both".equals(nature)) { //$NON-NLS-1$
//                natureFilter = FILTER_BOTH;
//            } else if ("cnature".equals(nature)) { //$NON-NLS-1$
//                natureFilter = FILTER_C;
//            } else if ("ccnature".equals(nature)) { //$NON-NLS-1$
//                natureFilter = FILTER_CC;
//            } else {
//                natureFilter = FILTER_BOTH;
//            }
//        }
//
//        // Get the supported input file extensions
//        String inputs = element.getAttribute(ITool.SOURCES);
//        if (inputs != null) {
//            StringTokenizer tokenizer = new StringTokenizer(inputs, DEFAULT_SEPARATOR);
//            while (tokenizer.hasMoreElements()) {
//                getInputExtensionsList().add(tokenizer.nextToken());
//            }
//        }
//
//        // Get the interface (header file) extensions
//        String headers = element.getAttribute(INTERFACE_EXTS);
//        if (headers != null) {
//            StringTokenizer tokenizer = new StringTokenizer(headers, DEFAULT_SEPARATOR);
//            while (tokenizer.hasMoreElements()) {
//                getInterfaceExtensionsList().add(tokenizer.nextToken());
//            }
//        }
//
//        // Get the output extension
//        outputExtensions = element.getAttribute(ITool.OUTPUTS);
//
//        // Get the tool invocation command
//        command = element.getAttribute(ITool.COMMAND);
//
//        // Get the flag to control output
//        outputFlag = element.getAttribute(ITool.OUTPUT_FLAG);
//
//        // Get the output prefix
//        outputPrefix = element.getAttribute(ITool.OUTPUT_PREFIX);
//
//        // Get command line pattern
//        commandLinePattern = element.getAttribute(ITool.COMMAND_LINE_PATTERN);
//
//        // Get advancedInputCategory
//        String advInput = element.getAttribute(ITool.ADVANCED_INPUT_CATEGORY);
//        if (advInput != null) {
//            advancedInputCategory = Boolean.parseBoolean(advInput);
//        }
//
//        // Get customBuildStep
//        String cbs = element.getAttribute(ITool.CUSTOM_BUILD_STEP);
//        if (cbs != null) {
//            customBuildStep = Boolean.parseBoolean(cbs);
//        }
//
//        // Get the announcement text
//        announcement = element.getAttribute(ITool.ANNOUNCEMENT);
//
//        String tmp = element.getAttribute(SUPPORTS_MANAGED_BUILD);
//        if (tmp != null)
//            supportsManagedBuild = Boolean.valueOf(tmp);
//
//        // isHidden
//        String hidden = element.getAttribute(ITool.IS_HIDDEN);
//        if (hidden != null) {
//            isHidden = Boolean.valueOf(hidden);
//        }
//
//        scannerConfigDiscoveryProfileId = element.getAttribute(IToolChain.SCANNER_CONFIG_PROFILE_ID);
//
//        tmp = element.getAttribute(IS_SYSTEM);
//        if (tmp != null)
//            isTest = Boolean.valueOf(tmp).booleanValue();
//
//        // hook me up
//        //   ManagedBuildManager.addExtensionTool(this);
//
//        // set up the category map
//     
//        categoryIds.add(this.getId());
//		// Map the categories by ID for resolution later
//        categoryMap.put(getId(), this);
//
//        myOptionEnablementExpression.clear();
//        IConfigurationElement enablements[] = element.getChildren(OptionEnablementExpression.NAME);
//        for (IConfigurationElement curEnablement : enablements) {
//            myOptionEnablementExpression.add(new OptionEnablementExpression(curEnablement));
//        }
//        booleanExpressionCalculator = new BooleanExpressionApplicabilityCalculator(myOptionEnablementExpression);
//
//        // Load children
//        IConfigurationElement[] toolElements = element.getChildren();
//        for (int l = 0; l < toolElements.length; ++l) {
//            IConfigurationElement toolElement = toolElements[l];
//            if (loadChild(root, toolElement)) {
//                // do nothing
//            } else if (toolElement.getName().equals(ITool.INPUT_TYPE)) {
//                InputType inputType = new InputType(this, root, toolElement);
//                addInputType(inputType);
//            } else if (toolElement.getName().equals(ITool.OUTPUT_TYPE)) {
//                OutputType outputType = new OutputType(this, root, toolElement);
//                addOutputType(outputType);
//            } else if (toolElement.getName().equals(IEnvVarBuildPath.BUILD_PATH_ELEMENT_NAME)) {
//                addEnvVarBuildPath(new EnvVarBuildPath(this, toolElement));
//            } else if (toolElement.getName().equals(SupportedProperties.SUPPORTED_PROPERTIES)) {
//                loadProperties(toolElement);
//            }
//        }
    }

    /**
     * Constructor to create a new tool for a tool-chain based on the information
     * defined in the plugin.xml manifest.
     *
     * @param parent
     *            The parent of this tool. This can be a ToolChain or a
     *            ResourceConfiguration.
     * @param element
     *            The element containing the information about the tool.
     * @param managedBuildRevision
     *            the fileVersion of Managed Build System
     */
    public Tool(IBuildObject parent, IExtensionPoint root, IConfigurationElement element) {
        this(root, element);
        this.parent = parent;
    }

    /**
     * This constructor is called to create a Tool whose attributes and children
     * will be
     * added by separate calls.
     *
     * @param parent
     *            - The parent of the tool, if any
     * @param superClass
     *            - The superClass, if any
     * @param Id
     *            - The id for the new tool
     * @param name
     *            - The name for the new tool
     * @param isExtensionElement
     *            - Indicates whether this is an extension element or a managed
     *            project element
     */
    public Tool(ToolChain parent, ITool superClass, String Id, String name, boolean isExtensionElement) {
        //        this.parent = parent;
        //        setSuperClassInternal(superClass);
        //        setManagedBuildRevision(parent.getManagedBuildRevision());
        //        if (getSuperClass() != null) {
        //            superClassId = getSuperClass().getId();
        //        }
        //
        //        setId(Id);
        //        setName(name);
        //        setVersion(getVersionFromId());
        //
        //        //		if(!superClass.isExtensionElement()){
        //        //			((Tool)superClass).updateScannerInfoSettingsToInputTypes();
        //        //		}
        //
        //        isExtensionTool = isExtensionElement;
        //        if (isExtensionElement) {
        //            // Hook me up to the Managed Build Manager
        //            //            ManagedBuildManager.addExtensionTool(this);
        //        } else {
        //            setRebuildState(true);
        //        }
    }

    /**
     * This constructor is called to create a Tool whose attributes and children
     * will be
     * added by separate calls.
     *
     * @param parent
     *            - The parent of the tool, if any
     * @param superClass
     *            - The superClass, if any
     * @param Id
     *            - The id for the new tool
     * @param name
     *            - The name for the new tool
     * @param isExtensionElement
     *            - Indicates whether this is an extension element or a managed
     *            project element
     */

//    public Tool(ResourceConfiguration parent, ITool superClass, String Id, String name, boolean isExtensionElement) {
//        //        this.parent = parent;
//        //        setSuperClassInternal(superClass);
//        //        setManagedBuildRevision(parent.getManagedBuildRevision());
//        //        if (getSuperClass() != null) {
//        //            superClassId = getSuperClass().getId();
//        //        }
//        //        setId(Id);
//        //        setName(name);
//        //        setVersion(getVersionFromId());
//        //
//        //        isExtensionTool = isExtensionElement;
//        //        //		if(superClass != null && !superClass.isExtensionElement()){
//        //        //			((Tool)superClass).updateScannerInfoSettingsToInputTypes();
//        //        //		}
//        //        if (isExtensionElement) {
//        //            // Hook me up to the Managed Build Manager
//        //            //            ManagedBuildManager.addExtensionTool(this);
//        //        } else {
//        //            setRebuildState(true);
//        //        }
//    }

    /**
     * Create a <code>Tool</code> based on the specification stored in the
     * project file (.cdtbuild).
     *
     * @param parent
     *            The <code>IToolChain</code> or <code>IResourceConfiguration</code>
     *            the tool will be added to.
     * @param element
     *            The XML element that contains the tool settings.
     * @param managedBuildRevision
     *            the fileVersion of Managed Build System
     */
    public Tool(IBuildObject parent, ICStorageElement element, String managedBuildRevision) {
        //        this.parent = parent;
        //        isExtensionTool = false;
        //
        //        // Set the managedBuildRevsion
        //        setManagedBuildRevision(managedBuildRevision);
        //
        //        // Initialize from the XML attributes
        //        loadFromProject(element);
        //
        //        // set up the category map
        //        addOptionCategory(this);
        //
        //        // Load children
        //        ICStorageElement toolElements[] = element.getChildren();
        //        for (int i = 0; i < toolElements.length; ++i) {
        //            ICStorageElement toolElement = toolElements[i];
        //            if (loadChild(toolElement)) {
        //                // do nothing
        //            } else if (toolElement.getName().equals(ITool.INPUT_TYPE)) {
        //                InputType inputType = new InputType(this, toolElement);
        //                addInputType(inputType);
        //            } else if (toolElement.getName().equals(ITool.OUTPUT_TYPE)) {
        //                OutputType outputType = new OutputType(this,root, toolElement);
        //                addOutputType(outputType);
        //            }
        //        }
        //
        //        String rebuild = PropertyManager.getInstance().getProperty(this, REBUILD_STATE);
        //        if (rebuild == null || Boolean.valueOf(rebuild).booleanValue())
        //            rebuildState = true;

    }


    public Tool(IBuildObject parent, String toolSuperClassId, String Id, String name, Tool tool) {
   //     super(resolvedDefault);
        //        this.parent = parent;
        //        superClassId = toolSuperClassId;
        //        setId(Id);
        //        setName(name);
        //
        //        // Set the managedBuildRevision & the version
        //        setManagedBuildRevision(tool.getManagedBuildRevision());
        //        setVersion(getVersionFromId());
        //
        //        isExtensionTool = false;
        //        boolean copyIds = Id.equals(tool.id);
        //
        //        //  Copy the remaining attributes
        //        if (tool.versionsSupported != null) {
        //            versionsSupported = tool.versionsSupported;
        //        }
        //        if (tool.convertToId != null) {
        //            convertToId = tool.convertToId;
        //        }
        //        if (tool.unusedChildren != null) {
        //            unusedChildren = tool.unusedChildren;
        //        }
        //        if (tool.errorParserIds != null) {
        //            errorParserIds = tool.errorParserIds;
        //        }
        //        if (tool.isAbstract != null) {
        //            isAbstract = tool.isAbstract;
        //        }
        //        if (tool.command != null) {
        //            command = tool.command;
        //        }
        //        if (tool.commandLinePattern != null) {
        //            commandLinePattern = tool.commandLinePattern;
        //        }
        //        if (tool.inputExtensions != null) {
        //            inputExtensions = new ArrayList<>(tool.inputExtensions);
        //        }
        //        if (tool.interfaceExtensions != null) {
        //            interfaceExtensions = new ArrayList<>(tool.interfaceExtensions);
        //        }
        //        if (tool.natureFilter != null) {
        //            natureFilter = tool.natureFilter;
        //        }
        //        if (tool.outputExtensions != null) {
        //            outputExtensions = tool.outputExtensions;
        //        }
        //        if (tool.outputFlag != null) {
        //            outputFlag = tool.outputFlag;
        //        }
        //        if (tool.outputPrefix != null) {
        //            outputPrefix = tool.outputPrefix;
        //        }
        //        if (tool.advancedInputCategory != null) {
        //            advancedInputCategory = tool.advancedInputCategory;
        //        }
        //        if (tool.customBuildStep != null) {
        //            customBuildStep = tool.customBuildStep;
        //        }
        //        if (tool.announcement != null) {
        //            announcement = tool.announcement;
        //        }
        //        if (tool.isHidden != null) {
        //            isHidden = tool.isHidden;
        //        }
        //        supportsManagedBuild = tool.supportsManagedBuild;
        //
        //        commandLineGenerator = tool.commandLineGenerator;
        //        if (commandLineGenerator == null) {
        //            // only need XML if the generator hasn't been created yet
        //            commandLineGeneratorElement = tool.commandLineGeneratorElement;
        //        }
        //
        //        //        dependencyGenerator = tool.dependencyGenerator;
        //        //        if (dependencyGenerator == null) {
        //        //            // only need XML if the generator hasn't been created yet
        //        //            dependencyGeneratorElement = tool.dependencyGeneratorElement;
        //        //        }
        //
        //        pathconverterElement = tool.pathconverterElement;
        //        optionPathConverter = tool.optionPathConverter;
        //
        //        if (tool.envVarBuildPathList != null)
        //            envVarBuildPathList = new ArrayList<>(tool.envVarBuildPathList);
        //
        //        //		tool.updateScannerInfoSettingsToInputTypes();
        //
        //        //  Clone the children in superclass
        //        super.copyChildren(tool);
        //        //  Clone the children
        //        //        if (tool.inputTypeList != null) {
        //        //            @SuppressWarnings("unchecked")
        //        //            HashMap<String, PathInfoCache> clone = (HashMap<String, PathInfoCache>) tool.discoveredInfoMap.clone();
        //        //            discoveredInfoMap = clone;
        //        //            for (InputType inputType : tool.getInputTypeList()) {
        //        //                PathInfoCache cache = discoveredInfoMap.remove(getTypeKey(inputType));
        //        //                int nnn = ManagedBuildManager.getRandomNumber();
        //        //                String subId;
        //        //                String subName;
        //        //                if (inputType.getSuperClass() != null) {
        //        //                    subId = copyIds ? inputType.id : inputType.getSuperClass().getId() + "." + nnn; //$NON-NLS-1$
        //        //                    subName = inputType.getSuperClass().getName();
        //        //                } else {
        //        //                    subId = copyIds ? inputType.id : inputType.getId() + "." + nnn; //$NON-NLS-1$
        //        //                    subName = inputType.getName();
        //        //                }
        //        //                InputType newInputType = new InputType(this, subId, subName, inputType);
        //        //                addInputType(newInputType);
        //        //                if (cache != null) {
        //        //                    discoveredInfoMap.put(getTypeKey(newInputType), cache);
        //        //                }
        //        //            }
        //        //        }
        //        if (tool.outputTypeList != null) {
        //            for (OutputType outputType : tool.getOutputTypeList()) {
        //                int nnn = ManagedBuildManager.getRandomNumber();
        //                String subId;
        //                String subName;
        //                if (outputType.getSuperClass() != null) {
        //                    subId = copyIds ? outputType.id : outputType.getSuperClass().getId() + "." + nnn; //$NON-NLS-1$
        //                    subName = outputType.getSuperClass().getName();
        //                } else {
        //                    subId = copyIds ? outputType.id : outputType.getId() + "." + nnn; //$NON-NLS-1$
        //                    subName = outputType.getName();
        //                }
        //                OutputType newOutputType = new OutputType(this, subId, subName, outputType);
        //                addOutputType(newOutputType);
        //            }
        //        }
        //
        //        // icon
        //        if (tool.iconPathURL != null) {
        //            iconPathURL = tool.iconPathURL;
        //        }
        //
        //        if (copyIds) {
        //            isDirty = tool.isDirty;
        //            rebuildState = tool.rebuildState;
        //        } else {
        //            setRebuildState(true);
        //        }
    }

    /*
     *  E L E M E N T   A T T R I B U T E   R E A D E R S   A N D   W R I T E R S
     */

    /* (non-Javadoc)
     * Initialize the tool information from the XML element
     * specified in the argument
     *
     * @param element An XML element containing the tool information
     */
    protected void loadFromProject(ICStorageElement element) {

//        loadNameAndID(element);
//
//        // version
//        setVersion(getVersionFromId());
//
//        // isAbstract
//        if (element.getAttribute(IS_ABSTRACT) != null) {
//            String isAbs = element.getAttribute(IS_ABSTRACT);
//            if (isAbs != null) {
//                isAbstract = Boolean.parseBoolean(isAbs);
//            }
//        }
//
//        // Get the 'versionSupported' attribute
//        if (element.getAttribute(VERSIONS_SUPPORTED) != null) {
//            versionsSupported = element.getAttribute(VERSIONS_SUPPORTED);
//        }
//
//        // Get the 'convertToId' id
//        if (element.getAttribute(CONVERT_TO_ID) != null) {
//            convertToId = element.getAttribute(CONVERT_TO_ID);
//        }
//
//        // Get the semicolon separated list of IDs of the error parsers
//        if (element.getAttribute(IToolChain.ERROR_PARSERS) != null) {
//            errorParserIds = element.getAttribute(IToolChain.ERROR_PARSERS);
//        }
//
//        // Get the nature filter
//        if (element.getAttribute(NATURE) != null) {
//            String nature = element.getAttribute(NATURE);
//            if (nature != null) {
//                if ("both".equals(nature)) { //$NON-NLS-1$
//                    natureFilter = FILTER_BOTH;
//                } else if ("cnature".equals(nature)) { //$NON-NLS-1$
//                    natureFilter = FILTER_C;
//                } else if ("ccnature".equals(nature)) { //$NON-NLS-1$
//                    natureFilter = FILTER_CC;
//                } else {
//                    natureFilter = FILTER_BOTH;
//                }
//            }
//        }
//
//        // Get the supported input file extension
//        if (element.getAttribute(ITool.SOURCES) != null) {
//            String inputs = element.getAttribute(ITool.SOURCES);
//            if (inputs != null) {
//                StringTokenizer tokenizer = new StringTokenizer(inputs, DEFAULT_SEPARATOR);
//                while (tokenizer.hasMoreElements()) {
//                    getInputExtensionsList().add(tokenizer.nextToken());
//                }
//            }
//        }
//
//        // Get the interface (header file) extensions
//        if (element.getAttribute(INTERFACE_EXTS) != null) {
//            String headers = element.getAttribute(INTERFACE_EXTS);
//            if (headers != null) {
//                StringTokenizer tokenizer = new StringTokenizer(headers, DEFAULT_SEPARATOR);
//                while (tokenizer.hasMoreElements()) {
//                    getInterfaceExtensionsList().add(tokenizer.nextToken());
//                }
//            }
//        }
//
//        // Get the output extension
//        if (element.getAttribute(ITool.OUTPUTS) != null) {
//            outputExtensions = element.getAttribute(ITool.OUTPUTS);
//        }
//
//        // Get the tool invocation command
//        if (element.getAttribute(ITool.COMMAND) != null) {
//            command = element.getAttribute(ITool.COMMAND);
//        }
//
//        // Get the flag to control output
//        if (element.getAttribute(ITool.OUTPUT_FLAG) != null) {
//            outputFlag = element.getAttribute(ITool.OUTPUT_FLAG);
//        }
//
//        // Get the output prefix
//        if (element.getAttribute(ITool.OUTPUT_PREFIX) != null) {
//            outputPrefix = element.getAttribute(ITool.OUTPUT_PREFIX);
//        }
//
//        // Get command line pattern
//        if (element.getAttribute(ITool.COMMAND_LINE_PATTERN) != null) {
//            commandLinePattern = element.getAttribute(ITool.COMMAND_LINE_PATTERN);
//        }
//
//        // advancedInputCategory
//        if (element.getAttribute(ITool.ADVANCED_INPUT_CATEGORY) != null) {
//            String advInput = element.getAttribute(ITool.ADVANCED_INPUT_CATEGORY);
//            if (advInput != null) {
//                advancedInputCategory = Boolean.parseBoolean(advInput);
//            }
//        }
//
//        // customBuildStep
//        if (element.getAttribute(ITool.CUSTOM_BUILD_STEP) != null) {
//            String cbs = element.getAttribute(ITool.CUSTOM_BUILD_STEP);
//            if (cbs != null) {
//                customBuildStep = Boolean.parseBoolean(cbs);
//            }
//        }
//
//        // Get the announcement text
//        if (element.getAttribute(ITool.ANNOUNCEMENT) != null) {
//            announcement = element.getAttribute(ITool.ANNOUNCEMENT);
//        }
//
//        // Get the tool hidden setting
//        if (element.getAttribute(ITool.IS_HIDDEN) != null) {
//            String hidden = element.getAttribute(ITool.IS_HIDDEN);
//            if (hidden != null) {
//                isHidden = Boolean.valueOf(hidden);
//            }
//        }
//
//        // icon - was saved as URL in string form
//        if (element.getAttribute(IOptionCategory.ICON) != null) {
//            String iconPath = element.getAttribute(IOptionCategory.ICON);
//            try {
//                iconPathURL = new URL(iconPath);
//            } catch (MalformedURLException e) {
//                // Print a warning
//                ManagedBuildManager.outputIconError(iconPath);
//                iconPathURL = null;
//            }
//        }
//
//        scannerConfigDiscoveryProfileId = element.getAttribute(IToolChain.SCANNER_CONFIG_PROFILE_ID);
    }

//    void resolveProjectReferences(boolean onLoad) {
//        if (superClassId != null && superClassId.length() > 0) {
//            ITool tool = null;//TOFIX JABA ManagedBuildManager.getExtensionTool(superClassId);
//            if (tool == null) {
//                Configuration cfg = (Configuration) getParentResourceInfo().getParent();
//                tool = cfg.findToolById(superClassId);
//            }
//            if (tool != null)
//                superClass = tool;
//
//        }
//    }

    private void loadProperties(IConfigurationElement el) {
        supportedProperties = new SupportedProperties(el);
    }

    /*
     *  P A R E N T   A N D   C H I L D   H A N D L I N G
     */

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getParent()
     */
    @Override
    public IBuildObject getParent() {
        return parent;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#setParent(IBuildObject)
     */
    public void setToolParent(IBuildObject newParent) {
        this.parent = newParent;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getTopOptionCategory()
     */
    @Override
    public IOptionCategory getTopOptionCategory() {
        return this;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#createInputType(IInputType, String, String, boolean)
     */
    public IInputType createInputType(IInputType superClass, String Id, String name, boolean isExtensionElement) {
        //        InputType type = superClass == null || superClass.isExtensionElement()
        //                ? new InputType(this, superClass, Id, name, isExtensionElement)
        //                : new InputType(this, Id, name, (InputType) superClass);
        InputType type = new InputType(this, Id, name, (InputType) superClass);
        if (superClass != null) {
            BuildLanguageData data = (BuildLanguageData) typeToDataMap.remove(superClass);
            if (data != null) {
                data.updateInputType(type);
                typeToDataMap.put(type, data);
            }
        }
        addInputType(type);

        return type;
    }


    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getInputTypes()
     */
    @Override
    public IInputType[] getInputTypes() {
        return getAllInputTypes();
    }

    public IInputType[] getAllInputTypes() {
    	return getInputTypeList().toArray(new IInputType[0]);
//        IInputType[] types = null;
//        // Merge our input types with our superclass' input types.
//        if (getSuperClass() != null) {
//            types = ((Tool) getSuperClass()).getAllInputTypes();
//        }
//        // Our options take precedence.
//        Vector<InputType> ourTypes = getInputTypeList();
//        if (types != null) {
//            // Avoid replacing a replacement. See bug 303735
//            boolean[] typesWasReplaced = new boolean[types.length];
//
//            for (int i = 0; i < ourTypes.size(); i++) {
//                IInputType ourType = ourTypes.get(i);
//                int j;
//                for (j = 0; j < types.length; j++) {
//                    IInputType otherTypeToCheck = null;//TOFIX JABA ManagedBuildManager.getExtensionInputType(types[j]);
//                    if (otherTypeToCheck == null)
//                        otherTypeToCheck = types[j];
//
//                    if (ourType.getSuperClass() != null
//                            && ourType.getSuperClass().getId().equals(otherTypeToCheck.getId())
//                            && !typesWasReplaced[j]) {
//                        types[j] = ourType;
//                        typesWasReplaced[j] = true;
//                        break;
//                    }
//                }
//                //  No Match?  Add it.
//                if (j == types.length) {
//                    IInputType[] newTypes = new IInputType[types.length + 1];
//                    boolean[] newTypesWasReplaced = new boolean[types.length + 1];
//                    for (int k = 0; k < types.length; k++) {
//                        newTypes[k] = types[k];
//                        newTypesWasReplaced[k] = typesWasReplaced[k];
//                    }
//                    newTypes[j] = ourType;
//                    types = newTypes;
//                    typesWasReplaced = newTypesWasReplaced;
//                }
//            }
//        } else {
//            types = ourTypes.toArray(new IInputType[ourTypes.size()]);
//        }
//        return types;
    }



    public IInputType getAllInputTypeById(String id) {
    	return getInputTypeMap().get(id);
//        IInputType type = getInputTypeMap().get(id);
//        if (type == null) {
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getAllInputTypeById(id);
//            }
//        }
//
//        return type;
    }




    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getOutputTypes()
     */
    @Override
    public IOutputType[] getOutputTypes() {
        IOutputType[] types = getAllOutputTypes();

        return filterOutputTypes(types);
    }

    public IOutputType[] getAllOutputTypes() {
    	 Vector<OutputType> ourTypes = getOutputTypeList();
    	return ourTypes.toArray(new IOutputType[ourTypes.size()]);
//    	
//        IOutputType[] types = null;
//        // Merge our output types with our superclass' output types.
//        if (getSuperClass() != null) {
//            types = ((Tool) getSuperClass()).getAllOutputTypes();
//        }
//        // Our options take precedence.
//        Vector<OutputType> ourTypes = getOutputTypeList();
//        if (types != null) {
//            for (int i = 0; i < ourTypes.size(); i++) {
//                IOutputType ourType = ourTypes.get(i);
//                int j;
//                for (j = 0; j < types.length; j++) {
//                    if (ourType.getSuperClass() != null && ourType.getSuperClass().getId().equals(types[j].getId())) {
//                        types[j] = ourType;
//                        break;
//                    }
//                }
//                //  No Match?  Add it.
//                if (j == types.length) {
//                    IOutputType[] newTypes = new IOutputType[types.length + 1];
//                    for (int k = 0; k < types.length; k++) {
//                        newTypes[k] = types[k];
//                    }
//                    newTypes[j] = ourType;
//                    types = newTypes;
//                }
//            }
//        } else {
//            types = ourTypes.toArray(new IOutputType[ourTypes.size()]);
//        }
//        return types;
    }

    private IOutputType[] filterOutputTypes(IOutputType types[]) {
        if (isExtensionTool || types.length == 0)
            return types;

        List<OutputType> list = new ArrayList<>(types.length);
        for (IOutputType itype : types) {
            OutputType type = (OutputType) itype;
            if (type.isEnabled(this))
                list.add(type);
        }

        return list.toArray(new OutputType[list.size()]);
    }

    private boolean hasOutputTypes() {
        Vector<OutputType> ourTypes = getOutputTypeList();
        if (ourTypes.size() > 0)
            return true;
        return false;
    }

    @Override
    public IOutputType getOutputTypeById(String id) {
        OutputType type = (OutputType) getAllOutputTypeById(id);

        if (isExtensionTool || type == null || type.isEnabled(this))
            return type;
        return null;
    }

    public IOutputType getAllOutputTypeById(String id) {
        IOutputType type = getOutputTypeMap().get(id);
//        if (type == null) {
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getAllOutputTypeById(id);
//            }
//        }
        return type;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.IOptionCategory#getOwner()
     */
    @Override
    public IOptionCategory getOwner() {
        return null;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.IOptionCategory#getIconPath()
     */
    @Override
    public URL getIconPath() {
//        if (iconPathURL == null && getSuperClass() != null) {
//            return getSuperClass().getTopOptionCategory().getIconPath();
//        }
        return iconPathURL;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.IOptionCategory#getOptions()
     */
    //@Override
    public Object[][] getOptions(IConfiguration configuration) {
        // Find the child of the configuration that represents the same tool.
        // It could be the tool itself, or a "sub-class" of the tool.
        if (configuration != null) {
           List< ITool> tools = configuration.getTools();
            return getOptions(tools);
        } else {
            return getAllOptions(this);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.IOptionCategory#getOptions()
     */
    //@Override
    public Object[][] getOptions(IResourceConfiguration resConfig) {
        List<ITool> tools = resConfig.getTools();
        return getOptions(tools);
    }

    public Object[][] getOptions(IResourceInfo resConfig) {
        List<ITool> tools = resConfig.getTools();
        return getOptions(tools);
    }

    private Object[][] getOptions(List<ITool> tools) {
    	return getAllOptions(this);
//        ITool catTool = this;
//        ITool tool = null;
//        for (ITool curTool : tools) {
//            ITool superTool = curTool;
//            do {
//                if (catTool == superTool) {
//                    tool = curTool;
//                    break;
//                }
//            } while ((superTool = superTool.getSuperClass()) != null);
//            if (tool != null)
//                break;
//        }
//        // Get all of the tool's options and see which ones are part of
//        // this category.
//        if (tool == null)
//            return null;
//
//        return getAllOptions(tool);
    }

    private Object[][] getAllOptions(ITool tool) {
        List< IOption> allOptions = tool.getOptions();
        Object[][] retOptions = new Object[allOptions.size()][2];
        int index = 0;
        for (IOption option : allOptions) {
            IOptionCategory optCat = option.getCategory();
            if (optCat instanceof ITool) {
                //  Determine if the category is this tool or a superclass
                    if (optCat == this) {
                        retOptions[index] = new Object[2];
                        retOptions[index][0] = tool;
                        retOptions[index][1] = option;
                        index++;
                    }
                }
            }

        return retOptions;
//       List< IOption> allOptions = tool.getOptions();
//        Object[][] retOptions = new Object[allOptions.size()][2];
//        int index = 0;
//        for (IOption option : allOptions) {
//            IOptionCategory optCat = option.getCategory();
//            if (optCat instanceof ITool) {
//                //  Determine if the category is this tool or a superclass
//                ITool current = this;
//                boolean match = false;
//                do {
//                    if (optCat == current) {
//                        match = true;
//                        break;
//                    }
//                } while ((current = current.getSuperClass()) != null);
//                if (match) {
//                    retOptions[index] = new Object[2];
//                    retOptions[index][0] = tool;
//                    retOptions[index][1] = option;
//                    index++;
//                }
//            }
//        }
//
//        return retOptions;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.IOptionCategory#getTool()
     */
    //@Override
    public ITool getTool() {
        return this;
    }



    /* (non-Javadoc)
     * Memory-safe way to access the list of input types
     */
    private Vector<InputType> getInputTypeList() {
        if (inputTypeList == null) {
            inputTypeList = new Vector<>();
        }
        return inputTypeList;
    }

    /* (non-Javadoc)
     * Memory-safe way to access the list of IDs to input types
     */
    private Map<String, InputType> getInputTypeMap() {
        if (inputTypeMap == null) {
            inputTypeMap = new HashMap<>();
        }
        return inputTypeMap;
    }

    public void addInputType(InputType type) {
        getInputTypeList().add(type);
        getInputTypeMap().put(type.getId(), type);
    }

    /* (non-Javadoc)
     * Memory-safe way to access the list of output types
     */
    private Vector<OutputType> getOutputTypeList() {
        if (outputTypeList == null) {
            outputTypeList = new Vector<>();
        }
        return outputTypeList;
    }

    /* (non-Javadoc)
     * Memory-safe way to access the list of IDs to output types
     */
    private Map<String, OutputType> getOutputTypeMap() {
        if (outputTypeMap == null) {
            outputTypeMap = new HashMap<>();
        }
        return outputTypeMap;
    }

    public void addOutputType(OutputType type) {
        getOutputTypeList().add(type);
        getOutputTypeMap().put(type.getId(), type);
    }

    /*
     *  M O D E L   A T T R I B U T E   A C C E S S O R S
     */



    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getName()
     */
    @Override
    public String getName() {
        return name ;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#isAbstract()
     */
    @Override
    public boolean isAbstract() {
        if (isAbstract != null) {
            return isAbstract.booleanValue();
        } else {
            return false; // Note: no inheritance from superClass
        }
    }




    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getErrorParserIds()
     */
    @Override
    public String getErrorParserIds() {
        return errorParserIds;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getErrorParserList()
     */
    @Override
    public String[] getErrorParserList() {
        String parserIDs = getErrorParserIds();
        String[] errorParsers;
        if (parserIDs != null) {
            // Check for an empty string
            if (parserIDs.length() == 0) {
                errorParsers = new String[0];
            } else {
                StringTokenizer tok = new StringTokenizer(parserIDs, ";"); //$NON-NLS-1$
                List<String> list = new ArrayList<>(tok.countTokens());
                while (tok.hasMoreElements()) {
                    list.add(tok.nextToken());
                }
                String[] strArr = { "" }; //$NON-NLS-1$
                errorParsers = list.toArray(strArr);
            }
        } else {
            errorParsers = new String[0];
        }
        return errorParsers;
    }

    public Set<String> contributeErrorParsers(Set<String> set) {
        if (getErrorParserIds() != null) {
            if (set == null)
                set = new HashSet<>();
            String ids[] = getErrorParserList();
            if (ids.length != 0)
                set.addAll(Arrays.asList(ids));
        }
        return set;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getInputExtensions()
     * @deprecated
     */
    //@Override
    public List<String> getInputExtensions() {
        String[] exts = getPrimaryInputExtensions();
        List<String> extList = new ArrayList<>();
        for (String ext : exts) {
            extList.add(ext);
        }
        return extList;
    }

    private List<String> getInputExtensionsAttribute() {
//        if ((inputExtensions == null) || (inputExtensions.size() == 0)) {
//            // If I have a superClass, ask it
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getInputExtensionsAttribute();
//            } else {
//                inputExtensions = new ArrayList<>();
//            }
//        }
        return inputExtensions;
    }

    private List<String> getInputExtensionsList() {
        if (inputExtensions == null) {
            inputExtensions = new ArrayList<>();
        }
        return inputExtensions;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getDefaultInputExtension()
     */
    @Override
    public String getDefaultInputExtension() {
        // Find the primary input type
        IInputType type = getPrimaryInputType();
        if (type != null) {
            String[] exts = type.getSourceExtensions(this);
            // Use the first entry in the list
            if (exts.length > 0)
                return exts[0];
        }
        // If none, use the input extensions specified for the Tool (backwards compatibility)
        List<String> extsList = getInputExtensionsAttribute();
        // Use the first entry in the list
        if (extsList != null && extsList.size() > 0)
            return extsList.get(0);
        return EMPTY_STRING;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getPrimaryInputExtensions()
     */
    @Override
    public String[] getPrimaryInputExtensions() {
        IInputType type = getPrimaryInputType();
        if (type != null) {
            String[] exts = type.getSourceExtensions(this);
            // Use the first entry in the list
            if (exts.length > 0)
                return exts;
        }
        // If none, use the input extensions specified for the Tool (backwards compatibility)
        List<String> extsList = getInputExtensionsAttribute();
        // Use the first entry in the list
        if (extsList != null && extsList.size() > 0) {
            return extsList.toArray(new String[extsList.size()]);
        }
        return EMPTY_STRING_ARRAY;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getAllInputExtensions()
     */
    @Override
    public String[] getAllInputExtensions() {
        return getAllInputExtensions(getProject());
    }

    public String[] getAllInputExtensions(IProject project) {
        IInputType[] types = getInputTypes();
        if (types != null && types.length > 0) {
            List<String> allExts = new ArrayList<>();
            for (IInputType type : types) {
                String[] exts = ((InputType) type).getSourceExtensions(this, project);
                for (String ext : exts) {
                    allExts.add(ext);
                }
            }
            if (allExts.size() > 0) {
                return allExts.toArray(new String[allExts.size()]);
            }
        }
        // If none, use the input extensions specified for the Tool (backwards compatibility)
        List<String> extsList = getInputExtensionsAttribute();
        if (extsList != null && extsList.size() > 0) {
            return extsList.toArray(new String[extsList.size()]);
        }
        return EMPTY_STRING_ARRAY;
    }

    @Override
    public IInputType getPrimaryInputType() {
        //TOFIX JABA 
        //Primary input types no longer exists
        return null;
        //        IInputType type = null;
        //        IInputType[] types = getInputTypes();
        //        if (types != null && types.length > 0) {
        //            for (int i = 0; i < types.length; i++) {
        //                if (i == 0)
        //                    type = types[0];
        //                if (types[i].getPrimaryInput() == true) {
        //                    type = types[i];
        //                    break;
        //                }
        //            }
        //        }
        //        return type;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getAdditionalDependencies()
     */
    @Override
    public IPath[] getAdditionalDependencies() {
        //TODO JABA dead code removal
        return null;
        //        List<IPath> allDeps = new ArrayList<>();
        //        IInputType[] types = getInputTypes();
        //        for (IInputType type : types) {
        //            if (type != getPrimaryInputType()) {
        //                if (type.getOptionId() != null) {
        //                    IOption option = getOptionBySuperClassId(type.getOptionId());
        //                    if (option != null) {
        //                        try {
        //                            List<IPath> inputs = new ArrayList<>();
        //                            int optType = option.getValueType();
        //                            if (optType == IOption.STRING) {
        //                                inputs.add(Path.fromOSString(option.getStringValue()));
        //                            } else if (optType == IOption.STRING_LIST || optType == IOption.LIBRARIES
        //                                    || optType == IOption.OBJECTS || optType == IOption.INCLUDE_FILES
        //                                    || optType == IOption.LIBRARY_PATHS || optType == IOption.LIBRARY_FILES
        //                                    || optType == IOption.MACRO_FILES) {
        //                                @SuppressWarnings("unchecked")
        //                                List<String> inputNames = (List<String>) option.getValue();
        //                                filterValues(optType, inputNames);
        //                                for (String s : inputNames)
        //                                    inputs.add(Path.fromOSString(s));
        //                            }
        //                            allDeps.addAll(inputs);
        //                        } catch (BuildException ex) {
        //                        }
        //                    }
        //                } else if (type.getBuildVariable() != null && type.getBuildVariable().length() > 0) {
        //                    allDeps.add(Path.fromOSString("$(" + type.getBuildVariable() + ")")); //$NON-NLS-1$ //$NON-NLS-2$
        //                }
        //            }
        //        }
        //        return allDeps.toArray(new IPath[allDeps.size()]);
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getAllDependencyExtensions()
     */
    @Override
    public String[] getAllDependencyExtensions() {
        IInputType[] types = getInputTypes();
        if (types != null && types.length > 0) {
            List<String> allExts = new ArrayList<>();
            for (IInputType t : types)
                for (String s : t.getDependencyExtensions(this))
                    allExts.add(s);

            if (allExts.size() > 0)
                return allExts.toArray(new String[allExts.size()]);
        }
        // If none, use the header extensions specified for the Tool (backwards compatibility)
        List<String> extsList = getHeaderExtensionsAttribute();
        if (extsList != null && extsList.size() > 0) {
            return extsList.toArray(new String[extsList.size()]);
        }
        return EMPTY_STRING_ARRAY;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getInterfaceExtension()
     * @deprecated
     */
    //@Override
    public List<String> getInterfaceExtensions() {
        return getHeaderExtensionsAttribute();
    }

    private List<String> getHeaderExtensionsAttribute() {
//        if (interfaceExtensions == null || interfaceExtensions.size() == 0) {
//            // If I have a superClass, ask it
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getHeaderExtensionsAttribute();
//            } else {
//                if (interfaceExtensions == null) {
//                    interfaceExtensions = new ArrayList<>();
//                }
//            }
//        }
        return interfaceExtensions;
    }

    private List<String> getInterfaceExtensionsList() {
        if (interfaceExtensions == null) {
            interfaceExtensions = new ArrayList<>();
        }
        return interfaceExtensions;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getOutputFlag()
     */
    @Override
    public String getOutputFlag() {
//        if (outputFlag == null) {
//            // If I have a superClass, ask it
//            if (getSuperClass() != null) {
//                return getSuperClass().getOutputFlag();
//            } else {
//                return EMPTY_STRING;
//            }
//        }
        return outputFlag;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getToolCommand()
     */
    @Override
    public String getToolCommand() {
//        if (command == null) {
//            // If I have a superClass, ask it
//            if (getSuperClass() != null) {
//                return getSuperClass().getToolCommand();
//            } else {
//                return EMPTY_STRING;
//            }
//        }
        return command;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getCommandLinePattern()
     */
    @Override
    public String getCommandLinePattern() {
//        if (commandLinePattern == null) {
//            if (getSuperClass() != null) {
//                return getSuperClass().getCommandLinePattern();
//            } else {
//                if (getCustomBuildStep()) {
//                    return DEFAULT_CBS_PATTERN; // Default pattern
//                } else {
//                    return DEFAULT_PATTERN; // Default pattern
//                }
//            }
//        }
        return commandLinePattern;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getAdvancedInputCategory()
     */
    @Override
    public boolean getAdvancedInputCategory() {
//        if (advancedInputCategory == null) {
//            if (getSuperClass() != null) {
//                return getSuperClass().getAdvancedInputCategory();
//            } else {
//                return false; // default is false
//            }
//        }
        return advancedInputCategory.booleanValue();
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getCustomBuildStep()
     */
    @Override
    public boolean getCustomBuildStep() {
//        if (customBuildStep == null) {
//            if (getSuperClass() != null) {
//                return getSuperClass().getCustomBuildStep();
//            } else {
//                return false; // default is false
//            }
//        }
        return customBuildStep.booleanValue();
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getAnnouncement()
     */
    @Override
    public String getAnnouncement() {
        String an = getAnnouncementAttribute();
        if (an == null) {
            an = Tool_default_announcement + WHITESPACE + getName(); // + "(" + getId() + ")";
        }
        return an;
    }

    public String getAnnouncementAttribute() {
//        if (announcement == null) {
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getAnnouncementAttribute();
//            }
//            return null;
//        }
        return announcement;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getCommandLineGeneratorElement()
     */
    public IConfigurationElement getCommandLineGeneratorElement() {
//        if (commandLineGeneratorElement == null) {
//            if (getSuperClass() != null) {
//                return ((Tool) getSuperClass()).getCommandLineGeneratorElement();
//            }
//        }
        return commandLineGeneratorElement;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#setCommandLineGeneratorElement(String)
     */
    public void setCommandLineGeneratorElement(IConfigurationElement element) {
        commandLineGeneratorElement = element;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getCommandLineGenerator()
     */
    @Override
    public IManagedCommandLineGenerator getCommandLineGenerator() {
        return null;
        //        if (commandLineGenerator != null) {
        //            return commandLineGenerator;
        //        }
        //        IConfigurationElement element = getCommandLineGeneratorElement();
        //        if (element != null) {
        //            try {
        //                if (element.getAttribute(COMMAND_LINE_GENERATOR) != null) {
        //                    commandLineGenerator = (IManagedCommandLineGenerator) element
        //                            .createExecutableExtension(COMMAND_LINE_GENERATOR);
        //                    commandLineGeneratorElement = null; // no longer needed now that we've created one
        //                    return commandLineGenerator;
        //                }
        //            } catch (CoreException e) {
        //            }
        //        }
        //        return new ManagedCommandLineGenerator();
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getDependencyGeneratorElement()
     * @deprecated
     */
    //    public IConfigurationElement getDependencyGeneratorElement() {
    //        //  First try the primary InputType
    //        IInputType type = getPrimaryInputType();
    //        if (type != null) {
    //            IConfigurationElement primary = ((InputType) type).getDependencyGeneratorElement();
    //            if (primary != null)
    //                return primary;
    //        }
    //
    //        //  If not found, use the deprecated attribute
    //        return getToolDependencyGeneratorElement();
    //    }

    //    /* (non-Javadoc)
    //     * @see org.eclipse.cdt.managedbuilder.core.ITool#getDependencyGeneratorElementForExtension()
    //     */
    //    public IConfigurationElement getDependencyGeneratorElementForExtension(String sourceExt) {
    //        IInputType[] types = getInputTypes();
    //        if (types != null) {
    //            for (IInputType type : types) {
    //                if (type.isSourceExtension(this, sourceExt)) {
    //                    return ((InputType) type).getDependencyGeneratorElement();
    //                }
    //            }
    //        }
    //
    //        //  If not found, use the deprecated attribute
    //        return getToolDependencyGeneratorElement();
    //    }

    //    private IConfigurationElement getToolDependencyGeneratorElement() {
    //        if (dependencyGeneratorElement == null) {
    //            if (getSuperClass() != null) {
    //                return ((Tool) getSuperClass()).getToolDependencyGeneratorElement();
    //            }
    //        }
    //        return dependencyGeneratorElement;
    //    }

    //	/* (non-Javadoc)
    //	 * @see org.eclipse.cdt.managedbuilder.core.ITool#setDependencyGeneratorElement(String)
    //	 * @deprecated
    //	 */
    //	private void setDependencyGeneratorElement(IConfigurationElement element) {
    //		dependencyGeneratorElement = element;
    //		setDirty(true);
    //	}

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getDependencyGenerator()
     * @deprecated
     */
    //    @Override
    //    public IManagedDependencyGenerator getDependencyGenerator() {
    //        if (dependencyGenerator != null) {
    //            if (dependencyGenerator instanceof IManagedDependencyGenerator)
    //                return (IManagedDependencyGenerator) dependencyGenerator;
    //            else
    //                return null;
    //        }
    //        IConfigurationElement element = getDependencyGeneratorElement();
    //        if (element != null) {
    //            try {
    //                if (element.getAttribute(DEP_CALC_ID) != null) {
    //                    dependencyGenerator = (IManagedDependencyGeneratorType) element
    //                            .createExecutableExtension(DEP_CALC_ID);
    //                    if (dependencyGenerator != null) {
    //                        if (dependencyGenerator instanceof IManagedDependencyGenerator) {
    //                            dependencyGeneratorElement = null; // no longer needed now that we've created one
    //                            return (IManagedDependencyGenerator) dependencyGenerator;
    //                        } else
    //                            return null;
    //                    }
    //                }
    //            } catch (CoreException e) {
    //            }
    //        }
    //        return null;
    //    }

    //    /* (non-Javadoc)
    //     * @see org.eclipse.cdt.managedbuilder.core.ITool#getDependencyGeneratorForExtension()
    //     */
    //    @Override
    //    public IManagedDependencyGeneratorType getDependencyGeneratorForExtension(String sourceExt) {
    //        if (dependencyGenerator != null) {
    //            return dependencyGenerator;
    //        }
    //        IConfigurationElement element = getDependencyGeneratorElementForExtension(sourceExt);
    //        if (element != null) {
    //            try {
    //                if (element.getAttribute(DEP_CALC_ID) != null) {
    //                    dependencyGenerator = (IManagedDependencyGeneratorType) element
    //                            .createExecutableExtension(DEP_CALC_ID);
    //                    return dependencyGenerator;
    //                }
    //            } catch (CoreException e) {
    //            }
    //        }
    //        return null;
    //    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getNatureFilter()
     */
    @Override
    public int getNatureFilter() {
//        if (natureFilter == null) {
//            // If I have a superClass, ask it
//            if (getSuperClass() != null) {
//                return getSuperClass().getNatureFilter();
//            } else {
//                return FILTER_BOTH;
//            }
//        }
        return natureFilter.intValue();
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getOutputExtensions()
     * @deprecated
     */
    //@Override
    public String[] getOutputExtensions() {
        return getOutputsAttribute();
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getOutputsAttribute()
     */
    @Override
    public String[] getOutputsAttribute() {
//        // TODO:  Why is this treated differently than inputExtensions?
//        if (outputExtensions == null) {
//            if (getSuperClass() != null) {
//                return getSuperClass().getOutputsAttribute();
//            } else {
//                return null;
//            }
//        }
        return outputExtensions.split(DEFAULT_SEPARATOR);
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#getOutputType(java.lang.String)
     */
    @Override
    public IOutputType getOutputType(String outputExtension) {
        IOutputType type = null;
        IOutputType[] types = getOutputTypes();
        if (types != null && types.length > 0) {
            for (IOutputType t : types) {
                if (t.isOutputExtension(this, outputExtension)) {
                    type = t;
                    break;
                }
            }
        }
        return type;
    }






    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getCommandFlags()
     */
    //@Override
    public String[] getCommandFlags() throws BuildException {
        return getToolCommandFlags(null, null);
    }

    /**
     * This method used internally by the Tool to obtain the command flags with the
     * build macros resolved,
     * but could be also used by other MBS components to adjust the tool flags
     * resolution
     * behavior by passing the method some custom macro substitutor
     *
     * @return the command flags with the build macros resolved
     */
    public String[] getToolCommandFlags(IPath inputFileLocation, IPath outputFileLocation,
            SupplierBasedCdtVariableSubstitutor macroSubstitutor, IMacroContextInfoProvider provider) {
        List<IOption> opts = getOptions();
        ArrayList<String> flags = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (IOption option : opts) {
            if (option == null)
                continue;
            sb.setLength(0);

            // check to see if the option has an applicability calculator
            IOptionApplicability applicabilityCalculator = option.getApplicabilityCalculator();
            IOptionCategory cat = option.getCategory();
            IOptionCategoryApplicability catApplicabilityCalculator = cat.getApplicabilityCalculator();

            IBuildObject config = null;
            IBuildObject parent = getParent();
            if (parent instanceof IResourceConfiguration) {
                config = parent;
            } else if (parent instanceof IToolChain) {
                config = ((IToolChain) parent).getParent();
            }

            if ((catApplicabilityCalculator == null
                    || catApplicabilityCalculator.isOptionCategoryVisible(config, this, cat))
                    && (applicabilityCalculator == null
                            || applicabilityCalculator.isOptionUsedInCommandLine(config, this, option))) {

                // update option in case when its value changed.
                // This code is added to fix bug #219684 and
                // avoid using "getOptionToSet()"
                //                if (applicabilityCalculator != null
                //                        && !(applicabilityCalculator instanceof BooleanExpressionApplicabilityCalculator)) {
                //                    if (option.getSuperClass() != null)
                //                        option = getOptionBySuperClassId(option.getSuperClass().getId());
                //                    // bug #405904 - if the option is an extension element (first time we build),
                //                    // use the option id as a superclass id, otherwise we won't find the option we may have just
                //                    // set and will end up with the default setting
                //                    else if (option.isExtensionElement())
                //                        option = getOptionBySuperClassId(option.getId());
                //                    else
                //                        option = getOptionById(option.getId());
                //                }

                try {
                    boolean generateDefaultCommand = true;
                    IOptionCommandGenerator commandGenerator = option.getCommandGenerator();
                    if (commandGenerator != null) {
                        switch (option.getValueType()) {
                        case IOption.BOOLEAN:
                        case IOption.ENUMERATED:
                        case IOption.TREE:
                        case IOption.STRING:
                        case IOption.STRING_LIST:
                        case IOption.INCLUDE_FILES:
                        case IOption.INCLUDE_PATH:
                        case IOption.LIBRARY_PATHS:
                        case IOption.LIBRARY_FILES:
                        case IOption.MACRO_FILES:
                        case IOption.UNDEF_INCLUDE_FILES:
                        case IOption.UNDEF_INCLUDE_PATH:
                        case IOption.UNDEF_LIBRARY_PATHS:
                        case IOption.UNDEF_LIBRARY_FILES:
                        case IOption.UNDEF_MACRO_FILES:
                        case IOption.PREPROCESSOR_SYMBOLS:
                        case IOption.UNDEF_PREPROCESSOR_SYMBOLS:
                            IMacroContextInfo info = provider.getMacroContextInfo(BuildMacroProvider.CONTEXT_FILE,
                                    new FileContextData(inputFileLocation, outputFileLocation, option, this));
                            if (info != null) {
                                macroSubstitutor.setMacroContextInfo(info);
                                String command = commandGenerator.generateCommand(option, macroSubstitutor);
                                if (command != null) {
                                    sb.append(command);
                                    generateDefaultCommand = false;
                                }
                            }
                            break;
                        default:
                            break;
                        }
                    }
                    if (generateDefaultCommand) {
                        switch (option.getValueType()) {
                        case IOption.BOOLEAN:
                            String boolCmd;
                            if (option.getBooleanValue()) {
                                boolCmd = option.getCommand();
                            } else {
                                // Note: getCommandFalse is new with CDT 2.0
                                boolCmd = option.getCommandFalse();
                            }
                            if (boolCmd != null && boolCmd.length() > 0) {
                                sb.append(boolCmd);
                            }
                            break;

                        case IOption.ENUMERATED:
                            String enumVal = option.getEnumCommand(option.getSelectedEnum());
                            if (enumVal.length() > 0) {
                                sb.append(enumVal);
                            }
                            break;

                        case IOption.TREE:
                            String treeVal = option.getCommand(option.getStringValue());
                            if (treeVal.length() > 0) {
                                sb.append(treeVal);
                            }
                            break;

                        case IOption.STRING: {
                            String strCmd = option.getCommand();
                            String val = option.getStringValue();
                            IMacroContextInfo info = provider.getMacroContextInfo(IBuildMacroProvider.CONTEXT_FILE,
                                    new FileContextData(inputFileLocation, outputFileLocation, option, this));
                            if (info != null) {
                                macroSubstitutor.setMacroContextInfo(info);
                                if (val.length() > 0
                                        && (val = CdtVariableResolver.resolveToString(val, macroSubstitutor))
                                                .length() > 0) {
                                    sb.append(evaluateCommand(strCmd, val));
                                }
                            }
                        }
                            break;

                        case IOption.STRING_LIST:
                        case IOption.INCLUDE_FILES:
                        case IOption.INCLUDE_PATH:
                        case IOption.LIBRARY_PATHS:
                        case IOption.LIBRARY_FILES:
                        case IOption.MACRO_FILES:
                        case IOption.UNDEF_INCLUDE_FILES:
                        case IOption.UNDEF_INCLUDE_PATH:
                        case IOption.UNDEF_LIBRARY_PATHS:
                        case IOption.UNDEF_LIBRARY_FILES:
                        case IOption.UNDEF_MACRO_FILES: {
                            String listCmd = option.getCommand();
                            IMacroContextInfo info = provider.getMacroContextInfo(IBuildMacroProvider.CONTEXT_FILE,
                                    new FileContextData(inputFileLocation, outputFileLocation, option, this));
                            if (info != null) {
                                macroSubstitutor.setMacroContextInfo(info);
                                String[] list = CdtVariableResolver.resolveStringListValues(
                                        option.getBasicStringListValue(), macroSubstitutor, true);
                                if (list != null) {
                                    for (String temp : list) {
                                        if (temp.length() > 0 && !temp.equals(EMPTY_QUOTED_STRING))
                                            sb.append(evaluateCommand(listCmd, temp)).append(WHITE_SPACE);
                                    }
                                }
                            }
                        }
                            break;

                        case IOption.PREPROCESSOR_SYMBOLS:
                        case IOption.UNDEF_PREPROCESSOR_SYMBOLS: {
                            String defCmd = option.getCommand();
                            IMacroContextInfo info = provider.getMacroContextInfo(IBuildMacroProvider.CONTEXT_FILE,
                                    new FileContextData(inputFileLocation, outputFileLocation, option, this));
                            if (info != null) {
                                macroSubstitutor.setMacroContextInfo(info);
                                String[] symbols = CdtVariableResolver.resolveStringListValues(
                                        option.getBasicStringListValue(), macroSubstitutor, true);
                                if (symbols != null) {
                                    for (String temp : symbols) {
                                        if (temp.length() > 0)
                                            sb.append(evaluateCommand(defCmd, temp) + WHITE_SPACE);
                                    }
                                }
                            }
                        }
                            break;

                        default:
                            break;
                        }
                    }

                    if (sb.toString().trim().length() > 0)
                        flags.add(sb.toString().trim());

                } catch (BuildException e) {
                    // Bug 315187 one broken option shouldn't cascade to all other options breaking the build...
                    Status s = new Status(IStatus.ERROR, Activator.getId(),
                            MessageFormat.format(Tool_Problem_Discovering_Args_For_Option, option, option.getId()), e);
                    Activator.log(new CoreException(s));
                } catch (CdtVariableException e) {
                    Status s = new Status(IStatus.ERROR, Activator.getId(),
                            MessageFormat.format(Tool_Problem_Discovering_Args_For_Option, option, option.getId()), e);
                    Activator.log(new CoreException(s));
                }
            }
        }
        String[] f = new String[flags.size()];
        return flags.toArray(f);
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getToolCommandFlags(org.eclipse.core.runtime.IPath, org.eclipse.core.runtime.IPath)
     */
    @Override
    public String[] getToolCommandFlags(IPath inputFileLocation, IPath outputFileLocation) throws BuildException {
        SupplierBasedCdtVariableSubstitutor macroSubstitutor = new BuildfileMacroSubstitutor(null, EMPTY_STRING,
                WHITE_SPACE);
        return getToolCommandFlags(inputFileLocation, outputFileLocation, macroSubstitutor,
                BuildMacroProvider.getDefault());
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#isHeaderFile(java.lang.String)
     */
    @Override
    public boolean isHeaderFile(String ext) {
        if (ext == null) {
            return false;
        }
        String[] exts = getAllDependencyExtensions();
        for (String dep : exts) {
            if (ext.equals(dep))
                return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#producesFileType(java.lang.String)
     */
    @Override
    public boolean producesFileType(String extension) {
        if (extension == null) {
            return false;
        }
        //  Check the output-types first
        if (getOutputType(extension) != null) {
            return true;
        }
        //  If there are no OutputTypes, check the attribute
        if (!hasOutputTypes()) {
            String[] exts = getOutputsAttribute();
            if (exts != null) {
                for (String ext : exts) {
                    if (ext.equals(extension))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * @return the pathconverterElement
     */
    public IConfigurationElement getPathconverterElement() {
        return pathconverterElement;
    }

    @Override
    public IOptionPathConverter getOptionPathConverter() {
        // Use existing converter
        if (optionPathConverter != null) {
            return optionPathConverter;
        }
        if (optionPathConverter == null) {
            // If there is not yet a optionPathConverter try to construct from configuration element
            IConfigurationElement element = getPathconverterElement();
            if (element != null) {
                try {
                    if (element.getAttribute(ITool.OPTIONPATHCONVERTER) != null) {
                        optionPathConverter = (IOptionPathConverter) element
                                .createExecutableExtension(ITool.OPTIONPATHCONVERTER);
                    }
                } catch (CoreException e) {
                }
            }
//            if (optionPathConverter == null) {
//                // If there is still no optionPathConverter, ask superclass of this tool whether it has a converter
//                if (getSuperClass() != null) {
//                    ITool superTool = getSuperClass();
//                    optionPathConverter = superTool.getOptionPathConverter();
//                }
//            }
            // If there is still no converter, ask the toolchain for a
            // global converter
            if ((optionPathConverter == null) && (getParent() instanceof IResourceConfiguration)) {
                // The tool belongs to a resource configuration
                IResourceConfiguration resourceConfiguration = (IResourceConfiguration) getParent();
                IConfiguration configuration = resourceConfiguration.getParent();
                if (null != configuration) {
                    IToolChain toolchain = configuration.getToolChain();
                    optionPathConverter = toolchain.getOptionPathConverter();
                }
            }
            if ((optionPathConverter == null) && (getParent() instanceof IToolChain)) {
                // The tool belongs to a toolchain
                IToolChain toolchain = (IToolChain) getParent();
                optionPathConverter = toolchain.getOptionPathConverter();
            }
        }

        pathconverterElement = null; // discard now that we've created one
        return optionPathConverter;
    }

    /*
     *  O B J E C T   S T A T E   M A I N T E N A N C E
     */

    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#isExtensionElement()
     */
    @Override
    public boolean isExtensionElement() {
        return isExtensionTool;
    }

    /**
     * Look for ${VALUE} in the command string
     */
    public String evaluateCommand(String command, String values) {
        final int DOLLAR_VALUE_LENGTH = 8;

        if (command == null)
            return values.trim();

        String ret = command;
        boolean found = false;
        int start = 0;
        int index;
        int len;
        while ((index = ret.indexOf("${", start)) >= 0 && //$NON-NLS-1$
                (len = ret.length()) >= index + DOLLAR_VALUE_LENGTH) {
            start = index;
            index = index + 2;
            int ch = ret.charAt(index);
            if (ch == 'v' || ch == 'V') {
                index++;
                ch = ret.charAt(index);
                if (ch == 'a' || ch == 'A') {
                    index++;
                    ch = ret.charAt(index);
                    if (ch == 'l' || ch == 'L') {
                        index++;
                        ch = ret.charAt(index);
                        if (ch == 'u' || ch == 'U') {
                            index++;
                            ch = ret.charAt(index);
                            if (ch == 'e' || ch == 'E') {
                                index++;
                                ch = ret.charAt(index);
                                if (ch == '}') {
                                    String temp = ""; //$NON-NLS-1$
                                    index++;
                                    found = true;
                                    if (start > 0) {
                                        temp = ret.substring(0, start);
                                    }
                                    temp = temp.concat(values.trim());
                                    if (len > index) {
                                        start = temp.length();
                                        ret = temp.concat(ret.substring(index));
                                        index = start;
                                    } else {
                                        ret = temp;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            start = index;
        }
        if (found)
            return ret.trim();
        return (command + values).trim();
    }




    /* (non-Javadoc)
     * @see org.eclipse.cdt.managedbuilder.core.ITool#getEnvVarBuildPaths()
     */
    @Override
    public IEnvVarBuildPath[] getEnvVarBuildPaths() {
//        if (envVarBuildPathList != null) {
            return envVarBuildPathList.toArray(new IEnvVarBuildPath[envVarBuildPathList.size()]);
//        } else if (getSuperClass() != null)
//            return getSuperClass().getEnvVarBuildPaths();
//        return null;
    }

    private void addEnvVarBuildPath(IEnvVarBuildPath path) {
        if (path == null)
            return;
        if (envVarBuildPathList == null)
            envVarBuildPathList = new ArrayList<>();

        envVarBuildPathList.add(path);
    }


    public IConfigurationElement getPreviousMbsVersionConversionElement() {
        return previousMbsVersionConversionElement;
    }

    public IConfigurationElement getCurrentMbsVersionConversionElement() {
        return currentMbsVersionConversionElement;
    }

    public IProject getProject() {
        IBuildObject toolParent = getParent();
        if (toolParent != null) {
            if (toolParent instanceof IToolChain) {
                IConfiguration config = ((IToolChain) toolParent).getParent();
                if (config == null)
                    return null;
                return (IProject) config.getOwner();
            } else if (toolParent instanceof IResourceConfiguration) {
                return (IProject) ((IResourceConfiguration) toolParent).getOwner();
            }
        }
        return null;
    }

    public String[] getContentTypeFileSpecs(IContentType type) {
        return getContentTypeFileSpecs(type, getProject());
    }

    public String[] getContentTypeFileSpecs(IContentType type, IProject project) {
        String[] globalSpecs = type.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
        IContentTypeSettings settings = null;
        //		IProject project = getProject();
        if (project != null) {
            IScopeContext projectScope = new ProjectScope(project);
            try {
                settings = type.getSettings(projectScope);
            } catch (Exception e) {
            }
            if (settings != null) {
                String[] specs = settings.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
                if (specs.length > 0) {
                    int total = globalSpecs.length + specs.length;
                    String[] projSpecs = new String[total];
                    int i = 0;
                    for (String spec : specs) {
                        projSpecs[i] = spec;
                        i++;
                    }
                    for (String spec : globalSpecs) {
                        projSpecs[i] = spec;
                        i++;
                    }
                    return projSpecs;
                }
            }
        }
        return globalSpecs;
    }

    @Override
    public CLanguageData getCLanguageData(IInputType type) {
        //JABA dead code
        return null;
        //        initDataMap();
        //        return typeToDataMap.get(type);
    }



    public boolean supportsLanguageSettings() {
        List<IOption> options = getOptions();
        boolean found = false;
        for (IOption option : options) {
            try {
                int type = option.getValueType();
                if (ManagedBuildManager.optionTypeToEntryKind(type) != 0) {
                    found = true;
                    break;
                }
            } catch (BuildException e) {
            }
        }
        return found;
    }

    @Override
    public CLanguageData[] getCLanguageDatas() {
        //TOFIX JABA don't know what this does
        return null;
        //        initDataMap();
        //        return typeToDataMap.values().toArray(new BuildLanguageData[typeToDataMap.size()]);
    }

    @Override
    public IInputType getInputTypeForCLanguageData(CLanguageData data) {
        if (data instanceof BuildLanguageData)
            return ((BuildLanguageData) data).getInputType();
        return null;
    }

    @Override
    public IResourceInfo getParentResourceInfo() {
        if (parent instanceof IFileInfo)
            return (IResourceInfo) parent;
//        else if (parent instanceof IToolChain)
//            return ((IToolChain) parent).getParentFolderInfo();
        return null;
    }

    @Override
    public IInputType getEditableInputType(IInputType base) {
        if (base.getParent() == this)
            return base;

        IInputType extType = base;
        for (; extType != null; extType = extType.getSuperClass()) {
            // empty body
        }
        String id;
        if (extType != null) {
            id = ManagedBuildManager.calculateChildId(extType.getId(), null);
        } else {
            id = ManagedBuildManager.calculateChildId(getId(), null);
        }
        InputType newType = (InputType) createInputType(base, id, base.getName(), false);

        return newType;
    }

    private BooleanExpressionApplicabilityCalculator getBooleanExpressionCalculator() {
        if (booleanExpressionCalculator == null) {
            if (superClass != null) {
                return ((Tool) superClass).getBooleanExpressionCalculator();
            }
        }
        return booleanExpressionCalculator;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled(getParentResourceInfo());
    }

    public boolean isEnabled(IResourceInfo rcInfo) {

        BooleanExpressionApplicabilityCalculator calc = getBooleanExpressionCalculator();
        if (calc == null)
            return true;

        return calc.isToolUsedInCommandLine(rcInfo, this);
    }

    @Override
    public boolean matches(ITool tool) {
        if (tool == this)
            return true;

        ITool rT = ManagedBuildManager.getRealTool(this);
        if (rT == null)
            return false;

        return rT == ManagedBuildManager.getRealTool(tool);
    }

    /*	public SupportedProperties getSupportedProperties(){
    		Map map = findSupportedProperties();
    		if(map != null)
    			return new HashMap(map);
    		return null;
    	}
    */
    private SupportedProperties findSupportedProperties() {
        if (supportedProperties == null) {
            if (superClass != null) {
                return ((Tool) superClass).findSupportedProperties();
            }
        }
        return supportedProperties;
    }

    @Override
    public boolean supportsBuild(boolean managed) {
        if (supportsManagedBuild == null) {
            if (superClass != null) {
                return ((Tool) superClass).supportsBuild(managed);
            }
            return true;
        }
        return supportsManagedBuild.booleanValue();
    }

    public String getNameAndVersion() {
        String name = getName();
        String version = ManagedBuildManager.getVersionFromIdAndVersion(getId());
        if (version != null && version.length() != 0) {
            return new StringBuilder().append(name).append(" (").append(version).append("").toString(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return name;
    }

    public IConfigurationElement getConverterModificationElement(ITool toTool) {
        Map<String, IConfigurationElement> map = ManagedBuildManager.getConversionElements(this);
        IConfigurationElement element = null;
        if (!map.isEmpty()) {
            for (IConfigurationElement el : map.values()) {
                String toId = el.getAttribute("toId"); //$NON-NLS-1$
                ITool to = toTool;
//                if (toId != null) {
//                    for (; to != null; to = to.getSuperClass()) {
//                        if (toId.equals(to.getId()))
//                            break;
//                    }
//                }

                if (to != null) {
                    element = el;
                    break;
                }
            }
        }

        return element;
    }




    @Override
    public boolean isSystemObject() {
    	return isTest;
//        if (isTest)
//            return true;
//
//        if (getConvertToId().length() != 0)
//            return true;
//
//        IBuildObject bo = getParent();
//        if (bo instanceof IToolChain)
//            return ((IToolChain) bo).isSystemObject();
//        return false;
    }

    @Override
    public boolean isHidden() {
//        if (isHidden == null) {
//            if (getSuperClass() != null) {
//                return getSuperClass().isHidden();
//            }
//            return false; // default is false
//        }
        return isHidden.booleanValue();
    }

    @Override
    public String getUniqueRealName() {
        String name = getName();
        if (name == null) {
            name = getId();
        } else {
            String version = ManagedBuildManager.getVersionFromIdAndVersion(getId());
            if (version != null) {
                StringBuilder buf = new StringBuilder();
                buf.append(name);
                buf.append(" (v").append(version).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
                name = buf.toString();
            }
        }
        return name;
    }

    private boolean typeContributesToScannerConfig(InputType inType) {
        if (inType.getDiscoveryProfileId(this) != null)
            return true;

        return false;
    }

    public boolean hasScannerConfigSettings(IInputType type) {
        if (type == null) {
            boolean has = hasScannerConfigSettings();
            if (has)
                return has;
//            ITool superClass = getSuperClass();
//            if (superClass != null && superClass instanceof Tool)
//                return ((Tool) superClass).hasScannerConfigSettings(type);
            return false;
        }
        return ((InputType) type).hasScannerConfigSettings();
    }

    private boolean hasScannerConfigSettings() {

        if (getDiscoveryProfileIdAttribute() != null)
            return true;

        return false;
    }

    //    public PathInfoCache setDiscoveredPathInfo(IInputType type, PathInfoCache info) {
    //        return discoveredInfoMap.put(getTypeKey(type), info);
    //    }
    //
    //    public PathInfoCache getDiscoveredPathInfo(IInputType type) {
    //        return discoveredInfoMap.get(getTypeKey(type));
    //    }
    //
    //    public PathInfoCache clearDiscoveredPathInfo(IInputType type) {
    //        return discoveredInfoMap.remove(getTypeKey(type));
    //    }
    //
    //    public void clearAllDiscoveredPathInfo() {
    //        discoveredInfoMap.clear();
    //    }

    //    public void clearAllDiscoveredInfo() {
    //        discoveredInfoMap.clear();
    //    }

    //    private String getTypeKey(IInputType type) {
    //        if (type != null)
    //            return type.getId();
    //        return null;
    //    }

    public String getDiscoveryProfileIdAttribute() {
        if (scannerConfigDiscoveryProfileId == null && superClass != null)
            return ((Tool) superClass).getDiscoveryProfileIdAttribute();
        return scannerConfigDiscoveryProfileId;
    }

    private IToolChain getToolChain() {
        IBuildObject bo = getParent();
        IToolChain tCh = null;
        if (bo instanceof IToolChain) {
            tCh = ((IToolChain) bo);
        } else if (bo instanceof IFileInfo) {
            tCh = ((ResourceConfiguration) bo).getBaseToolChain();
        }
        return tCh;
    }

    public String getDiscoveryProfileId() {
        String id = getDiscoveryProfileIdAttribute();
        if (id == null) {
            IToolChain tc = getToolChain();
            if (tc != null)
                id = tc.getScannerConfigDiscoveryProfileId();
        }
        return id;
    }

    public boolean hasCustomSettings(Tool tool) {
        if (superClass == null)
            return true;

        ITool realTool = ManagedBuildManager.getRealTool(this);
        ITool otherRealTool = ManagedBuildManager.getRealTool(tool);
        if (realTool != otherRealTool)
            return true;

        if (hasCustomSettings())
            return true;

        if (outputTypeList != null && outputTypeList.size() != 0) {
            for (OutputType outType : outputTypeList) {
                if (outType.hasCustomSettings())
                    return true;
            }
        }
        Tool superTool = (Tool) superClass;

        if (command != null && !command.equals(superTool.getToolCommand()))
            return true;

        if (errorParserIds != null && !errorParserIds.equals(superTool.getErrorParserIds()))
            return true;

        if (commandLinePattern != null && !commandLinePattern.equals(superTool.getCommandLinePattern()))
            return true;

        if (customBuildStep != null && customBuildStep.booleanValue() != superTool.getCustomBuildStep())
            return true;

        if (announcement != null && !announcement.equals(superTool.getAnnouncement()))
            return true;

        if (isHidden != null && isHidden.booleanValue() != superTool.isHidden())
            return true;

        //        if (discoveredInfoMap != null && discoveredInfoMap.size() != 0)
        //            return true;

        if (isAnyOptionModified(this, tool))
            return true;

        return false;
    }

    private boolean isAnyOptionModified(ITool t1, ITool t2) {
        for (IOption op1 : t1.getOptions()) {
            for (IOption op2 : t2.getOptions()) {
                // find matching option
                try {
                    if (op1.getValueType() == op2.getValueType() && op1.getName() != null
                            && op1.getName().equals(op2.getName())) {
                        Object ob1 = op1.getValue();
                        Object ob2 = op2.getValue();
                        if (ob1 == null && ob2 == null)
                            break;
                        // values are different ?
                        if ((ob1 == null || ob2 == null) || !(ob1.equals(ob2)))
                            return true;
                        else
                            break;
                    }
                } catch (BuildException e) {
                    return true; // unprobable
                }
            }
        }
        return false;
    }

    public IOption[] getOptionsOfType(int type) {
        List<IOption> list = new ArrayList<>();
        for (IOption op : getOptions()) {
            try {
                if (op.getValueType() == type)
                    list.add(op);
            } catch (BuildException e) {
                Activator.log(e);
            }
        }
        return list.toArray(new Option[list.size()]);
    }

    @Override
    public IOptionCategoryApplicability getApplicabilityCalculator() {
        // Tool does not have any ApplicabilityCalculator.
        return null;
    }

    @Override
    public String[] getExtraFlags(int optionType) {
        if (optionType != IOption.LIBRARIES && optionType != IOption.OBJECTS) {
            // Early exit to avoid performance penalty
            return new String[0];
        }

        Vector<String> flags = new Vector<>();
        for (IOption option : getOptions()) {
            try {
                if (option.getValueType() != optionType) {
                    continue;
                }

                // check to see if the option has an applicability calculator
                IOptionApplicability applicabilityCalculator = option.getApplicabilityCalculator();

                if (applicabilityCalculator == null
                        || applicabilityCalculator.isOptionUsedInCommandLine(this, this, option)) {
                    boolean generateDefaultCommand = true;
                    IOptionCommandGenerator commandGenerator = option.getCommandGenerator();
                    if (commandGenerator != null) {
                        SupplierBasedCdtVariableSubstitutor macroSubstitutor = new BuildfileMacroSubstitutor(null,
                                EMPTY_STRING, WHITE_SPACE);
                        IMacroContextInfoProvider provider = BuildMacroProvider.getDefault();
                        IMacroContextInfo info = provider.getMacroContextInfo(BuildMacroProvider.CONTEXT_OPTION,
                                new OptionContextData(option, this));
                        if (info != null) {
                            macroSubstitutor.setMacroContextInfo(info);
                            String command = commandGenerator.generateCommand(option, macroSubstitutor);
                            if (command != null) {
                                flags.add(command);
                                generateDefaultCommand = false;
                            }
                        }
                    }

                    if (generateDefaultCommand) {
                        switch (optionType) {
                        case IOption.LIBRARIES: {
                            String command = option.getCommand();
                            String[] libs = option.getLibraries();
                            for (String lib : libs) {
                                try {
                                    String resolved[] = ManagedBuildManager.getBuildMacroProvider()
                                            .resolveStringListValueToMakefileFormat(lib, " ", //$NON-NLS-1$
                                                    " ", //$NON-NLS-1$
                                                    IBuildMacroProvider.CONTEXT_OPTION,
                                                    new OptionContextData(option, this));
                                    if (resolved != null && resolved.length > 0) {
                                        for (String string : resolved) {
                                            if (!string.isEmpty()) {
                                                flags.add(command + string);
                                            }
                                        }
                                    }
                                } catch (BuildMacroException e) {
                                    // TODO: report error
                                    continue;
                                }
                            }
                            break;
                        }
                        case IOption.OBJECTS: {
                            String userObjs[] = option.getUserObjects();
                            if (userObjs != null && userObjs.length > 0) {
                                for (String userObj : userObjs) {
                                    try {
                                        String resolved[] = ManagedBuildManager.getBuildMacroProvider()
                                                .resolveStringListValueToMakefileFormat(userObj, "", //$NON-NLS-1$
                                                        " ", //$NON-NLS-1$
                                                        IBuildMacroProvider.CONTEXT_OPTION,
                                                        new OptionContextData(option, this));
                                        if (resolved != null && resolved.length > 0) {
                                            flags.addAll(Arrays.asList(resolved));
                                        }
                                    } catch (BuildMacroException e) {
                                        // TODO: report error
                                        continue;
                                    }
                                }
                            }
                            break;
                        }
                        default:
                            // Cannot happen
                            break;
                        }
                    }
                }
            } catch (BuildException | CdtVariableException e) {
                // TODO: report error
                continue;
            }
        }
        return flags.toArray(new String[flags.size()]);
    }

    /* (non-Javadoc)
     * @see org.eclipse.cdt.core.build.managed.ITool#buildsFileType(java.lang.String)
     */
    @Override
    public boolean buildsFileType(IFile file) {
        for (InputType inputType : inputTypeList) {
            if (inputType.isAssociatedWith(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<IInputType> getMatchingInputTypes(IFile file, String macroName) {
        String safeMacroName = macroName;
        if (macroName == null) {
            safeMacroName = new String();
        }
        List<IInputType> ret = new LinkedList<>();
        for (InputType inputType : inputTypeList) {
            if (inputType.isAssociatedWith(file)) {
                ret.add(inputType);
            } else {
                if (safeMacroName.equals(inputType.getAssignToOptionId())) {
                    ret.add(inputType);
                }
            }
        }
        return ret;
    }

    public void resolveFields() throws Exception {
        // TODO Auto-generated method stub

    }

	@Override
	public IInputType getInputTypeByID(String id) {
		IInputType[] allInputTypes =getAllInputTypes();
		for(IInputType curInputType:allInputTypes) {
			if(id.equals(curInputType.getId())){
				return curInputType;
			}
		}
		return null;
	}

}

