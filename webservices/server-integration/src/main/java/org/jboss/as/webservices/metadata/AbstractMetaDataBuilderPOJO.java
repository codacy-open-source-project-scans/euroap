/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.webservices.metadata;

import static org.jboss.as.webservices.util.ASHelper.getContextRoot;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.webservices.logging.WSLogger;
import org.jboss.as.webservices.metadata.model.POJOEndpoint;
import org.jboss.as.webservices.util.WebMetaDataHelper;
import org.jboss.metadata.javaee.spec.ParamValueMetaData;
import org.jboss.metadata.web.jboss.JBossServletsMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.spec.SecurityConstraintMetaData;
import org.jboss.metadata.web.spec.ServletMappingMetaData;
import org.jboss.metadata.web.spec.WebResourceCollectionMetaData;
import org.jboss.ws.common.integration.WSConstants;
import org.jboss.ws.common.integration.WSHelper;
import org.jboss.wsf.spi.deployment.Deployment;
import org.jboss.wsf.spi.metadata.j2ee.JSEArchiveMetaData;
import org.jboss.wsf.spi.metadata.j2ee.JSESecurityMetaData;
import org.jboss.wsf.spi.metadata.j2ee.PublishLocationAdapter;
import org.jboss.wsf.spi.metadata.webservices.JBossWebservicesMetaData;

/**
 * Builds container independent meta data from WEB container meta data.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:tdiesler@redhat.com">Thomas Diesler</a>
 * @author <a href="mailto:alessio.soldano@jboss.com">Alessio Soldano</a>
 */
abstract class AbstractMetaDataBuilderPOJO {

    AbstractMetaDataBuilderPOJO() {
        super();
    }

    /**
     * Builds universal JSE meta data model that is AS agnostic.
     *
     * @param dep webservice deployment
     * @return universal JSE meta data model
     */
    JSEArchiveMetaData create(final Deployment dep) {
        if (WSLogger.ROOT_LOGGER.isTraceEnabled()) {
            WSLogger.ROOT_LOGGER.tracef("Creating JBoss agnostic meta data for POJO webservice deployment: %s", dep.getSimpleName());
        }
        final JBossWebMetaData jbossWebMD = WSHelper.getRequiredAttachment(dep, JBossWebMetaData.class);
        final DeploymentUnit unit = WSHelper.getRequiredAttachment(dep, DeploymentUnit.class);
        final List<POJOEndpoint> pojoEndpoints = getPojoEndpoints(unit);
        final JSEArchiveMetaData.Builder builder = new JSEArchiveMetaData.Builder();

        // set context root
        final String contextRoot = getContextRoot(dep, jbossWebMD);
        builder.setContextRoot(contextRoot);
        WSLogger.ROOT_LOGGER.tracef("Setting context root: %s", contextRoot);

        // set servlet url patterns mappings
        final Map<String, String> servletMappings = getServletUrlPatternsMappings(jbossWebMD, pojoEndpoints);
        builder.setServletMappings(servletMappings);

        // set servlet class names mappings
        final Map<String, String> servletClassNamesMappings = getServletClassMappings(jbossWebMD, pojoEndpoints);
        builder.setServletClassNames(servletClassNamesMappings);

        // set security domain
        final String securityDomain = jbossWebMD.getSecurityDomain();
        builder.setSecurityDomain(securityDomain);

        // set wsdl location resolver
        final JBossWebservicesMetaData jbossWebservicesMD = WSHelper.getOptionalAttachment(dep, JBossWebservicesMetaData.class);
        if (jbossWebservicesMD != null) {
            final PublishLocationAdapter resolver = new PublishLocationAdapterImpl(jbossWebservicesMD.getWebserviceDescriptions());
            builder.setPublishLocationAdapter(resolver);
        }

        // set security meta data
        final List<JSESecurityMetaData> jseSecurityMDs = getSecurityMetaData(jbossWebMD.getSecurityConstraints());
        builder.setSecurityMetaData(jseSecurityMDs);

        // set config name and file
        setConfigNameAndFile(builder, jbossWebMD, jbossWebservicesMD);

        return builder.build();
    }

    protected abstract List<POJOEndpoint> getPojoEndpoints(final DeploymentUnit unit);

    /**
     * Sets config name and config file.
     *
     * @param builder universal JSE meta data model builder
     * @param jbossWebMD jboss web meta data
     */
    private void setConfigNameAndFile(final JSEArchiveMetaData.Builder builder, final JBossWebMetaData jbossWebMD, final JBossWebservicesMetaData jbossWebservicesMD) {
        if (jbossWebservicesMD != null
                && jbossWebservicesMD.getConfigName() != null) {
            final String configName = jbossWebservicesMD.getConfigName();
            builder.setConfigName(configName);
            WSLogger.ROOT_LOGGER.tracef("Setting config name: %s", configName);
            final String configFile = jbossWebservicesMD.getConfigFile();
            builder.setConfigFile(configFile);
            WSLogger.ROOT_LOGGER.tracef("Setting config file: %s", configFile);

            // ensure higher priority against web.xml context parameters
            return;
        }

        final List<ParamValueMetaData> contextParams = jbossWebMD.getContextParams();
        if (contextParams != null) {
            for (final ParamValueMetaData contextParam : contextParams) {
                if (WSConstants.JBOSSWS_CONFIG_NAME.equals(contextParam.getParamName())) {
                    final String configName = contextParam.getParamValue();
                    builder.setConfigName(configName);
                    WSLogger.ROOT_LOGGER.tracef("Setting config name: %s", configName);
                }
                if (WSConstants.JBOSSWS_CONFIG_FILE.equals(contextParam.getParamName())) {
                    final String configFile = contextParam.getParamValue();
                    builder.setConfigFile(configFile);
                    WSLogger.ROOT_LOGGER.tracef("Setting config file: %s", configFile);
                }
            }
        }
    }

    /**
     * Builds security meta data.
     *
     * @param securityConstraintsMD security constraints meta data
     * @return universal JSE security meta data model
     */
    private List<JSESecurityMetaData> getSecurityMetaData(final List<SecurityConstraintMetaData> securityConstraintsMD) {
        final List<JSESecurityMetaData> jseSecurityMDs = new LinkedList<JSESecurityMetaData>();

        if (securityConstraintsMD != null) {
            for (final SecurityConstraintMetaData securityConstraintMD : securityConstraintsMD) {
                final JSESecurityMetaData.Builder jseSecurityMDBuilder = new JSESecurityMetaData.Builder();

                // transport guarantee
                jseSecurityMDBuilder.setTransportGuarantee(securityConstraintMD.getTransportGuarantee().name());

                // web resources
                for (final WebResourceCollectionMetaData webResourceMD : securityConstraintMD.getResourceCollections()) {
                    jseSecurityMDBuilder.addWebResource(webResourceMD.getName(), webResourceMD.getUrlPatterns());
                }

                jseSecurityMDs.add(jseSecurityMDBuilder.build());
            }
        }

        return jseSecurityMDs;
    }

    /**
     * Returns servlet name to url pattern mappings.
     *
     * @param jbossWebMD jboss web meta data
     * @return servlet name to url pattern mappings
     */
    private Map<String, String> getServletUrlPatternsMappings(final JBossWebMetaData jbossWebMD, final List<POJOEndpoint> pojoEndpoints) {
        final Map<String, String> mappings = new HashMap<String, String>();
        final List<ServletMappingMetaData> servletMappings = WebMetaDataHelper.getServletMappings(jbossWebMD);

        for (final POJOEndpoint pojoEndpoint : pojoEndpoints) {
            mappings.put(pojoEndpoint.getName(), pojoEndpoint.getUrlPattern());
            if (!pojoEndpoint.isDeclared()) {
                final String endpointName = pojoEndpoint.getName();
                final List<String> urlPatterns = WebMetaDataHelper.getUrlPatterns(pojoEndpoint.getUrlPattern());
                WebMetaDataHelper.newServletMapping(endpointName, urlPatterns, servletMappings);
            }
        }

        return mappings;
    }

    /**
     * Returns servlet name to servlet class mappings.
     *
     * @param jbossWebMD jboss web meta data
     * @return servlet name to servlet mappings
     */
    private Map<String, String> getServletClassMappings(final JBossWebMetaData jbossWebMD, final List<POJOEndpoint> pojoEndpoints) {
        final Map<String, String> mappings = new HashMap<String, String>();
        final JBossServletsMetaData servlets = WebMetaDataHelper.getServlets(jbossWebMD);

        for (final POJOEndpoint pojoEndpoint : pojoEndpoints) {
            final String pojoName = pojoEndpoint.getName();
            final String pojoClassName = pojoEndpoint.getClassName();
            mappings.put(pojoName, pojoClassName);
            if (!pojoEndpoint.isDeclared()) {
                final String endpointName = pojoEndpoint.getName();
                final String endpointClassName = pojoEndpoint.getClassName();
                WebMetaDataHelper.newServlet(endpointName, endpointClassName, servlets);
            }
        }

        return mappings;
    }

}
