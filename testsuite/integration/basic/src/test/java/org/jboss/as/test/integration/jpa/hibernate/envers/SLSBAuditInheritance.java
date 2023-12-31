/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jpa.hibernate.envers;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;

/**
 * @author Madhumita Sadhukhan
 */
@Stateless
public class SLSBAuditInheritance {

    @PersistenceContext(unitName = "myPlayer")
    EntityManager em;

    public SoccerPlayer createSoccerPlayer(String firstName, String lastName, String game, String clubName) {

        SoccerPlayer socplayer = new SoccerPlayer();
        socplayer.setFirstName(firstName);
        socplayer.setLastName(lastName);
        socplayer.setGame(game);
        socplayer.setClubName(clubName);
        em.persist(socplayer);
        return socplayer;
    }

    public SoccerPlayer updateSoccerPlayer(SoccerPlayer p) {

        em.merge(p);
        return p;
    }

    public SoccerPlayer retrieveSoccerPlayerbyId(int id) {

        AuditReader reader = AuditReaderFactory.get(em);
        SoccerPlayer val = reader.find(SoccerPlayer.class, id, 1);
        return val;
    }

}
