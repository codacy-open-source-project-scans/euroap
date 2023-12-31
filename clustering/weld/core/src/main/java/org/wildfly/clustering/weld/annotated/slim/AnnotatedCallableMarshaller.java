/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.weld.annotated.slim;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedType;

import org.infinispan.protostream.descriptors.WireType;
import org.jboss.weld.annotated.slim.SlimAnnotatedType;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamMarshaller;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamReader;
import org.wildfly.clustering.marshalling.protostream.ProtoStreamWriter;

/**
 * Generic marshaller for an {@link AnnotatedCallable}.
 * @author Paul Ferraro
 */
public class AnnotatedCallableMarshaller<X, E extends Executable, T extends SlimAnnotatedType<X>, C extends AnnotatedCallable<? super X>, SC extends AnnotatedCallable<X>> implements ProtoStreamMarshaller<SC> {

    interface CallableLocator<X, E extends Executable> {
        E lookup(int position, Class<X> declaringClass);
    }

    private static final int TYPE_INDEX = 1;
    private static final int DECLARING_TYPE_INDEX = 2;
    private static final int POSITION_INDEX = 3;

    private final Class<SC> targetClass;
    private final Class<T> typeClass;
    private final CallableLocator<X, E> locator;
    private final Function<T, Set<C>> enumerator;
    private final ToIntFunction<E> indexer;

    public AnnotatedCallableMarshaller(Class<SC> targetClass, Class<T> typeClass, CallableLocator<X, E> locator, Function<T, Set<C>> enumerator, ToIntFunction<E> indexer) {
        this.targetClass = targetClass;
        this.typeClass = typeClass;
        this.locator = locator;
        this.enumerator = enumerator;
        this.indexer = indexer;
    }

    @Override
    public Class<? extends SC> getJavaClass() {
        return this.targetClass;
    }

    @Override
    public SC readFrom(ProtoStreamReader reader) throws IOException {
        T type = null;
        Class<X> declaringType = null;
        int position = 0;
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            switch (WireType.getTagFieldNumber(tag)) {
                case TYPE_INDEX:
                    type = reader.readObject(this.typeClass);
                    break;
                case DECLARING_TYPE_INDEX:
                    declaringType = reader.readObject(Class.class);
                    break;
                case POSITION_INDEX:
                    position = reader.readUInt32();
                    break;
                default:
                    reader.skipField(tag);
            }
        }
        E callable = this.locator.lookup(position, (declaringType != null) ? declaringType : type.getJavaClass());
        for (AnnotatedCallable<? super X> annotatedCallable : this.enumerator.apply(type)) {
            if (annotatedCallable.getJavaMember().equals(callable)) {
                return this.targetClass.cast(annotatedCallable);
            }
        }
        throw new IllegalStateException();
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, SC callable) throws IOException {
        AnnotatedType<X> type = callable.getDeclaringType();
        if (type != null) {
            writer.writeObject(TYPE_INDEX, type);
        }
        @SuppressWarnings("unchecked")
        E executable = (E) callable.getJavaMember();
        if (executable != null) {
            Class<?> declaringType = executable.getDeclaringClass();
            if (declaringType != type.getJavaClass()) {
                writer.writeObject(DECLARING_TYPE_INDEX, declaringType);
            }
            int position = this.indexer.applyAsInt(executable);
            if (position > 0) {
                writer.writeUInt32(POSITION_INDEX, position);
            }
        }
    }
}
