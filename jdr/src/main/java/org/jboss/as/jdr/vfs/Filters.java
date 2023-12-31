/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jdr.vfs;

import org.jboss.as.jdr.util.WildcardPattern;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;
import org.jboss.vfs.util.MatchAllVirtualFileFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author csams@redhat.com
 *         Date: 11/23/12
 */
public class Filters {

    public static final VirtualFileFilter TRUE = MatchAllVirtualFileFilter.INSTANCE;

    public static VirtualFileFilter not(final VirtualFileFilter filter) {
        return new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                return !filter.accepts(file);
            }
        };
    }

    public static VirtualFileFilter and(final VirtualFileFilter... filters) {
        return new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                for(VirtualFileFilter f: filters) {
                    if(!f.accepts(file)){
                        return false;
                    }
                }
                return true;
            }
        };
    }

    public static VirtualFileFilter or(final VirtualFileFilter... filters) {
        return new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                for(VirtualFileFilter f: filters) {
                    if(f.accepts(file)){
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public static VirtualFileFilter wildcard(final String p){
        return new VirtualFileFilter() {
            private WildcardPattern pattern = new WildcardPattern(p);
            @Override
            public boolean accepts(VirtualFile file) {
                return pattern.matches(file.getPathName());
            }
        };
    }

    public static BlocklistFilter wildcardBlockList() {
        return new WildcardBlocklistFilter();
    }

    public static BlocklistFilter wildcardBlocklistFilter(final String... patterns){
        return new WildcardBlocklistFilter(patterns);
    }

    public static BlocklistFilter regexBlockList() {
        return new RegexBlocklistFilter();
    }

    public static BlocklistFilter regexBlockList(String... patterns) {
        return new RegexBlocklistFilter(patterns);
    }

    public static VirtualFileFilter suffix(final String s){
        return new VirtualFileFilter() {
            @Override
            public boolean accepts(VirtualFile file) {
                return file.getPathName().endsWith(s);
            }
        };
    }

    public interface BlocklistFilter extends VirtualFileFilter {
        void add(final String... patterns);
    }

    private static class WildcardBlocklistFilter implements BlocklistFilter {

        private final List<WildcardPattern> patterns;

        public WildcardBlocklistFilter() {
            patterns = new ArrayList<WildcardPattern>();
            patterns.add(new WildcardPattern("*-users.properties"));
        }

        public WildcardBlocklistFilter(final String... patterns) {
            this.patterns = new ArrayList<WildcardPattern>(patterns.length);
            add(patterns);
        }

        @Override
        public boolean accepts(VirtualFile file) {
            for(WildcardPattern p: this.patterns){
                if(p.matches(file.getName())){
                    return false;
                }
            }
            return true;
        }

        public void add(final String... patterns){
            for(String p: patterns) {
                this.patterns.add(new WildcardPattern(p));
            }
        }
    }

    private static class RegexBlocklistFilter implements BlocklistFilter {
        private final List<Pattern> patterns;

        public RegexBlocklistFilter(){
            this.patterns = Arrays.asList(Pattern.compile(".*-users.properties"));
        }

        public RegexBlocklistFilter(final String... patterns){
            this.patterns = new ArrayList<Pattern>(patterns.length);
            add(patterns);
        }

        @Override
        public boolean accepts(final VirtualFile file) {
            for(Pattern p: this.patterns){
                if(p.matcher(file.getName()).matches()){
                    return false;
                }
            }
            return true;
        }

        public void add(final String... patterns){
            for(String p: patterns){
                this.patterns.add(Pattern.compile(p));
            }
        }
    }
}
