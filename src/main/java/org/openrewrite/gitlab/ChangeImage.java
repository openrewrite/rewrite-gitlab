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
import org.openrewrite.internal.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeImage extends Recipe {

    @Option(displayName = "Old image name",
            description = "The name of the image to match, without tag or digest. Supports glob patterns, " +
                          "where `*` also matches `/`, so `*/eclipse-temurin` matches the image under any registry " +
                          "prefix, but not a bare `eclipse-temurin`.",
            example = "eclipse-temurin")
    String oldImageName;

    @Option(displayName = "Old tag",
            description = "The tag of the image to match. Supports glob patterns. Matches any tag when omitted.",
            example = "17*",
            required = false)
    @Nullable
    String oldTag;

    @Option(displayName = "New image name",
            description = "The name of the image to use instead. Keeps the existing name when omitted.",
            example = "registry.example.com/eclipse-temurin",
            required = false)
    @Nullable
    String newImageName;

    @Option(displayName = "New tag",
            description = "The tag of the image to use instead. Keeps the existing tag when omitted.",
            example = "21-jdk",
            required = false)
    @Nullable
    String newTag;

    String displayName = "Change GitLab CI image";

    String description = "Change the name or tag of `image` and `services` entries in `.gitlab-ci.yml`.";

    @Override
    public Validated<Object> validate() {
        return super.validate().and(Validated.test("newImageName", "A new image name or new tag is required.",
                newImageName, name -> name != null || newTag != null));
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new ChangeImageVisitor(reference -> {
            if (!matches(reference)) {
                return reference;
            }
            ImageReference changed = reference;
            if (newImageName != null) {
                changed = changed.withName(newImageName);
            }
            if (newTag != null) {
                changed = changed.withTag(newTag);
            }
            return changed;
        }));
    }

    private boolean matches(ImageReference reference) {
        if (!StringUtils.matchesGlob(reference.getName(), oldImageName)) {
            return false;
        }
        if (oldTag == null) {
            return true;
        }
        // Without a tag or digest an image is pulled by its `latest` tag
        String tag = reference.getTag() == null && reference.getDigest() == null ? "latest" : reference.getTag();
        return tag != null && StringUtils.matchesGlob(tag, oldTag);
    }
}
