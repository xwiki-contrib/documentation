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

import jakarta.inject.Named;
import jakarta.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.component.wiki.WikiComponent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MacroMarkerBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.rendering.transformation.TransformationContext;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.uiextension.UIExtension;
import org.xwiki.uiextension.UIExtensionManager;
import org.xwiki.uiextension.internal.filter.SortByParameterFilter;

import com.xpn.xwiki.XWikiContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link DocumentationContentExtensions}.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(SortByParameterFilter.class)
class DocumentationContentExtensionsTest
{
    private static final WikiReference WIKI_REFERENCE = new WikiReference("wiki");

    private static final DocumentReference ADMIN_REFERENCE = new DocumentReference("wiki", "XWiki", "Admin");

    private static final DocumentReference USER_REFERENCE = new DocumentReference("wiki", "XWiki", "User");

    @InjectMockComponents
    private DocumentationContentExtensions contentExtensions;

    @MockComponent
    private UIExtensionManager uiExtensionManager;

    @MockComponent
    private AuthorizationManager authorization;

    @MockComponent
    private Provider<XWikiContext> xcontextProvider;

    @MockComponent
    @Named("html/5.0")
    private BlockRenderer htmlRenderer;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private XDOM pageXDOM;

    private MacroTransformationContext macroContext;

    @BeforeEach
    void setUp()
    {
        XWikiContext xcontext = mock(XWikiContext.class);
        when(this.xcontextProvider.get()).thenReturn(xcontext);
        when(xcontext.getWikiId()).thenReturn(WIKI_REFERENCE.getName());
        when(this.authorization.hasAccess(Right.ADMIN, ADMIN_REFERENCE, WIKI_REFERENCE)).thenReturn(true);

        // Render the words of the blocks, which is enough to tell whether an extension displayed something.
        doAnswer(invocation -> {
            Block block = invocation.getArgument(0);
            WikiPrinter printer = invocation.getArgument(1);
            block.<WordBlock>getBlocks(new ClassBlockMatcher(WordBlock.class), Block.Axes.DESCENDANT_OR_SELF)
                .forEach(word -> printer.print(word.getWord()));
            return null;
        }).when(this.htmlRenderer).render(any(Block.class), any());

        // The page displaying the extensions, which already has a heading using the id of an extension heading.
        this.pageXDOM = new XDOM(List.of());
        this.pageXDOM.getIdGenerator().generateUniqueId("H", "Usage");
        this.macroContext = new MacroTransformationContext(new TransformationContext(this.pageXDOM, Syntax.XWIKI_2_1));
        this.macroContext.setXDOM(this.pageXDOM);
    }

    @Test
    void getExtensionsWrittenByAdministratorsSortedByOrder()
    {
        UIExtension second = extension("second", ADMIN_REFERENCE, "20", new XDOM(List.of()));
        UIExtension first = extension("first", ADMIN_REFERENCE, "10", new XDOM(List.of()));
        UIExtension byUser = extension("byUser", USER_REFERENCE, "1", new XDOM(List.of()));
        UIExtension withoutAuthor = extension("withoutAuthor", null, "1", new XDOM(List.of()));
        // An extension implemented in Java, which has no author.
        UIExtension javaExtension = mock(UIExtension.class);
        when(this.uiExtensionManager.get(DocumentationContentExtensions.EXTENSION_POINT))
            .thenReturn(List.of(second, byUser, withoutAuthor, javaExtension, first));

        assertEquals(List.of(first, second), this.contentExtensions.getExtensions());
    }

    @Test
    void displayExtensionsAsBlocks()
    {
        HeaderBlock heading = new HeaderBlock(List.of(new WordBlock("Usage")), HeaderLevel.LEVEL2, "HUsage");
        XDOM output = new XDOM(List.of(heading));
        output.getMetaData().addMetaData(MetaData.SOURCE, "wiki:DocApp.Code.RenderingMacrosUIX");
        UIExtension extension = extension("extension", ADMIN_REFERENCE, "10", output);
        Block otherOutput = new ParagraphBlock(List.of(new WordBlock("other")));
        UIExtension otherExtension = extension("other", ADMIN_REFERENCE, "20", otherOutput);
        Block compositeChild = new ParagraphBlock(List.of(new WordBlock("composite")));
        UIExtension compositeExtension =
            extension("composite", ADMIN_REFERENCE, "30", new CompositeBlock(List.of(compositeChild)));
        when(this.uiExtensionManager.get(DocumentationContentExtensions.EXTENSION_POINT))
            .thenReturn(List.of(extension, otherExtension, compositeExtension));

        List<Block> result = this.contentExtensions.display(this.macroContext);

        assertEquals(3, result.size());
        // The headings stay blocks, so that the TOC macro can see them, with ids unique in the page.
        MetaDataBlock metadata = (MetaDataBlock) result.get(0);
        assertEquals("wiki:DocApp.Code.RenderingMacrosUIX", metadata.getMetaData().getMetaData(MetaData.SOURCE));
        HeaderBlock displayedHeading = (HeaderBlock) metadata.getChildren().get(0);
        assertEquals("HUsage-1", displayedHeading.getId());
        assertEquals(otherOutput, result.get(1));
        assertEquals(compositeChild, result.get(2));
        // The extension output, which may be cached, isn't modified.
        assertEquals("HUsage", heading.getId());
        assertSame(output, heading.getParent());
    }

    @Test
    void displayNothingWhenTheExtensionsDisplayNothing()
    {
        // An extension executed but displaying nothing, for example because the page doesn't have the object whose
        // data it displays.
        UIExtension emptyExtension = extension("empty", ADMIN_REFERENCE, "10",
            new XDOM(List.of(new MacroMarkerBlock("velocity", Map.of(), "", List.of(), false))));
        // An extension which wasn't executed.
        UIExtension notExecutedExtension =
            extension("notExecuted", ADMIN_REFERENCE, "20", new MacroBlock("velocity", Map.of(), "", false));
        when(this.uiExtensionManager.get(DocumentationContentExtensions.EXTENSION_POINT))
            .thenReturn(List.of(emptyExtension, notExecutedExtension));

        assertTrue(this.contentExtensions.display(this.macroContext).isEmpty());
    }

    @Test
    void displayExtensionsWithoutTheMacrosLeftUnexecuted()
    {
        MacroBlock leftMacro = new MacroBlock("velocity", Map.of(), "$xcontext.user", false);
        XDOM output = new XDOM(List.of(new ParagraphBlock(List.of(new WordBlock("text"))),
            new MacroMarkerBlock("include", Map.of(), List.of(leftMacro), false)));
        UIExtension extension = extension("extension", ADMIN_REFERENCE, "10", output);
        when(this.uiExtensionManager.get(DocumentationContentExtensions.EXTENSION_POINT))
            .thenReturn(List.of(extension));

        List<Block> result = this.contentExtensions.display(this.macroContext);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT).isEmpty());
        assertEquals("Removed the macro [velocity] left unexecuted by the documentation content extension "
            + "[extension].", this.logCapture.getMessage(0));
    }

    @Test
    void displayExtensionsWithoutPageXDOM()
    {
        HeaderBlock heading = new HeaderBlock(List.of(new WordBlock("Usage")), HeaderLevel.LEVEL2, "HUsage");
        UIExtension extension = extension("extension", ADMIN_REFERENCE, "10", new XDOM(List.of(heading)));
        when(this.uiExtensionManager.get(DocumentationContentExtensions.EXTENSION_POINT))
            .thenReturn(List.of(extension));
        this.macroContext.setXDOM(null);

        List<Block> result = this.contentExtensions.display(this.macroContext);

        assertEquals("HUsage", ((HeaderBlock) result.get(0).getChildren().get(0)).getId());
    }

    private static UIExtension extension(String id, DocumentReference authorReference, String order, Block output)
    {
        UIExtension extension = mock(UIExtension.class, withSettings().extraInterfaces(WikiComponent.class));
        when(extension.getId()).thenReturn(id);
        when(((WikiComponent) extension).getAuthorReference()).thenReturn(authorReference);
        when(extension.getParameters()).thenReturn(Map.of("order", order));
        when(extension.execute()).thenReturn(output);
        return extension;
    }
}
