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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.List;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddCache extends Recipe {

    @Option(displayName = "Cache key",
            description = "The cache key expression.",
            example = "$CI_COMMIT_REF_SLUG")
    String key;

    @Option(displayName = "Paths",
            description = "Paths to cache.",
            example = ".cache/,vendor/")
    List<String> paths;

    @Option(displayName = "Policy",
            description = "The cache policy.",
            example = "pull-push",
            required = false)
    @Nullable
    String policy;

    @Option(displayName = "Job name",
            description = "The job to add cache to. If not provided, applies to the `default` section.",
            example = "build_job",
            required = false)
    @Nullable
    String jobName;

    @Option(displayName = "Accept theirs",
            description = "When the setting already exists, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add cache configuration";

    String description = "Add `cache` configuration to `.gitlab-ci.yml` for faster builds.";

    @Override
    public List<Recipe> getRecipeList() {
        String target = jobName != null ? jobName : "default";
        StringBuilder snippet = new StringBuilder();
        snippet.append(target).append(":\n  cache:\n    key: ").append(key).append("\n    paths:\n");
        if (paths != null) {
            for (String p : paths) {
                snippet.append("      - ").append(p).append("\n");
            }
        }
        if (policy != null) {
            snippet.append("    policy: ").append(policy);
        }
        return singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        snippet.toString(),
                        acceptTheirs,
                        null,
                        ".gitlab-ci.yml",
                        null,
                        null,
                        null)
        );
    }
}
