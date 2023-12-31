/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.manual.elytron.seccontext;

import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.WAR_WHOAMI;
import static org.wildfly.test.manual.elytron.seccontext.SeccontextUtil.switchIdentity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import javax.naming.NamingException;

/**
 * Stateless Jakarta Enterprise Beans responsible for calling remote Jakarta Enterprise Beans or Servlet.
 *
 * @author Josef Cacek
 */
@Stateless
@RolesAllowed({ "entry", "admin", "no-server2-identity", "authz" })
@DeclareRoles({ "entry", "whoami", "servlet", "admin", "no-server2-identity", "authz" })
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class EntryBean implements Entry {

    @Resource
    private SessionContext context;

    @Override
    public String whoAmI() {
        return context.getCallerPrincipal().getName();
    }

    @Override
    public String[] doubleWhoAmI(CallAnotherBeanInfo info) {
        final Callable<String> callable = () -> {
            return getWhoAmIBean(info.getLookupEjbAppName(), info.getProviderUrl(),
                    info.isStatefullWhoAmI()).getCallerPrincipal().getName();
        };

        return whoAmIAndCall(info, callable);
    }

    public String[] whoAmIAndIllegalStateException(CallAnotherBeanInfo info) {
        final Callable<String> callable = () -> {
            return getWhoAmIBean(info.getLookupEjbAppName(), info.getProviderUrl(),
                    info.isStatefullWhoAmI()).throwIllegalStateException();
        };

        return whoAmIAndCall(info, callable);
    }

    public String[] whoAmIAndServer2Exception(CallAnotherBeanInfo info) {
        final Callable<String> callable = () -> {
            return getWhoAmIBean(info.getLookupEjbAppName(), info.getProviderUrl(),
                    info.isStatefullWhoAmI()).throwServer2Exception();
        };

        return whoAmIAndCall(info, callable);
    }

    @Override
    public String readUrl(String username, String password, ReAuthnType type, final URL url) {
        final Callable<String> callable = () -> {
            URLConnection conn = url.openConnection();
            conn.connect();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return br.readLine();
            }
        };
        String result = null;
        String firstWho = context.getCallerPrincipal().getName();
        try {
            result = switchIdentity(username, password, callable, type);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            result = sw.toString();
        } finally {
            String secondLocalWho = context.getCallerPrincipal().getName();
            if (!secondLocalWho.equals(firstWho)) {
                throw new IllegalStateException(
                        "Local getCallerPrincipal changed from '" + firstWho + "' to '" + secondLocalWho);
            }
        }
        return result;
    }

    private String[] whoAmIAndCall(CallAnotherBeanInfo info, Callable<String> callable) {
        String[] result = new String[2];
        result[0] = context.getCallerPrincipal().getName();

        try {
            result[1] = switchIdentity(info.getUsername(), info.getPassword(), info.getAuthzName(), callable, info.getType());
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            result[1] = sw.toString();
        } finally {
            String secondLocalWho = context.getCallerPrincipal().getName();
            if (!secondLocalWho.equals(result[0])) {
                throw new IllegalStateException(
                        "Local getCallerPrincipal changed from '" + result[0] + "' to '" + secondLocalWho);
            }
        }
        return result;
    }

    private WhoAmI getWhoAmIBean(String ejbAppName, String providerUrl, boolean statefullWhoAmI) throws NamingException {
        return SeccontextUtil.lookup(
                SeccontextUtil.getRemoteEjbName(ejbAppName == null ? WAR_WHOAMI : ejbAppName, "WhoAmIBean",
                        WhoAmI.class.getName(), statefullWhoAmI), providerUrl);
    }
}
