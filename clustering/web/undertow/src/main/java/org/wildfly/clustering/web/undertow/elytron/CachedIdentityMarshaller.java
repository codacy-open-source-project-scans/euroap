/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.web.undertow.elytron;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.cache.CachedIdentity;

/**
 * Marshaller for a {@link CachedIdentity}.
 * @author Paul Ferraro
 */
public class CachedIdentityMarshaller implements ProtoStreamMarshaller<CachedIdentity> {

    private static final int MECHANISM_INDEX = 1;
    private static final int NON_PROGRAMMIC_NAME_INDEX = 2;
    private static final int PROGRAMMATIC_NAME_INDEX = 3;
    private static final int ROLE_INDEX = 4;

    private static final String DEFAULT_MECHANISM = HttpServletRequest.FORM_AUTH;

    @Override
    public CachedIdentity readFrom(ProtoStreamReader reader) throws IOException {
        String mechanism = DEFAULT_MECHANISM;
        String name = null;
        boolean programmatic = false;
        List<String> roles = new LinkedList<>();
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            switch (WireType.getTagFieldNumber(tag)) {
                case MECHANISM_INDEX:
                    mechanism = reader.readString();
                    break;
                case PROGRAMMATIC_NAME_INDEX:
                    programmatic = true;
                case NON_PROGRAMMIC_NAME_INDEX:
                    name = reader.readString();
                    break;
                case ROLE_INDEX:
                    roles.add(reader.readString());
                    break;
                default:
                    reader.skipField(tag);
            }
        }
        return new CachedIdentity(mechanism, programmatic, new NamePrincipal(name), toSet(roles));
    }

    private static Set<String> toSet(List<String> roles) {
        switch (roles.size()) {
            case 0:
                return Set.of();
            case 1:
                return Set.of(roles.get(0));
            default:
                return Set.of(roles.toArray(String[]::new));
        }
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, CachedIdentity identity) throws IOException {
        String mechanism = identity.getMechanismName();
        if (!mechanism.equals(DEFAULT_MECHANISM)) {
            writer.writeString(MECHANISM_INDEX, mechanism);
        }
        String name = identity.getName();
        if (name != null) {
            writer.writeString(identity.isProgrammatic() ? PROGRAMMATIC_NAME_INDEX : NON_PROGRAMMIC_NAME_INDEX, name);
        }
        for (String role : identity.getRoles()) {
            writer.writeString(ROLE_INDEX, role);
        }
    }

    @Override
    public Class<? extends CachedIdentity> getJavaClass() {
        return CachedIdentity.class;
    }
}
