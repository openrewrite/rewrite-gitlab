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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveComponentInput extends Recipe {
    private static final String ALIASES = "ALIASES";

    @Option(displayName = "Component",
            description = "The path of the component to match, without the version. Matches every component when omitted.",
            example = "$CI_SERVER_FQDN/group/maven-build",
            required = false)
    @Nullable
    String component;

    @Option(displayName = "Version",
            description = "Version of the component to match: an exact version such as `0.10.0`, `~latest` or `main`, " +
                          "or a semver pattern such as `1.x` or `~1`. Matches any version when omitted.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Input name",
            description = "The name of the input to remove.",
            example = "java_version")
    String inputName;

    String displayName = "Remove GitLab component input";

    String description = "Remove an input from GitLab component includes.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ComponentMatcher matcher = new ComponentMatcher(component, version);
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                getCursor().putMessage(ALIASES, YamlAnchors.aliasesIn(document));
                return super.visitDocument(document, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);
                if (matcher.findInclude(getCursor()) == null) {
                    return m;
                }
                Set<String> aliases = getCursor().getNearestMessage(ALIASES, emptySet());
                Yaml.Mapping inputs = ComponentInputs.inputs(m, aliases);
                Yaml.Mapping.Entry input = inputs == null ? null : ComponentInputs.find(inputs, inputName);
                if (input == null || !Collections.disjoint(YamlAnchors.anchorsIn(input), aliases)) {
                    return m;
                }
                Yaml.Mapping remaining = inputs.withEntries(remove(inputs.getEntries(), input));
                if (!remaining.getEntries().isEmpty()) {
                    return ComponentInputs.withInputs(m, remaining);
                }
                return m.withEntries(remove(m.getEntries(), requireNonNull(ComponentInputs.find(m, ComponentInputs.INPUTS))));
            }
        });
    }

    private static List<Yaml.Mapping.Entry> remove(List<Yaml.Mapping.Entry> entries, Yaml.Mapping.Entry removed) {
        List<Yaml.Mapping.Entry> remaining = new ArrayList<>(entries);
        int index = remaining.indexOf(removed);
        remaining.remove(index);
        // The first entry follows the parent's key or dash, so its prefix carries over to the next entry
        if (index == 0 && !remaining.isEmpty()) {
            remaining.set(0, remaining.get(0).withPrefix(removed.getPrefix()));
        }
        return remaining;
    }
}
