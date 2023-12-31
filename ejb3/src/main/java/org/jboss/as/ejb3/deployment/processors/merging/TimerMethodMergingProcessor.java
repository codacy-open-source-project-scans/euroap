/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.merging;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.ejb.Schedule;
import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.TimedObject;
import jakarta.ejb.Timeout;

import org.jboss.as.ee.component.EEApplicationClasses;
import org.jboss.as.ee.metadata.MethodAnnotationAggregator;
import org.jboss.as.ee.metadata.RuntimeAnnotationInformation;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.timerservice.spi.AutoTimer;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.deployment.processors.dd.MethodResolutionUtils;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.metadata.ejb.common.IScheduleTarget;
import org.jboss.metadata.ejb.common.ITimeoutTarget;
import org.jboss.metadata.ejb.spec.EnterpriseBeanMetaData;
import org.jboss.metadata.ejb.spec.NamedMethodMetaData;
import org.jboss.metadata.ejb.spec.ScheduleMetaData;
import org.jboss.metadata.ejb.spec.TimerMetaData;

/**
 * Deployment unit processor that merges the annotation information with the information in the deployment descriptor
 *
 * @author Stuart Douglas
 */
public class TimerMethodMergingProcessor extends AbstractMergingProcessor<EJBComponentDescription> {


    public TimerMethodMergingProcessor() {
        super(EJBComponentDescription.class);
    }

    @Override
    protected void handleAnnotations(final DeploymentUnit deploymentUnit, final EEApplicationClasses applicationClasses, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final EJBComponentDescription description) throws DeploymentUnitProcessingException {
        final RuntimeAnnotationInformation<AutoTimer> scheduleAnnotationData = MethodAnnotationAggregator.runtimeAnnotationInformation(componentClass, applicationClasses, deploymentReflectionIndex, Schedule.class);
        final Set<Method> timerAnnotationData = MethodAnnotationAggregator.runtimeAnnotationPresent(componentClass, applicationClasses, deploymentReflectionIndex, Timeout.class);
        final Method timeoutMethod;
        if (timerAnnotationData.size() > 1) {
            throw EjbLogger.ROOT_LOGGER.componentClassHasMultipleTimeoutAnnotations(componentClass);
        } else if (timerAnnotationData.size() == 1) {
            timeoutMethod = timerAnnotationData.iterator().next();
        } else {
            timeoutMethod = null;
        }
        description.setTimeoutMethod(timeoutMethod);


        //now for the schedule methods
        for (Map.Entry<Method, List<AutoTimer>> entry : scheduleAnnotationData.getMethodAnnotations().entrySet()) {

            for (AutoTimer timer : entry.getValue()) {
                description.addScheduleMethod(entry.getKey(), timer);
            }
        }
    }

    @Override
    protected void handleDeploymentDescriptor(final DeploymentUnit deploymentUnit, final DeploymentReflectionIndex deploymentReflectionIndex, final Class<?> componentClass, final EJBComponentDescription description) throws DeploymentUnitProcessingException {
        final EnterpriseBeanMetaData descriptorData = description.getDescriptorData();
        if (descriptorData != null
                && (description.isSession() || description.isMessageDriven())) {
            assert descriptorData instanceof ITimeoutTarget : descriptorData + " is not an ITimeoutTarget";
            ITimeoutTarget target = (ITimeoutTarget) descriptorData;
            if (target.getTimeoutMethod() != null) {
                parseTimeoutMethod(target, description, componentClass, deploymentReflectionIndex);
            }
            parseScheduleMethods(descriptorData, description, componentClass, deploymentReflectionIndex);
        }

        //now check to see if the class implemented TimedObject
        //if so, this will take precedence over annotations
        //or the method specified in the deployment descriptor
        if (TimedObject.class.isAssignableFrom(componentClass)) {
            Class<?> c = componentClass;
            while (c != null && c != Object.class) {
                final ClassReflectionIndex index = deploymentReflectionIndex.getClassIndex(c);
                //TimedObject takes precedence
                Method method = index.getMethod(Void.TYPE, "ejbTimeout", jakarta.ejb.Timer.class);
                if (method != null) {

                    final Method otherMethod = description.getTimeoutMethod();
                    if (otherMethod != null && !otherMethod.equals(method)) {
                        throw EjbLogger.ROOT_LOGGER.invalidEjbEntityTimeout("3.1 18.2.5.3", componentClass);
                    }
                    description.setTimeoutMethod(method);
                    break;
                }
                c = c.getSuperclass();
            }
        }
    }


    private void parseScheduleMethods(final EnterpriseBeanMetaData beanMetaData, final EJBComponentDescription sessionBean, final Class<?> componentClass, final DeploymentReflectionIndex deploymentReflectionIndex) throws DeploymentUnitProcessingException {
        if (beanMetaData instanceof IScheduleTarget) {
            IScheduleTarget md = (IScheduleTarget) beanMetaData;
            if (md.getTimers() != null) {
                for (final TimerMetaData timer : md.getTimers()) {
                    AutoTimer autoTimer = new AutoTimer();

                    autoTimer.getTimerConfig().setInfo(timer.getInfo());
                    autoTimer.getTimerConfig().setPersistent(timer.isPersistent());

                    final ScheduleExpression scheduleExpression = autoTimer.getScheduleExpression();
                    final ScheduleMetaData schedule = timer.getSchedule();
                    if (schedule != null) {
                        scheduleExpression.dayOfMonth(schedule.getDayOfMonth());
                        scheduleExpression.dayOfWeek(schedule.getDayOfWeek());
                        scheduleExpression.hour(schedule.getHour());
                        scheduleExpression.minute(schedule.getMinute());
                        scheduleExpression.month(schedule.getMonth());
                        scheduleExpression.second(schedule.getSecond());
                        scheduleExpression.year(schedule.getYear());
                    }
                    if (timer.getEnd() != null) {
                        scheduleExpression.end(timer.getEnd().getTime());
                    }
                    if (timer.getStart() != null) {
                        scheduleExpression.start(timer.getStart().getTime());
                    }
                    scheduleExpression.timezone(timer.getTimezone());
                    sessionBean.addScheduleMethod(MethodResolutionUtils.resolveMethod(timer.getTimeoutMethod(), componentClass, deploymentReflectionIndex), autoTimer);

                }
            }

        }
    }


    private void parseTimeoutMethod(final ITimeoutTarget beanMetaData, final EJBComponentDescription sessionBean, final Class<?> componentClass, final DeploymentReflectionIndex deploymentReflectionIndex) throws DeploymentUnitProcessingException {
        //resolve timeout methods
        final NamedMethodMetaData methodData = beanMetaData.getTimeoutMethod();
        sessionBean.setTimeoutMethod(MethodResolutionUtils.resolveMethod(methodData, componentClass, deploymentReflectionIndex));
    }
}
