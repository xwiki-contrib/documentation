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
import java.util.stream.Collectors;

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.properties.PropertyDescriptor;
import org.xwiki.properties.internal.DefaultBeanDescriptor;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.parser.ContentParser;
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
import com.xpn.xwiki.objects.classes.PasswordClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentationPropertyMacro}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(DocumentationPropertyDisplayer.class)
class DocumentationPropertyMacroTest
{
    private static final DocumentReference DOCUMENT_REFERENCE =
        new DocumentReference("wiki", List.of("documentation", "code-macro"), "WebHome");

    private static final DocumentReference AUTHOR_REFERENCE = new DocumentReference("wiki", "XWiki", "Author");

    private static final String CLASS_NAME = "DocApp.Code.RenderingMacroClass";

    private static final DocumentReference CLASS_REFERENCE =
        new DocumentReference("wiki", List.of("DocApp", "Code"), "RenderingMacroClass");

    private static final String EXAMPLES = "= Example =\nSome example";

    @InjectMockComponents
    private DocumentationPropertyMacro macro;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @MockComponent
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @MockComponent
    private ContentParser contentParser;

    @MockComponent
    private TransformationManager transformationManager;

    @MockComponent
    private AuthorExecutor authorExecutor;

    private XWikiContext xcontext;

    private XWikiDocument document;

    private BaseObject object;

    private BaseClass xclass;

    private TextAreaClass examplesClass;

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

        when(this.documentReferenceResolver.resolve(CLASS_NAME)).thenReturn(CLASS_REFERENCE);
        this.object = mock(BaseObject.class);
        when(this.document.getXObject(CLASS_REFERENCE)).thenReturn(this.object);
        when(this.object.getLargeStringValue("examples")).thenReturn(EXAMPLES);
        this.xclass = mock(BaseClass.class);
        when(this.xclass.getDocumentReference()).thenReturn(CLASS_REFERENCE);
        when(this.object.getXClass(this.xcontext)).thenReturn(this.xclass);
        this.examplesClass = mock(TextAreaClass.class);
        when(this.examplesClass.getContentType()).thenReturn("wikitext");
        when(this.xclass.get("examples")).thenReturn(this.examplesClass);

        when(this.entityReferenceSerializer.serialize(any())).thenAnswer(invocation -> {
            Object reference = invocation.getArgument(0);
            return reference instanceof SpaceReference ? "wiki:DocApp.Code" : reference.toString();
        });
        when(this.authorExecutor.call(any(), any(), any()))
            .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(0).call());

        this.macroBlock = new MacroBlock("documentationProperty", Map.of(), false);
        XDOM pageXDOM = new XDOM(List.of(this.macroBlock));
        this.macroContext = new MacroTransformationContext(new TransformationContext(pageXDOM, Syntax.XWIKI_2_1));
        this.macroContext.setXDOM(pageXDOM);
        this.macroContext.setCurrentMacroBlock(this.macroBlock);
    }

    @Test
    void displayPropertyAsBlocksExecutedWithTheDocumentAuthorRights() throws Exception
    {
        HeaderBlock heading = new HeaderBlock(List.of(new WordBlock("Example")), HeaderLevel.LEVEL1, "HExample");
        when(this.contentParser.parse(EXAMPLES, Syntax.XWIKI_2_1, DOCUMENT_REFERENCE))
            .thenReturn(new XDOM(List.of(heading)));

        List<Block> result = this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext);

        // The heading stays a block, so that the TOC macro can see it.
        assertEquals(List.of(heading), ((MetaDataBlock) result.get(0)).getChildren());
        verify(this.authorExecutor).call(any(), eq(AUTHOR_REFERENCE), eq(DOCUMENT_REFERENCE));
    }

    @Test
    void displayNothingWhenThePageHasNoObjectOfTheClass() throws Exception
    {
        when(this.document.getXObject(CLASS_REFERENCE)).thenReturn(null);

        assertEquals(List.of(), this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext));
    }

    @Test
    void displayNothingWithoutContextDocument() throws Exception
    {
        when(this.xcontext.getDoc()).thenReturn(null);

        assertEquals(List.of(), this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext));
    }

    @Test
    void refuseClassOutsideOfTheDocumentationApplication() throws Exception
    {
        DocumentReference userClassReference = new DocumentReference("wiki", "XWiki", "XWikiUsers");
        when(this.documentReferenceResolver.resolve("XWiki.XWikiUsers")).thenReturn(userClassReference);

        assertRefused("XWiki.XWikiUsers",
            "Only the properties of the classes of the [wiki:DocApp.Code] space of the current wiki can be displayed.");
        verify(this.document, never()).getXObject(userClassReference);
    }

    @Test
    void refuseClassOfTheDocApplicationParentSpace() throws Exception
    {
        DocumentReference classReference = new DocumentReference("wiki", "DocApp", "SomeClass");
        when(this.documentReferenceResolver.resolve("DocApp.SomeClass")).thenReturn(classReference);

        assertRefused("DocApp.SomeClass",
            "Only the properties of the classes of the [wiki:DocApp.Code] space of the current wiki can be displayed.");
    }

    @Test
    void refuseClassOfAnotherWiki() throws Exception
    {
        DocumentReference classReference =
            new DocumentReference("otherwiki", List.of("DocApp", "Code"), "RenderingMacroClass");
        when(this.documentReferenceResolver.resolve("otherwiki:" + CLASS_NAME)).thenReturn(classReference);

        assertRefused("otherwiki:" + CLASS_NAME,
            "Only the properties of the classes of the [wiki:DocApp.Code] space of the current wiki can be displayed.");
    }

    @Test
    void refusePropertyWhichIsNotATextArea() throws Exception
    {
        PasswordClass passwordClass = mock(PasswordClass.class);
        when(this.xclass.get("password")).thenReturn(passwordClass);

        assertRefused(CLASS_NAME, "password",
            "The property [password] isn't a text area of class [wiki:DocApp.Code.RenderingMacroClass].");
    }

    @Test
    void refuseMissingProperty() throws Exception
    {
        assertRefused(CLASS_NAME, "missing",
            "The property [missing] isn't a text area of class [wiki:DocApp.Code.RenderingMacroClass].");
    }

    @Test
    void refusePropertyWhenTheClassIsMissing() throws Exception
    {
        when(this.object.getXClass(this.xcontext)).thenReturn(null);

        assertRefused(CLASS_NAME, "examples", "The property [examples] isn't a text area of class [null].");
    }

    @ParameterizedTest
    @ValueSource(strings = { "puretext", "velocitycode", "velocitywiki" })
    void refuseTextAreaNotHoldingWikiContent(String contentType) throws Exception
    {
        when(this.examplesClass.getContentType()).thenReturn(contentType);

        assertRefused(CLASS_NAME, "examples",
            "The property [examples] of class [wiki:DocApp.Code.RenderingMacroClass] doesn't hold wiki content.");
    }

    @Test
    void refusePropertyWhichContainsTheMacroDisplayingIt() throws Exception
    {
        // The property contains the macro displaying it.
        when(this.contentParser.parse(any(), any(), any())).thenAnswer(invocation -> {
            MacroExecutionException exception = assertThrows(MacroExecutionException.class,
                () -> this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext));
            assertEquals("The property [examples] of class [wiki:DocApp.Code.RenderingMacroClass] cannot display "
                + "itself.", exception.getMessage());
            return new XDOM(List.of());
        });

        this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext);

        // The property can be displayed again once its display is finished.
        this.macro.execute(parameters(CLASS_NAME, "examples"), null, this.macroContext);

        verify(this.contentParser, times(2)).parse(any(), any(), any());
    }

    @Test
    void descriptor()
    {
        assertFalse(this.macro.supportsInlineMode());
        assertEquals(Set.of("Internal"), this.macro.getDescriptor().getDefaultCategories());
        // The class is given by the "class" parameter.
        assertEquals(Set.of("class", "property"), new DefaultBeanDescriptor(DocumentationPropertyMacroParameters.class)
            .getProperties().stream().map(PropertyDescriptor::getId).collect(Collectors.toSet()));
    }

    private void assertRefused(String className, String message) throws Exception
    {
        assertRefused(className, "examples", message);
    }

    private void assertRefused(String className, String property, String message) throws Exception
    {
        MacroExecutionException exception = assertThrows(MacroExecutionException.class,
            () -> this.macro.execute(parameters(className, property), null, this.macroContext));
        assertEquals(message, exception.getMessage());
        verify(this.contentParser, never()).parse(any(), any(), any());
    }

    private static DocumentationPropertyMacroParameters parameters(String className, String property)
    {
        DocumentationPropertyMacroParameters parameters = new DocumentationPropertyMacroParameters();
        parameters.setClassName(className);
        parameters.setProperty(property);
        return parameters;
    }
}
