/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.protostream;

import java.io.IOException;

import org.infinispan.protostream.descriptors.WireType;

/**
 * @author Paul Ferraro
 */
public enum StackTraceElementMarshaller implements ProtoStreamMarshaller<StackTraceElement> {
    INSTANCE;

    private static final int CLASS_NAME_INDEX = 1;
    private static final int METHOD_NAME_INDEX = 2;
    private static final int FILE_NAME_INDEX = 3;
    private static final int LINE_NUMBER_INDEX = 4;
    private static final int CLASS_LOADER_NAME_INDEX = 5;
    private static final int MODULE_NAME_INDEX = 6;
    private static final int MODULE_VERSION_INDEX = 7;

    @Override
    public StackTraceElement readFrom(ProtoStreamReader reader) throws IOException {
        String className = null;
        String methodName = null;
        String fileName = null;
        int line = -1;
        String classLoaderName = null;
        String moduleName = null;
        String moduleVersion = null;
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            switch (WireType.getTagFieldNumber(tag)) {
                case CLASS_NAME_INDEX:
                    className = reader.readAny(String.class);
                    break;
                case METHOD_NAME_INDEX:
                    methodName = reader.readAny(String.class);
                    break;
                case FILE_NAME_INDEX:
                    fileName = reader.readAny(String.class);
                    break;
                case LINE_NUMBER_INDEX:
                    line = reader.readUInt32();
                    if (line == 0) {
                        // Native method
                        line = -2;
                    }
                    break;
                case CLASS_LOADER_NAME_INDEX:
                    classLoaderName = reader.readAny(String.class);
                    break;
                case MODULE_NAME_INDEX:
                    moduleName = reader.readAny(String.class);
                    break;
                case MODULE_VERSION_INDEX:
                    moduleVersion = reader.readAny(String.class);
                    break;
                default:
                    reader.skipField(tag);
            }
        }
        return new StackTraceElement(classLoaderName, moduleName, moduleVersion, className, methodName, fileName, line);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, StackTraceElement element) throws IOException {
        writer.writeAny(CLASS_NAME_INDEX, element.getClassName());
        writer.writeAny(METHOD_NAME_INDEX, element.getMethodName());
        String fileName = element.getFileName();
        if (fileName != null) {
            writer.writeAny(FILE_NAME_INDEX, fileName);
        }
        int line = element.getLineNumber();
        boolean nativeMethod = element.isNativeMethod();
        if (nativeMethod || line > 0) {
            writer.writeUInt32(LINE_NUMBER_INDEX, nativeMethod ? 0 : line);
        }
        String classLoaderName = element.getClassLoaderName();
        if (classLoaderName != null) {
            writer.writeAny(CLASS_LOADER_NAME_INDEX, classLoaderName);
        }
        String moduleName = element.getModuleName();
        if (moduleName != null) {
            writer.writeAny(MODULE_NAME_INDEX, moduleName);
        }
        String moduleVersion = element.getModuleVersion();
        if (moduleVersion != null) {
            writer.writeAny(MODULE_VERSION_INDEX, moduleVersion);
        }
    }

    @Override
    public Class<? extends StackTraceElement> getJavaClass() {
        return StackTraceElement.class;
    }
}
