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
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.meta.ComputedFieldMetaClass;
import com.xpn.xwiki.objects.meta.PageMetaClass;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Page test of the {@code DocApp.Code.DocumentationNavigationPanel} panel.
 *
 * @version $Id$
 */
@ComponentList({
    ComputedFieldMetaClass.class,
    PageMetaClass.class,
    ModelScriptService.class,
    TestDocumentTreeMacro.class
})
@ReferenceComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
class DocumentationNavigationPanelPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    private static final String SPECIAL_TARGET = "T\"}}{{/documentTree}}~x";

    @Test
    void specialCharactersInTheSpaceNamesAreEscapedInTheTreeParameters() throws Exception
    {
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "DocumentationMacros"));
        DocumentReference documentationClass = new DocumentReference(WIKI, CODE_SPACE, "DocumentationClass");
        loadPage(documentationClass);
        XWikiDocument panel = loadPage(new DocumentReference(WIKI, CODE_SPACE, "DocumentationNavigationPanel"));
        String panelContent =
            panel.getXObject(new DocumentReference(WIKI, "Panels", "PanelClass")).getLargeStringValue("content");

        XWikiDocument page = this.xwiki.getDocument(
            new DocumentReference(WIKI, List.of("Doc", "product", SPECIAL_TARGET, "Page"), "WebHome"), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(panelContent);
        page.newXObject(documentationClass, this.context).setStringValue("target", "user");
        this.xwiki.saveDocument(page, this.context);

        // Make the page the current document, so that $doc is bound to it in the panel content.
        this.context.setDoc(page);
        Document document = renderHTMLPage(page);

        Elements trees = document.select(".documentTree");
        assertEquals(1, trees.size(), document.html());
        assertEquals("document:xwiki:Doc.product." + SPECIAL_TARGET + ".WebHome", trees.get(0).attr("data-root"));
        assertEquals("document:xwiki:Doc.product." + SPECIAL_TARGET + ".Page.WebHome",
            trees.get(0).attr("data-openTo"));
    }
}
