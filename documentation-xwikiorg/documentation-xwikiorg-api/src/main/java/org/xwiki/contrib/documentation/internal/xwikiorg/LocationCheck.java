/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.documentation.internal.xwikiorg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Verify that a documentation page is located in the main wiki, under {@code documentation.xs.<audience>} (bundled
 * extensions) or {@code documentation.extensions.<audience>} (non-bundled extensions), where {@code <audience>} is
 * {@code user}, {@code admin} or {@code dev}, and that the audience part of its location matches its target.
 *
 * @version $Id$
 * @since 1.15
 */
@Component
@Singleton
@Named("location")
public class LocationCheck implements DocumentationCheck
{
    private static final LocalDocumentReference DOCUMENTATION_CLASS_REFERENCE =
        new LocalDocumentReference(List.of("DocApp", "Code"), "DocumentationClass");

    private static final String ROOT_SPACE = "documentation";

    private static final Set<String> SECTIONS = Set.of("xs", "extensions");

    private static final String USER = "user";

    private static final Map<String, String> AUDIENCES =
        Map.of(USER, USER, "administrator", "admin", "developer", "dev");

    private static final String LOCATION_CONTEXT = "Page reference: [%s], Target: [%s], Expected location: [%s]";

    private static final int AUDIENCE_INDEX = 2;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Override
    public List<DocumentationViolation> check(XWikiDocument document)
    {
        List<DocumentationViolation> violations = new ArrayList<>();
        DocumentReference reference = document.getDocumentReference();
        List<String> spaces =
            reference.getSpaceReferences().stream().map(EntityReference::getName).collect(Collectors.toList());
        String target = getTarget(document);
        String expectedAudience = AUDIENCES.get(target);

        String section = spaces.size() > 1 && SECTIONS.contains(spaces.get(1)) ? spaces.get(1) : "xs|extensions";
        String expectedLocation = String.format("%s:%s.%s.%s", this.wikiDescriptorManager.getMainWikiId(),
            ROOT_SPACE, section, expectedAudience != null ? expectedAudience : "user|admin|dev");
        String context = String.format(LOCATION_CONTEXT, this.serializer.serialize(reference), target,
            expectedLocation);

        if (!isInDocumentationTree(reference, spaces)) {
            violations.add(new DocumentationViolation(
                "Documentation pages must be located in the main wiki, under documentation.xs (bundled extensions) "
                    + "or documentation.extensions (non-bundled extensions), then under the target audience "
                    + "(user, admin or dev).",
                context, DocumentationViolationSeverity.ERROR));
        } else if (expectedAudience != null && !expectedAudience.equals(spaces.get(AUDIENCE_INDEX))) {
            violations.add(new DocumentationViolation(
                "The audience part of the page location must match the target of the page "
                    + "(user: user, administrator: admin, developer: dev).",
                context, DocumentationViolationSeverity.ERROR));
        }
        return violations;
    }

    private boolean isInDocumentationTree(DocumentReference reference, List<String> spaces)
    {
        if (!this.wikiDescriptorManager.isMainWiki(reference.getWikiReference().getName())
            || spaces.size() <= AUDIENCE_INDEX)
        {
            return false;
        }
        boolean isUnderAudience = ROOT_SPACE.equals(spaces.get(0)) && SECTIONS.contains(spaces.get(1))
            && AUDIENCES.containsValue(spaces.get(AUDIENCE_INDEX));
        // The audience page itself is not under the audience page.
        return isUnderAudience && (spaces.size() > AUDIENCE_INDEX + 1 || !"WebHome".equals(reference.getName()));
    }

    private String getTarget(XWikiDocument document)
    {
        BaseObject docObject = document.getXObject(DOCUMENTATION_CLASS_REFERENCE);
        return docObject != null ? docObject.getStringValue("target") : "";
    }
}
