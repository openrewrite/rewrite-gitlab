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
import org.openrewrite.yaml.MergeYamlVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.YamlVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddComponentInput extends Recipe {
    private static final String MERGE_KEY = "<<";

    @Option(displayName = "Component",
            description = "The path of the component to match, without the version.",
            example = "$CI_SERVER_FQDN/group/maven-build")
    String component;

    @Option(displayName = "Version",
            description = "Version of the component to match: an exact version such as `0.10.0`, `~latest` or `main`, " +
                          "or a semver pattern such as `1.x` or `~1`. Matches any version when omitted.",
            example = "1.x",
            required = false)
    @Nullable
    String version;

    @Option(displayName = "Input name",
            description = "The name of the input to add.",
            example = "java_version")
    String inputName;

    @Option(displayName = "Value",
            description = "The value of the input to add.",
            example = "21")
    String value;

    String displayName = "Add GitLab component input";

    String description = "Add an input to GitLab component includes that do not set it yet.";

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test("value", "Must be a single YAML value.",
                value, v -> parseInputs() != null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ComponentMatcher matcher = new ComponentMatcher(component, version);
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new YamlVisitor<ExecutionContext>() {
            @Override
            public Yaml visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                Yaml.Documents d = documents;
                for (Yaml.Mapping include : includesMissingInput(documents, matcher)) {
                    Yaml.Mapping incoming = parseInputs();
                    if (incoming == null) {
                        return documents;
                    }
                    d = (Yaml.Documents) new MergeYamlVisitor<>(include, incoming, true, null, null, null)
                            .visitNonNull(d, ctx);
                }
                return d;
            }
        });
    }

    private List<Yaml.Mapping> includesMissingInput(Yaml.Documents documents, ComponentMatcher matcher) {
        Set<String> aliases = YamlAnchors.aliasesIn(documents);
        return new YamlIsoVisitor<List<Yaml.Mapping>>() {
            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, List<Yaml.Mapping> missing) {
                if (matcher.findInclude(getCursor()) != null && isMissingInput(mapping, aliases)) {
                    missing.add(mapping);
                }
                return super.visitMapping(mapping, missing);
            }
        }.reduce(documents, new ArrayList<>());
    }

    // Inputs may already be set through a merge key, and an own `inputs:` would replace the merged ones
    private boolean isMissingInput(Yaml.Mapping include, Set<String> aliases) {
        if (ComponentInputs.find(include, ComponentInputs.INPUTS) == null) {
            return ComponentInputs.find(include, MERGE_KEY) == null;
        }
        Yaml.Mapping inputs = ComponentInputs.inputs(include, aliases);
        return inputs != null && inputs.getOpeningBracePrefix() == null &&
               ComponentInputs.find(inputs, inputName) == null &&
               ComponentInputs.find(inputs, MERGE_KEY) == null;
    }

    private Yaml.@Nullable Mapping parseInputs() {
        return YamlParser.builder().build()
                .parse(ComponentInputs.INPUTS + ":\n  " + inputName + ": " + value)
                .filter(Yaml.Documents.class::isInstance)
                .map(Yaml.Documents.class::cast)
                .filter(docs -> docs.getDocuments().size() == 1)
                .map(docs -> docs.getDocuments().get(0).getBlock())
                .filter(AddComponentInput::isSingleInput)
                .map(Yaml.Mapping.class::cast)
                .findFirst()
                .orElse(null);
    }

    private static boolean isSingleInput(Yaml.Block block) {
        if (!(block instanceof Yaml.Mapping) || ((Yaml.Mapping) block).getEntries().size() != 1) {
            return false;
        }
        Yaml.Block inputs = ((Yaml.Mapping) block).getEntries().get(0).getValue();
        return inputs instanceof Yaml.Mapping && ((Yaml.Mapping) inputs).getEntries().size() == 1;
    }
}
