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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class UpgradeJavaVersion extends Recipe {
    private static final Pattern MAJOR = Pattern.compile("\\d{1,3}");

    @Option(displayName = "Java version",
            description = "The Java major version to upgrade to.",
            example = "21")
    Integer version;

    String displayName = "Upgrade Java version in GitLab CI";

    String description = "Upgrade the `java_version` component input in `.gitlab-ci.yml`. The Java version of " +
                         "container images is upgraded by `org.openrewrite.java.migrate.UpgradeJavaVersion` " +
                         "of rewrite-migrate-java, alongside that of other CI configurations and build files.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new ChangeComponentInput(null, null, "java_version", null, String.valueOf(version))
                .getVisitor(this::isLowerMajor);
    }

    private boolean isLowerMajor(Yaml.Scalar javaVersion) {
        return MAJOR.matcher(javaVersion.getValue()).matches() && Integer.parseInt(javaVersion.getValue()) < version;
    }
}
