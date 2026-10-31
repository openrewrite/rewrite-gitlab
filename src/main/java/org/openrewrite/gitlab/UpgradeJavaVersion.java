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
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@EqualsAndHashCode(callSuper = false)
@Value
public class UpgradeJavaVersion extends Recipe {
    private static final Pattern MAJOR = Pattern.compile("\\d{1,3}");
    private static final String SUFFIX = "(-[a-z][\\w.-]*)?";

    // A patch or update version, such as `.0.9_9` or `u392-b08` of Temurin, is dropped as it isn't published for the new major
    private static final String PATCH = "(?:u\\d+(?:-b\\d+)?|(?:\\.\\d+)+(?:_\\d+)?)?";
    private static final Pattern JDK_TAG = Pattern.compile("()(\\d{1,3})" + PATCH + SUFFIX);
    private static final Pattern MAVEN_TAG = Pattern.compile("((?:.+-)?(?:eclipse-temurin|amazoncorretto)-)(\\d{1,3})" + SUFFIX);
    private static final Pattern GRADLE_TAG = Pattern.compile("((?:.+-)?jdk)(\\d{1,3})" + SUFFIX);

    @Option(displayName = "Java version",
            description = "The Java major version to upgrade to.",
            example = "21")
    Integer version;

    String displayName = "Upgrade Java version in GitLab CI";

    String description = "Upgrade the Java version of JDK, Maven and Gradle images and of the `java_version` " +
                         "component input in `.gitlab-ci.yml`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> javaVersionInput =
                new ChangeComponentInput(null, null, "java_version", null, String.valueOf(version))
                        .getVisitor(this::isLowerMajor);
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new ChangeImageVisitor(this::upgrade) {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                doAfterVisit(javaVersionInput);
                return super.visitDocuments(documents, ctx);
            }
        });
    }

    private boolean isLowerMajor(Yaml.Scalar javaVersion) {
        return MAJOR.matcher(javaVersion.getValue()).matches() && Integer.parseInt(javaVersion.getValue()) < version;
    }

    private ImageReference upgrade(ImageReference reference) {
        Pattern tagPattern = tagPattern(reference.getRepository());
        if (tagPattern == null || reference.getTag() == null) {
            return reference;
        }
        Matcher tag = tagPattern.matcher(reference.getTag());
        if (!tag.matches() || Integer.parseInt(tag.group(2)) >= version) {
            return reference;
        }
        String suffix = tag.group(3) == null ? "" : tag.group(3);
        return reference.withTag(tag.group(1) + version + suffix);
    }

    private static @Nullable Pattern tagPattern(String repository) {
        switch (repository) {
            case "eclipse-temurin":
            case "amazoncorretto":
                return JDK_TAG;
            case "maven":
                return MAVEN_TAG;
            case "gradle":
                return GRADLE_TAG;
            default:
                return null;
        }
    }
}
