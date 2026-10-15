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
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.script.LiveDataScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.RenderingScriptServiceComponentList;
import org.xwiki.rendering.internal.configuration.DefaultRenderingConfigurationComponentList;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.meta.ComputedFieldMetaClass;
import com.xpn.xwiki.objects.meta.PageMetaClass;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test of the documentation search forms displayed by {@code DocApp.Code.LandingPageSheet} and
 * {@code DocApp.Code.DocumentationSheet}, which restrict the search to the space of the current page.
 *
 * @version $Id$
 */
@ComponentList({
    ComputedFieldMetaClass.class,
    PageMetaClass.class,
    ModelScriptService.class
})
@ReferenceComponentList
@RenderingScriptServiceComponentList
@DefaultRenderingConfigurationComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
class DocumentationSearchFormPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    private static final String SPECIAL_SPACE = "S\"<b>x</b>{{/html}}~y";

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "DocumentationMacros"));
    }

    @Test
    void specialCharactersInTheSpaceNameAreEscapedInTheLandingPageSearchForm() throws Exception
    {
        DocumentReference landingPageClass = new DocumentReference(WIKI, CODE_SPACE, "LandingPageClass");
        loadPage(landingPageClass);

        XWikiDocument page = createPage(new DocumentReference(WIKI, SPECIAL_SPACE, "WebHome"), "LandingPageSheet");
        page.newXObject(landingPageClass, this.context).setIntValue("listChildren", 1);
        this.xwiki.saveDocument(page, this.context);

        assertSearchForm(render(page));
    }

    @Test
    void specialCharactersInTheSpaceNameAreEscapedInTheDocumentationPageSearchForm() throws Exception
    {
        DocumentReference documentationClass = new DocumentReference(WIKI, CODE_SPACE, "DocumentationClass");
        loadPage(documentationClass);

        // The search form is displayed when there are other documentation pages in the same location.
        LiveData liveData = new LiveData();
        liveData.setCount(2);
        LiveDataScriptService liveDataScriptService = this.componentManager.registerMockComponent(
            ScriptService.class, "liveData", LiveDataScriptService.class, false);
        when(liveDataScriptService.query(any(Map.class))).thenReturn(liveData);

        XWikiDocument page = createPage(new DocumentReference(WIKI, SPECIAL_SPACE, "Page"), "DocumentationSheet");
        page.newXObject(documentationClass, this.context).setStringValue("type", "reference");
        this.xwiki.saveDocument(page, this.context);

        assertSearchForm(render(page));
    }

    private void assertSearchForm(Document document)
    {
        Element form = document.selectFirst("form");
        assertNotNull(form, document.html());
        assertEquals(SPECIAL_SPACE, form.selectFirst("input[name=f_space_prefix]").attr("value"));
        assertTrue(form.select("b").isEmpty(), form.html());
        assertNotNull(form.selectFirst("input[name=text]"), form.html());
    }

    /**
     * Creates a page whose content is the content of the given sheet, so that the sheet is rendered with the page as
     * the current document.
     */
    private XWikiDocument createPage(DocumentReference reference, String sheetName) throws Exception
    {
        XWikiDocument sheet = loadPage(new DocumentReference(WIKI, CODE_SPACE, sheetName));
        XWikiDocument page = this.xwiki.getDocument(reference, this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(sheet.getContent());
        return page;
    }

    private Document render(XWikiDocument page) throws Exception
    {
        this.context.setDoc(page);
        return renderHTMLPage(page);
    }
}
