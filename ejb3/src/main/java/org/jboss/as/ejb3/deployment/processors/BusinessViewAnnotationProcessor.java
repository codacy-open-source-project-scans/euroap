/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.deployment.processors;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.ejb.Local;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Remote;

import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.metadata.MetadataCompleteMarker;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.component.session.SessionBeanComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EjbJarVersion;
import org.jboss.modules.Module;

import static org.jboss.as.ee.component.Attachments.EE_MODULE_DESCRIPTION;
import static org.jboss.as.ejb3.deployment.processors.ViewInterfaces.getPotentialViewInterfaces;

/**
 * Processes {@link Local @Local} and {@link @Remote} annotation of a session bean and sets up the {@link SessionBeanComponentDescription}
 * out of it.
 * <p/>
 *
 * @author Jaikiran Pai
 */
public class BusinessViewAnnotationProcessor implements DeploymentUnitProcessor {

    private final boolean appclient;

    public BusinessViewAnnotationProcessor(final boolean appclient) {
        this.appclient = appclient;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        if (MetadataCompleteMarker.isMetadataComplete(deploymentUnit)) {
            return;
        }

        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(EE_MODULE_DESCRIPTION);
        final Collection<ComponentDescription> componentDescriptions = eeModuleDescription.getComponentDescriptions();
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        if(module == null) {
            return;
        }
        final ClassLoader moduleClassLoader = module.getClassLoader();
        if (componentDescriptions != null) {
            for (ComponentDescription componentDescription : componentDescriptions) {
                if (componentDescription instanceof SessionBeanComponentDescription == false) {
                    continue;
                }
                final Class<?> ejbClass = this.getEjbClass(componentDescription.getComponentClassName(), moduleClassLoader);
                try {
                    this.processViewAnnotations(deploymentUnit, ejbClass, (SessionBeanComponentDescription) componentDescription);
                } catch (Exception e) {
                    throw EjbLogger.ROOT_LOGGER.failedToProcessBusinessInterfaces(ejbClass, e);
                }
            }
        }
        if (appclient) {
            for (ComponentDescription componentDescription : deploymentUnit.getAttachmentList(org.jboss.as.ee.component.Attachments.ADDITIONAL_RESOLVABLE_COMPONENTS)) {
                if (componentDescription instanceof SessionBeanComponentDescription == false) {
                    continue;
                }
                final Class<?> ejbClass = this.getEjbClass(componentDescription.getComponentClassName(), moduleClassLoader);
                try {
                    this.processViewAnnotations(deploymentUnit, ejbClass, (SessionBeanComponentDescription) componentDescription);
                } catch (Exception e) {
                    throw EjbLogger.ROOT_LOGGER.failedToProcessBusinessInterfaces(ejbClass, e);
                }
            }
        }
    }

    /**
     * Processes the session bean for remote and local views and updates the {@link SessionBeanComponentDescription}
     * accordingly
     *
     * @param deploymentUnit The deployment unit
     * @param sessionBeanClass The bean class
     * @param sessionBeanComponentDescription
     *                         The component description
     * @throws DeploymentUnitProcessingException
     *
     */
    private void processViewAnnotations(final DeploymentUnit deploymentUnit, final Class<?> sessionBeanClass, final SessionBeanComponentDescription sessionBeanComponentDescription) throws DeploymentUnitProcessingException {
        final Collection<Class<?>> remoteBusinessInterfaces = this.getRemoteBusinessInterfaces(deploymentUnit, sessionBeanClass);
        if (remoteBusinessInterfaces != null && !remoteBusinessInterfaces.isEmpty()) {
            sessionBeanComponentDescription.addRemoteBusinessInterfaceViews(this.toString(remoteBusinessInterfaces));
        }

        // fetch the local business interfaces of the bean
        Collection<Class<?>> localBusinessInterfaces = this.getLocalBusinessInterfaces(deploymentUnit, sessionBeanClass);
        if (localBusinessInterfaces != null && !localBusinessInterfaces.isEmpty()) {
            sessionBeanComponentDescription.addLocalBusinessInterfaceViews(this.toString(localBusinessInterfaces));
        }

        if (hasNoInterfaceView(sessionBeanClass)) {
            sessionBeanComponentDescription.addNoInterfaceView();
        }

        // Enterprise Beans 3.1 FR 4.9.7 & 4.9.8, if the bean exposes no views
        if (hasNoViews(sessionBeanComponentDescription)) {
            final Set<Class<?>> potentialBusinessInterfaces = getPotentialBusinessInterfaces(sessionBeanClass);
            if (potentialBusinessInterfaces.isEmpty()) {
                sessionBeanComponentDescription.addNoInterfaceView();
            } else if (potentialBusinessInterfaces.size() == 1) {
                sessionBeanComponentDescription.addLocalBusinessInterfaceViews(potentialBusinessInterfaces.iterator().next().getName());
            } else if (isEjbVersionGreaterThanOrEqualTo32(deploymentUnit)) {
                // Jakarta Enterprise Beans 3.2 spec states (section 4.9.7):
                // ... or if the bean class is annotated with neither the Local nor the Remote annotation, all implemented interfaces (excluding the interfaces listed above)
                // are assumed to be local business interfaces of the bean
                sessionBeanComponentDescription.addLocalBusinessInterfaceViews(toString(potentialBusinessInterfaces));
            }
        }
    }

    private Collection<Class<?>> getRemoteBusinessInterfaces(final DeploymentUnit deploymentUnit, final Class<?> sessionBeanClass) throws DeploymentUnitProcessingException {
        final Remote remoteViewAnnotation;
        try {
            remoteViewAnnotation = sessionBeanClass.getAnnotation(Remote.class);
        } catch (ArrayStoreException e) {
            // https://bugs.openjdk.java.net/browse/JDK-7183985
            // Class.findAnnotation() has a bug under JDK < 11 which throws ArrayStoreException
            throw EjbLogger.ROOT_LOGGER.missingClassInAnnotation(Remote.class.getSimpleName(), sessionBeanClass.getName());
        }
        if (remoteViewAnnotation == null) {
            Collection<Class<?>> interfaces = getBusinessInterfacesFromInterfaceAnnotations(sessionBeanClass, Remote.class);
            if (!interfaces.isEmpty()) {
                return interfaces;
            }
            return Collections.emptySet();
        }
        Class<?>[] remoteViews = remoteViewAnnotation.value();
        if (remoteViews == null || remoteViews.length == 0) {
            Set<Class<?>> interfaces = getPotentialBusinessInterfaces(sessionBeanClass);
            // For version < 3.2, the Jakarta Enterprise Beans spec didn't allow more than one implementing interfaces to be considered as remote when the bean class had the @Remote annotation without any explicit value.
            // Jakarta Enterprise Beans 3.2 allows it (core spec, section 4.9.7)
            if (interfaces.size() != 1 && !isEjbVersionGreaterThanOrEqualTo32(deploymentUnit)) {
                throw EjbLogger.ROOT_LOGGER.beanWithRemoteAnnotationImplementsMoreThanOneInterface(sessionBeanClass);
            }
            return interfaces;
        }
        return Arrays.asList(remoteViews);
    }

    private Collection<Class<?>> getLocalBusinessInterfaces(final DeploymentUnit deploymentUnit, final Class<?> sessionBeanClass) throws DeploymentUnitProcessingException {
        final Local localViewAnnotation;
        try {
            localViewAnnotation = sessionBeanClass.getAnnotation(Local.class);
        } catch (ArrayStoreException e) {
            // https://bugs.openjdk.java.net/browse/JDK-7183985
            // Class.findAnnotation() has a bug under JDK < 11 which throws ArrayStoreException
            throw EjbLogger.ROOT_LOGGER.missingClassInAnnotation(Local.class.getSimpleName(), sessionBeanClass.getName());
        }
        if (localViewAnnotation == null) {
            Collection<Class<?>> interfaces = getBusinessInterfacesFromInterfaceAnnotations(sessionBeanClass, Local.class);
            if (!interfaces.isEmpty()) {
                return interfaces;
            }
            return Collections.emptySet();
        }
        Class<?>[] localViews = localViewAnnotation.value();
        if (localViews == null || localViews.length == 0) {
            Set<Class<?>> interfaces = getPotentialBusinessInterfaces(sessionBeanClass);
            // For version < 3.2, the Jakarta Enterprise Beans spec didn't allow more than one implementing interfaces to be considered as local when the bean class had the @Local annotation without any explicit value.
            // Jakarta Enterprise Beans 3.2 allows it (core spec, section 4.9.7)
            if (interfaces.size() != 1 && !isEjbVersionGreaterThanOrEqualTo32(deploymentUnit)) {
                throw EjbLogger.ROOT_LOGGER.beanWithLocalAnnotationImplementsMoreThanOneInterface(sessionBeanClass);
            }
            return interfaces;
        }

        return Arrays.asList(localViews);
    }

    private static Collection<Class<?>> getBusinessInterfacesFromInterfaceAnnotations(Class<?> sessionBeanClass, Class<? extends Annotation> annotation) throws DeploymentUnitProcessingException {
        final Set<Class<?>> potentialBusinessInterfaces = getPotentialBusinessInterfaces(sessionBeanClass);
        final Set<Class<?>> businessInterfaces = new HashSet<Class<?>>();
        for (Class<?> iface : potentialBusinessInterfaces) {
            try {
                if (iface.getAnnotation(annotation) != null) {
                    businessInterfaces.add(iface);
                }
            } catch (ArrayStoreException e) {
                // https://bugs.openjdk.java.net/browse/JDK-7183985
                // Class.findAnnotation() has a bug under JDK < 11 which throws ArrayStoreException
                throw EjbLogger.ROOT_LOGGER.missingClassInAnnotation(annotation.getSimpleName(), iface.getName());
            }
        }
        return businessInterfaces;
    }

    /**
     * Returns all interfaces implemented by a bean that are eligible to be business interfaces
     *
     * @param sessionBeanClass The bean class
     * @return A collection of all potential business interfaces
     */
    private static Set<Class<?>> getPotentialBusinessInterfaces(Class<?> sessionBeanClass) {
        return getPotentialViewInterfaces(sessionBeanClass);
    }

    /**
     * Returns true if the <code>sessionBeanClass</code> has a {@link LocalBean no-interface view annotation}.
     * Else returns false.
     *
     * @param sessionBeanClass The session bean {@link Class class}
     * @return
     */
    private static boolean hasNoInterfaceView(Class<?> sessionBeanClass) {
        return sessionBeanClass.getAnnotation(LocalBean.class) != null;
    }

    private static boolean hasNoViews(SessionBeanComponentDescription sessionBeanComponentDescription) {
        return sessionBeanComponentDescription.getViews() == null || sessionBeanComponentDescription.getViews().isEmpty();
    }

    private Class<?> getEjbClass(String className, ClassLoader cl) throws DeploymentUnitProcessingException {
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    private Collection<String> toString(Collection<Class<?>> classes) {
        final Collection<String> classNames = new ArrayList<String>(classes.size());
        for (Class<?> klass : classes) {
            classNames.add(klass.getName());
        }
        return classNames;
    }

    private boolean isEjbVersionGreaterThanOrEqualTo32(final DeploymentUnit deploymentUnit) {
        if (deploymentUnit == null) {
            return false;
        }
        final EjbJarMetaData ejbJarMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        // if there's no EjbJarMetadata then it means that there's no ejb-jar.xml. That effectively means that the version of this Jakarta Enterprise Beans deployment is the "latest"
        // which in this case (i.e. starting WildFly 8 version) is "greater than or equal to 3.2". Hence return true.
        if (ejbJarMetaData == null) {
            return true;
        }
        // let the ejb jar metadata tell us what the version is
        return ejbJarMetaData.isVersionGreaterThanOrEqual(EjbJarVersion.EJB_3_2);
    }
}
