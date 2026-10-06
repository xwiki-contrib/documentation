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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.model.internal.reference.DefaultStringEntityReferenceSerializer;
import org.xwiki.model.internal.reference.DefaultSymbolScheme;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LocationCheck}.
 *
 * @version $Id$
 * @since 1.15
 */
@ComponentTest
@ComponentList({ DefaultStringEntityReferenceSerializer.class, DefaultSymbolScheme.class })
class LocationCheckTest
{
    private static final String LOCATION_MESSAGE = "Documentation pages must be located in the main wiki, under "
        + "documentation.xs (bundled extensions) or documentation.extensions (non-bundled extensions), then under "
        + "the target audience (user, admin or dev).";

    private static final String AUDIENCE_MESSAGE = "The audience part of the page location must match the target "
        + "of the page (user: user, administrator: admin, developer: dev).";

    @InjectMockComponents
    private LocationCheck check;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @BeforeEach
    void setUp()
    {
        when(this.wikiDescriptorManager.getMainWikiId()).thenReturn("xwiki");
        when(this.wikiDescriptorManager.isMainWiki("xwiki")).thenReturn(true);
    }

    private XWikiDocument createDocument(String wiki, String target, String name, String... spaces)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference()).thenReturn(new DocumentReference(wiki, Arrays.asList(spaces), name));
        if (target != null) {
            BaseObject docObject = mock(BaseObject.class);
            when(docObject.getStringValue("target")).thenReturn(target);
            when(document.getXObject(any(EntityReference.class))).thenReturn(docObject);
        }
        return document;
    }

    private void assertViolation(String expectedMessage, String expectedContext,
        List<DocumentationViolation> violations)
    {
        assertEquals(1, violations.size());
        assertEquals(expectedMessage, violations.get(0).getViolationMessage());
        assertEquals(expectedContext, violations.get(0).getViolationContext());
        assertEquals(DocumentationViolationSeverity.ERROR, violations.get(0).getViolationSeverity());
    }

    @Test
    void checkWhenLocationMatchesTarget()
    {
        assertEquals(0, this.check.check(
            createDocument("xwiki", "user", "WebHome", "documentation", "xs", "user", "like")).size());
        assertEquals(0, this.check.check(
            createDocument("xwiki", "administrator", "WebHome", "documentation", "xs", "admin", "like", "configure"))
            .size());
        assertEquals(0, this.check.check(
            createDocument("xwiki", "developer", "page", "documentation", "extensions", "dev")).size());
    }

    @Test
    void checkWhenTargetIsMissing()
    {
        assertEquals(0, this.check.check(
            createDocument("xwiki", null, "WebHome", "documentation", "extensions", "dev", "api")).size());
        assertEquals(0, this.check.check(
            createDocument("xwiki", "", "WebHome", "documentation", "extensions", "dev", "api")).size());
    }

    @Test
    void checkWhenAudienceDoesNotMatchTarget()
    {
        assertViolation(AUDIENCE_MESSAGE, "Page reference: [xwiki:documentation.xs.user.like.WebHome], "
            + "Target: [administrator], Expected location: [xwiki:documentation.xs.admin]",
            this.check.check(createDocument("xwiki", "administrator", "WebHome", "documentation", "xs", "user",
                "like")));
    }

    @Test
    void checkWhenNotInMainWiki()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [sub:documentation.xs.user.like.WebHome], "
            + "Target: [user], Expected location: [xwiki:documentation.xs.user]",
            this.check.check(createDocument("sub", "user", "WebHome", "documentation", "xs", "user", "like")));
    }

    @Test
    void checkWhenNotUnderDocumentation()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [xwiki:Documentation.UserGuide.WebHome], "
            + "Target: [user], Expected location: [xwiki:documentation.xs|extensions.user]",
            this.check.check(createDocument("xwiki", "user", "WebHome", "Documentation", "UserGuide")));
    }

    @Test
    void checkWhenSectionIsInvalid()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [xwiki:documentation.contrib.user.like.WebHome], "
            + "Target: [user], Expected location: [xwiki:documentation.xs|extensions.user]",
            this.check.check(createDocument("xwiki", "user", "WebHome", "documentation", "contrib", "user",
                "like")));
    }

    @Test
    void checkWhenAudienceIsInvalid()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [xwiki:documentation.xs.developer.like.WebHome], "
            + "Target: [developer], Expected location: [xwiki:documentation.xs.dev]",
            this.check.check(createDocument("xwiki", "developer", "WebHome", "documentation", "xs", "developer",
                "like")));
    }

    @Test
    void checkWhenPageIsDirectlyInSection()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [xwiki:documentation.xs.like], "
            + "Target: [], Expected location: [xwiki:documentation.xs.user|admin|dev]",
            this.check.check(createDocument("xwiki", null, "like", "documentation", "xs")));
    }

    @Test
    void checkWhenPageIsTheAudiencePage()
    {
        assertViolation(LOCATION_MESSAGE, "Page reference: [xwiki:documentation.xs.user.WebHome], "
            + "Target: [user], Expected location: [xwiki:documentation.xs.user]",
            this.check.check(createDocument("xwiki", "user", "WebHome", "documentation", "xs", "user")));
    }
}
