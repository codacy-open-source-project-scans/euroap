/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.microprofile.config.smallrye;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.staxmapper.XMLElementWriter;


/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MicroProfileConfigExtension implements Extension {

    /**
     * The name of our subsystem within the model.
     */
    public static final String SUBSYSTEM_NAME = "microprofile-config-smallrye";

    protected static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    protected static final PathElement CONFIG_SOURCE_PATH = PathElement.pathElement("config-source");
    protected static final PathElement CONFIG_SOURCE_PROVIDER_PATH = PathElement.pathElement("config-source-provider");

    private static final String RESOURCE_NAME = MicroProfileConfigExtension.class.getPackage().getName() + ".LocalDescriptions";

    protected static final ModelVersion VERSION_1_0_0 = ModelVersion.create(1, 0, 0);
    protected static final ModelVersion VERSION_1_1_0 = ModelVersion.create(1, 1, 0);
    protected static final ModelVersion VERSION_2_0_0 = ModelVersion.create(2, 0, 0);
    private static final ModelVersion CURRENT_MODEL_VERSION = VERSION_2_0_0;

    private static final MicroProfileConfigSubsystemParser_1_0 PARSER_1_0 = new MicroProfileConfigSubsystemParser_1_0();
    private static final MicroProfileConfigSubsystemParser_2_0 PARSER_2_0 = new MicroProfileConfigSubsystemParser_2_0();
    private static final XMLElementWriter<SubsystemMarshallingContext> CURRENT_WRITER = PARSER_2_0;

    static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        return getResourceDescriptionResolver(true, keyPrefix);
    }

    static ResourceDescriptionResolver getResourceDescriptionResolver(final boolean useUnprefixedChildTypes, final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder();
        for (String kp : keyPrefix) {
            if (prefix.length() > 0){
                prefix.append('.');
            }
            prefix.append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, MicroProfileConfigExtension.class.getClassLoader(), true, useUnprefixedChildTypes);
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_MODEL_VERSION);
        subsystem.registerXMLElementWriter(CURRENT_WRITER);

        IterableRegistry<ConfigSourceProvider> providers = new IterableRegistry<>();
        IterableRegistry<ConfigSource> sources = new IterableRegistry<>();

        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new MicroProfileSubsystemDefinition(providers, sources));
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        registration.registerSubModel(new ConfigSourceDefinition(providers, sources));
        registration.registerSubModel(new ConfigSourceProviderDefinition(providers));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, MicroProfileConfigSubsystemParser_1_0.NAMESPACE, PARSER_1_0);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, MicroProfileConfigSubsystemParser_2_0.NAMESPACE, PARSER_2_0);
    }

}
