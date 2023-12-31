/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.jboss.as.test.integration.security.common.Utils.createTemporaryFolder;
import static org.jboss.as.test.shared.CliUtils.asAbsolutePath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.logging.Logger;

/**
 * Configuration for properties-realms Elytron resource.
 *
 * @author Josef Cacek
 */
public class PropertiesRealm extends AbstractUserAttributeValuesCapableElement implements SecurityRealm {

    private static final Logger LOGGER = Logger.getLogger(PropertiesRealm.class);

    private final String groupsAttribute;
    private final boolean plainText; // true by default
    private File tempFolder;

    private PropertiesRealm(Builder builder) {
        super(builder);
        this.groupsAttribute = builder.groupsAttribute;
        this.plainText = builder.plainText;
    }

    @Override
    public void create(CLIWrapper cli) throws Exception {
        this.tempFolder = createTemporaryFolder("ely-" + name);
        final Properties usersProperties = new Properties();
        final Properties rolesProperties = new Properties();
        for (UserWithAttributeValues user : getUsersWithAttributeValues()) {
            usersProperties.setProperty(user.getName(), user.getPassword());
            rolesProperties.setProperty(user.getName(), String.join(",", user.getValues()));
        }
        File usersFile = writeProperties(usersProperties, "users.properties");
        File rolesFile = writeProperties(rolesProperties, "roles.properties");

        // /subsystem=elytron/properties-realm=test:add(users-properties={path=/tmp/users.properties, plain-text=true},
        // groups-properties={path=/tmp/groups.properties}, groups-attribute="groups")
        final String groupsAttrStr = groupsAttribute == null ? "" : String.format(", groups-attribute=\"%s\"", groupsAttribute);
        cli.sendLine(String.format(
                "/subsystem=elytron/properties-realm=%s:add(users-properties={path=\"%s\", plain-text=%b}, groups-properties={path=\"%s\"}%s)",
                name, asAbsolutePath(usersFile), plainText, asAbsolutePath(rolesFile), groupsAttrStr));
    }

    @Override
    public void remove(CLIWrapper cli) throws Exception {
        cli.sendLine(String.format("/subsystem=elytron/properties-realm=%s:remove()", name));
        FileUtils.deleteQuietly(tempFolder);
        tempFolder = null;
    }

    /**
     * Creates builder to build {@link PropertiesRealm}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private File writeProperties(Properties properties, String fileName) throws IOException {
        File result = new File(tempFolder, fileName);
        LOGGER.debugv("Creating property file {0}", result);
        try (FileOutputStream fos = new FileOutputStream(result)) {
            if (plainText) {
                properties.store(fos, null);
            } else {
                properties.store(fos, "$REALM_NAME=" + name + "$");
            }
        }
        return result;
    }

    /**
     * Builder to build {@link PropertiesRealm}.
     */
    public static final class Builder extends AbstractUserAttributeValuesCapableElement.Builder<Builder> {
        private String groupsAttribute;
        private boolean plainText = true;

        private Builder() {
        }

        public Builder withGroupsAttribute(String groupsAttribute) {
            this.groupsAttribute = groupsAttribute;
            return this;
        }

        public Builder withPlainText(boolean plainText) {
            this.plainText = plainText;
            return this;
        }

        public PropertiesRealm build() {
            return new PropertiesRealm(this);
        }

        @Override
        public Builder self() {
            return this;
        }
    }
}
