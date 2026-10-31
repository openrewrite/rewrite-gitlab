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
package org.openrewrite.gitlab.table;

import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class GitLabIncludes extends DataTable<GitLabIncludes.Row> {

    public GitLabIncludes(Recipe recipe) {
        super(recipe, "GitLab CI includes",
                "Components, templates and files included by `.gitlab-ci.yml`.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The `.gitlab-ci.yml` file containing the include.")
        String sourcePath;

        @Column(displayName = "Include type",
                description = "One of `component`, `template`, `remote`, `project` or `local`.")
        String type;

        @Column(displayName = "Reference",
                description = "The component path without its version, the template name, the remote URL, " +
                              "the project and file as `project:file`, or the local path.")
        String reference;

        @Column(displayName = "Version",
                description = "The component version after the last `@`, or the `ref` of a `project` include.")
        @Nullable
        String version;

        @Column(displayName = "Inputs",
                description = "The inputs passed to the include, as one `key=value` pair per line.")
        @Nullable
        String inputs;
    }
}
