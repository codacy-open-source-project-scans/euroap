/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.mail.extension;

import org.jboss.as.controller.PathElement;

/**
 * @author <a href="tomaz.cerar@gmail.com">Tomaz Cerar</a>
 * @created 26.7.11 15:21
 */
interface MailSubsystemModel {
    String LOGIN = "login";
    String SERVER_TYPE = "server";
    String SMTP_SERVER = "smtp-server";
    String POP3_SERVER = "pop3-server";
    String IMAP_SERVER = "imap-server";
    String CUSTOM_SERVER = "custom-server";

    String MAIL_SESSION = "mail-session";
    String LOGIN_USERNAME = "name";
    String USER_NAME = "username";
    String PASSWORD = "password";
    String JNDI_NAME = "jndi-name";
    String DEBUG = "debug";
    String OUTBOUND_SOCKET_BINDING_REF = "outbound-socket-binding-ref";
    String SSL = "ssl";
    String TLS = "tls";
    String FROM = "from";
    String POP3 = "pop3";
    String SMTP = "smtp";
    String IMAP = "imap";
    String NAME = "name";
    String CUSTOM = "custom";
    String PROPERTY = "property";


    PathElement POP3_SERVER_PATH = PathElement.pathElement(SERVER_TYPE, POP3);
    PathElement SMTP_SERVER_PATH = PathElement.pathElement(SERVER_TYPE, SMTP);
    PathElement IMAP_SERVER_PATH = PathElement.pathElement(SERVER_TYPE, IMAP);
    PathElement CUSTOM_SERVER_PATH = PathElement.pathElement(CUSTOM);
}
