/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import static org.jboss.as.ee.component.Attachments.EE_APPLICATION_DESCRIPTION;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.as.ee.component.ComponentView;
import org.jboss.as.ee.component.EEApplicationDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.ee.component.ViewDescription;
import org.jboss.as.ee.component.ViewManagedReferenceFactory;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.component.EJBViewDescription;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.remote.RemoteViewManagedReferenceFactory;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.metadata.ejb.spec.MethodInterfaceType;
import org.jboss.modules.Module;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * Implementation of {@link InjectionSource} responsible for finding a specific bean instance with a bean name and interface.
 *
 * @author John Bailey
 * @author Stuart Douglas
 */
public class EjbInjectionSource extends InjectionSource {
    private final String beanName;
    private final String typeName;
    private final String bindingName;
    private final DeploymentUnit deploymentUnit;
    private final boolean appclient;
    private volatile String error = null;
    private volatile ServiceName resolvedViewName;
    private volatile RemoteViewManagedReferenceFactory remoteFactory;
    private volatile boolean resolved = false;

    public EjbInjectionSource(final String beanName, final String typeName, final String bindingName, final DeploymentUnit deploymentUnit, final boolean appclient) {
        this.beanName = beanName;
        this.typeName = typeName;
        this.bindingName = bindingName;
        this.deploymentUnit = deploymentUnit;
        this.appclient = appclient;
    }

    public EjbInjectionSource(final String typeName, final String bindingName, final DeploymentUnit deploymentUnit, final boolean appclient) {
        this.bindingName = bindingName;
        this.deploymentUnit = deploymentUnit;
        this.appclient = appclient;
        this.beanName = null;
        this.typeName = typeName;
    }

    public void getResourceValue(final ResolutionContext resolutionContext, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) throws DeploymentUnitProcessingException {
        resolve();

        if (error != null) {
            throw new DeploymentUnitProcessingException(error);
        }

        if (remoteFactory != null) {
            //because we are using the Jakarta Enterprise Beans: lookup namespace we do not need a dependency
            injector.inject(remoteFactory);
        } else if (!appclient) {
            //we do not add a dependency if this is the appclient
            //as local injections are simply ignored
            serviceBuilder.addDependency(resolvedViewName, ComponentView.class, new ViewManagedReferenceFactory.Injector(injector));
        }
    }

    /**
     * Checks if this Jakarta Enterprise Beans injection has been resolved yet, and if not resolves it.
     */
    private void resolve() {
        if (!resolved) {
            synchronized (this) {
                if (!resolved) {

                    final Set<ViewDescription> views = getViews();

                    final Set<EJBViewDescription> ejbsForViewName = new HashSet<EJBViewDescription>();
                    for (final ViewDescription view : views) {
                        if (view instanceof EJBViewDescription) {
                            final MethodInterfaceType viewType = ((EJBViewDescription) view).getMethodIntf();
                            // @EJB injection *shouldn't* consider the @WebService endpoint view or MDBs
                            if (viewType == MethodInterfaceType.ServiceEndpoint || viewType == MethodInterfaceType.MessageEndpoint) {
                                continue;
                            }
                            ejbsForViewName.add((EJBViewDescription) view);
                        }
                    }


                    if (ejbsForViewName.isEmpty()) {
                        if (beanName == null) {
                            error = EjbLogger.ROOT_LOGGER.ejbNotFound(typeName, bindingName);
                        } else {
                            error = EjbLogger.ROOT_LOGGER.ejbNotFound(typeName, beanName, bindingName);
                        }
                    } else if (ejbsForViewName.size() > 1) {
                        if (beanName == null) {
                            error = EjbLogger.ROOT_LOGGER.moreThanOneEjbFound(typeName, bindingName, ejbsForViewName);
                        } else {
                            error = EjbLogger.ROOT_LOGGER.moreThanOneEjbFound(typeName, beanName, bindingName, ejbsForViewName);
                        }
                    } else {
                        final EJBViewDescription description = ejbsForViewName.iterator().next();
                        final EJBViewDescription ejbViewDescription = (EJBViewDescription) description;
                        //for remote interfaces we do not want to use a normal binding
                        //we need to bind the remote proxy factory into JNDI instead to get the correct behaviour

                        if (ejbViewDescription.getMethodIntf() == MethodInterfaceType.Remote || ejbViewDescription.getMethodIntf() == MethodInterfaceType.Home) {
                            final EJBComponentDescription componentDescription = (EJBComponentDescription) description.getComponentDescription();
                            final EEModuleDescription moduleDescription = componentDescription.getModuleDescription();
                            final String earApplicationName = moduleDescription.getEarApplicationName();
                            final Supplier<ClassLoader> viewClassLoader = new Supplier<>() {
                                @Override
                                public ClassLoader get() throws IllegalStateException, IllegalArgumentException {
                                    final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
                                    return module != null ? module.getClassLoader() : null;
                                }
                            };
                            remoteFactory = new RemoteViewManagedReferenceFactory(earApplicationName, moduleDescription.getModuleName(), moduleDescription.getDistinctName(), componentDescription.getComponentName(), description.getViewClassName(), componentDescription.isStateful(), viewClassLoader, appclient);
                        }
                        final ServiceName serviceName = description.getServiceName();
                        resolvedViewName = serviceName;
                    }
                    resolved = true;
                }
            }
        }
    }

    private Set<ViewDescription> getViews() {
        final EEApplicationDescription applicationDescription = deploymentUnit.getAttachment(EE_APPLICATION_DESCRIPTION);
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final Set<ViewDescription> componentsForViewName;
        if (beanName != null) {
            componentsForViewName = applicationDescription.getComponents(beanName, typeName, deploymentRoot.getRoot());
        } else {
            componentsForViewName = applicationDescription.getComponentsForViewName(typeName, deploymentRoot.getRoot());
        }
        return componentsForViewName;
    }

    public boolean equals(Object o) {
        if (this == o) { return true; }

        if (!(o instanceof EjbInjectionSource)) { return false; }

        resolve();

        if (error != null) {
            //we can't do a real equals comparison in this case, so just return false
            return false;
        }

        final EjbInjectionSource other = (EjbInjectionSource) o;
        return eq(typeName, other.typeName) && eq(resolvedViewName, other.resolvedViewName);
    }

    public int hashCode() {
        return typeName.hashCode();
    }

    private static boolean eq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
