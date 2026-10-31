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

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.yaml.MergeYaml.InsertMode;

import java.util.List;

import static java.util.Arrays.asList;

/// The canonical order of the keywords in `.gitlab-ci.yml`, used both to sort existing keywords and to place a new
/// keyword among existing ones without moving them.
@RequiredArgsConstructor
class KeywordOrder {
    static final KeywordOrder GLOBAL = new KeywordOrder(asList(
            "include", "stages", "variables", "workflow", "default"));

    static final KeywordOrder JOB = new KeywordOrder(asList(
            "<<", "extends", "inherit",
            "stage", "needs", "dependencies",
            "rules", "only", "except", "when", "start_in", "manual_confirmation", "allow_failure",
            "trigger", "image", "services", "tags", "resource_group", "environment",
            "parallel", "interruptible", "timeout", "retry",
            "inputs", "variables", "id_tokens", "identity", "secrets", "cache",
            "hooks", "before_script", "script", "run", "after_script",
            "coverage", "artifacts", "pages", "release", "dast_configuration"));

    private final List<String> keywords;

    int rank(String key) {
        int index = keywords.indexOf(key);
        return index < 0 ? keywords.size() : index;
    }

    /// Places a new `key` after the last of the `existing` keys that ranks before it, or before the first existing
    /// key when none does. An unrecognized key, such as a new job, goes last.
    Placement placement(List<String> existing, String key) {
        if (existing.isEmpty() || !keywords.contains(key)) {
            return new Placement(InsertMode.Last, null);
        }
        int rank = rank(key);
        String after = null;
        for (String existingKey : existing) {
            if (rank(existingKey) < rank) {
                after = existingKey;
            }
        }
        return after == null ? new Placement(InsertMode.Before, existing.get(0)) : new Placement(InsertMode.After, after);
    }

    @Value
    static class Placement {
        InsertMode mode;

        @Nullable
        String property;
    }
}
