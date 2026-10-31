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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

import static java.util.Collections.emptySet;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeComponentInput extends Recipe {
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
            description = "The name of the input to change.",
            example = "java_version")
    String inputName;

    @Option(displayName = "New input name",
            description = "The new name of the input. Keeps the existing name when omitted.",
            example = "jdk_version",
            required = false)
    @Nullable
    String newInputName;

    @Option(displayName = "New value",
            description = "The new value of the input. Keeps the existing value when omitted.",
            example = "21",
            required = false)
    @Nullable
    String newValue;

    String displayName = "Change GitLab component input";

    String description = "Rename an input already set on GitLab component includes, or change its value.";

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test("newInputName", "A new input name or new value is required.",
                newInputName, name -> name != null || newValue != null));
    }

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
                Yaml.Mapping inputs = ComponentInputs.inputs(m, getCursor().getNearestMessage(ALIASES, emptySet()));
                Yaml.Mapping.Entry input = inputs == null ? null : ComponentInputs.find(inputs, inputName);
                Yaml.Mapping.Entry changed = input == null ? null : change(input, inputs);
                if (changed == null) {
                    return m;
                }
                return ComponentInputs.withInputs(m, inputs.withEntries(ListUtils.map(inputs.getEntries(),
                        entry -> entry == input ? changed : entry)));
            }
        });
    }

    private Yaml.Mapping.@Nullable Entry change(Yaml.Mapping.Entry input, Yaml.Mapping inputs) {
        Yaml.Mapping.Entry changed = input;
        if (newInputName != null && !newInputName.equals(inputName)) {
            if (!(input.getKey() instanceof Yaml.Scalar) || ComponentInputs.find(inputs, newInputName) != null) {
                return null;
            }
            changed = changed.withKey(((Yaml.Scalar) input.getKey()).withValue(newInputName));
        }
        if (newValue != null && !isValue(input.getValue(), newValue)) {
            // An alias takes its value from the anchor, which is defined and used elsewhere
            if (!(input.getValue() instanceof Yaml.Scalar) || isBlockScalar((Yaml.Scalar) input.getValue())) {
                return null;
            }
            Yaml.Scalar value = withValue((Yaml.Scalar) input.getValue(), newValue);
            if (value == null) {
                return null;
            }
            changed = changed.withValue(value);
        }
        return changed == input ? null : changed;
    }

    private static Yaml.@Nullable Scalar withValue(Yaml.Scalar scalar, String value) {
        Yaml.Scalar changed = scalar.withValue(value);
        if (parsesBack(changed)) {
            return changed;
        }
        Yaml.Scalar quoted = changed.withStyle(Yaml.Scalar.Style.DOUBLE_QUOTED);
        return scalar.getStyle() == Yaml.Scalar.Style.PLAIN && parsesBack(quoted) ? quoted : null;
    }

    private static boolean parsesBack(Yaml.Scalar scalar) {
        return YamlParser.builder().build()
                .parse("key: " + quote(scalar))
                .filter(Yaml.Documents.class::isInstance)
                .map(docs -> ((Yaml.Documents) docs).getDocuments())
                .filter(docs -> docs.size() == 1 && docs.get(0).getBlock() instanceof Yaml.Mapping)
                .map(docs -> ((Yaml.Mapping) docs.get(0).getBlock()).getEntries())
                .anyMatch(entries -> entries.size() == 1 && isValue(entries.get(0).getValue(), scalar.getValue()));
    }

    private static String quote(Yaml.Scalar scalar) {
        switch (scalar.getStyle()) {
            case DOUBLE_QUOTED:
                return '"' + scalar.getValue() + '"';
            case SINGLE_QUOTED:
                return "'" + scalar.getValue() + "'";
            default:
                return scalar.getValue();
        }
    }

    private static boolean isValue(Yaml.Block value, String expected) {
        return value instanceof Yaml.Scalar && expected.equals(((Yaml.Scalar) value).getValue());
    }

    private static boolean isBlockScalar(Yaml.Scalar scalar) {
        return scalar.getStyle() == Yaml.Scalar.Style.LITERAL || scalar.getStyle() == Yaml.Scalar.Style.FOLDED;
    }
}
