/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.connector.subsystems.jca;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.jca.core.api.bootstrap.CloneableBootstrapContext;
import org.jboss.jca.core.tracer.Tracer;
import org.jboss.jca.deployers.common.Configuration;

/**
 * Configuration
 *
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 */
public class JcaSubsystemConfiguration implements Configuration {

    /** Preform Jakarta Bean Validation */
    private final AtomicBoolean beanValidation = new AtomicBoolean(true);

    /** Preform archive validation */
    private final AtomicBoolean archiveValidation = new AtomicBoolean(true);

    /** Archive validation: Fail on Warn */
    private final AtomicBoolean archiveValidationFailOnWarn = new AtomicBoolean(false);

    /** Archive validation: Fail on Error */
    private final AtomicBoolean archiveValidationFailOnError = new AtomicBoolean(true);

    /** Default bootstrap context */
    private CloneableBootstrapContext defaultBootstrapContext;

    /** Bootstrap contexts */
    private Map<String, CloneableBootstrapContext> bootstrapContexts = new HashMap<String, CloneableBootstrapContext>(0);

    /**
     * Create a new ConnectorSubsystemConfiguration.
     */
    public JcaSubsystemConfiguration() {
    }

    /**
     * Set if Jakarta Bean Validation should be performed
     * @param value The value
     */
    public void setBeanValidation(boolean value) {
        beanValidation.set(value);
    }

    /**
     * Should Jakarta Bean Validation be performed
     * @return True if validation; otherwise false
     */
    public boolean getBeanValidation() {
        return beanValidation.get();
    }

    /**
     * Set if tracer should be performed
     *
     * @param value The value
     */
    public void setTracer(boolean value) {
        Tracer.setEnabled(value);
    }

    /**
     * Should Jakarta Bean Validation be performed
     *
     * @return True if validation; otherwise false
     */
    public boolean getTracer() {
        return Tracer.isEnabled();
    }


    /**
     * Set if archive validation should be performed
     * @param value The value
     */
    public void setArchiveValidation(boolean value) {
        archiveValidation.set(value);
    }

    /**
     * Should archive validation be performed
     * @return True if validation; otherwise false
     */
    public boolean getArchiveValidation() {
        return archiveValidation.get();
    }

    /**
     * Set if a failed warning archive validation report should fail the
     * deployment
     * @param value The value
     */
    public void setArchiveValidationFailOnWarn(boolean value) {
        archiveValidationFailOnWarn.set(value);
    }

    /**
     * Does a failed archive validation warning report fail the deployment
     * @return True if failing; otherwise false
     */
    public boolean getArchiveValidationFailOnWarn() {
        return archiveValidationFailOnWarn.get();
    }

    /**
     * Set if a failed error archive validation report should fail the
     * deployment
     * @param value The value
     */
    public void setArchiveValidationFailOnError(boolean value) {
        archiveValidationFailOnError.set(value);
    }

    /**
     * Does a failed archive validation error report fail the deployment
     * @return True if failing; otherwise false
     */
    public boolean getArchiveValidationFailOnError() {
        return archiveValidationFailOnError.get();
    }

    @Override
    public void setDefaultBootstrapContext(CloneableBootstrapContext value) {
        this.defaultBootstrapContext = value;

    }

    @Override
    public CloneableBootstrapContext getDefaultBootstrapContext() {
        return this.defaultBootstrapContext;
    }

    @Override
    public void setBootstrapContexts(Map<String, CloneableBootstrapContext> value) {
        this.bootstrapContexts = value;
    }

    @Override
    public Map<String, CloneableBootstrapContext> getBootstrapContexts() {
        return bootstrapContexts;
    }
}
