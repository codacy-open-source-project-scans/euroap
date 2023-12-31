/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.ejb3.validator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.PassivationCapable;

import org.hibernate.validator.cdi.spi.BeanNames;
import org.hibernate.validator.metadata.BeanMetaDataClassNormalizer;

/**
 *
 * This class is used to provide bean validation with a interface instead of Jakarta Enterprise Beans proxy. This is necessary as Jakarta Enterprise Beans proxy
 * does not contain generics data.
 * @see <a href="https://issues.redhat.com/browse/WFLY-11566">WFLY-11566</a>
 *
 * @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */

public class EjbProxyBeanMetaDataClassNormalizer implements BeanMetaDataClassNormalizer, Bean<BeanMetaDataClassNormalizer>, PassivationCapable {

    private static final Set<Type> TYPES = Collections.singleton(BeanMetaDataClassNormalizer.class);
    private static final Set<Annotation> QUALIFIERS = Collections.singleton(NamedLiteral.of(BeanNames.BEAN_META_DATA_CLASS_NORMALIZER));


    @Override
    public <T> Class<? super T> normalize(Class<T> clazz) {
        if (EjbProxy.class.isAssignableFrom(clazz) && clazz.getSuperclass() != Object.class) {
            return clazz.getSuperclass();
        }
        return clazz;
    }

    @Override
    public Class<?> getBeanClass() {
        return BeanMetaDataClassNormalizer.class;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

//    This method is removed from jakarta.enterprise.inject.spi.Bean.
//    So comment out @Override to be able to compile with either javax or jakarta cdi-api
//    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public BeanMetaDataClassNormalizer create(CreationalContext<BeanMetaDataClassNormalizer> creationalContext) {
        return new EjbProxyBeanMetaDataClassNormalizer();
    }

    @Override
    public void destroy(BeanMetaDataClassNormalizer beanMetaDataClassNormalizer, CreationalContext<BeanMetaDataClassNormalizer> creationalContext) {

    }

    @Override
    public Set<Type> getTypes() {
        return TYPES;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return QUALIFIERS;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public String getName() {
        return BeanNames.BEAN_META_DATA_CLASS_NORMALIZER;
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String getId() {
        return EjbProxyBeanMetaDataClassNormalizer.class.getName();
    }
}
