/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.net.ssl.SSLContext;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.remote.EJBClientContextService;
import org.jboss.as.ejb3.remote.RemotingProfileService;
import org.jboss.as.network.OutboundConnection;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.common.context.ContextManager;
import org.wildfly.discovery.Discovery;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;
import org.xnio.OptionMap;

/**
 * A deployment processor which associates the {@link EJBClientContext}, belonging to a deployment unit,
 * with the deployment unit's classloader.
 *
 * @author Stuart Douglas
 * @author Jaikiran Pai
 */
public class EjbClientContextSetupProcessor implements DeploymentUnitProcessor {

    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        if (module == null) {
            return;
        }

        RegistrationService registrationService = new RegistrationService(module);
        ServiceName registrationServiceName = deploymentUnit.getServiceName().append("ejb3","client-context","registration-service");
        final ServiceName profileServiceName = getProfileServiceName(phaseContext);
        final ServiceBuilder<Void> builder = phaseContext.getServiceTarget().addService(registrationServiceName, registrationService)
            .addDependency(getEJBClientContextServiceName(phaseContext), EJBClientContextService.class, registrationService.ejbClientContextInjectedValue)
            .addDependency(getDiscoveryServiceName(phaseContext), Discovery.class, registrationService.discoveryInjector);
        if (profileServiceName != null) {
            builder.addDependency(profileServiceName, RemotingProfileService.class, registrationService.profileServiceInjectedValue);
        }
        builder.install();


        final EEModuleDescription moduleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        if (moduleDescription == null) {
            return;
        }
        //we need to make sure all our components have a dependency on the EJB client context registration, which in turn implies a dependency on the context
        for(final ComponentDescription component : moduleDescription.getComponentDescriptions()) {
            component.addDependency(registrationServiceName);
        }
    }

    private ServiceName getEJBClientContextServiceName(final DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parentDeploymentUnit = deploymentUnit.getParent();
        // The top level parent deployment unit will have the attachment containing the EJB client context
        // service name
        ServiceName serviceName;
        if (parentDeploymentUnit != null) {
            serviceName = parentDeploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_CLIENT_CONTEXT_SERVICE_NAME);
        } else {
            serviceName = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_CLIENT_CONTEXT_SERVICE_NAME);
        }
        if (serviceName != null) {
            return serviceName;
        }
        return EJBClientContextService.DEFAULT_SERVICE_NAME;
    }

    private ServiceName getDiscoveryServiceName(final DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parentDeploymentUnit = deploymentUnit.getParent();
        if (parentDeploymentUnit != null) {
            return DiscoveryService.BASE_NAME.append(parentDeploymentUnit.getName());
        } else {
            return DiscoveryService.BASE_NAME.append(deploymentUnit.getName());
        }
    }

    private ServiceName getProfileServiceName(final DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit parentDeploymentUnit = deploymentUnit.getParent();
        if (parentDeploymentUnit != null) {
            return parentDeploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_REMOTING_PROFILE_SERVICE_NAME);
        } else {
            return deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_REMOTING_PROFILE_SERVICE_NAME);
        }
    }

    private static final class RegistrationService implements Service<Void> {

        private final Module module;

        final InjectedValue<EJBClientContextService> ejbClientContextInjectedValue = new InjectedValue<>();
        final InjectedValue<Discovery> discoveryInjector = new InjectedValue<>();
        final InjectedValue<RemotingProfileService> profileServiceInjectedValue = new InjectedValue<>();

        private RegistrationService(Module module) {
            this.module = module;
        }

        @Override
        public void start(StartContext context) throws StartException {

            try {
                doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    // associate the EJB client context and discovery setup with the deployment classloader
                    final EJBClientContextService ejbClientContextService = ejbClientContextInjectedValue.getValue();
                    final EJBClientContext ejbClientContext = ejbClientContextService.getClientContext();
                    final AuthenticationContext ejbClientClustersAuthenticationContext = ejbClientContextService.getClustersAuthenticationContext();
                    final ModuleClassLoader classLoader = module.getClassLoader();
                    EjbLogger.DEPLOYMENT_LOGGER.debugf("Registering EJB client context %s for classloader %s", ejbClientContext, classLoader);
                    final ContextManager<AuthenticationContext> authenticationContextManager = AuthenticationContext.getContextManager();
                    final RemotingProfileService profileService = profileServiceInjectedValue.getOptionalValue();
                    if (profileService != null || ejbClientClustersAuthenticationContext != null) {
                        // this is cheating but it works for our purposes
                        AuthenticationContext authenticationContext = authenticationContextManager.getClassLoaderDefault(classLoader);
                        if (authenticationContext == null) {
                            authenticationContext = authenticationContextManager.get();
                        }
                        final AuthenticationContext finalAuthenticationContext = authenticationContext;
                        authenticationContextManager.setClassLoaderDefaultSupplier(classLoader, () -> {
                            AuthenticationContext transformed = finalAuthenticationContext;
                            // now transform it
                            if (profileService != null) {
                                for (RemotingProfileService.RemotingConnectionSpec connectionSpec : profileService.getConnectionSpecs()) {
                                    transformed = transformOne(connectionSpec, transformed);
                                }
                            }
                            if (ejbClientClustersAuthenticationContext != null) {
                                transformed = ejbClientClustersAuthenticationContext.with(transformed);
                            }

                            return transformed;
                        });
                    }
                    EJBClientContext.getContextManager().setClassLoaderDefault(classLoader, ejbClientContext);
                    Discovery.getContextManager().setClassLoaderDefault(classLoader, discoveryInjector.getValue());
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw (StartException) e.getCause();
            }
        }

        @Override
        public void stop(StopContext context) {
            // de-associate the EJB client context with the deployment classloader
            doPrivileged((PrivilegedAction<Void>) () -> {
                final ModuleClassLoader classLoader = module.getClassLoader();
                EjbLogger.DEPLOYMENT_LOGGER.debugf("unRegistering EJB client context for classloader %s", classLoader);
                EJBClientContext.getContextManager().setClassLoaderDefault(classLoader, null);
                Discovery.getContextManager().setClassLoaderDefault(classLoader, null);
                // this is redundant but should be safe
                AuthenticationContext.getContextManager().setClassLoaderDefault(classLoader, null);
                return null;
            });
        }

        @Override
        public Void getValue() throws IllegalStateException, IllegalArgumentException {
            return null;
        }

        private static AuthenticationContext transformOne(RemotingProfileService.RemotingConnectionSpec connectionSpec, AuthenticationContext context) {
            final OutboundConnection connectionService = connectionSpec.getSupplier().get();
            AuthenticationConfiguration authenticationConfiguration = connectionService.getAuthenticationConfiguration();
            SSLContext sslContext = connectionService.getSSLContext();
            final URI destinationUri = connectionService.getDestinationUri();
            MatchRule rule = MatchRule.ALL;
            final String scheme = destinationUri.getScheme();
            if (scheme != null) {
                rule = rule.matchProtocol(scheme);
            }
            final String host = destinationUri.getHost();
            if (host != null) {
                rule = rule.matchHost(host);
            }
            final int port = destinationUri.getPort();
            if (port != -1) {
                rule = rule.matchPort(port);
            }
            final String path = destinationUri.getPath();
            if (path != null && ! path.isEmpty()) {
                rule = rule.matchPath(path);
            }
            MatchRule ejbRule = rule.matchAbstractType("ejb", "jboss");
            MatchRule jtaRule = rule.matchAbstractType("jta", "jboss");
            final OptionMap connectOptions = connectionSpec.getConnectOptions();
            authenticationConfiguration = RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(connectOptions, authenticationConfiguration);
            AuthenticationConfiguration ejbConfiguration = CLIENT.getAuthenticationConfiguration(destinationUri, context, - 1, "ejb", "jboss");
            AuthenticationConfiguration jtaConfiguration = CLIENT.getAuthenticationConfiguration(destinationUri, context, - 1, "jta", "jboss");
            if (sslContext == null) {
                try {
                    sslContext = CLIENT.getSSLContext(destinationUri, context);
                } catch (GeneralSecurityException e) {
                    throw EjbLogger.ROOT_LOGGER.failedToObtainSSLContext(e);
                }
            }
            final SSLContext finalSSLContext = sslContext;
            AuthenticationContext mergedAuthenticationContext = context.with(0, ejbRule, ejbConfiguration.with(authenticationConfiguration)).with(jtaRule, jtaConfiguration.with(authenticationConfiguration));
            return mergedAuthenticationContext.withSsl(0, rule, () -> finalSSLContext);
        }
    }
}
