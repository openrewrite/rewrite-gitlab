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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.util.Collections.emptyMap;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeComponent extends Recipe {
    private static final String INCLUDED_COMPONENTS = "INCLUDED_COMPONENTS";

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
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                getCursor().putMessage(INCLUDED_COMPONENTS, includedComponents(document, getCursor().getParentOrThrow()));
                return super.visitDocument(document, ctx);
            }

            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                ComponentReference reference = matcher.find(getCursor());
                if (reference == null) {
                    return e;
                }
                ComponentReference changed = change(reference);
                Yaml.Scalar value = (Yaml.Scalar) e.getValue();
                if (changed.toString().equals(value.getValue()) ||
                    isIncludedElsewhere(e, reference, changed, getCursor().getNearestMessage(INCLUDED_COMPONENTS, emptyMap()))) {
                    return e;
                }
                return e.withValue(value.withValue(changed.toString()));
            }

            private boolean isIncludedElsewhere(Yaml.Mapping.Entry entry, ComponentReference reference,
                                                ComponentReference changed, Map<UUID, ComponentReference> included) {
                boolean pathChanged = !changed.getPath().equals(reference.getPath());
                for (Map.Entry<UUID, ComponentReference> other : included.entrySet()) {
                    ComponentReference otherReference = other.getValue();
                    if (other.getKey().equals(entry.getId())) {
                        continue;
                    }
                    if (pathChanged && changed.getPath().equals(otherReference.getPath()) ||
                        changed.equals(matcher.matches(otherReference) ? change(otherReference) : otherReference)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private ComponentReference change(ComponentReference reference) {
        if (newComponent != null) {
            reference = reference.withPath(newComponent);
        }
        if (newComponentVersion != null) {
            reference = reference.withVersion(newComponentVersion);
        }
        return reference;
    }

    private static Map<UUID, ComponentReference> includedComponents(Yaml.Document document, Cursor parent) {
        ComponentMatcher anyComponent = new ComponentMatcher(null, null);
        return new YamlIsoVisitor<Map<UUID, ComponentReference>>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Map<UUID, ComponentReference> included) {
                ComponentReference reference = anyComponent.find(getCursor());
                if (reference != null) {
                    included.put(entry.getId(), reference);
                }
                return super.visitMappingEntry(entry, included);
            }
        }.reduce(document, new HashMap<>(), parent);
    }
}
