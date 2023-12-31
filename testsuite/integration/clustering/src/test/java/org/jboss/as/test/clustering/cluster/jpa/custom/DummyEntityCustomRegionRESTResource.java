/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.jpa.custom;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

/**
 * @author Tommaso Borgato
 */
@RequestScoped
@Path("/custom-region")
public class DummyEntityCustomRegionRESTResource {

    @PersistenceContext(unitName = "MainPUCustomRegion")
    private EntityManager em;

    @Resource(name = "infinispan/hibernate")
    private CacheContainer container;

    @Inject
    private UserTransaction tx;

    @GET
    @Path("/create/{id}")
    public void createNew(@PathParam(value = "id") Long id) throws Exception {
        final DummyEntityCustomRegion entity = new DummyEntityCustomRegion();
        entity.setId(id);
        tx.begin();
        em.persist(entity);
        tx.commit();
    }

    @GET
    @Path("/cache/{id}")
    public void addToCacheByQuerying(@PathParam(value = "id") Long id) {
        em.createQuery("select b from DummyEntityCustomRegion b where b.id=:id", DummyEntityCustomRegion.class)
                .setParameter("id", id)
                .setHint("jakarta.persistence.cache.storeMode", CacheStoreMode.USE)
                .setHint("jakarta.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS)
                .getSingleResult();
    }

    @GET
    @Path("/evict/{id}")
    public void evict(@PathParam(value = "id") Long id) {
        em.getEntityManagerFactory().getCache().evict(DummyEntityCustomRegion.class, id);
    }

    @GET
    @Path("/isInCache/{id}")
    @Produces("text/plain")
    public boolean isEntityInCache(@PathParam(value = "id") Long id) {
        return em.getEntityManagerFactory().getCache().contains(DummyEntityCustomRegion.class, id);
    }

    @GET
    @Path("/clear-statistics")
    @Produces("text/plain")
    public Response clearStatistics() {
        Statistics stats = em.unwrap(Session.class).getSessionFactory().getStatistics();
        stats.clear();
        return Response.ok().build();
    }

    @GET
    @Path("/region-name")
    @Produces("text/plain")
    public Response getRegionNameForEntity(@QueryParam("name") String dummyEntityRegionName, @QueryParam("id") Long id) {
        for (String name : container.getCacheNames()) {
            if (name.contains(dummyEntityRegionName)) {
                Cache cache = container.getCache(name);
                for (Object cs: cache.keySet()) {
                    if (cs.toString().equalsIgnoreCase(String.format("%s#%d", DummyEntityCustomRegion.class.getCanonicalName(), id))) {
                        return Response.ok(name).build();
                    }
                }
            }
        }
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("/is-replicated/{cache-name}")
    @Produces("text/plain")
    public Response getIsReplicated(@PathParam(value = "cache-name") String cacheName) {
        boolean res = container.getCache(cacheName).getCacheConfiguration().clustering().cacheMode().isReplicated();
        return Response.ok().entity(Boolean.toString(res)).build();
    }

    @GET
    @Path("/is-invalidation/{cache-name}")
    @Produces("text/plain")
    public Response getIsInvalidation(@PathParam(value = "cache-name") String cacheName) {
        boolean res = container.getCache(cacheName).getCacheConfiguration().clustering().cacheMode().isInvalidation();
        return Response.ok().entity(Boolean.toString(res)).build();
    }

    @GET
    @Path("/eviction-max-entries/{cache-name}")
    @Produces("text/plain")
    public Response getEvictionMaxEntries(@PathParam(value = "cache-name") String cacheName) {
        Object res = container.getCache(cacheName).getCacheConfiguration().memory().size();
        return Response.ok().entity(res).build();
    }

    @GET
    @Path("/expiration-lifespan/{cache-name}")
    @Produces("text/plain")
    public Response getExpirationLifespan(@PathParam(value = "cache-name") String cacheName) {
        Object res = container.getCache(cacheName).getCacheConfiguration().expiration().lifespan();
        return Response.ok().entity(res).build();
    }

    @GET
    @Path("/expiration-max-idle/{cache-name}")
    @Produces("text/plain")
    public Response getExpirationMaxIdle(@PathParam(value = "cache-name") String cacheName) {
        Object res = container.getCache(cacheName).getCacheConfiguration().expiration().maxIdle();
        return Response.ok().entity(res).build();
    }

    @GET
    @Path("/expiration-wake-up-interval/{cache-name}")
    @Produces("text/plain")
    public Response getExpirationWakeUpInterval(@PathParam(value = "cache-name") String cacheName) {
        Object res = container.getCache(cacheName).getCacheConfiguration().expiration().wakeUpInterval();
        return Response.ok().entity(res).build();
    }
}
