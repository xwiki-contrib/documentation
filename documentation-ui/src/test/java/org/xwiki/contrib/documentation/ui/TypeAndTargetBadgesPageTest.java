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
package org.xwiki.contrib.documentation.ui;

import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.model.validation.script.ModelValidationScriptService;
import org.xwiki.rendering.RenderingScriptServiceComponentList;
import org.xwiki.rendering.internal.configuration.DefaultRenderingConfigurationComponentList;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.meta.ComputedFieldMetaClass;
import com.xpn.xwiki.objects.meta.PageMetaClass;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test of the {@code DocApp.Code.TypeBadge} and {@code DocApp.Code.TargetBadge} UI extensions, displaying the
 * type and the target audience of a documentation page.
 *
 * @version $Id$
 */
@ComponentList({
    ComputedFieldMetaClass.class,
    PageMetaClass.class,
    ModelScriptService.class
})
@RenderingScriptServiceComponentList
@DefaultRenderingConfigurationComponentList
@ReferenceComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
class TypeAndTargetBadgesPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    private static final DocumentReference DOCUMENTATION_CLASS =
        new DocumentReference(WIKI, CODE_SPACE, "DocumentationClass");

    private static final DocumentReference UIX_CLASS = new DocumentReference(WIKI, "XWiki", "UIExtensionClass");

    private static final String DOC = "Doc";

    private static final String PRODUCT = "product";

    private static final String WEB_HOME = "WebHome";

    private static final String SPECIAL_TARGET = "us]]er~";

    private static final String SPECIAL_CHARACTERS = "a]]b{{c~d\"e";

    private static final String CAPITALIZED_SPECIAL_CHARACTERS = "A]]b{{c~d\"e";

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(DOCUMENTATION_CLASS);
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "DocumentationMacros"));

        // The type page name is transformed using the configured naming strategy, which is not tested here.
        ModelValidationScriptService modelValidationScriptService = this.componentManager.registerMockComponent(
            ScriptService.class, "modelvalidation", ModelValidationScriptService.class, false);
        when(modelValidationScriptService.transformName(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void specialCharactersInTheTypeAreEscaped() throws Exception
    {
        Elements badges = renderBadge("TypeBadge", List.of(DOC, PRODUCT, "user", "Page"), "type", SPECIAL_CHARACTERS);

        assertEquals(1, badges.size());
        assertEquals(CAPITALIZED_SPECIAL_CHARACTERS, badges.get(0).text());
        assertEquals(0, badges.get(0).select("a").size());
    }

    @Test
    void specialCharactersInTheTypeAreEscapedInTheLinkToTheTypePage() throws Exception
    {
        createPage(new DocumentReference(WIKI, List.of(DOC, PRODUCT, SPECIAL_TARGET, SPECIAL_CHARACTERS), WEB_HOME));

        Elements badges =
            renderBadge("TypeBadge", List.of(DOC, PRODUCT, SPECIAL_TARGET, "Page"), "type", SPECIAL_CHARACTERS);

        assertLink(badges, "Doc.product.us]]er~.a]]b{{c~d\"e.WebHome");
    }

    @Test
    void specialCharactersInTheTargetAreEscaped() throws Exception
    {
        Elements badges =
            renderBadge("TargetBadge", List.of(DOC, PRODUCT, "user", "Page"), "target", SPECIAL_CHARACTERS);

        assertEquals(1, badges.size());
        assertEquals(CAPITALIZED_SPECIAL_CHARACTERS, badges.get(0).text());
        assertEquals(0, badges.get(0).select("a").size());
    }

    @Test
    void specialCharactersInTheTargetAreEscapedInTheLinkToTheTargetPage() throws Exception
    {
        createPage(new DocumentReference(WIKI, List.of(DOC, PRODUCT, SPECIAL_TARGET), WEB_HOME));

        Elements badges =
            renderBadge("TargetBadge", List.of(DOC, PRODUCT, SPECIAL_TARGET, "Page"), "target", SPECIAL_CHARACTERS);

        assertLink(badges, "Doc.product.us]]er~.WebHome");
    }

    private void assertLink(Elements badges, String expectedReference)
    {
        assertEquals(1, badges.size());
        Elements links = badges.get(0).select("a");
        assertEquals(1, links.size());
        assertEquals(CAPITALIZED_SPECIAL_CHARACTERS, links.get(0).text());
        // The test environment renders the link target as the serialized document reference.
        assertEquals(expectedReference, links.get(0).attr("href"));
    }

    private void createPage(DocumentReference reference) throws Exception
    {
        XWikiDocument document = this.xwiki.getDocument(reference, this.context);
        document.setContent("content");
        this.xwiki.saveDocument(document, this.context);
    }

    private Elements renderBadge(String badgePage, List<String> spaces, String property, String value)
        throws Exception
    {
        XWikiDocument badgeDocument = loadPage(new DocumentReference(WIKI, CODE_SPACE, badgePage));
        String badgeContent = badgeDocument.getXObject(UIX_CLASS).getLargeStringValue("content");

        XWikiDocument page = this.xwiki.getDocument(new DocumentReference(WIKI, spaces, WEB_HOME), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(badgeContent);
        BaseObject documentationObject = page.newXObject(DOCUMENTATION_CLASS, this.context);
        documentationObject.setStringValue(property, value);
        this.xwiki.saveDocument(page, this.context);

        // Make the page the current document, so that $doc is bound to it in the UI extension content.
        this.context.setDoc(page);
        Document document = renderHTMLPage(page);
        return document.select(".documentation-badge");
    }
}
