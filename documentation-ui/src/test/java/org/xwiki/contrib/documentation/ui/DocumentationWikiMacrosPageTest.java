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
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.RenderingScriptServiceComponentList;
import org.xwiki.rendering.internal.configuration.DefaultRenderingConfigurationComponentList;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroContentMacro;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xwiki.test.page.WikiMacroSetup.loadWikiMacro;

/**
 * Page test of the {@code deprecated}, {@code image} and {@code version} wiki macros, checking that their parameter
 * values are displayed as they are.
 *
 * @version $Id$
 */
@ComponentList({
    WikiMacroContentMacro.class
})
@RenderingScriptServiceComponentList
@DefaultRenderingConfigurationComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
@WikiMacroFactoryComponentClass
class DocumentationWikiMacrosPageTest extends PageTest
{
    private static final String PRODUCT = "P\"]]{{x";

    private static final String PRODUCT_PARAMETER = "P~\"]]{{x";

    @BeforeEach
    void setUp() throws Exception
    {
        for (String macroPage : List.of("DeprecatedMacro", "ImageMacro", "VersionMacro")) {
            loadWikiMacro(this, this.componentManager,
                new DocumentReference("xwiki", List.of("DocApp", "Code"), macroPage));
        }
    }

    @Test
    void specialCharactersInTheDeprecatedMacroParametersAreEscaped() throws Exception
    {
        Document document = render("{{deprecated product=\"" + PRODUCT_PARAMETER
            + "\" since=\"1.0~~\" useInstead=\"U%)(%z{{/deprecated}}\"}}body{{/deprecated}}");

        Element notice = document.selectFirst(".deprecationNotice");
        assertNotNull(notice, document.html());
        assertEquals(PRODUCT, notice.attr("data-xwikiorg-product"));
        assertEquals("1.0~", notice.attr("data-xwikiorg-deprecated-since"));
        assertEquals("Deprecated " + PRODUCT + " 1.0~. Use U%)(%z{{/deprecated}} instead.",
            notice.selectFirst(".badge").text());
    }

    @Test
    void specialCharactersInTheImageMacroParametersAreEscaped() throws Exception
    {
        Document document = render("{{image reference=\"a]]b||c~~d.png\" width=\"10px~\" y\" "
            + "caption=\"C**d**~~e{{x\"/}}");

        Element image = document.selectFirst("img");
        assertNotNull(image, document.html());
        // The test environment renders the image URL as the attachment reference.
        assertEquals("a]]b||c~d.png", image.attr("src"));
        assertTrue(image.parent().attr("style").endsWith(" width:10px\" y;"), image.parent().attr("style"));
        assertEquals("C**d**~e{{x", document.selectFirst("em").text());
    }

    @Test
    void specialCharactersInTheVersionMacroParametersAreEscaped() throws Exception
    {
        Document document =
            render("{{version product=\"" + PRODUCT_PARAMETER + "\" since=\"1.0~~,2.0\"}}content{{/version}}");

        Element badge = document.selectFirst(".badge");
        assertNotNull(badge, document.html());
        assertEquals(PRODUCT + " 1.0~+, 2.0+", badge.text());
        assertEquals("Since " + PRODUCT + " 1.0~, 2.0", badge.attr("title"));
        assertEquals(PRODUCT, badge.attr("data-xwikiorg-product"));
        assertEquals("1.0~, 2.0", badge.attr("data-xwikiorg-versions-since"));
    }

    private Document render(String content) throws Exception
    {
        XWikiDocument page = this.xwiki.getDocument(new DocumentReference("xwiki", "Space", "Page"), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(content);
        this.xwiki.saveDocument(page, this.context);
        this.context.setDoc(page);
        return renderHTMLPage(page);
    }
}
