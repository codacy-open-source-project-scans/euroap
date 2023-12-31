/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.weld.ejb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.infinispan.protostream.descriptors.WireType;
import org.jboss.as.weld.ejb.SerializedStatefulSessionObject;
import org.jboss.ejb.client.SessionID;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;

/**
 * @author Paul Ferraro
 */
public class SerializedStatefulSessionObjectMarshaller implements ProtoStreamMarshaller<SerializedStatefulSessionObject> {

    private static final int COMPONENT_SERVICE_NAME_INDEX = 1;
    private static final int SESSION_ID_INDEX = 2;
    private static final int VIEW_CLASS_INDEX = 3;
    private static final int VIEW_SERVICE_NAME_INDEX = 4;

    @Override
    public Class<? extends SerializedStatefulSessionObject> getJavaClass() {
        return SerializedStatefulSessionObject.class;
    }

    @Override
    public SerializedStatefulSessionObject readFrom(ProtoStreamReader reader) throws IOException {
        String componentServiceName = null;
        SessionID id = null;
        List<Class<?>> viewClasses = new LinkedList<>();
        List<String> viewServiceNames = new LinkedList<>();
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            switch (WireType.getTagFieldNumber(tag)) {
                case COMPONENT_SERVICE_NAME_INDEX:
                    componentServiceName = reader.readString();
                    break;
                case SESSION_ID_INDEX:
                    id = reader.readObject(SessionID.class);
                    break;
                case VIEW_CLASS_INDEX:
                    viewClasses.add(reader.readObject(Class.class));
                    break;
                case VIEW_SERVICE_NAME_INDEX:
                    viewServiceNames.add(reader.readString());
                    break;
                default:
                    reader.skipField(tag);
            }
        }
        Map<Class<?>, String> views = new HashMap<>();
        Iterator<Class<?>> classes = viewClasses.iterator();
        Iterator<String> names = viewServiceNames.iterator();
        while (classes.hasNext() && names.hasNext()) {
            views.put(classes.next(), names.next());
        }
        return new SerializedStatefulSessionObject(componentServiceName, id, views);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, SerializedStatefulSessionObject object) throws IOException {
        String componentServiceName = object.getComponentServiceName();
        if (componentServiceName != null) {
            writer.writeString(COMPONENT_SERVICE_NAME_INDEX, componentServiceName);
        }
        SessionID id = object.getSessionID();
        if (id != null) {
            writer.writeObject(SESSION_ID_INDEX, id);
        }
        Map<Class<?>, String> views = object.getServiceNames();
        for (Map.Entry<Class<?>, String> entry : views.entrySet()) {
            writer.writeObject(VIEW_CLASS_INDEX, entry.getKey());
            writer.writeString(VIEW_SERVICE_NAME_INDEX, entry.getValue());
        }
    }
}
