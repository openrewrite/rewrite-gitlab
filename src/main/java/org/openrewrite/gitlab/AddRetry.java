/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddRetry extends Recipe {

    @Option(displayName = "Max retries",
            description = "Maximum number of retries (0-2).",
            example = "2")
    int max;

    @Option(displayName = "When",
            description = "List of failure types to retry on.",
            example = "runner_system_failure,stuck_or_timeout_failure",
            required = false)
    @Nullable
    List<String> when;

    @Option(displayName = "Job name",
            description = "The job to add retry to. If not provided, applies to the `default` section.",
            example = "build_job",
            required = false)
    @Nullable
    String jobName;

    @Option(displayName = "Accept theirs",
            description = "When the setting already exists, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add retry configuration";

    String description = "Add `retry` configuration to `.gitlab-ci.yml` for resilience against infrastructure failures.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        StringBuilder snippet = new StringBuilder();
        snippet.append("retry:\n  max: ").append(max);
        if (when != null && !when.isEmpty()) {
            snippet.append("\n  when:\n");
            for (String w : when) {
                snippet.append("    - ").append(w).append("\n");
            }
        }
        return MergeKeyword.job(jobName, snippet.toString(), acceptTheirs).getVisitor();
    }
}
