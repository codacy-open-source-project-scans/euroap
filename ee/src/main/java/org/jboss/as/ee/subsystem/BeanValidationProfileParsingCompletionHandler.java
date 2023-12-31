/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ee.subsystem;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.dmr.ModelNode;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

/**
 * {@link org.jboss.as.controller.parsing.ProfileParsingCompletionHandler} that installs a default Jakarta Bean Validation extension and subsystem if the
 * profile included a legacy EE subsystem version but not Jakarta Bean Validation subsystem.
 *
 * @author Eduardo Martins
 */
public class BeanValidationProfileParsingCompletionHandler implements ProfileParsingCompletionHandler {

    private static final String BEAN_VALIDATION_NAMESPACE_PREFIX = "urn:jboss:domain:bean-validation:";

    private static final String BEAN_VALIDATION_SUBSYSTEM = "bean-validation";

    private static final String BEAN_VALIDATION_MODULE = "org.wildfly.extension.bean-validation";


    @Override
    public void handleProfileParsingCompletion(Map<String, List<ModelNode>> profileBootOperations, List<ModelNode> otherBootOperations) {
        List<ModelNode> legacyEEOps = null;
        // Check all namespace versions which include bean validation
        for (Namespace namespace : EnumSet.allOf(Namespace.class)) {
            if (namespace.isBeanValidationIncluded()) {
                legacyEEOps = profileBootOperations.get(namespace.getUriString());
                if (legacyEEOps != null) {
                    break;
                }
            }
        }
        if (legacyEEOps != null) {
            boolean hasBeanValidationOp = false;
            for (String namespace : profileBootOperations.keySet()) {
                if (namespace.startsWith(BEAN_VALIDATION_NAMESPACE_PREFIX)) {
                    hasBeanValidationOp = true;
                    break;
                }
            }
            if (!hasBeanValidationOp) {
                // See if we need to add the extension as well
                boolean hasBeanValidationExtension = false;
                for (ModelNode op : otherBootOperations) {
                    PathAddress pa = PathAddress.pathAddress(op.get(OP_ADDR));
                    if (pa.size() == 1 && EXTENSION.equals(pa.getElement(0).getKey())
                            && BEAN_VALIDATION_MODULE.equals(pa.getElement(0).getValue())) {
                        hasBeanValidationExtension = true;
                        break;
                    }
                }

                if (!hasBeanValidationExtension) {
                    final ModelNode addBeanValidationExtensionOp = new ModelNode();
                    addBeanValidationExtensionOp.get(OP).set(ADD);
                    PathAddress beanValidationExtensionAddress = PathAddress.pathAddress(PathElement.pathElement(EXTENSION, BEAN_VALIDATION_MODULE));
                    addBeanValidationExtensionOp.get(OP_ADDR).set(beanValidationExtensionAddress.toModelNode());
                    addBeanValidationExtensionOp.get(MODULE).set(BEAN_VALIDATION_MODULE);
                    otherBootOperations.add(addBeanValidationExtensionOp);
                }

                // legacy EE subsystem and no bean validation subsystem, add it
                final ModelNode addBeanValidationSubsystemOp = new ModelNode();
                addBeanValidationSubsystemOp.get(OP).set(ADD);
                PathAddress beanValidationSubsystemAddress = PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, BEAN_VALIDATION_SUBSYSTEM));
                addBeanValidationSubsystemOp.get(OP_ADDR).set(beanValidationSubsystemAddress.toModelNode());
                legacyEEOps.add(addBeanValidationSubsystemOp);
            }
        }
    }
}
