/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.xts.txnclient;

import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.handler.MessageContext;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.txn.deployment.TransactionRollbackSetupAction;
import org.jboss.as.xts.XTSHandlerDeploymentProcessor;
import org.jboss.as.xts.jandex.EndpointMetaData;
import org.jboss.as.xts.logging.XtsAsLogger;
import org.jboss.wsf.spi.deployment.Endpoint;
import org.jboss.wsf.spi.invocation.Invocation;
import org.wildfly.transaction.client.ContextTransactionManager;

import java.util.List;

/**
 * <p>
 *   Handler integrating the handle message event with WildFly Transaction Client (WFTC) SPI.
 * </p>
 * <p>
 *   This handler manages the outbound processing where it has to be processed
 *   before the {@link org.jboss.jbossts.txbridge.inbound.OptionalJaxWSTxInboundBridgeHandler}.
 *   Check the handler enlistment at {@link XTSHandlerDeploymentProcessor#updateXTSEndpoint(String, EndpointMetaData, List, DeploymentUnit)}.
 * <p>
 * <p>
 *   <i>NOTE:</i> the order of the Jakarta XML Web Services handlers are defined as:
 *   <q>For outbound messages handler processing starts with the first handler in the chain
 *      and proceeds in the same order as the handler chain.
 *      For inbound messages the order of processing is reversed.
 *   </q>
 * </p>
 * <p>
 *   This handler works on outbound side. The inbound side is handled by WS integration class:
 *   {@link org.jboss.as.webservices.invocation.AbstractInvocationHandler#invokeInternal(Endpoint, Invocation)}.
 *   There is the place where WFTC imports the Narayana transaction for the incoming WS message.
 * </p>
 * <p>
 *   The outbound filter is useful for suspending the WFTC wrapper transaction. Otherwise only the underlaying Narayana transaction is suspended
 *   (Narayana XTS txbridge inbound filter does so).
 *   Then when the call leaves EJB there is invoked handler {@link TransactionRollbackSetupAction} verifying existence of the transactions.
 *   If the the WFTC transaction is not suspended then the setup action rolls-back it which leads to an errors in the server log.
 * </p>
 */
public class WildflyTransactionClientTxBridgeIntegrationHandler implements Handler<MessageContext> {

    @Override
    public boolean handleMessage(MessageContext messageContext) {
        final Boolean isOutbound = (Boolean) messageContext.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

        try {
            if (isOutbound != null && isOutbound
                    // suspending context before returning to the client
                    && ContextTransactionManager.getInstance() != null
                    && ContextTransactionManager.getInstance().getStatus() != Status.STATUS_NO_TRANSACTION) {
                ContextTransactionManager.getInstance().suspend();
            }
        } catch (SystemException se) {
            XtsAsLogger.ROOT_LOGGER.cannotGetTransactionStatus(messageContext, se);
        }
        // continue with message handling
        return true;
    }

    @Override
    public boolean handleFault(MessageContext context) {
        // do nothing just continue with processing
        return true;
    }

    @Override
    public void close(MessageContext context) {
        // no action needed
    }

}
