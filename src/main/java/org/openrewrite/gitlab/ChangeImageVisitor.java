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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static java.util.Collections.emptySet;

/// Changes the image references of `image:` and `services:` entries. An image that comes in through an alias is
/// changed once, where its anchor is defined, so that every alias follows.
@RequiredArgsConstructor
class ChangeImageVisitor extends YamlIsoVisitor<ExecutionContext> {
    private static final String IMAGES = "IMAGES";

    // A compact flow mapping such as `{name: x}` is parsed as a scalar
    private static final Pattern REFERENCE = Pattern.compile("[^\\s{}\\[\\],]+");

    private final UnaryOperator<ImageReference> change;

    @Override
    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
        Set<UUID> images = new Images(document).reduce(document, new HashSet<>(), getCursor().getParentOrThrow());
        getCursor().putMessage(IMAGES, images);
        return super.visitDocument(document, ctx);
    }

    @Override
    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
        Yaml.Scalar s = super.visitScalar(scalar, ctx);
        if (!getCursor().<Set<UUID>>getNearestMessage(IMAGES, emptySet()).contains(s.getId()) ||
            !REFERENCE.matcher(s.getValue()).matches()) {
            return s;
        }
        ImageReference reference = ImageReference.parse(s.getValue());
        if (reference.hasVariable()) {
            return s;
        }
        ImageReference changed = change.apply(reference);
        // A digest pins the content of the old tag
        if (!Objects.equals(changed.getTag(), reference.getTag())) {
            changed = changed.withDigest(null);
        }
        String value = changed.toString();
        return value.equals(s.getValue()) ? s : s.withValue(value);
    }

    private static class Images extends YamlIsoVisitor<Set<UUID>> {
        private final Map<UUID, Yaml> aliases;

        Images(Yaml.Document document) {
            this.aliases = YamlAnchors.resolveAliases(document);
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Set<UUID> scalars) {
            if (ImageKeywords.isImage(getCursor())) {
                image(entry.getValue(), scalars);
            } else if (ImageKeywords.isServices(getCursor())) {
                Yaml services = resolve(entry.getValue());
                if (services instanceof Yaml.Sequence) {
                    for (Yaml.Sequence.Entry service : ((Yaml.Sequence) services).getEntries()) {
                        image(service.getBlock(), scalars);
                    }
                }
            }
            return super.visitMappingEntry(entry, scalars);
        }

        private void image(Yaml.Block image, Set<UUID> scalars) {
            Yaml resolved = resolve(image);
            if (resolved instanceof Yaml.Scalar) {
                scalars.add(resolved.getId());
            } else if (resolved instanceof Yaml.Mapping) {
                for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) resolved).getEntries()) {
                    if ("name".equals(entry.getKey().getValue())) {
                        image(entry.getValue(), scalars);
                    }
                }
            }
        }

        private @Nullable Yaml resolve(Yaml.Block block) {
            return block instanceof Yaml.Alias ? aliases.get(block.getId()) : block;
        }
    }
}
