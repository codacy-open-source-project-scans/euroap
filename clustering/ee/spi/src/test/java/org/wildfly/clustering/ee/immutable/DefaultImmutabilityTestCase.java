/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.clustering.ee.immutable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.security.AllPermission;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;
import java.time.zone.ZoneOffsetTransitionRule;
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;
import java.time.zone.ZoneRules;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.wildfly.clustering.ee.Immutability;

/**
 * Unit test for {@link DefaultImmutability}
 *
 * @author Paul Ferraro
 */
public class DefaultImmutabilityTestCase {

    @Test
    public void test() throws Exception {
        this.test(new CompositeImmutability(EnumSet.allOf(DefaultImmutability.class)));
    }

    protected void test(Immutability immutability) throws Exception {
        assertFalse(immutability.test(new Object()));
        assertFalse(immutability.test(new Date()));
        assertFalse(immutability.test(new AtomicInteger()));
        assertFalse(immutability.test(new AtomicLong()));
        assertTrue(immutability.test(null));
        assertTrue(immutability.test(Collections.emptyEnumeration()));
        assertTrue(immutability.test(Collections.emptyIterator()));
        assertTrue(immutability.test(Collections.emptyList()));
        assertTrue(immutability.test(Collections.emptyListIterator()));
        assertTrue(immutability.test(Collections.emptyMap()));
        assertTrue(immutability.test(Collections.emptyNavigableMap()));
        assertTrue(immutability.test(Collections.emptyNavigableSet()));
        assertTrue(immutability.test(Collections.emptySet()));
        assertTrue(immutability.test(Collections.emptySortedMap()));
        assertTrue(immutability.test(Collections.emptySortedSet()));
        assertTrue(immutability.test(Boolean.TRUE));
        assertTrue(immutability.test('a'));
        assertTrue(immutability.test(this.getClass()));
        assertTrue(immutability.test(Currency.getInstance(Locale.US)));
        assertTrue(immutability.test(Locale.getDefault()));
        assertTrue(immutability.test(Integer.valueOf(1).byteValue()));
        assertTrue(immutability.test(Integer.valueOf(1).shortValue()));
        assertTrue(immutability.test(1));
        assertTrue(immutability.test(1L));
        assertTrue(immutability.test(1F));
        assertTrue(immutability.test(1.0));
        assertTrue(immutability.test(BigInteger.valueOf(1)));
        assertTrue(immutability.test(BigDecimal.valueOf(1)));
        assertTrue(immutability.test(InetAddress.getLocalHost()));
        assertTrue(immutability.test(new InetSocketAddress(InetAddress.getLocalHost(), 80)));
        assertTrue(immutability.test(MathContext.UNLIMITED));
        assertTrue(immutability.test("test"));
        assertTrue(immutability.test(TimeZone.getDefault()));
        assertTrue(immutability.test(UUID.randomUUID()));
        assertTrue(immutability.test(TimeUnit.DAYS));
        File file = new File(System.getProperty("user.home"));
        assertTrue(immutability.test(file));
        assertTrue(immutability.test(file.toURI()));
        assertTrue(immutability.test(file.toURI().toURL()));
        assertTrue(immutability.test(FileSystems.getDefault().getRootDirectories().iterator().next()));
        assertTrue(immutability.test(new AllPermission()));

        assertTrue(immutability.test(DateTimeFormatter.BASIC_ISO_DATE));
        assertTrue(immutability.test(DecimalStyle.STANDARD));
        assertTrue(immutability.test(Duration.ZERO));
        assertTrue(immutability.test(Instant.now()));
        assertTrue(immutability.test(LocalDate.now()));
        assertTrue(immutability.test(LocalDateTime.now()));
        assertTrue(immutability.test(LocalTime.now()));
        assertTrue(immutability.test(MonthDay.now()));
        assertTrue(immutability.test(Period.ZERO));
        assertTrue(immutability.test(ValueRange.of(0L, 10L)));
        assertTrue(immutability.test(WeekFields.ISO));
        assertTrue(immutability.test(Year.now()));
        assertTrue(immutability.test(YearMonth.now()));
        assertTrue(immutability.test(ZoneOffset.UTC));
        assertTrue(immutability.test(ZoneRules.of(ZoneOffset.UTC).nextTransition(Instant.now())));
        assertTrue(immutability.test(ZoneOffsetTransitionRule.of(Month.JANUARY, 1, DayOfWeek.SUNDAY, LocalTime.MIDNIGHT, true, TimeDefinition.STANDARD, ZoneOffset.UTC, ZoneOffset.ofHours(1), ZoneOffset.ofHours(2))));
        assertTrue(immutability.test(ZoneRules.of(ZoneOffset.UTC)));
        assertTrue(immutability.test(ZonedDateTime.now()));
        assertTrue(immutability.test(new JCIPImmutableObject()));

        assertTrue(immutability.test(Collections.singleton("1")));
        assertTrue(immutability.test(Collections.singletonList("1")));
        assertTrue(immutability.test(Collections.singletonMap("1", "2")));

        assertTrue(immutability.test(Collections.singleton(new JCIPImmutableObject())));
        assertTrue(immutability.test(Collections.singletonList(new JCIPImmutableObject())));
        assertTrue(immutability.test(Collections.singletonMap("1", new JCIPImmutableObject())));
        assertTrue(immutability.test(new AbstractMap.SimpleImmutableEntry<>("1", new JCIPImmutableObject())));

        assertTrue(immutability.test(Collections.unmodifiableCollection(Arrays.asList("1", "2"))));
        assertTrue(immutability.test(Collections.unmodifiableList(Arrays.asList("1", "2"))));
        assertTrue(immutability.test(Collections.unmodifiableMap(Collections.singletonMap("1", "2"))));
        assertTrue(immutability.test(Collections.unmodifiableNavigableMap(new TreeMap<>(Collections.singletonMap("1", "2")))));
        assertTrue(immutability.test(Collections.unmodifiableNavigableSet(new TreeSet<>(Collections.singleton("1")))));
        assertTrue(immutability.test(Collections.unmodifiableSet(Collections.singleton("1"))));
        assertTrue(immutability.test(Collections.unmodifiableSortedMap(new TreeMap<>(Collections.singletonMap("1", "2")))));
        assertTrue(immutability.test(Collections.unmodifiableSortedSet(new TreeSet<>(Collections.singleton("1")))));
        assertTrue(immutability.test(List.of()));
        assertTrue(immutability.test(List.of(1)));
        assertTrue(immutability.test(List.of(1, 2)));
        assertTrue(immutability.test(List.of(1, 2, 3)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6, 7)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6, 7, 8)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
        assertTrue(immutability.test(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)));
        assertTrue(immutability.test(Set.of()));
        assertTrue(immutability.test(Set.of(1)));
        assertTrue(immutability.test(Set.of(1, 2)));
        assertTrue(immutability.test(Set.of(1, 2, 3)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6, 7)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6, 7, 8)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
        assertTrue(immutability.test(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)));
        assertTrue(immutability.test(Map.of()));
        assertTrue(immutability.test(Map.of(1, "1")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5", 6, "6")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5", 6, "6", 7, "7")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5", 6, "6", 7, "7", 8, "8")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5", 6, "6", 7, "7", 8, "8", 9, "9")));
        assertTrue(immutability.test(Map.of(1, "1", 2, "2", 3, "3", 4, "4", 5, "5", 6, "6", 7, "7", 8, "8", 9, "9", 10, "10")));
        assertTrue(immutability.test(Map.ofEntries()));
        assertTrue(immutability.test(Map.ofEntries(Map.entry(1, "1"))));
        assertTrue(immutability.test(Map.ofEntries(Map.entry(1, "1"), Map.entry(2, "2"))));

        Object mutableObject = new AtomicInteger();
        assertFalse(immutability.test(Collections.singletonList(mutableObject)));
        assertFalse(immutability.test(Collections.singletonMap("1", mutableObject)));
        assertFalse(immutability.test(new AbstractMap.SimpleImmutableEntry<>("1", mutableObject)));
    }

    @net.jcip.annotations.Immutable
    static class JCIPImmutableObject {
    }
}
