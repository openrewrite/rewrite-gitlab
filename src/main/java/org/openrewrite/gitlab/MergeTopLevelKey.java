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

/// Merges a top-level key into `.gitlab-ci.yml`. A key that is not yet present is placed near the top of the
/// file, after the first of `predecessors` that is present, rather than appended after the jobs. The insert mode
/// and property only position new entries within a key that is already present.
@RequiredArgsConstructor
class MergeTopLevelKey {
    private final String key;

    @Language("yml")
    private final String yaml;

    private final @Nullable Boolean acceptTheirs;
    private final @Nullable String objectIdentifyingProperty;
    private final @Nullable InsertMode insertMode;
    private final @Nullable String insertProperty;
    private final List<String> predecessors;

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
        List<Yaml.Mapping.Entry> entries = rootEntries(documents.getDocuments().get(0));
        InsertMode mode = insertMode;
        String property = insertProperty;
        if (!entries.isEmpty() && entries.stream().noneMatch(e -> key.equals(e.getKey().getValue()))) {
            String predecessor = predecessors.stream()
                    .filter(p -> entries.stream().anyMatch(e -> p.equals(e.getKey().getValue())))
                    .findFirst()
                    .orElse(null);
            mode = predecessor == null ? InsertMode.Before : InsertMode.After;
            property = predecessor == null ? entries.get(0).getKey().getValue() : predecessor;
        }
        return (Yaml.Documents) new MergeYaml("$", yaml, acceptTheirs, objectIdentifyingProperty, null, mode, property, null)
                .getVisitor()
                .visitNonNull(documents, ctx);
    }

    private static boolean isHeader(Yaml.Document document) {
        List<Yaml.Mapping.Entry> entries = rootEntries(document);
        return entries.size() == 1 && "spec".equals(entries.get(0).getKey().getValue());
    }

    private static List<Yaml.Mapping.Entry> rootEntries(Yaml.Document document) {
        return document.getBlock() instanceof Yaml.Mapping ? ((Yaml.Mapping) document.getBlock()).getEntries() : emptyList();
    }
}
