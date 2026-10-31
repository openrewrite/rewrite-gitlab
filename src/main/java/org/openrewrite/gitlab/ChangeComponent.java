/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gitlab;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeComponent extends Recipe {

    @Option(displayName = "Old Component",
            description = "The path of the component to match, without the version.",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline")
    String oldComponent;

    @Option(displayName = "Old Component version",
            description = "Version of the existing component to match: an exact version such as `0.10.0`, `~latest` or `main`, " +
                          "or a semver pattern such as `1.x` or `~1`. Matches any version when omitted.",
            example = "1.x",
            required = false)
    @Nullable
    String oldComponentVersion;

    @Option(displayName = "New Component",
            description = "Path of the new component to use instead. Keeps the existing path when omitted.",
            example = "$CI_SERVER_FQDN/components/opentofu/validate-plan-apply",
            required = false)
    @Nullable
    String newComponent;

    @Option(displayName = "New Component version",
            description = "Version of the new component to use instead. Keeps the existing version when omitted.",
            example = "2.0.0",
            required = false)
    @Nullable
    String newComponentVersion;

    String displayName = "Change GitLab Component";

    String description = "Change a GitLab Component in use.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ComponentMatcher matcher = new ComponentMatcher(oldComponent, oldComponentVersion);
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                ComponentReference reference = matcher.find(getCursor());
                if (reference == null) {
                    return e;
                }
                if (newComponent != null) {
                    reference = reference.withPath(newComponent);
                }
                if (newComponentVersion != null) {
                    reference = reference.withVersion(newComponentVersion);
                }
                Yaml.Scalar value = (Yaml.Scalar) e.getValue();
                String changed = reference.toString();
                return changed.equals(value.getValue()) ? e : e.withValue(value.withValue(changed));
            }
        });
    }
}
