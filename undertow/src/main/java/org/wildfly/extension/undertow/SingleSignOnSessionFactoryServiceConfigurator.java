/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import static org.wildfly.extension.undertow.ApplicationSecurityDomainSingleSignOnDefinition.Attribute.CREDENTIAL;
import static org.wildfly.extension.undertow.ApplicationSecurityDomainSingleSignOnDefinition.Attribute.KEY_ALIAS;
import static org.wildfly.extension.undertow.ApplicationSecurityDomainSingleSignOnDefinition.Attribute.KEY_STORE;
import static org.wildfly.extension.undertow.ApplicationSecurityDomainSingleSignOnDefinition.Attribute.SSL_CONTEXT;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.clustering.controller.CommonUnaryRequirement;
import org.jboss.as.clustering.controller.CredentialSourceDependency;
import org.jboss.as.clustering.controller.ResourceServiceConfigurator;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.service.CompositeDependency;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SupplierDependency;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.http.util.sso.DefaultSingleSignOnSessionFactory;
import org.wildfly.security.http.util.sso.SingleSignOnManager;
import org.wildfly.security.http.util.sso.SingleSignOnSessionFactory;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * @author Paul Ferraro
 */
public class SingleSignOnSessionFactoryServiceConfigurator extends SingleSignOnSessionFactoryServiceNameProvider implements ResourceServiceConfigurator, Supplier<SingleSignOnSessionFactory> {

    private final SupplierDependency<SingleSignOnManager> manager;

    private volatile SupplierDependency<KeyStore> keyStore;
    private volatile SupplierDependency<SSLContext> sslContext;
    private volatile SupplierDependency<CredentialSource> credentialSource;
    private volatile String keyAlias;

    public SingleSignOnSessionFactoryServiceConfigurator(String securityDomainName) {
        super(securityDomainName);
        this.manager = new ServiceSupplierDependency<>(new SingleSignOnManagerServiceNameProvider(securityDomainName));
    }

    @Override
    public ServiceConfigurator configure(OperationContext context, ModelNode model) throws OperationFailedException {
        String keyStore = KEY_STORE.resolveModelAttribute(context, model).asString();
        this.keyStore = new ServiceSupplierDependency<>(CommonUnaryRequirement.KEY_STORE.getServiceName(context, keyStore));
        this.keyAlias = KEY_ALIAS.resolveModelAttribute(context, model).asString();
        this.credentialSource = new CredentialSourceDependency(context, CREDENTIAL, model);
        String sslContext = SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
        this.sslContext = (sslContext != null) ? new ServiceSupplierDependency<>(CommonUnaryRequirement.SSL_CONTEXT.getServiceName(context, sslContext)) : null;
        return this;
    }

    @Override
    public ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = target.addService(this.getServiceName());
        Consumer<SingleSignOnSessionFactory> factory = new CompositeDependency(this.manager, this.keyStore, this.credentialSource, this.sslContext).register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(factory, Function.identity(), this);
        return builder.setInstance(service);
    }

    @Override
    public SingleSignOnSessionFactory get() {
        KeyStore store = this.keyStore.get();
        String alias = this.keyAlias;
        CredentialSource source = this.credentialSource.get();
        try {
            if (!store.containsAlias(alias)) {
                throw UndertowLogger.ROOT_LOGGER.missingKeyStoreEntry(alias);
            }
            if (!store.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                throw UndertowLogger.ROOT_LOGGER.keyStoreEntryNotPrivate(alias);
            }
            PasswordCredential credential = source.getCredential(PasswordCredential.class);
            if (credential == null) {
                throw UndertowLogger.ROOT_LOGGER.missingCredential(source.toString());
            }
            ClearPassword password = credential.getPassword(ClearPassword.class);
            if (password == null) {
                throw UndertowLogger.ROOT_LOGGER.credentialNotClearPassword(credential.toString());
            }
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry(alias, new KeyStore.PasswordProtection(password.getPassword()));
            KeyPair keyPair = new KeyPair(entry.getCertificate().getPublicKey(), entry.getPrivateKey());
            Optional<SSLContext> context = Optional.ofNullable(this.sslContext).map(dependency -> dependency.get());
            return new DefaultSingleSignOnSessionFactory(this.manager.get(), keyPair, connection -> context.ifPresent(ctx -> connection.setSSLSocketFactory(ctx.getSocketFactory())));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
