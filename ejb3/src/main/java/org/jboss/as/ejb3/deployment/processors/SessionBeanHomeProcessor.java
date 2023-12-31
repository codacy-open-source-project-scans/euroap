/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import jakarta.ejb.EJBHome;
import jakarta.ejb.Handle;

import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.ComponentStartService;
import org.jboss.as.ee.component.ComponentView;
import org.jboss.as.ee.component.DependencyConfigurator;
import org.jboss.as.ee.component.ViewConfiguration;
import org.jboss.as.ee.component.ViewConfigurator;
import org.jboss.as.ee.component.ViewDescription;
import org.jboss.as.ee.component.ViewService;
import org.jboss.as.ee.component.deployers.AbstractComponentConfigProcessor;
import org.jboss.as.ee.component.interceptors.InterceptorOrder;
import org.jboss.as.ee.utils.ClassLoadingUtils;
import org.jboss.as.ejb3.component.EJBViewDescription;
import org.jboss.as.ejb3.component.interceptors.EjbMetadataInterceptor;
import org.jboss.as.ejb3.component.interceptors.HomeRemoveInterceptor;
import org.jboss.as.ejb3.component.interceptors.SessionBeanHomeInterceptorFactory;
import org.jboss.as.ejb3.component.session.InvalidRemoveExceptionMethodInterceptor;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.ejb3.component.stateful.StatefulComponentDescription;
import org.jboss.as.ejb3.component.stateless.StatelessComponentDescription;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.msc.service.ServiceBuilder;

/**
 * Processor that hooks up home interfaces for session beans
 *
 * @author Stuart Douglas
 */
public class SessionBeanHomeProcessor extends AbstractComponentConfigProcessor {

    @Override
    protected void processComponentConfig(final DeploymentUnit deploymentUnit, final DeploymentPhaseContext phaseContext, final CompositeIndex index, final ComponentDescription componentDescription) throws DeploymentUnitProcessingException {

        if (componentDescription instanceof SessionBeanComponentDescription) {
            final SessionBeanComponentDescription ejbComponentDescription = (SessionBeanComponentDescription) componentDescription;

            //check for EJB's with a local home interface
            if (ejbComponentDescription.getEjbLocalHomeView() != null) {
                final EJBViewDescription view = ejbComponentDescription.getEjbLocalHomeView();
                final EJBViewDescription ejbLocalView = ejbComponentDescription.getEjbLocalView();
                configureHome(componentDescription, ejbComponentDescription, view, ejbLocalView);
            }
            if (ejbComponentDescription.getEjbHomeView() != null) {
                final EJBViewDescription view = ejbComponentDescription.getEjbHomeView();
                final EJBViewDescription ejbRemoteView = ejbComponentDescription.getEjbRemoteView();
                configureHome(componentDescription, ejbComponentDescription, view, ejbRemoteView);
            }
        }
    }

    private void configureHome(final ComponentDescription componentDescription, final SessionBeanComponentDescription ejbComponentDescription, final EJBViewDescription homeView, final EJBViewDescription ejbObjectView) {
        homeView.getConfigurators().add(new ViewConfigurator() {

            @Override
            public void configure(final DeploymentPhaseContext context, final ComponentConfiguration componentConfiguration, final ViewDescription description, final ViewConfiguration configuration) throws DeploymentUnitProcessingException {

                configuration.addClientPostConstructInterceptor(org.jboss.invocation.Interceptors.getTerminalInterceptorFactory(), InterceptorOrder.ClientPostConstruct.TERMINAL_INTERCEPTOR);
                configuration.addClientPreDestroyInterceptor(org.jboss.invocation.Interceptors.getTerminalInterceptorFactory(), InterceptorOrder.ClientPreDestroy.TERMINAL_INTERCEPTOR);

                //loop over methods looking for create methods:
                for (Method method : configuration.getProxyFactory().getCachedMethods()) {
                    if (method.getName().startsWith("create")) {
                        //we have a create method
                        if (ejbObjectView == null) {
                            throw EjbLogger.ROOT_LOGGER.invalidEjbLocalInterface(componentDescription.getComponentName());
                        }

                        Method initMethod;
                        if (ejbComponentDescription instanceof StatelessComponentDescription) {
                            initMethod = null;
                        } else if (ejbComponentDescription instanceof StatefulComponentDescription) {
                            initMethod = resolveStatefulInitMethod((StatefulComponentDescription) ejbComponentDescription, method);
                            if (initMethod == null) {
                                continue;
                            }
                        } else {
                            throw EjbLogger.ROOT_LOGGER.localHomeNotAllow(ejbComponentDescription);
                        }

                        final SessionBeanHomeInterceptorFactory factory = new SessionBeanHomeInterceptorFactory(initMethod);
                        //add a dependency on the view to create

                        configuration.getDependencies().add(new DependencyConfigurator<ViewService>() {
                            @Override
                            public void configureDependency(final ServiceBuilder<?> serviceBuilder, final ViewService service) throws DeploymentUnitProcessingException {
                                serviceBuilder.addDependency(ejbObjectView.getServiceName(), ComponentView.class, factory.getViewToCreate());
                            }
                        });

                        //add the interceptor
                        configuration.addClientInterceptor(method, ViewDescription.CLIENT_DISPATCHER_INTERCEPTOR_FACTORY, InterceptorOrder.Client.CLIENT_DISPATCHER);
                        configuration.addViewInterceptor(method, factory, InterceptorOrder.View.HOME_METHOD_INTERCEPTOR);

                    } else if (method.getName().equals("getEJBMetaData") && method.getParameterCount() == 0 && ((EJBViewDescription)description).getMethodIntf() == MethodInterfaceType.Home) {

                        final Class<?> ejbObjectClass;
                        try {
                            ejbObjectClass = ClassLoadingUtils.loadClass(ejbObjectView.getViewClassName(), context.getDeploymentUnit());
                        } catch (ClassNotFoundException e) {
                            throw EjbLogger.ROOT_LOGGER.failedToLoadViewClassForComponent(e, componentDescription.getComponentName());
                        }
                        final EjbMetadataInterceptor factory = new EjbMetadataInterceptor(ejbObjectClass, configuration.getViewClass().asSubclass(EJBHome.class), null, true, componentDescription instanceof StatelessComponentDescription);

                        //add a dependency on the view to create
                        componentConfiguration.getStartDependencies().add(new DependencyConfigurator<ComponentStartService>() {
                            @Override
                            public void configureDependency(final ServiceBuilder<?> serviceBuilder, final ComponentStartService service) throws DeploymentUnitProcessingException {
                                serviceBuilder.addDependency(configuration.getViewServiceName(), ComponentView.class, factory.getHomeView());
                            }
                        });
                        //add the interceptor
                        configuration.addClientInterceptor(method, ViewDescription.CLIENT_DISPATCHER_INTERCEPTOR_FACTORY, InterceptorOrder.Client.CLIENT_DISPATCHER);
                        configuration.addViewInterceptor(method, new ImmediateInterceptorFactory(factory), InterceptorOrder.View.HOME_METHOD_INTERCEPTOR);

                    } else if (method.getName().equals("remove") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
                        configuration.addClientInterceptor(method, ViewDescription.CLIENT_DISPATCHER_INTERCEPTOR_FACTORY, InterceptorOrder.Client.CLIENT_DISPATCHER);
                        configuration.addViewInterceptor(method, InvalidRemoveExceptionMethodInterceptor.FACTORY, InterceptorOrder.View.INVALID_METHOD_EXCEPTION);
                    } else if (method.getName().equals("remove") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Handle.class) {
                        configuration.addClientInterceptor(method, ViewDescription.CLIENT_DISPATCHER_INTERCEPTOR_FACTORY, InterceptorOrder.Client.CLIENT_DISPATCHER);
                        configuration.addViewInterceptor(method, HomeRemoveInterceptor.FACTORY, InterceptorOrder.View.HOME_METHOD_INTERCEPTOR);
                    }

                }
            }

        });
    }


    private Method resolveStatefulInitMethod(final StatefulComponentDescription description, final Method method) throws DeploymentUnitProcessingException {

        //for a SFSB we need to resolve the corresponding init method for this create method

        Method initMethod = null;
        //first we try and resolve methods that have additiona resolution data associated with them
        for (Map.Entry<Method, String> entry : description.getInitMethods().entrySet()) {
            String name = entry.getValue();
            Method init = entry.getKey();
            if (name != null
                    && Arrays.equals(init.getParameterTypes(), method.getParameterTypes())
                    && init.getName().equals(name)) {
                initMethod = init;
            }
        }
        //now try and resolve the init methods with no additional resolution data
        if (initMethod == null) {
            for (Map.Entry<Method, String> entry : description.getInitMethods().entrySet()) {
                Method init = entry.getKey();
                if (entry.getValue() == null
                        && Arrays.equals(init.getParameterTypes(), method.getParameterTypes())) {
                    initMethod = init;
                    break;
                }
            }
        }
        if (initMethod == null) {
            for (Class<?> exceptionClass : method.getExceptionTypes()) {
                if (jakarta.ejb.CreateException.class == exceptionClass) {
                    throw EjbLogger.ROOT_LOGGER.failToCallEjbCreateForHomeInterface(method, description.getEJBClassName());
                }
            }
        }
        return initMethod;
    }

}
