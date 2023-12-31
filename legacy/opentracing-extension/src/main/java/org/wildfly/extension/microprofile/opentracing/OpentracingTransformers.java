/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.microprofile.opentracing;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.kohsuke.MetaInfServices;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.PROPAGATION;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.REPORTER_FLUSH_INTERVAL;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.REPORTER_LOG_SPANS;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.REPORTER_MAX_QUEUE_SIZE;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SAMPLER_MANAGER_HOST_PORT;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SAMPLER_PARAM;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SAMPLER_TYPE;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SENDER_AUTH_PASSWORD;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SENDER_AUTH_TOKEN;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SENDER_AUTH_USER;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SENDER_BINDING;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.SENDER_ENDPOINT;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.TRACEID_128BIT;
import static org.wildfly.extension.microprofile.opentracing.TracerAttributes.TRACER_TAGS;

/**
 *
 * @author Emmanuel Hugonnet (c) 2019 Red Hat, Inc.
 */
@MetaInfServices
public class OpentracingTransformers implements ExtensionTransformerRegistration {

    @Override
    public String getSubsystemName() {
        return SubsystemExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());
        registerTransformers_WF_20(builder.createBuilder(SubsystemExtension.VERSION_3_0_0, SubsystemExtension.VERSION_2_0_0));
        registerTransformers_WF_19(builder.createBuilder(SubsystemExtension.VERSION_2_0_0, SubsystemExtension.VERSION_1_0_0));
        builder.buildAndRegister(registration, new ModelVersion[]{SubsystemExtension.VERSION_3_0_0, SubsystemExtension.VERSION_2_0_0, SubsystemExtension.VERSION_1_0_0});
    }

    private static void registerTransformers_WF_20(ResourceTransformationDescriptionBuilder subsystem) {
        subsystem.addChildResource(JaegerTracerConfigurationDefinition.TRACER_CONFIGURATION_PATH)
                .getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS,
                        PROPAGATION, SAMPLER_TYPE, SAMPLER_PARAM,
                        SAMPLER_MANAGER_HOST_PORT, SENDER_BINDING, SENDER_ENDPOINT,
                        SENDER_AUTH_TOKEN, SENDER_AUTH_USER, SENDER_AUTH_PASSWORD,
                        REPORTER_LOG_SPANS, REPORTER_FLUSH_INTERVAL,
                        REPORTER_MAX_QUEUE_SIZE, TRACER_TAGS, TRACEID_128BIT)
                .end();
    }

    private static void registerTransformers_WF_19(ResourceTransformationDescriptionBuilder subsystem) {
        subsystem.rejectChildResource(JaegerTracerConfigurationDefinition.TRACER_CONFIGURATION_PATH);
        subsystem.getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.DEFINED, SubsystemDefinition.DEFAULT_TRACER)
                .setDiscard(DiscardAttributeChecker.UNDEFINED, SubsystemDefinition.DEFAULT_TRACER)
                .end();
    }
}
