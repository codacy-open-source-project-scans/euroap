/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.mdb.cdi;

import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import org.jboss.as.test.integration.ejb.mdb.JMSMessagingUtil;
import org.junit.Assert;

/**
 * @author baranowb
 *
 */
@Stateless()
public class MDBProxyBean implements MDBProxy{

    @EJB(mappedName = "java:module/JMSMessagingUtil")
    private JMSMessagingUtil jmsUtil;

    @Resource(mappedName = REPLY_QUEUE_JNDI_NAME)
    private Queue replyQueue;

    @Resource(mappedName = QUEUE_JNDI_NAME)
    private Queue queue;

    @Override
    public void trigger() throws Exception {
        final String goodMorning = "Good morning";
        // send as ObjectMessage
        // this.jmsUtil = (JMSMessagingUtil) getInitialContext().lookup(getEJBJNDIBinding());
        this.jmsUtil.sendTextMessage(goodMorning, this.queue, this.replyQueue);
        // wait for an reply
        final Message reply = this.jmsUtil.receiveMessage(replyQueue, 5000);
        // test the reply
        final TextMessage textMessage = (TextMessage) reply;
        Assert.assertEquals("Unexpected reply message on reply queue: " + this.replyQueue, CdiIntegrationMDB.REPLY,
                textMessage.getText());
    }
}
