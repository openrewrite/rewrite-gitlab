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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Set;

final class ComponentInputs {
    static final String INPUTS = "inputs";

    private ComponentInputs() {
    }

    static Yaml.Mapping.@Nullable Entry find(Yaml.Mapping mapping, String key) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            if (key.equals(entry.getKey().getValue())) {
                return entry;
            }
        }
        return null;
    }

    /// Returns the include's own `inputs:` mapping, or `null` when it has none that can be changed in place.
    static Yaml.@Nullable Mapping inputs(Yaml.Mapping include, Set<String> aliases) {
        Yaml.Mapping.Entry inputs = find(include, INPUTS);
        if (inputs == null || !(inputs.getValue() instanceof Yaml.Mapping)) {
            return null;
        }
        Yaml.Mapping mapping = (Yaml.Mapping) inputs.getValue();
        return isAliased(mapping.getAnchor(), aliases) ? null : mapping;
    }

    static Yaml.Mapping withInputs(Yaml.Mapping include, Yaml.Mapping inputs) {
        return include.withEntries(ListUtils.map(include.getEntries(),
                entry -> INPUTS.equals(entry.getKey().getValue()) ? entry.withValue(inputs) : entry));
    }

    // Changing an anchored node also changes it wherever it is aliased
    static boolean isAliased(Yaml.@Nullable Anchor anchor, Set<String> aliases) {
        return anchor != null && aliases.contains(anchor.getKey());
    }
}
