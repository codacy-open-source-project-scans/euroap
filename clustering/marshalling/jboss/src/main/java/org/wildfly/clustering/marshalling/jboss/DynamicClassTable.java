/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.clustering.marshalling.jboss;

import java.io.Externalizable;
import java.io.Serializable;
import java.security.PrivilegedAction;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.TimeZone;

import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * {@link org.jboss.marshalling.ClassTable} implementation that dynamically loads {@link ClassTableContributor} instances visible from a given {@link ClassLoader}.
 * @author Paul Ferraro
 */
public class DynamicClassTable extends SimpleClassTable {

    public DynamicClassTable(ClassLoader loader) {
        this(List.of(loader));
    }

    public DynamicClassTable(List<ClassLoader> loaders) {
        super(findClasses(loaders));
    }

    private static List<Class<?>> findClasses(List<ClassLoader> loaders) {
        List<Class<?>> knownClasses = WildFlySecurityManager.doUnchecked(new PrivilegedAction<List<Class<?>>>() {
            @Override
            public List<Class<?>> run() {
                List<Class<?>> classes = new LinkedList<>();
                for (ClassLoader loader : loaders) {
                    for (ClassTableContributor contributor : ServiceLoader.load(ClassTableContributor.class, loader)) {
                        classes.addAll(contributor.getKnownClasses());
                    }
                }
                return classes;
            }
        });

        List<Class<?>> classes = new ArrayList<>(knownClasses.size() + 36);
        classes.add(Serializable.class);
        classes.add(Externalizable.class);

        // Add common-use non-public JDK implementation classes
        classes.add(Clock.systemDefaultZone().getClass());
        classes.add(TimeZone.getDefault().getClass());
        classes.add(ZoneId.systemDefault().getClass());

        List<Void> randomAccessList = Collections.emptyList();
        List<Void> nonRandomAccessList = new LinkedList<>();
        // Add collection wrapper types
        classes.add(Collections.checkedCollection(randomAccessList, Void.class).getClass());
        classes.add(Collections.checkedCollection(nonRandomAccessList, Void.class).getClass());
        classes.add(Collections.checkedList(randomAccessList, Void.class).getClass());
        classes.add(Collections.checkedList(nonRandomAccessList, Void.class).getClass());
        classes.add(Collections.checkedMap(Collections.emptyMap(), Void.class, Void.class).getClass());
        classes.add(Collections.checkedNavigableMap(Collections.emptyNavigableMap(), Void.class, Void.class).getClass());
        classes.add(Collections.checkedNavigableSet(Collections.emptyNavigableSet(), Void.class).getClass());
        classes.add(Collections.checkedQueue(new LinkedList<>(), Void.class).getClass());
        classes.add(Collections.checkedSet(Collections.emptySet(), Void.class).getClass());
        classes.add(Collections.checkedSortedMap(Collections.emptySortedMap(), Void.class, Void.class).getClass());
        classes.add(Collections.checkedSortedSet(Collections.emptySortedSet(), Void.class).getClass());

        classes.add(Collections.synchronizedCollection(randomAccessList).getClass());
        classes.add(Collections.synchronizedCollection(nonRandomAccessList).getClass());
        classes.add(Collections.synchronizedList(randomAccessList).getClass());
        classes.add(Collections.synchronizedList(nonRandomAccessList).getClass());
        classes.add(Collections.synchronizedMap(Collections.emptyMap()).getClass());
        classes.add(Collections.synchronizedNavigableMap(Collections.emptyNavigableMap()).getClass());
        classes.add(Collections.synchronizedNavigableSet(Collections.emptyNavigableSet()).getClass());
        classes.add(Collections.synchronizedSet(Collections.emptySet()).getClass());
        classes.add(Collections.synchronizedSortedMap(Collections.emptySortedMap()).getClass());
        classes.add(Collections.synchronizedSortedSet(Collections.emptySortedSet()).getClass());

        classes.add(Collections.unmodifiableCollection(randomAccessList).getClass());
        classes.add(Collections.unmodifiableCollection(nonRandomAccessList).getClass());
        classes.add(Collections.unmodifiableList(randomAccessList).getClass());
        classes.add(Collections.unmodifiableList(nonRandomAccessList).getClass());
        classes.add(Collections.unmodifiableMap(Collections.emptyMap()).getClass());
        classes.add(Collections.unmodifiableNavigableMap(Collections.emptyNavigableMap()).getClass());
        classes.add(Collections.unmodifiableNavigableSet(Collections.emptyNavigableSet()).getClass());
        classes.add(Collections.unmodifiableSet(Collections.emptySet()).getClass());
        classes.add(Collections.unmodifiableSortedMap(Collections.emptySortedMap()).getClass());
        classes.add(Collections.unmodifiableSortedSet(Collections.emptySortedSet()).getClass());

        classes.addAll(knownClasses);

        return classes;
    }
}
