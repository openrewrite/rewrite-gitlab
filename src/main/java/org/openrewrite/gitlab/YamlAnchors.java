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
