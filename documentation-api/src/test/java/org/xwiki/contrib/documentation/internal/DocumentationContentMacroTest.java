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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.documentation.internal.DocumentationContentMacroParameters.Field;
import org.xwiki.display.internal.DocumentDisplayer;
import org.xwiki.display.internal.DocumentDisplayerParameters;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ContentParser;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.rendering.transformation.TransformationManager;
import org.xwiki.security.authorization.AuthorExecutor;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationContentMacro}, {@link DocumentationContentDisplayer} and
 * {@link DocumentationFAQDisplayer}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ DocumentationContentDisplayer.class, DocumentationFAQDisplayer.class })
class DocumentationContentMacroTest
{
    private static final DocumentReference DOCUMENT_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "install"), "WebHome");

    private static final DocumentReference AUTHOR_REFERENCE = new DocumentReference("wiki", "XWiki", "Author");

    private static final String SOURCE = "wiki:documentation.install.WebHome";

    private static final String FAQ = "= Question =\nAnswer";

    @InjectMockComponents
    private DocumentationContentMacro macro;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("content")
    private DocumentDisplayer documentDisplayer;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private TransformationManager transformationManager;

    @MockComponent
    private AuthorExecutor authorExecutor;

    @MockComponent
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    private XWikiContext xcontext;

    private XWikiDocument document;

    private BaseObject documentationObject;

    private TextAreaClass faqClass;

    private XDOM pageXDOM;

    private MacroBlock macroBlock;

    private MacroTransformationContext macroContext;

    @BeforeEach
    void setUp() throws Exception
    {
        this.xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(this.xcontext);

        this.document = mock(XWikiDocument.class);
        when(this.document.getDocumentReference()).thenReturn(DOCUMENT_REFERENCE);
        when(this.document.getSyntax()).thenReturn(Syntax.XWIKI_2_1);
        when(this.document.getAuthorReference()).thenReturn(AUTHOR_REFERENCE);
        when(this.xcontext.getDoc()).thenReturn(this.document);

        this.documentationObject = mock(BaseObject.class);
        when(this.document.getXObject(any(EntityReference.class))).thenReturn(this.documentationObject);
        when(this.documentationObject.getLargeStringValue("faq")).thenReturn(FAQ);
        BaseClass documentationClass = mock(BaseClass.class);
        when(this.documentationObject.getXClass(this.xcontext)).thenReturn(documentationClass);
        this.faqClass = mock(TextAreaClass.class);
        when(documentationClass.get("faq")).thenReturn(this.faqClass);

        when(this.entityReferenceSerializer.serialize(DOCUMENT_REFERENCE)).thenReturn(SOURCE);

        when(this.authorExecutor.call(any(), any(), any()))
            .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(0).call());

        // The page calling the macro, which already has a heading using the id that the FAQ heading generates.
        this.macroBlock = new MacroBlock("documentationContent", Map.of(), false);
        this.pageXDOM = new XDOM(List.of(this.macroBlock));
        this.pageXDOM.getIdGenerator().generateUniqueId("H", "Question");
        TransformationContext transformationContext = new TransformationContext(this.pageXDOM, Syntax.XWIKI_2_1);
        transformationContext.setTargetSyntax(Syntax.XHTML_1_0);
        this.macroContext = new MacroTransformationContext(transformationContext);
        this.macroContext.setXDOM(this.pageXDOM);
        this.macroContext.setCurrentMacroBlock(this.macroBlock);
    }

    @Test
    void displayContentAsBlocksOfTheContextTranslation() throws Exception
    {
        XWikiDocument translation = mock(XWikiDocument.class);
        when(translation.getDocumentReference()).thenReturn(DOCUMENT_REFERENCE);
        when(this.xcontext.get("tdoc")).thenReturn(translation);
        HeaderBlock heading = new HeaderBlock(List.of(new WordBlock("Install")), HeaderLevel.LEVEL1);
        when(this.documentDisplayer.display(same(translation), any())).thenReturn(new XDOM(List.of(heading)));
        this.macroContext.getTransformationContext().setRestricted(true);

        List<Block> result = this.macro.execute(parameters(Field.CONTENT), null, this.macroContext);

        // The heading stays a block, so that the TOC macro can see it.
        MetaDataBlock metadata = (MetaDataBlock) result.get(0);
        assertEquals(List.of(heading), metadata.getChildren());
        assertEquals(SOURCE, metadata.getMetaData().getMetaData(MetaData.SOURCE));
        assertEquals(SOURCE, metadata.getMetaData().getMetaData(MetaData.BASE));

        // The content is executed in an isolated execution context, as the context document, and thus with the rights
        // of its content author.
        ArgumentCaptor<DocumentDisplayerParameters> captor = ArgumentCaptor.forClass(DocumentDisplayerParameters.class);
        verify(this.documentDisplayer).display(same(translation), captor.capture());
        DocumentDisplayerParameters displayParameters = captor.getValue();
        assertTrue(displayParameters.isExecutionContextIsolated());
        assertTrue(displayParameters.isTransformationContextIsolated());
        assertTrue(displayParameters.isContentTransformed());
        assertTrue(displayParameters.isContentTranslated());
        assertTrue(displayParameters.isTransformationContextRestricted());
        assertEquals(Syntax.XHTML_1_0, displayParameters.getTargetSyntax());
        assertSame(this.pageXDOM.getIdGenerator(), displayParameters.getIdGenerator());
        verify(this.authorExecutor, never()).call(any(), any(), any());
    }

    @Test
    void displayContentOfTheContextDocumentWhenTheContextTranslationIsAnotherDocument() throws Exception
    {
        XWikiDocument otherDocument = mock(XWikiDocument.class);
        when(otherDocument.getDocumentReference()).thenReturn(new DocumentReference("wiki", "Other", "WebHome"));
        when(this.xcontext.get("tdoc")).thenReturn(otherDocument);
        when(this.documentDisplayer.display(any(), any())).thenReturn(new XDOM(List.of()));

        this.macro.execute(parameters(Field.CONTENT), null, this.macroContext);

        verify(this.documentDisplayer).display(same(this.document), any());
    }

    @Test
    void displayContentFailure()
    {
        when(this.documentDisplayer.display(any(), any())).thenThrow(new RuntimeException("error"));

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters(Field.CONTENT), null, this.macroContext));

        assertEquals("Failed to display the content of document [wiki:documentation.install.WebHome]",
            exception.getMessage());
    }

    @Test
    void displayFAQAsBlocksExecutedWithTheDocumentAuthorRights() throws Exception
    {
        HeaderBlock heading = new HeaderBlock(List.of(new WordBlock("Question")), HeaderLevel.LEVEL1, "HQuestion");
        when(this.contentParser.parse(FAQ, Syntax.XWIKI_2_1, DOCUMENT_REFERENCE))
            .thenReturn(new XDOM(List.of(heading)));
        // Check that the FAQ is executed in place in the page, with the rights of the document author.
        doAnswer(invocation -> {
            MetaDataBlock metadata = invocation.getArgument(0);
            assertSame(this.pageXDOM, metadata.getRoot());
            TransformationContext transformationContext = invocation.getArgument(1);
            assertFalse(transformationContext.isRestricted());
            assertEquals(Syntax.XHTML_1_0, transformationContext.getTargetSyntax());
            return null;
        }).when(this.transformationManager).performTransformations(any(), any());

        List<Block> result = this.macro.execute(parameters(Field.FAQ), null, this.macroContext);

        MetaDataBlock metadata = (MetaDataBlock) result.get(0);
        assertEquals(List.of(heading), metadata.getChildren());
        assertEquals(SOURCE, metadata.getMetaData().getMetaData(MetaData.SOURCE));
        // The page already has a "HQuestion" heading.
        assertEquals("HQuestion-1", heading.getId());
        verify(this.authorExecutor).call(any(), eq(AUTHOR_REFERENCE), eq(DOCUMENT_REFERENCE));
        verify(this.transformationManager).performTransformations(same(metadata), any());
        // The macro is put back in the page, to be replaced by the result of its execution.
        assertEquals(List.of(this.macroBlock), this.pageXDOM.getChildren());
    }

    @Test
    void displayFAQRestrictedWhenTheDocumentIsRestricted() throws Exception
    {
        when(this.document.isRestricted()).thenReturn(true);

        assertFAQRestricted();
    }

    @Test
    void displayFAQRestrictedWhenTheFAQPropertyIsRestricted() throws Exception
    {
        when(this.faqClass.isRestricted()).thenReturn(true);

        assertFAQRestricted();
    }

    @Test
    void displayFAQRestrictedWhenTheRenderingIsRestricted() throws Exception
    {
        this.macroContext.getTransformationContext().setRestricted(true);

        assertFAQRestricted();
    }

    @Test
    void displayNothingWhenTheFAQIsEmpty() throws Exception
    {
        when(this.documentationObject.getLargeStringValue("faq")).thenReturn(" ");

        assertEquals(List.of(), this.macro.execute(parameters(Field.FAQ), null, this.macroContext));
        verify(this.contentParser, never()).parse(any(), any(), any());
    }

    @Test
    void displayNothingWhenThereIsNoDocumentationObject() throws Exception
    {
        when(this.document.getXObject(any(EntityReference.class))).thenReturn(null);

        assertEquals(List.of(), this.macro.execute(parameters(Field.FAQ), null, this.macroContext));
    }

    @Test
    void displayNothingWithoutContextDocument() throws Exception
    {
        when(this.xcontext.getDoc()).thenReturn(null);

        assertEquals(List.of(), this.macro.execute(parameters(Field.FAQ), null, this.macroContext));
    }

    @Test
    void displayFAQParseFailure() throws Exception
    {
        when(this.contentParser.parse(any(), any(), any())).thenThrow(new ParseException("error"));

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters(Field.FAQ), null, this.macroContext));

        assertEquals("Failed to parse the FAQ of document [wiki:documentation.install.WebHome]",
            exception.getMessage());
    }

    @Test
    void displayFAQExecutionFailure() throws Exception
    {
        when(this.contentParser.parse(any(), any(), any())).thenReturn(new XDOM(List.of()));
        doThrow(new Exception("error")).when(this.authorExecutor).call(any(), any(), any());

        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters(Field.FAQ), null, this.macroContext));

        assertEquals("Failed to execute the FAQ of document [wiki:documentation.install.WebHome]",
            exception.getMessage());
        assertEquals(List.of(this.macroBlock), this.pageXDOM.getChildren());
    }

    @Test
    void displayNothingWhenTheFieldContainsTheMacro() throws Exception
    {
        // The content contains the macro displaying the content.
        when(this.documentDisplayer.display(any(), any())).thenAnswer(invocation -> {
            assertEquals(List.of(), this.macro.execute(parameters(Field.CONTENT), null, this.macroContext));
            return new XDOM(List.of());
        });

        this.macro.execute(parameters(Field.CONTENT), null, this.macroContext);

        verify(this.documentDisplayer).display(any(), any());

        // The content can be displayed again once its display is finished.
        this.macro.execute(parameters(Field.CONTENT), null, this.macroContext);

        verify(this.documentDisplayer, times(2)).display(any(), any());
    }

    @Test
    void descriptor()
    {
        assertFalse(this.macro.supportsInlineMode());
        assertEquals(Set.of("Internal"), this.macro.getDescriptor().getDefaultCategories());
    }

    private void assertFAQRestricted() throws Exception
    {
        when(this.contentParser.parse(any(), any(), any())).thenReturn(new XDOM(List.of()));

        this.macro.execute(parameters(Field.FAQ), null, this.macroContext);

        ArgumentCaptor<TransformationContext> captor = ArgumentCaptor.forClass(TransformationContext.class);
        verify(this.transformationManager).performTransformations(any(), captor.capture());
        assertTrue(captor.getValue().isRestricted());
    }

    private static DocumentationContentMacroParameters parameters(Field field)
    {
        DocumentationContentMacroParameters parameters = new DocumentationContentMacroParameters();
        parameters.setField(field);
        return parameters;
    }
}
