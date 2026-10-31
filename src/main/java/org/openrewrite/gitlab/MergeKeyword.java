/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gitlab;

import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FindSourceFiles;
import org.openrewrite.Preconditions;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.MergeYaml.InsertMode;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/// Merges a snippet into `.gitlab-ci.yml`, placing a top-level `key` or a `jobKeyword` within it that is not yet
/// present where [KeywordOrder] puts it, rather than appending it after the jobs.
@RequiredArgsConstructor
class MergeKeyword {
    private static final Pattern LINE_START = Pattern.compile("(?m)^(?=.)");
    private static final Pattern REMOTE_URL = Pattern.compile("https?://");

    private final String key;
    private final @Nullable String jobKeyword;

    @Language("yml")
    private final String yaml;

    private final @Nullable Boolean acceptTheirs;
    private final @Nullable String objectIdentifyingProperty;
    private final @Nullable List<String> items;
    private final boolean beforeTrailingRemotes;
    private final boolean existingMappingOnly;

    static MergeKeyword global(@Language("yml") String yaml, @Nullable Boolean acceptTheirs,
                               @Nullable String objectIdentifyingProperty) {
        return new MergeKeyword(firstKey(yaml), null, yaml, acceptTheirs, objectIdentifyingProperty, null, false, false);
    }

    /// Merges an entry into the `include` list, ahead of any `remote` includes that end it.
    static MergeKeyword include(@Language("yml") String yaml, String objectIdentifyingProperty) {
        return new MergeKeyword("include", null, yaml, false, objectIdentifyingProperty, null, true, false);
    }

    /// Merges a keyword into the `default` section, adding the section when it is missing.
    static MergeKeyword defaults(@Language("yml") String keywordYaml, @Nullable Boolean acceptTheirs) {
        return section("default", keywordYaml, acceptTheirs, false);
    }

    /// Merges a keyword into a job defined in the file, or into the `default` section when `job` is `null`.
    /// A job that is missing, or not defined as a mapping, is left alone.
    static MergeKeyword job(@Nullable String job, @Language("yml") String keywordYaml, @Nullable Boolean acceptTheirs) {
        return job == null ? defaults(keywordYaml, acceptTheirs) : section(job, keywordYaml, acceptTheirs, true);
    }

    private static MergeKeyword section(String key, @Language("yml") String keywordYaml, @Nullable Boolean acceptTheirs,
                                        boolean existingMappingOnly) {
        String yaml = key + ":\n" + LINE_START.matcher(keywordYaml).replaceAll("  ");
        return new MergeKeyword(key, firstKey(keywordYaml), yaml, acceptTheirs, null, null, false, existingMappingOnly);
    }

    /// Merges a list of scalars, placing each item missing from an existing list by the order of `items`.
    static MergeKeyword list(String key, List<String> items, @Nullable Boolean acceptTheirs) {
        return new MergeKeyword(key, null, listYaml(key, items), acceptTheirs, null, items, false, false);
    }

    private static String firstKey(String yaml) {
        return yaml.substring(0, yaml.indexOf(':'));
    }

    private static String listYaml(String key, List<String> items) {
        return key + ":\n  - " + String.join("\n  - ", items);
    }

    TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof Yaml.Documents)) {
                    return tree;
                }
                Yaml.Documents documents = (Yaml.Documents) tree;
                List<Yaml.Document> docs = documents.getDocuments();
                if (docs.size() > 1 && isHeader(docs.get(0))) {
                    Yaml.Documents pipeline = documents.withDocuments(docs.subList(1, docs.size()));
                    Yaml.Documents merged = merge(pipeline, ctx);
                    return merged == pipeline ? documents :
                            documents.withDocuments(ListUtils.concat(docs.get(0), merged.getDocuments()));
                }
                return merge(documents, ctx);
            }
        });
    }

    private Yaml.Documents merge(Yaml.Documents documents, ExecutionContext ctx) {
        List<Yaml.Mapping.Entry> rootEntries = entries(documents.getDocuments().get(0).getBlock());
        Yaml.Mapping.Entry existing = find(rootEntries, key);
        if (existingMappingOnly && (existing == null || !(existing.getValue() instanceof Yaml.Mapping))) {
            return documents;
        }
        if (items != null && existing != null && existing.getValue() instanceof Yaml.Sequence) {
            return Boolean.TRUE.equals(acceptTheirs) ? documents :
                    mergeItems(documents, (Yaml.Sequence) existing.getValue(), items, ctx);
        }
        return mergeYaml(documents, yaml, placement(rootEntries, existing), ctx);
    }

    private Yaml.Documents mergeItems(Yaml.Documents documents, Yaml.Sequence sequence, List<String> listed,
                                      ExecutionContext ctx) {
        Set<String> present = sequence.getEntries().stream()
                .filter(e -> e.getBlock() instanceof Yaml.Scalar)
                .map(e -> ((Yaml.Scalar) e.getBlock()).getValue())
                .collect(toSet());
        String previous = null;
        for (String item : listed) {
            if (!present.contains(item)) {
                documents = mergeYaml(documents, listYaml(key, singletonList(item)),
                        itemPlacement(previous, listed, present), ctx);
                present.add(item);
            }
            previous = item;
        }
        return documents;
    }

    private static KeywordOrder.Placement itemPlacement(@Nullable String previous, List<String> listed, Set<String> present) {
        if (previous != null) {
            return new KeywordOrder.Placement(InsertMode.After, previous);
        }
        return listed.stream()
                .filter(present::contains)
                .findFirst()
                .map(next -> new KeywordOrder.Placement(InsertMode.Before, next))
                .orElse(KeywordOrder.Placement.LAST);
    }

    private Yaml.Documents mergeYaml(Yaml.Documents documents, @Language("yml") String snippet,
                                     KeywordOrder.Placement placement, ExecutionContext ctx) {
        return (Yaml.Documents) new MergeYaml("$", snippet, acceptTheirs, objectIdentifyingProperty, null,
                placement.getMode(), placement.getProperty(), null)
                .getVisitor()
                .visitNonNull(documents, ctx);
    }

    // MergeYaml applies the insert mode and property to every mapping it adds entries to, so a placement is only
    // computed for the one mapping that lacks the key being added
    private KeywordOrder.Placement placement(List<Yaml.Mapping.Entry> rootEntries, Yaml.Mapping.@Nullable Entry existing) {
        if (existing == null) {
            return KeywordOrder.GLOBAL.placement(keys(rootEntries), key);
        }
        if (jobKeyword != null && existing.getValue() instanceof Yaml.Mapping) {
            List<Yaml.Mapping.Entry> jobEntries = entries(existing.getValue());
            if (find(jobEntries, jobKeyword) == null) {
                KeywordOrder.Placement placement = KeywordOrder.JOB.placement(keys(jobEntries), jobKeyword);
                return insertsAfterBlockScalar(jobEntries, placement) ? KeywordOrder.Placement.LAST : placement;
            }
        }
        if (beforeTrailingRemotes && existing.getValue() instanceof Yaml.Sequence) {
            return beforeTrailingRemotes(((Yaml.Sequence) existing.getValue()).getEntries());
        }
        return KeywordOrder.Placement.LAST;
    }

    // An entry that follows a block scalar has no line break of its own, and MergeYaml moves it out of the job when
    // inserting between the two, so such a keyword is appended instead
    private static boolean insertsAfterBlockScalar(List<Yaml.Mapping.Entry> entries, KeywordOrder.Placement placement) {
        if (placement.getMode() != InsertMode.After) {
            return false;
        }
        int next = keys(entries).indexOf(placement.getProperty()) + 1;
        return next < entries.size() && !entries.get(next).getPrefix().contains("\n");
    }

    // MergeYaml finds the include to insert before by the same key as `identifier`, so the new include is appended
    // when that key does not single out the first of the trailing remote includes
    private static KeywordOrder.Placement beforeTrailingRemotes(List<Yaml.Sequence.Entry> includes) {
        int firstTrailingRemote = includes.size();
        while (firstTrailingRemote > 0 && isRemote(includes.get(firstTrailingRemote - 1).getBlock())) {
            firstTrailingRemote--;
        }
        if (firstTrailingRemote == includes.size()) {
            return KeywordOrder.Placement.LAST;
        }
        List<String> identifiers = includes.stream().map(e -> identifier(e.getBlock())).collect(toList());
        String remote = identifiers.get(firstTrailingRemote);
        return identifiers.indexOf(remote) == firstTrailingRemote ?
                new KeywordOrder.Placement(InsertMode.Before, remote) :
                KeywordOrder.Placement.LAST;
    }

    private static boolean isRemote(Yaml.Block include) {
        if (include instanceof Yaml.Scalar) {
            return REMOTE_URL.matcher(((Yaml.Scalar) include).getValue()).lookingAt();
        }
        return find(entries(include), "remote") != null;
    }

    private static String identifier(Yaml.Block include) {
        if (include instanceof Yaml.Scalar) {
            return ((Yaml.Scalar) include).getValue();
        }
        List<Yaml.Mapping.Entry> entries = entries(include);
        if (entries.isEmpty()) {
            return "";
        }
        Yaml.Mapping.Entry first = entries.get(0);
        return first.getValue() instanceof Yaml.Scalar ?
                first.getKey().getValue() + ": " + ((Yaml.Scalar) first.getValue()).getValue() :
                first.getKey().getValue();
    }

    private static boolean isHeader(Yaml.Document document) {
        List<Yaml.Mapping.Entry> entries = entries(document.getBlock());
        return entries.size() == 1 && "spec".equals(entries.get(0).getKey().getValue());
    }

    private static List<Yaml.Mapping.Entry> entries(Yaml.Block block) {
        return block instanceof Yaml.Mapping ? ((Yaml.Mapping) block).getEntries() : emptyList();
    }

    private static Yaml.Mapping.@Nullable Entry find(List<Yaml.Mapping.Entry> entries, String key) {
        return entries.stream().filter(e -> key.equals(e.getKey().getValue())).findFirst().orElse(null);
    }

    private static List<String> keys(List<Yaml.Mapping.Entry> entries) {
        return entries.stream().map(e -> e.getKey().getValue()).collect(toList());
    }
}
