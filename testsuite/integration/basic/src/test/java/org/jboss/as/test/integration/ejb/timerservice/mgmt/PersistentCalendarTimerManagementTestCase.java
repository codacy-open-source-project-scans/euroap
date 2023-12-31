/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.timerservice.mgmt;

import java.io.Serializable;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * Test non persistent interval timer.
 *
 * @author: baranowb
 */
@RunWith(Arquillian.class)
@RunAsClient
@Ignore("Intermittently fails")
public class PersistentCalendarTimerManagementTestCase extends AbstractTimerManagementTestCase {

    @Deployment
    public static Archive createDeployment() {
        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, getArchiveName());
        ejbJar.addClasses(AbstractTimerManagementTestCase.class, CalendarTimerBean.class, AbstractTimerBean.class, SimpleFace.class);
        return ejbJar;
    }

    @After
    public void clean() {
        super.clean();
    }

    @Before
    public void setup() throws Exception {
        super.setup();
    }

    @Test
    @InSequence(1)
    public void testResourceExistence() throws Exception {
        super.testResourceExistence();
    }

    @Test
    @InSequence(2)
    public void testSuspendAndActivate() throws Exception {
        super.testSuspendAndActivate();
    }

    @Test
    @InSequence(3)
    public void testCancel() throws Exception {
        super.testCancel();
    }

    @Test
    @InSequence(4)
    public void testTrigger() throws Exception {
        super.testTrigger();
    }

    @Test
    @InSequence(5)
    public void testSuspendAndTrigger() throws Exception {
        super.testSuspendAndTrigger();
    }

    protected String getBeanClassName() {
        return CalendarTimerBean.class.getSimpleName();
    }

    private Serializable info = "PersistentCalendarTimerCLITestCase";

    @Override
    protected Serializable getInfo() {
        return info;
    }

    @Override
    protected boolean isPersistent() {
        return true;
    }

    @Override
    protected void assertCalendar(final ModelNode timerDetails) {
        Assert.assertTrue("Calendar", this.isCalendarTimer(timerDetails));
    }
}
