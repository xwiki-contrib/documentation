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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.RenderingScriptServiceComponentList;
import org.xwiki.rendering.internal.configuration.DefaultRenderingConfigurationComponentList;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.meta.PageMetaClass;
import com.xpn.xwiki.objects.meta.StringMetaClass;
import com.xpn.xwiki.objects.meta.TextAreaMetaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test of the {@code DocApp.Code.UIXPUIX} UI extension, displaying the {@code DocApp.Code.UIXPClass} object of
 * the current page.
 *
 * @version $Id$
 */
@ComponentList({
    PageMetaClass.class,
    StringMetaClass.class,
    TextAreaMetaClass.class
})
@RenderingScriptServiceComponentList
@DefaultRenderingConfigurationComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
class UIXPUIXPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    private static final DocumentReference UIXP_CLASS = new DocumentReference("xwiki", CODE_SPACE, "UIXPClass");

    private String uixContent;

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(UIXP_CLASS);
        XWikiDocument uixDocument = loadPage(new DocumentReference("xwiki", CODE_SPACE, "UIXPUIX"));
        this.uixContent = uixDocument.getXObject(new DocumentReference("xwiki", "XWiki", "UIExtensionClass"))
            .getLargeStringValue("content");
    }

    @Test
    void contributorIdAndVersionAreDisplayed() throws Exception
    {
        Document document = render("org.example:extension", "1.0");

        Element link = getContributorLink(document);
        assertEquals("this extension", link.text());
        assertEquals("id=org.example:extension:1.0", getQueryString(link));
        assertTrue(document.text().contains("was added in version 1.0 of that extension"), document.text());
    }

    @Test
    void contributorIdAndVersionAreDisplayedAsEntered() throws Exception
    {
        Document document = render("org.example:**extension**", "1.0 **(beta)**");

        Element link = getContributorLink(document);
        assertEquals("this extension", link.text());
        assertEquals("id=org.example:**extension**:1.0 **(beta)**", getQueryString(link));
        assertTrue(document.text().contains("was added in version 1.0 **(beta)** of that extension"),
            document.text());
    }

    private Element getContributorLink(Document document)
    {
        return document.select("a[data-xwiki-translated-attribute-querystring]").first();
    }

    private String getQueryString(Element link)
    {
        // The "extensions" wiki doesn't exist in the test, so the link parameters are kept as attributes.
        return link.attr("data-xwiki-translated-attribute-querystring");
    }

    private Document render(String contributorId, String version) throws Exception
    {
        XWikiDocument page = this.xwiki.getDocument(new DocumentReference("xwiki", "Space", "Page"), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(this.uixContent);
        BaseObject uixpObject = page.newXObject(UIXP_CLASS, this.context);
        uixpObject.setStringValue("contributorid", contributorId);
        uixpObject.setStringValue("version", version);
        this.xwiki.saveDocument(page, this.context);

        // Make the page the current document, so that $doc is bound to it in the UI extension content.
        this.context.setDoc(page);
        this.context.setAction("view");
        return renderHTMLPage(page);
    }
}
