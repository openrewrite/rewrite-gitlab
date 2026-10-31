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
package org.openrewrite.gitlab.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.gitlab.YamlAnchors;
import org.openrewrite.gitlab.table.GitLabIncludes;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindIncludes extends Recipe {
    private static final List<String> TYPES = asList("component", "template", "remote", "project", "local");

    transient GitLabIncludes includes = new GitLabIncludes(this);

    String displayName = "Find GitLab CI includes";

    String description = "Find the components, templates and files that `.gitlab-ci.yml` includes, " +
            "and list each with its version and inputs.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JsonPathMatcher includeMatcher = new JsonPathMatcher("$.include");
        JsonPathMatcher includeListMatcher = new JsonPathMatcher("$.include[*]");
        Map<String, JsonPathMatcher> typeMatchers = new HashMap<>();
        for (String type : TYPES) {
            typeMatchers.put(type, new JsonPathMatcher("$.include." + type));
        }
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new YamlIsoVisitor<ExecutionContext>() {
            private Map<UUID, Yaml> aliases = emptyMap();

            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                aliases = YamlAnchors.resolveAliases(document);
                return super.visitDocument(document, ctx);
            }

            @Override
            public Yaml.Sequence.Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext ctx) {
                Yaml.Sequence.Entry e = super.visitSequenceEntry(entry, ctx);
                Yaml.Mapping.Entry parent = getCursor().firstEnclosing(Yaml.Mapping.Entry.class);
                if (e.getBlock() instanceof Yaml.Scalar && parent != null && "include".equals(parent.getKey().getValue()) &&
                    includeListMatcher.matches(getCursor())) {
                    return e.withBlock(includePath((Yaml.Scalar) e.getBlock(), ctx));
                }
                return e;
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                String type = e.getKey().getValue();
                if ("include".equals(type) && e.getValue() instanceof Yaml.Scalar && includeMatcher.matches(getCursor())) {
                    return e.withValue(includePath((Yaml.Scalar) e.getValue(), ctx));
                }
                JsonPathMatcher typeMatcher = typeMatchers.get(type);
                String value = scalar(e.getValue());
                if (typeMatcher == null || value == null || !typeMatcher.matches(getCursor())) {
                    return e;
                }
                String sourcePath = sourcePath();
                Yaml.Mapping include = getCursor().getParentTreeCursor().getValue();
                String inputs = inputs(value(include, "inputs"));
                List<GitLabIncludes.Row> rows = new ArrayList<>();
                if ("component".equals(type)) {
                    int at = value.lastIndexOf('@');
                    rows.add(new GitLabIncludes.Row(sourcePath, type,
                            at < 0 ? value : value.substring(0, at), at < 0 ? null : value.substring(at + 1), inputs));
                } else if ("project".equals(type)) {
                    String ref = scalar(value(include, "ref"));
                    for (String file : files(value(include, "file"))) {
                        rows.add(new GitLabIncludes.Row(sourcePath, type, value + ":" + file, ref, inputs));
                    }
                } else {
                    rows.add(new GitLabIncludes.Row(sourcePath, type, value, null, inputs));
                }
                if (rows.isEmpty()) {
                    return e;
                }
                rows.forEach(row -> includes.insertRow(ctx, row));
                return SearchResult.found(e);
            }

            private Yaml.Scalar includePath(Yaml.Scalar include, ExecutionContext ctx) {
                String path = include.getValue();
                String type = path.startsWith("http://") || path.startsWith("https://") ? "remote" : "local";
                includes.insertRow(ctx, new GitLabIncludes.Row(sourcePath(), type, path, null, null));
                return SearchResult.found(include);
            }

            private String sourcePath() {
                return getCursor().firstEnclosingOrThrow(Yaml.Documents.class).getSourcePath().toString();
            }

            private List<String> files(Yaml.@Nullable Block file) {
                if (file instanceof Yaml.Sequence) {
                    List<String> files = new ArrayList<>();
                    for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) file).getEntries()) {
                        String path = scalar(entry.getBlock());
                        if (path != null) {
                            files.add(path);
                        }
                    }
                    return files;
                }
                String path = scalar(file);
                return path == null ? emptyList() : singletonList(path);
            }

            private @Nullable String inputs(Yaml.@Nullable Block inputs) {
                Yaml.Mapping mapping = inputs == null ? null : YamlAnchors.mergedMapping(inputs, aliases);
                if (mapping == null || mapping.getEntries().isEmpty()) {
                    return null;
                }
                return mapping.getEntries().stream()
                        .map(entry -> entry.getKey().getValue() + "=" + render(entry.getValue()))
                        .collect(joining("\n"));
            }

            private String render(Yaml.Block block) {
                String scalar = scalar(block);
                if (scalar != null) {
                    return scalar;
                }
                if (block instanceof Yaml.Alias) {
                    return "*" + ((Yaml.Alias) block).getAnchor().getKey();
                }
                if (block instanceof Yaml.Sequence) {
                    return ((Yaml.Sequence) block).getEntries().stream()
                            .map(entry -> render(entry.getBlock()))
                            .collect(joining(", ", "[", "]"));
                }
                if (block instanceof Yaml.Mapping) {
                    return ((Yaml.Mapping) block).getEntries().stream()
                            .map(entry -> entry.getKey().getValue() + ": " + render(entry.getValue()))
                            .collect(joining(", ", "{", "}"));
                }
                return "";
            }

            private @Nullable String scalar(Yaml.@Nullable Block block) {
                Yaml resolved = block instanceof Yaml.Alias ? aliases.get(block.getId()) : block;
                return resolved instanceof Yaml.Scalar ? ((Yaml.Scalar) resolved).getValue() : null;
            }
        });
    }

    private static Yaml.@Nullable Block value(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
