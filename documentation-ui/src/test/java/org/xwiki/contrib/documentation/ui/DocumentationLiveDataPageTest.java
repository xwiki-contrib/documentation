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
import org.mockito.invocation.InvocationOnMock;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test of the Live Data listing the documentation pages, displayed by {@code DocApp.Code.LandingPageSheet} and in
 * the "More" section of {@code DocApp.Code.DocumentationSheet}.
 *
 * @version $Id$
 */
@ComponentList({
    ComputedFieldMetaClass.class,
    PageMetaClass.class,
    ModelScriptService.class,
    TestLiveDataMacro.class
})
@ReferenceComponentList
@RenderingScriptServiceComponentList
@DefaultRenderingConfigurationComponentList
@XWikiSyntax21ComponentList
@HTML50ComponentList
class DocumentationLiveDataPageTest extends PageTest
{
    private static final String WIKI = "xwiki";

    private static final List<String> CODE_SPACE = List.of("DocApp", "Code");

    /**
     * The most recently modified pages are listed first, so that readers see what changed lately.
     */
    private static final String SORT = "doc.date:desc";

    @BeforeEach
    void setUp() throws Exception
    {
        loadPage(new DocumentReference(WIKI, CODE_SPACE, "DocumentationMacros"));
    }

    @Test
    void landingPageListsMostRecentlyModifiedPagesFirst() throws Exception
    {
        DocumentReference landingPageClass = new DocumentReference(WIKI, CODE_SPACE, "LandingPageClass");
        loadPage(landingPageClass);

        XWikiDocument page = createPage(new DocumentReference(WIKI, "Landing", "WebHome"), "LandingPageSheet");
        page.newXObject(landingPageClass, this.context).setIntValue("listChildren", 1);
        this.xwiki.saveDocument(page, this.context);

        Element liveData = getLiveData(render(page));
        assertEquals("landingpage", liveData.attr("data-id"));
        assertEquals(SORT, liveData.attr("data-sort"));
    }

    @Test
    void moreSectionListsMostRecentlyModifiedPagesFirst() throws Exception
    {
        DocumentReference documentationClass = new DocumentReference(WIKI, CODE_SPACE, "DocumentationClass");
        loadPage(documentationClass);

        // The "More" section is displayed when there are other documentation pages in the same location.
        LiveData result = new LiveData();
        result.setCount(2);
        LiveDataScriptService liveDataScriptService = this.componentManager.registerMockComponent(
            ScriptService.class, "liveData", LiveDataScriptService.class, false);
        when(liveDataScriptService.query(any(Map.class))).thenReturn(result);
        when(liveDataScriptService.render(any(Map.class))).thenAnswer(this::renderLiveData);
        when(liveDataScriptService.render(any(Map.class), any(Map.class))).thenAnswer(this::renderLiveData);

        XWikiDocument page = createPage(new DocumentReference(WIKI, "Guide", "Page"), "DocumentationSheet");
        page.newXObject(documentationClass, this.context).setStringValue("type", "reference");
        this.xwiki.saveDocument(page, this.context);

        Element liveData = getLiveData(render(page));
        assertEquals("more", liveData.attr("data-id"));
        assertEquals(SORT, liveData.attr("data-sort"));
    }

    /**
     * Stands for the HTML of a Live Data rendered by the script service, holding the parameters the tests check.
     */
    private String renderLiveData(InvocationOnMock invocation)
    {
        Map<?, ?> parameters = invocation.getArgument(0);
        return String.format("<div class=\"liveData\" data-id=\"%s\" data-sort=\"%s\"></div>", parameters.get("id"),
            parameters.get("sort"));
    }

    private Element getLiveData(Document document)
    {
        Element liveData = document.selectFirst(".liveData");
        assertNotNull(liveData, document.html());
        return liveData;
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
