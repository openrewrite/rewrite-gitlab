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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/// Merges a snippet into `.gitlab-ci.yml`, placing a top-level `key` or a `jobKeyword` within it that is not yet
/// present where [KeywordOrder] puts it, rather than appending it after the jobs. The insert mode and property only
/// position new entries within a top-level list that is already present, such as `stages`.
@RequiredArgsConstructor
class MergeKeyword {
    private final String key;
    private final @Nullable String jobKeyword;

    @Language("yml")
    private final String yaml;

    private final @Nullable Boolean acceptTheirs;
    private final @Nullable String objectIdentifyingProperty;
    private final @Nullable InsertMode insertMode;
    private final @Nullable String insertProperty;

    static MergeKeyword global(String key, @Language("yml") String yaml, @Nullable Boolean acceptTheirs,
                               @Nullable String objectIdentifyingProperty,
                               @Nullable InsertMode insertMode, @Nullable String insertProperty) {
        return new MergeKeyword(key, null, yaml, acceptTheirs, objectIdentifyingProperty, insertMode, insertProperty);
    }

    /// Merges a keyword into the `default` section or a job.
    static MergeKeyword job(String job, String keyword, @Language("yml") String yaml, @Nullable Boolean acceptTheirs) {
        return new MergeKeyword(job, keyword, yaml, acceptTheirs, null, null, null);
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
        InsertMode mode = insertMode;
        String property = insertProperty;
        KeywordOrder.Placement placement = placement(documents.getDocuments().get(0).getBlock());
        if (placement != null) {
            mode = placement.getMode();
            property = placement.getProperty();
        }
        return (Yaml.Documents) new MergeYaml("$", yaml, acceptTheirs, objectIdentifyingProperty, null, mode, property, null)
                .getVisitor()
                .visitNonNull(documents, ctx);
    }

    // MergeYaml applies the insert mode and property to every mapping it adds entries to, so a placement is only
    // computed for the one mapping that lacks the key being added
    private KeywordOrder.@Nullable Placement placement(Yaml.Block root) {
        List<Yaml.Mapping.Entry> rootEntries = entries(root);
        Yaml.Mapping.Entry existing = find(rootEntries, key);
        if (existing == null) {
            return KeywordOrder.GLOBAL.placement(keys(rootEntries), key);
        }
        if (jobKeyword != null && existing.getValue() instanceof Yaml.Mapping) {
            List<Yaml.Mapping.Entry> jobEntries = entries(existing.getValue());
            if (find(jobEntries, jobKeyword) == null) {
                return KeywordOrder.JOB.placement(keys(jobEntries), jobKeyword);
            }
        }
        return null;
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
