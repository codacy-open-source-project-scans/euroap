/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.iiop.openjdk.tm;

import org.omg.CORBA.LocalObject;
import org.omg.IOP.Codec;
import org.omg.IOP.ENCODING_CDR_ENCAPS;
import org.omg.IOP.Encoding;
import org.omg.PortableInterceptor.ORBInitInfo;
import org.omg.PortableInterceptor.ORBInitializer;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;

/**
 * Implements an <code>org.omg.PortableInterceptor.ORBinitializer</code> that
 * installs the <code>TxIORInterceptor</code>
 *
 * @author <a href="mailto:adrian@jboss.com">Adrian Brock</a>
 */
@SuppressWarnings("unused")
public class TxIORInterceptorInitializer extends LocalObject implements ORBInitializer {
    /**
     * @since 4.0.1
     */
    static final long serialVersionUID = 963051265993070280L;

    public TxIORInterceptorInitializer() {
        // do nothing
    }

    public void pre_init(ORBInitInfo info) {
        // do nothing
    }

    public void post_init(ORBInitInfo info) {
        try {
            // Use CDR encapsulation with GIOP 1.0 encoding
            Encoding encoding = new Encoding(ENCODING_CDR_ENCAPS.value,
                    (byte) 1, /* GIOP version */
                    (byte) 0  /* GIOP revision*/);
            Codec codec = info.codec_factory().create_codec(encoding);
            info.add_ior_interceptor(new TxIORInterceptor(codec));
        } catch (Exception e) {
            throw IIOPLogger.ROOT_LOGGER.unexpectedException(e);
        }
    }
}