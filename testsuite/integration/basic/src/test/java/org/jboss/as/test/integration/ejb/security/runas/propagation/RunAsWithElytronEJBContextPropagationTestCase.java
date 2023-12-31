/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.security.runas.propagation;

import java.io.File;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.naming.InitialContext;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.jboss.as.test.shared.CLIServerSetupTask;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.naming.client.WildFlyInitialContextFactory;

/**
 * Testing EJB Elytron security context propagation with @RunAs annotation, using outbound connection to connect to back to this
 * server. Test for WFLY-11094
 *
 * @author tmiyar
 *
 */
@RunWith(Arquillian.class)
@ServerSetup(RunAsWithElytronEJBContextPropagationTestCase.ServerSetupTask.class)
@RunAsClient
public class RunAsWithElytronEJBContextPropagationTestCase extends AbstractCliTestBase {

    private static final String EJB_TEST_MODULE_NAME = "ejb-security-context-propagation";

    private static final String DEFAULT_CONNECTION_SERVER = "jboss";
    private static final String ORIGINAL_USERS_PATH = "application-users.properties";
    private static final String ORIGINAL_ROLES_PATH = "application-roles.properties";
    private static final String RELATIVE_TO = "jboss.server.config.dir";

    private static final String USERNAME = "user1";
    private static final String PASSWORD = "password1";
    private static final String ROLE = "role2";
    private static boolean removeRealmProperties = false;
    // Avoid problem on windows with path
    private static final String USERS_PATH = new File(
            RunAsWithElytronEJBContextPropagationTestCase.class.getResource("users.properties").getFile()).getAbsolutePath()
                    .replace("\\", "/");
    private static final String ROLES_PATH = new File(
            RunAsWithElytronEJBContextPropagationTestCase.class.getResource("roles.properties").getFile()).getAbsolutePath()
                    .replace("\\", "/");

    @Deployment(name = EJB_TEST_MODULE_NAME, managed = true, testable = false)
    public static Archive<?> createEjbClientDeployment() {
        final JavaArchive ejbClientJar = ShrinkWrap.create(JavaArchive.class, EJB_TEST_MODULE_NAME + ".jar");
        ejbClientJar.addClass(IntermediateCallerInRole.class).addClass(IntermediateCallerInRoleRemote.class)
                .addClass(CallerInRole.class).addClass(ServerCallerInRole.class).addAsManifestResource(
                        IntermediateCallerInRole.class.getPackage(), "jboss-ejb-client.xml", "jboss-ejb-client.xml");
        return ejbClientJar;
    }

    /**
     * Test that checks the first EJB is called by admin role, same user is used to call the second EJB were the role does not
     * change.
     *
     * The test uses http-remoting protocol.
     */
    @Test
    public void testRunAsWithElytronEJBContextPropagation() {

        InitialContext context = initContext();

        IntermediateCallerInRoleRemote intermediate;
        try {
            intermediate = (IntermediateCallerInRoleRemote) context
                    .lookup("ejb:/ejb-security-context-propagation/IntermediateCallerInRole!"
                            + IntermediateCallerInRoleRemote.class.getName());
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }

        Assert.assertFalse(intermediate.isCallerInRole(ROLE));
        Assert.assertTrue(intermediate.isServerCallerInRole(ROLE));
        closeContext(context);
    }

    private InitialContext initContext() {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, WildFlyInitialContextFactory.class.getName());
        props.put(Context.PROVIDER_URL, "remote+http://" + TestSuiteEnvironment.getServerAddress() + ":8080");
        props.put(Context.SECURITY_PRINCIPAL, USERNAME);
        props.put(Context.SECURITY_CREDENTIALS, PASSWORD);

        try {
            return new InitialContext(props);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeContext(InitialContext context) {

        try {
            context.close();
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }

    }

    public static class ServerSetupTask extends CLIServerSetupTask {
        public ServerSetupTask() {
            this.builder.node(DEFAULT_CONNECTION_SERVER)
                    .setup("/subsystem=ejb3/application-security-domain=ejbtest-domain:add(security-domain=ApplicationDomain)")
                    .setup("/subsystem=elytron/authentication-configuration=ejb-outbound-configuration:add(authentication-name=user2,security-domain=ApplicationDomain,realm=ApplicationRealm,forwarding-mode=authorization,credential-reference={clear-text=password2})")
                    .setup("/subsystem=elytron/authentication-context=ejb-outbound-context:add(match-rules=[{authentication-configuration=ejb-outbound-configuration,match-no-user=true}])")
                    .setup(String.format("/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=ejb-outbound:add(host=\"%s\",port=8080)", TestSuiteEnvironment.getServerAddress()))
                    .setup("/subsystem=remoting/remote-outbound-connection=ejb-outbound-connection:add(outbound-socket-binding-ref=ejb-outbound,authentication-context=ejb-outbound-context)")
                    .setup("/subsystem=elytron/sasl-authentication-factory=application-sasl-authentication:write-attribute(name=mechanism-configurations,value=[{mechanism-name=PLAIN},{mechanism-name=JBOSS-LOCAL-USER,realm-mapper=local},{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=ApplicationRealm}]}])")
                    .setup(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=users-properties.path,value=\"%s\")",
                            USERS_PATH))
                    .setup("/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=users-properties.plain-text,value=true)")
                    .setup("/subsystem=elytron/properties-realm=ApplicationRealm:undefine-attribute(name=users-properties.relative-to)")
                    .setup(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=groups-properties.path,value=\"%s\")",
                            ROLES_PATH))
                    .setup("/subsystem=elytron/properties-realm=ApplicationRealm:undefine-attribute(name=groups-properties.relative-to)")
                    .setup("/subsystem=elytron/simple-permission-mapper=auth-forwarding-permission-mapper:add(permission-mappings=[{principals=[anonymous]},"
                            + "{principals=[user2],permissions=[{class-name=org.wildfly.security.auth.permission.RunAsPrincipalPermission,target-name=*},{class-name=org.wildfly.security.auth.permission.LoginPermission},{class-name=org.wildfly.extension.batch.jberet.deployment.BatchPermission,"
                            + "module=org.wildfly.extension.batch.jberet,target-name=*},{class-name=org.wildfly.transaction.client.RemoteTransactionPermission,module=org.wildfly.transaction.client},{class-name=org.jboss.ejb.client.RemoteEJBPermission,module=org.jboss.ejb-client}]},{match-all=true,permissions=[{class-name=org.wildfly.security.auth.permission.LoginPermission},"
                            + "{class-name=org.wildfly.extension.batch.jberet.deployment.BatchPermission,module=org.wildfly.extension.batch.jberet,target-name=*},{class-name=org.wildfly.transaction.client.RemoteTransactionPermission,module=org.wildfly.transaction.client},{class-name=org.jboss.ejb.client.RemoteEJBPermission,module=org.jboss.ejb-client}]}])")
                    .setup("/subsystem=elytron/security-domain=ApplicationDomain:write-attribute(name=permission-mapper,value=auth-forwarding-permission-mapper)")
                    .teardown(
                            "/subsystem=elytron/sasl-authentication-factory=application-sasl-authentication:write-attribute(name=mechanism-configurations,value=[{mechanism-name=JBOSS-LOCAL-USER,realm-mapper=local},{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=ApplicationRealm}]}])")
                    .teardown("/subsystem=remoting/remote-outbound-connection=ejb-outbound-connection:remove")
                    .teardown(
                            "/socket-binding-group=standard-sockets/remote-destination-outbound-socket-binding=ejb-outbound:remove")
                    .teardown("/subsystem=elytron/authentication-context=ejb-outbound-context:remove")
                    .teardown("/subsystem=elytron/authentication-configuration=ejb-outbound-configuration:remove")
                    .teardown("/subsystem=ejb3/application-security-domain=ejbtest-domain:remove")
                    .teardown(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=users-properties.path,value=\"%s\")",
                            ORIGINAL_USERS_PATH))
                    .teardown(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=users-properties.relative-to,value=\"%s\")",
                            RELATIVE_TO))
                    .teardown(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:undefine-attribute(name=users-properties.plain-text)")
                    .teardown(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=groups-properties.path,value=\"%s\")",
                            ORIGINAL_ROLES_PATH))
                    .teardown(String.format(
                            "/subsystem=elytron/properties-realm=ApplicationRealm:write-attribute(name=groups-properties.relative-to,value=\"%s\")",
                            RELATIVE_TO))
                    .teardown(
                            "/subsystem=elytron/security-domain=ApplicationDomain:write-attribute(name=permission-mapper,value=default-permission-mapper)")
                    .teardown("/subsystem=elytron/simple-permission-mapper=auth-forwarding-permission-mapper:remove()");

        }
    }
}
