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
package org.xwiki.contrib.documentation.xwikiorg.ui;

import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.meta.DBListMetaClass;
import com.xpn.xwiki.objects.meta.PageMetaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Page test of the {@code DocApp.Code.ExtensionBadge} UI extension, displaying a badge for each extension id of the
 * {@code DocApp.Code.DocumentationExtensionClass} object of the current page.
 *
 * @version $Id$
 */
@ComponentList({
    DBListMetaClass.class,
    PageMetaClass.class
})
@XWikiSyntax21ComponentList
@HTML50ComponentList
class ExtensionBadgePageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    private static final DocumentReference EXTENSION_CLASS =
        new DocumentReference("xwiki", CODE_SPACE, "DocumentationExtensionClass");

    private String badgeContent;

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(EXTENSION_CLASS);
        XWikiDocument badgeDocument = loadPage(new DocumentReference("xwiki", CODE_SPACE, "ExtensionBadge"));
        this.badgeContent = badgeDocument.getXObject(new DocumentReference("xwiki", "XWiki", "UIExtensionClass"))
            .getLargeStringValue("content");
    }

    @Test
    void extensionIdIsPassedInTheQueryString() throws Exception
    {
        Elements links = renderBadges("xwiki:org.example:extension");

        assertEquals(1, links.size());
        assertEquals("Extension", links.get(0).text());
        assertEquals("id=org.example%3Aextension", getQueryString(links.get(0)));
    }

    @Test
    void specialCharactersInTheExtensionIdAreEscaped() throws Exception
    {
        Elements links = renderBadges("xwiki:a\"]]b{{c~d");

        assertEquals(1, links.size());
        assertEquals("Extension", links.get(0).text());
        assertEquals("id=a%22%5D%5Db%7B%7Bc%7Ed", getQueryString(links.get(0)));
    }

    @Test
    void specialCharactersInTheNPMIdAreEscaped() throws Exception
    {
        Elements links = renderBadges("npm:a]]b>>c||d~e");

        assertEquals(1, links.size());
        assertEquals("NPM", links.get(0).text());
        assertEquals("https://www.npmjs.com/package/a]]b>>c||d~e", links.get(0).attr("href"));
    }

    private String getQueryString(Element link)
    {
        // The "extensions" wiki doesn't exist in the test, so the link parameters are kept as attributes.
        return link.attr("data-xwiki-translated-attribute-querystring");
    }

    private Elements renderBadges(String extensionId) throws Exception
    {
        XWikiDocument page = this.xwiki.getDocument(new DocumentReference("xwiki", "Space", "Page"), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(this.badgeContent);
        BaseObject extensionObject = page.newXObject(EXTENSION_CLASS, this.context);
        extensionObject.setStringListValue("id", List.of(extensionId));
        this.xwiki.saveDocument(page, this.context);

        // Make the page the current document, so that $doc is bound to it in the UI extension content.
        this.context.setDoc(page);
        Document document = renderHTMLPage(page);
        return document.select(".documentation-badge a");
    }
}
