/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gitlab.ComponentMatcher;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindComponent extends Recipe {

    @Option(displayName = "Component",
            description = "The path of the component to look for, without the version.",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline")
    String component;

    @Option(displayName = "Version",
            description = "Version of the component to look for: an exact version such as `0.10.0`, `~latest` or `main`, " +
                          "or a semver pattern such as `1.x` or `~1`. Matches any version when omitted.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    String displayName = "Find GitLab Component";

    String description = "Find a GitLab Component in use.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ComponentMatcher matcher = new ComponentMatcher(component, version);
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                return matcher.find(getCursor()) == null ? e : SearchResult.found(e);
            }
        };
    }
}
