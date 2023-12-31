/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.web.common;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;

import jakarta.el.ExpressionFactory;
import jakarta.servlet.ServletContext;

/**
 * @author Stuart Douglas
 */
public interface ExpressionFactoryWrapper {

    AttachmentKey<AttachmentList<ExpressionFactoryWrapper>> ATTACHMENT_KEY = AttachmentKey.createList(ExpressionFactoryWrapper.class);

    ExpressionFactory wrap(ExpressionFactory expressionFactory, ServletContext servletContext);

}
