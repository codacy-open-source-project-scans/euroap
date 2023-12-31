/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.subsystem;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.remote.LocalTransportProvider;
import org.jboss.as.ejb3.remote.RemotingProfileService;
import org.jboss.as.network.OutboundConnection;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.EJBTransportProvider;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */
public class RemotingProfileAdd extends AbstractAddStepHandler {

    private static final String OUTBOUND_CONNECTION_CAPABILITY_NAME = "org.wildfly.remoting.outbound-connection";

    RemotingProfileAdd(AttributeDefinition... attributes) {
        super(attributes);
    }

    @Override
    protected void performRuntime(final OperationContext context,final ModelNode operation,final ModelNode model)
            throws OperationFailedException {
        context.addStep((context1, operation1) -> {
            // Install another RUNTIME handler to actually install the services. This will run after the
            // RUNTIME handler for any child resources. Doing this will ensure that child resource handlers don't
            // see the installed services and can just ignore doing any RUNTIME stage work
            context1.addStep(ServiceInstallStepHandler.INSTANCE, OperationContext.Stage.RUNTIME);
        }, OperationContext.Stage.RUNTIME);
    }

    protected void installServices(final OperationContext context, final PathAddress address, final ModelNode profileNode) throws OperationFailedException {
        try {
            final ModelNode staticEjbDiscoery = StaticEJBDiscoveryDefinition.INSTANCE.resolveModelAttribute(context, profileNode);
            List<StaticEJBDiscoveryDefinition.StaticEjbDiscovery> discoveryList = StaticEJBDiscoveryDefinition.createStaticEjbList(context, staticEjbDiscoery);

            final List<ServiceURL> urls = new ArrayList<>();

            for (StaticEJBDiscoveryDefinition.StaticEjbDiscovery resource : discoveryList) {
                ServiceURL.Builder builder = new ServiceURL.Builder();
                builder.setAbstractType("ejb")
                        .setAbstractTypeAuthority("jboss")
                        .setUri(new URI(resource.getUrl()));
                String distinctName = resource.getDistinct() == null ? "" : resource.getDistinct();
                String appName = resource.getApp() == null ? "" : resource.getApp();
                String moduleName = resource.getModule();

                if (distinctName.isEmpty()) {
                    if (appName.isEmpty()) {
                        builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE, AttributeValue.fromString(moduleName));
                    } else {
                        builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE, AttributeValue.fromString(appName + "/" + moduleName));
                    }
                } else {
                    if (appName.isEmpty()) {
                        builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT, AttributeValue.fromString(moduleName + "/" + distinctName));
                    } else {
                        builder.addAttribute(EJBClientContext.FILTER_ATTR_EJB_MODULE_DISTINCT, AttributeValue.fromString(appName + "/" + moduleName + "/" + distinctName));
                    }
                }
                urls.add(builder.create());
            }
            final Map<String, RemotingProfileService.RemotingConnectionSpec> map = new HashMap<>();
            final List<RemotingProfileService.HttpConnectionSpec> httpConnectionSpecs = new ArrayList<>();
            // populating the map after the fact is cheating, but it works thanks to the MSC start service "fence"

            final CapabilityServiceBuilder capabilityServiceBuilder = context.getCapabilityServiceTarget().addCapability(RemotingProfileResourceDefinition.REMOTING_PROFILE_CAPABILITY);
            final Consumer<RemotingProfileService> consumer = capabilityServiceBuilder.provides(RemotingProfileResourceDefinition.REMOTING_PROFILE_CAPABILITY);
            if (profileNode.hasDefined(EJB3SubsystemModel.REMOTING_EJB_RECEIVER)) {
                for (final Property receiverProperty : profileNode.get(EJB3SubsystemModel.REMOTING_EJB_RECEIVER).asPropertyList()) {

                    final ModelNode receiverNode = receiverProperty.getValue();
                    final String connectionRef = RemotingEjbReceiverDefinition.OUTBOUND_CONNECTION_REF.resolveModelAttribute(context, receiverNode).asString();
                    final long timeout = RemotingEjbReceiverDefinition.CONNECT_TIMEOUT.resolveModelAttribute(context, receiverNode).asLong();

                    final Supplier<OutboundConnection> connectionSupplier = capabilityServiceBuilder.requiresCapability(OUTBOUND_CONNECTION_CAPABILITY_NAME, OutboundConnection.class, connectionRef);

                    final ModelNode channelCreationOptionsNode = receiverNode.get(EJB3SubsystemModel.CHANNEL_CREATION_OPTIONS);
                    OptionMap channelCreationOptions = createChannelOptionMap(context, channelCreationOptionsNode);

                    map.put(connectionRef, new RemotingProfileService.RemotingConnectionSpec(
                        connectionRef,
                        connectionSupplier,
                        channelCreationOptions,
                        timeout
                    ));
                }
            }
            final boolean isLocalReceiverExcluded = RemotingProfileResourceDefinition.EXCLUDE_LOCAL_RECEIVER.resolveModelAttribute(context, profileNode).asBoolean();
            if (profileNode.hasDefined(EJB3SubsystemModel.REMOTE_HTTP_CONNECTION)) {
                for (final Property receiverProperty : profileNode.get(EJB3SubsystemModel.REMOTE_HTTP_CONNECTION)
                        .asPropertyList()) {
                    final ModelNode receiverNode = receiverProperty.getValue();

                    final String uri = RemoteHttpConnectionDefinition.URI.resolveModelAttribute(context,
                            receiverNode).asString();

                    httpConnectionSpecs.add(new RemotingProfileService.HttpConnectionSpec(uri));
                }
            }

            // if the local receiver is enabled for this context, then add a dependency on the appropriate LocalEjbReceive service
            Supplier<EJBTransportProvider> localTransportProviderSupplier = null;
            if (!isLocalReceiverExcluded) {
                final ModelNode passByValueNode = RemotingProfileResourceDefinition.LOCAL_RECEIVER_PASS_BY_VALUE.resolveModelAttribute(context, profileNode);

                if (passByValueNode.isDefined()) {
                    final ServiceName localTransportProviderServiceName = passByValueNode.asBoolean() == true ? LocalTransportProvider.BY_VALUE_SERVICE_NAME
                            : LocalTransportProvider.BY_REFERENCE_SERVICE_NAME;
                    localTransportProviderSupplier = capabilityServiceBuilder.requires(localTransportProviderServiceName);
                } else {
                    // setup a dependency on the default local Jakarta Enterprise Beans receiver service configured at the subsystem level
                    localTransportProviderSupplier = capabilityServiceBuilder.requires(LocalTransportProvider.DEFAULT_LOCAL_TRANSPORT_PROVIDER_SERVICE_NAME);
                }
            }
            final RemotingProfileService profileService = new RemotingProfileService(consumer, localTransportProviderSupplier, urls, map, httpConnectionSpecs);
            capabilityServiceBuilder.setInstance(profileService);
            capabilityServiceBuilder.install();

        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new OperationFailedException(e.getLocalizedMessage());
        }
    }

    private OptionMap createChannelOptionMap(final OperationContext context, final ModelNode channelCreationOptionsNode)
            throws OperationFailedException {
        final OptionMap optionMap;
        if (channelCreationOptionsNode.isDefined()) {
            final OptionMap.Builder optionMapBuilder = OptionMap.builder();
            final ClassLoader loader = this.getClass().getClassLoader();
            for (final Property optionProperty : channelCreationOptionsNode.asPropertyList()) {
                final String name = optionProperty.getName();
                final ModelNode propValueModel = optionProperty.getValue();
                final String type = RemoteConnectorChannelCreationOptionResource.CHANNEL_CREATION_OPTION_TYPE.resolveModelAttribute(context, propValueModel).asString();
                final String optionClassName = this.getClassNameForChannelOptionType(type);
                final String fullyQualifiedOptionName = optionClassName + "." + name;
                final Option option = Option.fromString(fullyQualifiedOptionName, loader);
                final String value = RemoteConnectorChannelCreationOptionResource.CHANNEL_CREATION_OPTION_VALUE.resolveModelAttribute(context, propValueModel).asString();
                optionMapBuilder.set(option, option.parseValue(value, loader));
            }
            optionMap = optionMapBuilder.getMap();
        } else {
            optionMap = OptionMap.EMPTY;
        }
        return optionMap;
    }

    private String getClassNameForChannelOptionType(final String optionType) {
        if ("remoting".equals(optionType)) {
            return RemotingOptions.class.getName();
        }
        if ("xnio".equals(optionType)) {
            return Options.class.getName();
        }
        throw EjbLogger.ROOT_LOGGER.unknownChannelCreationOptionType(optionType);
    }

    private static class ServiceInstallStepHandler implements OperationStepHandler {

        private static final ServiceInstallStepHandler INSTANCE = new ServiceInstallStepHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
            final ModelNode model = Resource.Tools.readModel(resource);
            final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
            RemotingProfileResourceDefinition.ADD_HANDLER.installServices(context, address, model);
        }
    }

}
