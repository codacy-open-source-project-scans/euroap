/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.marshalling.spi.util;

import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/**
 * Externalizer for hash table based sets constructed with a capacity rather than a size.
 * @author Paul Ferraro
 */
public class HashSetExternalizer<T extends Set<Object>> extends BoundedCollectionExternalizer<T> {
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final IntUnaryOperator CAPACITY = new IntUnaryOperator() {
        @Override
        public int applyAsInt(int size) {
            // Generate a suitable capacity for a given initial size
            return size * 2;
        }
    };

    public HashSetExternalizer(Class<T> targetClass, IntFunction<T> factory) {
        super(targetClass, new CapacityFactory<>(factory));
    }

    /**
     * Creates a hash table based map or collection with an appropriate capacity given an initial size.
     * @param <T> the map or collection type.
     */
    public static class CapacityFactory<T> implements Function<Integer, T>, IntFunction<T> {
        private final IntFunction<T> factory;

        public CapacityFactory(IntFunction<T> factory) {
            this.factory = factory;
        }

        @Override
        public T apply(Integer size) {
            return this.apply(size.intValue());
        }

        @Override
        public T apply(int size) {
            return this.factory.apply(CAPACITY.applyAsInt(size));
        }
    }
}
