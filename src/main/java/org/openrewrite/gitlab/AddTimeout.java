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
public class AddTimeout extends Recipe {

    @Option(displayName = "Timeout",
            description = "The timeout duration.",
            example = "1 hour")
    String timeout;

    @Option(displayName = "Job name",
            description = "The job to set timeout on. If not provided, applies to the `default` section.",
            example = "build_job",
            required = false)
    @Nullable
    String jobName;

    @Option(displayName = "Accept theirs",
            description = "When the setting already exists, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add job timeout";

    String description = "Set `timeout` in `.gitlab-ci.yml` to prevent jobs from hanging indefinitely.";

    @Override
    public List<Recipe> getRecipeList() {
        String target = jobName != null ? jobName : "default";
        return singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        target + ":\n  timeout: " + timeout,
                        acceptTheirs,
                        null,
                        ".gitlab-ci.yml",
                        null,
                        null,
                        null)
        );
    }
}
