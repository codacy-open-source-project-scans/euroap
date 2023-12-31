/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.weld.arquillian;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.as.server.deployment.SetupAction;
import org.jboss.as.weld.logging.WeldLogger;
import org.jboss.msc.service.ServiceName;
import org.jboss.weld.Container;
import org.jboss.weld.context.bound.BoundConversationContext;
import org.jboss.weld.context.bound.BoundLiteral;
import org.jboss.weld.context.bound.BoundRequest;
import org.jboss.weld.context.bound.BoundRequestContext;
import org.jboss.weld.context.bound.BoundSessionContext;
import org.jboss.weld.context.bound.MutableBoundRequest;

/**
 * Sets up the session, request and conversation contexts for a weld deployment
 *
 * @author Stuart Douglas
 *
 */
public class WeldContextSetup implements SetupAction {

    private static class ContextMapThreadLocal extends ThreadLocal<Map<String, Object>> {
        @Override
        protected Map<String, Object> initialValue() {
            return new ConcurrentHashMap<String, Object>();
        }
    }

    private static final String STANDARD_BEAN_MANAGER_JNDI_NAME = "java:comp/BeanManager";

    private final ThreadLocal<Map<String, Object>> sessionContexts = new ContextMapThreadLocal();
    private final ThreadLocal<Map<String, Object>> requestContexts = new ContextMapThreadLocal();
    private final ThreadLocal<BoundRequest> boundRequests = new ThreadLocal<BoundRequest>();

    @SuppressWarnings("unchecked")
    public void setup(Map<String, Object> properties) {
        try {
            final BeanManager manager = (BeanManager) new InitialContext().lookup(STANDARD_BEAN_MANAGER_JNDI_NAME);

            if (manager != null && Container.available()) {

                final Bean<BoundSessionContext> sessionContextBean = (Bean<BoundSessionContext>) manager.resolve(manager
                        .getBeans(BoundSessionContext.class, BoundLiteral.INSTANCE));
                CreationalContext<?> ctx = manager.createCreationalContext(sessionContextBean);
                final BoundSessionContext sessionContext = (BoundSessionContext) manager.getReference(sessionContextBean,
                        BoundSessionContext.class, ctx);
                sessionContext.associate(sessionContexts.get());
                sessionContext.activate();

                final Bean<BoundRequestContext> requestContextBean = (Bean<BoundRequestContext>) manager.resolve(manager
                        .getBeans(BoundRequestContext.class, BoundLiteral.INSTANCE));
                ctx = manager.createCreationalContext(requestContextBean);
                final BoundRequestContext requestContext = (BoundRequestContext) manager.getReference(requestContextBean,
                        BoundRequestContext.class, ctx);
                requestContext.associate(requestContexts.get());
                requestContext.activate();

                final Bean<BoundConversationContext> conversationContextBean = (Bean<BoundConversationContext>) manager
                        .resolve(manager.getBeans(BoundConversationContext.class, BoundLiteral.INSTANCE));
                ctx = manager.createCreationalContext(conversationContextBean);
                final BoundConversationContext conversationContext = (BoundConversationContext) manager.getReference(
                        conversationContextBean, BoundConversationContext.class, ctx);
                BoundRequest request = new MutableBoundRequest(requestContexts.get(), sessionContexts.get());
                boundRequests.set(request);
                conversationContext.associate(request);
                conversationContext.activate();
            }
        } catch (NamingException e) {
            WeldLogger.ROOT_LOGGER.failedToSetupWeldContexts(e);
        }
    }

    @SuppressWarnings("unchecked")
    public void teardown(Map<String, Object> properties) {
        try {
            final BeanManager manager = (BeanManager) new InitialContext().lookup("java:comp/BeanManager");

            if (manager != null && Container.available()) {
                final Bean<BoundSessionContext> sessionContextBean = (Bean<BoundSessionContext>) manager.resolve(manager.getBeans(
                        BoundSessionContext.class, BoundLiteral.INSTANCE));
                CreationalContext<?> ctx = manager.createCreationalContext(sessionContextBean);
                final BoundSessionContext sessionContext = (BoundSessionContext) manager.getReference(sessionContextBean,
                        BoundSessionContext.class, ctx);
                sessionContext.invalidate();
                sessionContext.deactivate();
                sessionContext.dissociate(sessionContexts.get());

                final Bean<BoundRequestContext> requestContextBean = (Bean<BoundRequestContext>) manager.resolve(manager.getBeans(
                        BoundRequestContext.class, BoundLiteral.INSTANCE));
                ctx = manager.createCreationalContext(requestContextBean);
                final BoundRequestContext requestContext = (BoundRequestContext) manager.getReference(requestContextBean,
                        BoundRequestContext.class, ctx);
                requestContext.invalidate();
                requestContext.deactivate();
                requestContext.dissociate(requestContexts.get());

                final Bean<BoundConversationContext> conversationContextBean = (Bean<BoundConversationContext>) manager
                        .resolve(manager.getBeans(BoundConversationContext.class, BoundLiteral.INSTANCE));
                ctx = manager.createCreationalContext(conversationContextBean);
                final BoundConversationContext conversationContext = (BoundConversationContext) manager.getReference(
                        conversationContextBean, BoundConversationContext.class, ctx);
                conversationContext.invalidate();
                conversationContext.deactivate();
                conversationContext.dissociate(boundRequests.get());
            }
        } catch (NamingException e) {
            WeldLogger.ROOT_LOGGER.failedToTearDownWeldContexts(e);
        } finally {
            sessionContexts.remove();
            requestContexts.remove();
            boundRequests.remove();
        }
    }

    @Override
    public Set<ServiceName> dependencies() {
        return Collections.emptySet();
    }

    @Override
    public int priority() {
        return 100;
    }
}
