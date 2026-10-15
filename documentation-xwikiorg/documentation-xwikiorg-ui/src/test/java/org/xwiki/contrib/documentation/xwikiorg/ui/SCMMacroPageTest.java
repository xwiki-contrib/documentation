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
import org.xwiki.rendering.wikimacro.internal.WikiMacroContentMacro;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.xwiki.test.page.WikiMacroSetup.loadWikiMacro;

/**
 * Page test of the {@code DocApp.Code.SCMMacro} wiki macro.
 *
 * @version $Id$
 */
@ComponentList({
    WikiMacroContentMacro.class
})
@XWikiSyntax21ComponentList
@HTML50ComponentList
@WikiMacroFactoryComponentClass
class SCMMacroPageTest extends PageTest
{
    private static final DocumentReference PAGE = new DocumentReference("xwiki", "Space", "Page");

    @BeforeEach
    void setUp() throws Exception
    {
        loadWikiMacro(this, this.componentManager,
            new DocumentReference("xwiki", List.of("DocApp", "Code"), "SCMMacro"));
    }

    @Test
    void linkWithoutLabel() throws Exception
    {
        Document document = render("{{scm project=\"xwiki-commons\" path=\"pom.xml\"/}}");

        Element link = document.selectFirst("a");
        assertNotNull(link);
        assertEquals("https://github.com/xwiki/xwiki-commons/tree/master/pom.xml", link.attr("href"));
    }

    @Test
    void labelKeepsItsWikiSyntax() throws Exception
    {
        Document document = render("{{scm project=\"xwiki-commons\"}}a **label**{{/scm}}");

        Element link = document.selectFirst("a");
        assertNotNull(link);
        assertEquals("https://github.com/xwiki/xwiki-commons", link.attr("href"));
        assertEquals("a label", link.text());
        assertEquals("label", link.selectFirst("strong").text());
    }

    @Test
    void specialCharactersInTheLabelDoNotEndTheLink() throws Exception
    {
        Document document = render("{{scm project=\"xwiki-commons\"}}a]]b>>c~d{{/scm}}");

        Elements links = document.select("a");
        assertEquals(1, links.size());
        assertEquals("https://github.com/xwiki/xwiki-commons", links.get(0).attr("href"));
        assertEquals("a]]b>>cd", links.get(0).text());
    }

    private Document render(String content) throws Exception
    {
        XWikiDocument page = this.xwiki.getDocument(PAGE, this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(content);
        this.xwiki.saveDocument(page, this.context);
        return renderHTMLPage(page);
    }
}
