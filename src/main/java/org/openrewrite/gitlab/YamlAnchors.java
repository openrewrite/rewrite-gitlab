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

import org.jspecify.annotations.Nullable;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;

final class YamlAnchors {

    private YamlAnchors() {
    }

    // Anchor names may be reused, in which case an alias refers to the most recent anchor of that name before it
    static Map<UUID, Yaml> resolveAliases(Yaml.Document document) {
        Map<String, Yaml> anchored = new HashMap<>();
        return new YamlIsoVisitor<Map<UUID, Yaml>>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, Map<UUID, Yaml> resolved) {
                define(mapping.getAnchor(), mapping);
                return super.visitMapping(mapping, resolved);
            }

            @Override
            public Yaml.Sequence visitSequence(Yaml.Sequence sequence, Map<UUID, Yaml> resolved) {
                define(sequence.getAnchor(), sequence);
                return super.visitSequence(sequence, resolved);
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, Map<UUID, Yaml> resolved) {
                define(scalar.getAnchor(), scalar);
                return scalar;
            }

            @Override
            public Yaml.Alias visitAlias(Yaml.Alias alias, Map<UUID, Yaml> resolved) {
                Yaml target = anchored.get(alias.getAnchor().getKey());
                if (target != null) {
                    resolved.put(alias.getId(), target);
                }
                return alias;
            }

            private void define(Yaml.@Nullable Anchor anchor, Yaml node) {
                if (anchor != null) {
                    anchored.put(anchor.getKey(), node);
                }
            }
        }.reduce(document, new HashMap<>());
    }

    static Yaml.@Nullable Mapping mergedMapping(Yaml mergeSource, Map<UUID, Yaml> aliases) {
        Yaml merged = mergeSource instanceof Yaml.Alias ? aliases.get(mergeSource.getId()) : mergeSource;
        return merged instanceof Yaml.Mapping ? (Yaml.Mapping) merged : null;
    }

    static Set<String> anchorsIn(Yaml tree) {
        return new YamlIsoVisitor<Set<String>>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, Set<String> anchors) {
                add(mapping.getAnchor(), anchors);
                return super.visitMapping(mapping, anchors);
            }

            @Override
            public Yaml.Sequence visitSequence(Yaml.Sequence sequence, Set<String> anchors) {
                add(sequence.getAnchor(), anchors);
                return super.visitSequence(sequence, anchors);
            }

            @Override
            public Yaml.Scalar visitScalar(Yaml.Scalar scalar, Set<String> anchors) {
                add(scalar.getAnchor(), anchors);
                return scalar;
            }

            @Override
            public Yaml.Alias visitAlias(Yaml.Alias alias, Set<String> anchors) {
                return alias;
            }

            private void add(Yaml.@Nullable Anchor anchor, Set<String> anchors) {
                if (anchor != null) {
                    anchors.add(anchor.getKey());
                }
            }
        }.reduce(tree, new HashSet<>());
    }

    static Set<String> aliasesIn(Yaml tree) {
        return new YamlIsoVisitor<Set<String>>() {
            @Override
            public Yaml.Alias visitAlias(Yaml.Alias alias, Set<String> aliases) {
                aliases.add(alias.getAnchor().getKey());
                return alias;
            }
        }.reduce(tree, new HashSet<>());
    }
}
