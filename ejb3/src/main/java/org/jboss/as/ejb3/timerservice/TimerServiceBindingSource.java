/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.timerservice;

import org.jboss.as.ee.component.Component;
import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.ejb3.context.CurrentInvocationContext;
import org.jboss.as.naming.ContextListManagedReferenceFactory;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.invocation.InterceptorContext;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;

/**
 * An {@link InjectionSource} which returns a {@link ManagedReference reference} to a {@link jakarta.ejb.TimerService}
 * <p/>
 * @author Jaikiran Pai
 */
public class TimerServiceBindingSource extends InjectionSource {

    private static final TimerServiceManagedReferenceFactory TIMER_SERVICE_MANAGED_REFERENCE_FACTORY_INSTANCE = new TimerServiceManagedReferenceFactory();

    @Override
    public void getResourceValue(ResolutionContext resolutionContext, ServiceBuilder<?> serviceBuilder, DeploymentPhaseContext phaseContext, Injector<ManagedReferenceFactory> injector) throws DeploymentUnitProcessingException {
        injector.inject(TIMER_SERVICE_MANAGED_REFERENCE_FACTORY_INSTANCE);
    }

    /**
     * {@link ManagedReferenceFactory} for returning a {@link ManagedReference} to a {@link jakarta.ejb.TimerService}
     */
    private static class TimerServiceManagedReferenceFactory implements ContextListManagedReferenceFactory {

        private final TimerServiceManagedReference timerServiceManagedReference = new TimerServiceManagedReference();

        @Override
        public ManagedReference getReference() {
            return timerServiceManagedReference;
        }

        @Override
        public String getInstanceClassName() {
            return jakarta.ejb.TimerService.class.getName();
        }
    }

    /**
     * A {@link ManagedReference} to a {@link jakarta.ejb.TimerService}
     */
    private static class TimerServiceManagedReference implements ManagedReference {

        @Override
        public void release() {

        }

        @Override
        public Object getInstance() {
            // get the current invocation context and the EJBComponent out of it
            final InterceptorContext currentInvocationContext = CurrentInvocationContext.get();
            final EJBComponent ejbComponent = (EJBComponent) currentInvocationContext.getPrivateData(Component.class);
            if (ejbComponent == null) {
                throw EjbLogger.EJB3_TIMER_LOGGER.failToGetEjbComponent(currentInvocationContext);
            }
            return ejbComponent.getTimerService();
        }
    }


    // All Timer bindings are equivalent since they just use a thread local context
    public boolean equals(Object o) {
        return o instanceof TimerServiceBindingSource;
    }

    public int hashCode() {
        return 1;
    }
}
