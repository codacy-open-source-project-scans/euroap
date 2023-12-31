/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.protostream;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.infinispan.protostream.descriptors.WireType;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionPredicate;
import org.wildfly.common.function.Functions;

/**
 * Marshaller that reads/writes a single field by applying functions to a {@link ScalarMarshaller}.
 * @author Paul Ferraro
 * @param <T> the type of this marshaller
 * @param <V> the type of the mapped scalar marshaller
 */
public class FunctionalScalarMarshaller<T, V> implements ProtoStreamMarshaller<T> {

    private static final int VALUE_INDEX = 1;

    private final Class<? extends T> targetClass;
    private final Supplier<T> defaultFactory;
    private final ExceptionPredicate<T, IOException> skipWrite;
    private final ScalarMarshaller<V> marshaller;
    private final ExceptionFunction<T, V, IOException> function;
    private final ExceptionFunction<V, T, IOException> factory;

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    @SuppressWarnings("unchecked")
    public FunctionalScalarMarshaller(ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this((Class<T>) defaultFactory.get().getClass(), marshaller, defaultFactory, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param equals a predicate used to determine if {@link #writeTo(ProtoStreamWriter, Object)} should skip writing the field.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    @SuppressWarnings("unchecked")
    public FunctionalScalarMarshaller(ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, BiPredicate<V, V> equals, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this((Class<T>) defaultFactory.get().getClass(), marshaller, defaultFactory, equals, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param skipWrite a predicate used to determine if {@link #writeTo(ProtoStreamWriter, Object)} should skip writing the field.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    @SuppressWarnings("unchecked")
    public FunctionalScalarMarshaller(ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, ExceptionPredicate<T, IOException> skipWrite, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this((Class<T>) defaultFactory.get().getClass(), marshaller, defaultFactory, skipWrite, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param targetClass the type of this marshaller
     * @param marshaller the scalar marshaller used by this marshaller
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    public FunctionalScalarMarshaller(Class<? extends T> targetClass, ScalarMarshaller<V> marshaller, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this(targetClass, marshaller, Functions.constantSupplier(null), Objects::isNull, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param targetClass the type of this marshaller
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    public FunctionalScalarMarshaller(Class<? extends T> targetClass, ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this(targetClass, marshaller, defaultFactory, Objects::equals, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param targetClass the type of this marshaller
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param equals a predicate comparing the default value with the value to write, used to determine if {@link #writeTo(ProtoStreamWriter, Object)} should skip writing the field.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    public FunctionalScalarMarshaller(Class<? extends T> targetClass, ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, BiPredicate<V, V> equals, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this(targetClass, marshaller, defaultFactory, new ExceptionPredicate<T, IOException>() {
            @Override
            public boolean test(T value) throws IOException {
                return equals.test(function.apply(value), function.apply(defaultFactory.get()));
            }
        }, function, factory);
    }

    /**
     * Constructs a new single field marshaller based on single scalar marshaller.
     * @param targetClass the type of this marshaller
     * @param marshaller the scalar marshaller used by this marshaller
     * @param defaultFactory generates a default value returned by {@link #readFrom(ProtoStreamReader)} if no field was written.
     * @param skipWrite a predicate used to determine if {@link #writeTo(ProtoStreamWriter, Object)} should skip writing the field.
     * @param function a function that returns a value suitable for use by the specified scalar marshaller
     * @param factory a function applied to the value read from the specified scalar marshaller
     */
    public FunctionalScalarMarshaller(Class<? extends T> targetClass, ScalarMarshaller<V> marshaller, Supplier<T> defaultFactory, ExceptionPredicate<T, IOException> skipWrite, ExceptionFunction<T, V, IOException> function, ExceptionFunction<V, T, IOException> factory) {
        this.targetClass = targetClass;
        this.defaultFactory = defaultFactory;
        this.skipWrite = skipWrite;
        this.marshaller = marshaller;
        this.function = function;
        this.factory = factory;
    }

    @Override
    public T readFrom(ProtoStreamReader reader) throws IOException {
        T value = this.defaultFactory.get();
        while (!reader.isAtEnd()) {
            int tag = reader.readTag();
            switch (WireType.getTagFieldNumber(tag)) {
                case VALUE_INDEX:
                    value = this.factory.apply(this.marshaller.readFrom(reader));
                    break;
                default:
                    reader.skipField(tag);
            }
        }
        return value;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, T value) throws IOException {
        if (!this.skipWrite.test(value)) {
            writer.writeTag(VALUE_INDEX, this.marshaller.getWireType());
            this.marshaller.writeTo(writer, this.function.apply(value));
        }
    }

    @Override
    public Class<? extends T> getJavaClass() {
        return this.targetClass;
    }
}
