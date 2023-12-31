/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.picketlink.federation.model.handlers;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Enum defining alias for each supported built-in org.picketlink.idm.credential.handler.CredentialHandler provided by
 * PicketLink. The alias is used in the configuration without using the full qualified name of a type.</p>
 *
 * @author Pedro Igor
 */
public enum HandlerTypeEnum {

    // handlers
    SAML2_ISSUER_TRUST_HANDLER("SAML2IssuerTrustHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2IssuerTrustHandler"),
    SAML2_AUTHENTICATION_HANDLER("SAML2AuthenticationHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2AuthenticationHandler"),
    ROLES_GENERATION_HANDLER("RolesGenerationHandler", "org.picketlink.identity.federation.web.handlers.saml2.RolesGenerationHandler"),
    SAML2_ATTRIBUTE_HANDLER("SAML2AttributeHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2AttributeHandler"),
    SAML2_ENCRYPTION_HANDLER("SAML2EncryptionHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2EncryptionHandler"),
    SAML2_IN_RESPONSE_VERIFICATION_HANDLER("SAML2InResponseToVerificationHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2InResponseToVerificationHandler"),
    SAML2_LOGOUT_HANDLER("SAML2LogOutHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2LogOutHandler"),
    SAML2_SIGNATURE_GENERATION_HANDLER("SAML2SignatureGenerationHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2SignatureGenerationHandler"),
    SAML2_SIGNATURE_VALIDATION_HANDLER("SAML2SignatureValidationHandler", "org.picketlink.identity.federation.web.handlers.saml2.SAML2SignatureValidationHandler");


    private static final Map<String, HandlerTypeEnum> types = new HashMap<String, HandlerTypeEnum>();

    static {
        for (HandlerTypeEnum element : values()) {
            types.put(element.getAlias(), element);
        }
    }

    private final String alias;
    private final String type;

    private HandlerTypeEnum(String alias, String type) {
        this.alias = alias;
        this.type = type;
    }

    public static String forType(String alias) {
        HandlerTypeEnum resolvedType = types.get(alias);

        if (resolvedType != null) {
            return resolvedType.getType();
        }

        return null;
    }

    @Override
    public String toString() {
        return this.alias;
    }

    String getAlias() {
        return this.alias;
    }

    String getType() {
        return this.type;
    }
}
