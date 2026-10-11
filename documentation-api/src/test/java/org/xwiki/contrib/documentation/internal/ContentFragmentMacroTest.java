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
package org.xwiki.contrib.documentation.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationManager;
import org.xwiki.security.authorization.AuthorExecutor;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContentFragmentMacro}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ DocumentationPropertyDisplayer.class, ContentFragments.class })
class ContentFragmentMacroTest
{
    private static final DocumentReference CURRENT_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "install"), "WebHome");

    private static final DocumentReference OTHER_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "hide"), "WebHome");

    private static final DocumentReference AUTHOR_REFERENCE = new DocumentReference("wiki", "XWiki", "Author");

    private static final String CURRENT = "wiki:documentation.install.WebHome";

    private static final String OTHER = "wiki:documentation.hide.WebHome";

    private static final String NAME = "hidden-page-info";

    private static final String CONTENT = "= Hidden pages =\nSome content";

    @InjectMockComponents
    private ContentFragmentMacro macro;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private TransformationManager transformationManager;

    @MockComponent
    private AuthorExecutor authorExecutor;

    @MockComponent
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @MockComponent
    @Named("macro")
    private DocumentReferenceResolver<String> macroDocumentReferenceResolver;

    @MockComponent
    private ContextualAuthorizationManager authorization;

    private XWikiContext xcontext;

    private XWiki xwiki;

    private XWikiDocument currentDocument;

    private XWikiDocument otherDocument;

    private BaseObject fragmentObject;

    private XDOM pageXDOM;

    private MacroBlock macroBlock;

    private MacroTransformationContext macroContext;

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);
        this.xwiki = mock(XWiki.class);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);

        this.currentDocument = mockDocument(CURRENT_REFERENCE);
        when(this.xcontext.getDoc()).thenReturn(this.currentDocument);
        this.otherDocument = mockDocument(OTHER_REFERENCE);
        when(this.xwiki.getDocument(OTHER_REFERENCE, this.xcontext)).thenReturn(this.otherDocument);

        // The current page holds the content fragment, after a removed object and another content fragment.
        this.fragmentObject = mockFragment(NAME, CONTENT);
        List<BaseObject> fragments = Arrays.asList(null, mockFragment("other", "Other"), this.fragmentObject);
        when(this.currentDocument.getXObjects(any(EntityReference.class))).thenReturn(fragments);

        when(this.entityReferenceSerializer.serialize(CURRENT_REFERENCE)).thenReturn(CURRENT);
        when(this.entityReferenceSerializer.serialize(OTHER_REFERENCE)).thenReturn(OTHER);

        when(this.authorExecutor.call(any(), any(), any()))
            .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(0).call());

        // The page calling the macro, which already has a heading using the id that the fragment heading generates.
        this.macroBlock = new MacroBlock("fragment", Map.of("name", NAME), false);
        this.pageXDOM = new XDOM(List.of(this.macroBlock));
        this.pageXDOM.getIdGenerator().generateUniqueId("H", "Hiddenpages");
        when(this.macroDocumentReferenceResolver.resolve("", this.macroBlock)).thenReturn(CURRENT_REFERENCE);
        when(this.macroDocumentReferenceResolver.resolve("documentation.hide", this.macroBlock))
            .thenReturn(OTHER_REFERENCE);
        TransformationContext transformationContext = new TransformationContext(this.pageXDOM, Syntax.XWIKI_2_1);
        transformationContext.setTargetSyntax(Syntax.XHTML_1_0);
        this.macroContext = new MacroTransformationContext(transformationContext);
        this.macroContext.setXDOM(this.pageXDOM);
        this.macroContext.setCurrentMacroBlock(this.macroBlock);
    }

    @Test
    void displayFragmentOfTheCurrentPageAsBlocksExecutedWithTheAuthorRights() throws Exception
    {
        HeaderBlock heading =
            new HeaderBlock(List.of(new WordBlock("Hidden pages")), HeaderLevel.LEVEL1, "HHiddenpages");
        when(this.contentParser.parse(CONTENT, Syntax.XWIKI_2_1, CURRENT_REFERENCE))
            .thenReturn(new XDOM(List.of(heading)));
        // Check that the fragment is executed in place in the page.
        doAnswer(invocation -> {
            assertSame(this.pageXDOM, invocation.<MetaDataBlock>getArgument(0).getRoot());
            return null;
        }).when(this.transformationManager).performTransformations(any(), any());

        List<Block> result = this.macro.execute(parameters(null), null, this.macroContext);

        MetaDataBlock metadata = (MetaDataBlock) result.get(0);
        assertEquals(List.of(heading), metadata.getChildren());
        assertEquals(CURRENT, metadata.getMetaData().getMetaData(MetaData.SOURCE));
        // The page already has a "HHiddenpages" heading.
        assertEquals("HHiddenpages-1", heading.getId());
        verify(this.authorExecutor).call(any(), eq(AUTHOR_REFERENCE), eq(CURRENT_REFERENCE));
        verify(this.transformationManager).performTransformations(same(metadata), any());
        // The current page is already viewed by the current user.
        verify(this.authorization, never()).hasAccess(any(), any());
        assertEquals(List.of(this.macroBlock), this.pageXDOM.getChildren());
    }

    @Test
    void displayFragmentOfAnotherPage() throws Exception
    {
        BaseObject otherFragment = mockFragment(NAME, "Other content");
        when(this.otherDocument.getXObjects(any(EntityReference.class))).thenReturn(List.of(otherFragment));
        when(this.authorization.hasAccess(Right.VIEW, OTHER_REFERENCE)).thenReturn(true);
        XDOM fragmentXDOM = new XDOM(List.of(new ParagraphBlock(List.of(new WordBlock("Other")))));
        when(this.contentParser.parse("Other content", Syntax.XWIKI_2_1, OTHER_REFERENCE)).thenReturn(fragmentXDOM);

        List<Block> result = this.macro.execute(parameters("documentation.hide"), null, this.macroContext);

        MetaDataBlock metadata = (MetaDataBlock) result.get(0);
        // The relative references of the fragment are resolved against the page holding it.
        assertEquals(OTHER, metadata.getMetaData().getMetaData(MetaData.BASE));
        verify(this.authorExecutor).call(any(), eq(AUTHOR_REFERENCE), eq(OTHER_REFERENCE));
    }

    @Test
    void displayFragmentInline() throws Exception
    {
        WordBlock word = new WordBlock("Inline");
        when(this.contentParser.parse(any(), any(), any()))
            .thenReturn(new XDOM(List.of(new ParagraphBlock(List.of(word)))));
        this.macroContext.setInline(true);

        List<Block> result = this.macro.execute(parameters(""), null, this.macroContext);

        // The top level paragraph is removed so that the fragment can be displayed inside a paragraph.
        assertEquals(List.of(word), result.get(0).getChildren());
    }

    @Test
    void displayFragmentRestrictedWhenTheContentPropertyIsRestricted() throws Exception
    {
        TextAreaClass contentClass = mock(TextAreaClass.class);
        when(contentClass.isRestricted()).thenReturn(true);
        BaseClass fragmentClass = mock(BaseClass.class);
        when(fragmentClass.get("content")).thenReturn(contentClass);
        when(this.fragmentObject.getXClass(this.xcontext)).thenReturn(fragmentClass);
        when(this.contentParser.parse(any(), any(), any())).thenReturn(new XDOM(List.of()));

        this.macro.execute(parameters(null), null, this.macroContext);

        ArgumentCaptor<TransformationContext> captor = ArgumentCaptor.forClass(TransformationContext.class);
        verify(this.transformationManager).performTransformations(any(), captor.capture());
        assertTrue(captor.getValue().isRestricted());
    }

    @Test
    void displayNothingWhenTheFragmentIsEmpty() throws Exception
    {
        when(this.fragmentObject.getLargeStringValue("content")).thenReturn("");

        assertEquals(List.of(), this.macro.execute(parameters(null), null, this.macroContext));
        verify(this.contentParser, never()).parse(any(), any(), any());
    }

    @Test
    void failWhenTheCurrentUserCannotViewThePageHoldingTheFragment()
    {
        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters("documentation.hide"), null, this.macroContext));

        assertEquals("You are not allowed to view the page [wiki:documentation.hide.WebHome].",
            exception.getMessage());
    }

    @Test
    void failWhenThePageHoldingTheFragmentCannotBeLoaded() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, OTHER_REFERENCE)).thenReturn(true);
        when(this.xwiki.getDocument(OTHER_REFERENCE, this.xcontext)).thenThrow(new XWikiException());

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters("documentation.hide"), null, this.macroContext));

        assertEquals("Failed to load the page [wiki:documentation.hide.WebHome].", exception.getMessage());
    }

    @Test
    void failWhenTheFragmentDoesNotExist()
    {
        ContentFragmentMacroParameters parameters = parameters(null);
        parameters.setName("missing");

        MacroExecutionException exception =
            assertThrows(MacroExecutionException.class, () -> this.macro.execute(parameters, null, this.macroContext));

        assertEquals("There's no content fragment named [missing] in page [wiki:documentation.install.WebHome].",
            exception.getMessage());
    }

    @Test
    void failWhenTheFragmentDisplaysItself() throws Exception
    {
        when(this.contentParser.parse(any(), any(), any())).thenReturn(new XDOM(List.of()));
        // The fragment contains the macro displaying it.
        doAnswer(invocation -> {
            MacroExecutionException exception = assertThrows(MacroExecutionException.class,
                () -> this.macro.execute(parameters(null), null, this.macroContext));
            assertEquals("The content fragment [hidden-page-info] of page [wiki:documentation.install.WebHome] "
                + "cannot display itself.", exception.getMessage());
            return null;
        }).when(this.transformationManager).performTransformations(any(), any());

        this.macro.execute(parameters(null), null, this.macroContext);

        // The fragment can be displayed again once its display is finished.
        this.macro.execute(parameters(null), null, this.macroContext);

        verify(this.transformationManager, times(2)).performTransformations(any(), any());
    }

    @Test
    void descriptor()
    {
        assertTrue(this.macro.supportsInlineMode());
        assertEquals(Set.of("Content"), this.macro.getDescriptor().getDefaultCategories());
    }

    private XWikiDocument mockDocument(DocumentReference reference)
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getDocumentReference()).thenReturn(reference);
        when(document.getSyntax()).thenReturn(Syntax.XWIKI_2_1);
        when(document.getAuthorReference()).thenReturn(AUTHOR_REFERENCE);
        return document;
    }

    private static BaseObject mockFragment(String name, String content)
    {
        BaseObject object = mock(BaseObject.class);
        when(object.getStringValue("name")).thenReturn(name);
        when(object.getLargeStringValue("content")).thenReturn(content);
        return object;
    }

    private static ContentFragmentMacroParameters parameters(String reference)
    {
        ContentFragmentMacroParameters parameters = new ContentFragmentMacroParameters();
        parameters.setName(NAME);
        parameters.setReference(reference);
        return parameters;
    }
}
