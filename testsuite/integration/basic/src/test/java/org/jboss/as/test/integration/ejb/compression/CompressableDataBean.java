/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.ejb.compression;

import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;

/**
 * @author: Jaikiran Pai
 */
@Stateless
@Remote(MethodOverrideDataCompressionRemoteView.class)
public class CompressableDataBean implements MethodOverrideDataCompressionRemoteView {

    @Override
    public String echoWithRequestCompress(String msg) {
        return msg;
    }

    @Override
    public String echoWithNoExplicitDataCompressionHintOnMethod(String msg) {
        return msg;
    }

    @Override
    public String echoWithResponseCompress(String msg) {
        return msg;
    }

}
