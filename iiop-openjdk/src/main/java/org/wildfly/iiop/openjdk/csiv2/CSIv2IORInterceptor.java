/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.iiop.openjdk.csiv2;

import org.jboss.metadata.ejb.jboss.IORSecurityConfigMetaData;
import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.ORB;
import org.omg.CSIIOP.DetectMisordering;
import org.omg.CSIIOP.DetectReplay;
import org.omg.CSIIOP.Integrity;
import org.omg.IOP.Codec;
import org.omg.IOP.TAG_INTERNET_IOP;
import org.omg.IOP.TaggedComponent;
import org.omg.IOP.CodecPackage.InvalidTypeForEncoding;
import org.omg.PortableInterceptor.IORInfo;
import org.omg.PortableInterceptor.IORInterceptor;
import org.omg.SSLIOP.SSL;
import org.omg.SSLIOP.SSLHelper;
import org.omg.SSLIOP.TAG_SSL_SEC_TRANS;
import org.wildfly.iiop.openjdk.Constants;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;
import org.wildfly.iiop.openjdk.service.CorbaORBService;
import org.wildfly.iiop.openjdk.service.IORSecConfigMetaDataService;

/**
 * <p>
 * Implements an {@code org.omg.PortableInterceptor.IORInterceptor} that inserts CSIv2 info into an IOR.
 * </p>
 *
 * @author <a href="mailto:reverbel@ime.usp.br">Francisco Reverbel</a>
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class CSIv2IORInterceptor extends LocalObject implements IORInterceptor {

    // The minimum set of security options supported by the SSL mechanism (These options cannot be turned off, so they are always supported).
    private static final int MIN_SSL_OPTIONS = Integrity.value | DetectReplay.value | DetectMisordering.value;

    private TaggedComponent defaultSSLComponent;

    private TaggedComponent defaultCSIComponent;

    /**
     * <p>
     * Creates an instance of {@code CSIv2IORInterceptor} with the specified codec.
     * </p>
     *
     * @param codec the {@code Codec} used to encode the IOR security components.
     */
    public CSIv2IORInterceptor(Codec codec) {
        String sslPortString = CorbaORBService.getORBProperty(Constants.ORB_SSL_PORT);
        int sslPort = sslPortString == null ? 0 : Integer.parseInt(sslPortString);
        try {
            SSL ssl = new SSL((short) MIN_SSL_OPTIONS,
                    (short) 0, /* required options  */
                    (short) sslPort);
            ORB orb = ORB.init();
            Any any = orb.create_any();
            SSLHelper.insert(any, ssl);
            byte[] componentData = codec.encode_value(any);
            defaultSSLComponent = new TaggedComponent(TAG_SSL_SEC_TRANS.value, componentData);

            IORSecurityConfigMetaData iorSecurityConfigMetaData = IORSecConfigMetaDataService.getCurrent();
            if (iorSecurityConfigMetaData == null)
                iorSecurityConfigMetaData = new IORSecurityConfigMetaData();
            defaultCSIComponent = CSIv2Util.createSecurityTaggedComponent(iorSecurityConfigMetaData, codec, sslPort, orb);
        } catch (InvalidTypeForEncoding e) {
            throw IIOPLogger.ROOT_LOGGER.unexpectedException(e);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void establish_components(IORInfo info) {
        // check if CSIv2 policy is in effect for this IOR.
        CSIv2Policy csiv2Policy = null;

        try {
            csiv2Policy = (CSIv2Policy) info.get_effective_policy(CSIv2Policy.TYPE);
        } catch (BAD_PARAM e) {
            IIOPLogger.ROOT_LOGGER.debug("CSIv2Policy not found in IORInfo");
        } catch (Exception e) {
            IIOPLogger.ROOT_LOGGER.failedToFetchCSIv2Policy(e);
        }

        boolean interopIONA = Boolean.parseBoolean(CorbaORBService.getORBProperty(Constants.INTEROP_IONA));
        if (csiv2Policy != null) {
            // if csiv2Policy effective, stuff a copy of the TaggedComponents already created by the CSIv2Policy into the IOR's IIOP profile.
            TaggedComponent sslComponent = csiv2Policy.getSSLTaggedComponent();
            // if interop with IONA ASP is on, don't add the SSL component to the IOR.
            if (sslComponent != null && !interopIONA) {
                info.add_ior_component_to_profile(sslComponent, TAG_INTERNET_IOP.value);
            }
            TaggedComponent csiv2Component = csiv2Policy.getSecurityTaggedComponent();
            if (csiv2Component != null) {
                info.add_ior_component_to_profile(csiv2Component, TAG_INTERNET_IOP.value);
            }
        } else {
            if (defaultSSLComponent != null && !interopIONA) {
                // otherwise stuff the default SSL component (with the minimum set of SSL options) into the IOR's IIOP profile.
                info.add_ior_component_to_profile(defaultSSLComponent, TAG_INTERNET_IOP.value);
            }
            if (defaultCSIComponent != null) {
                // and stuff the default CSI component (with the minimum set of CSI options) into the IOR's IIOP profile.
                info.add_ior_component_to_profile(defaultCSIComponent, TAG_INTERNET_IOP.value);
            }
        }
    }

    @Override
    public String name() {
        return CSIv2IORInterceptor.class.getName();
    }
}
