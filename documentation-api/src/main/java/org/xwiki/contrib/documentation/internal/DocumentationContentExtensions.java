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

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.wiki.WikiComponent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.uiextension.UIExtension;
import org.xwiki.uiextension.UIExtensionFilter;
import org.xwiki.uiextension.UIExtensionManager;

import com.xpn.xwiki.XWikiContext;

/**
 * Executes the content extensions of the documentation sheet: the UI extensions of the
 * {@value #EXTENSION_POINT} extension point, which display the structured data of some documentation pages (for
 * example the description, usage, parameters and examples of a rendering macro) in place of their content.
 * <p>
 * Only the extensions whose author has the Admin right on the current wiki are executed, since they are displayed on
 * every documentation page, and they are executed in the order of their {@code order} parameter.
 *
 * @version $Id$
 * @since 1.19
 */
@Component(roles = DocumentationContentExtensions.class)
@Singleton
public class DocumentationContentExtensions
{
    /**
     * The extension point of the content extensions.
     */
    public static final String EXTENSION_POINT = "org.xwiki.contrib.documentation.sheet.content";

    private static final String ORDER_PARAMETER = "order";

    @Inject
    private UIExtensionManager uiExtensionManager;

    @Inject
    @Named("sortByParameter")
    private UIExtensionFilter sortByParameterFilter;

    @Inject
    private AuthorizationManager authorization;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("html/5.0")
    private BlockRenderer htmlRenderer;

    @Inject
    private Logger logger;

    /**
     * Returns the content extensions to execute, sorted by their {@code order} parameter. An extension isn't returned
     * when its author doesn't have the Admin right on the current wiki, or when it has no author (an extension
     * implemented in Java rather than in a wiki page), since only the wiki administrators may change what every
     * documentation page displays.
     *
     * @return the content extensions to execute, empty if there's none
     */
    public List<UIExtension> getExtensions()
    {
        WikiReference wikiReference = new WikiReference(this.xcontextProvider.get().getWikiId());
        List<UIExtension> extensions = new ArrayList<>();
        for (UIExtension extension : this.uiExtensionManager.get(EXTENSION_POINT)) {
            if (extension instanceof WikiComponent wikiComponent) {
                DocumentReference authorReference = wikiComponent.getAuthorReference();
                if (authorReference != null
                    && this.authorization.hasAccess(Right.ADMIN, authorReference, wikiReference))
                {
                    extensions.add(extension);
                }
            }
        }
        return this.sortByParameterFilter.filter(extensions, ORDER_PARAMETER);
    }

    /**
     * Executes the content extensions and returns their output as blocks to insert in the page XDOM (rather than as
     * already rendered HTML), so that the headings they display are visible to the other macros of the page, such as
     * the {@code toc} macro of the documentation sheet.
     * <p>
     * Each extension is executed with the rights of its own author, by the UI extension framework. Its output is
     * inserted as is, without being executed again: any macro left unexecuted in it is removed, so that nothing can be
     * executed with the rights of the page inserting it.
     *
     * @param context the context of the macro displaying the content extensions
     * @return the output of the content extensions, empty if none of them displayed anything, in which case the
     *     content of the page should be displayed instead
     */
    public List<Block> display(MacroTransformationContext context)
    {
        List<Block> result = new ArrayList<>();
        for (UIExtension extension : getExtensions()) {
            Block output = execute(extension);
            if (output == null) {
                continue;
            }
            if (context.getXDOM() != null) {
                UniqueIds.makeIdsUnique(output, context.getXDOM().getIdGenerator());
            }
            if (output instanceof XDOM xdom) {
                // Keep the metadata of the extension output (for example its source), which would be lost by only
                // inserting its children.
                result.add(new MetaDataBlock(xdom.getChildren(), xdom.getMetaData()));
            } else if (output instanceof CompositeBlock) {
                result.addAll(output.getChildren());
            } else {
                result.add(output);
            }
        }
        return result;
    }

    /**
     * @return a copy of the output of the extension, without the macros left unexecuted in it, or {@code null} if the
     *     extension displayed nothing
     */
    private Block execute(UIExtension extension)
    {
        // The extension output may be cached by the UI extension framework, so work on a copy of it.
        Block output = extension.execute().clone();
        if (output instanceof MacroBlock) {
            // Nothing was executed.
            return null;
        }
        removeMacros(output, extension);
        return isEmpty(output) ? null : output;
    }

    /**
     * Removes the macros that the execution of the extension left unexecuted (for example when the maximum number of
     * nested macro executions is reached): the macro transformation would execute them once inserted in the page,
     * with the rights of the page and not with the rights of the extension author.
     */
    private void removeMacros(Block output, UIExtension extension)
    {
        List<MacroBlock> macros = output.getBlocks(new ClassBlockMatcher(MacroBlock.class), Block.Axes.DESCENDANT);
        for (MacroBlock macro : macros) {
            this.logger.warn("Removed the macro [{}] left unexecuted by the documentation content extension [{}].",
                macro.getId(), extension.getId());
            macro.getParent().removeBlock(macro);
        }
    }

    /**
     * Tells whether the extension displayed nothing, for example because the current page doesn't hold the object
     * whose data it displays. The output is rendered to decide it, because an extension displaying nothing still
     * produces some (empty) blocks, while an extension executed asynchronously produces an empty placeholder which is
     * filled later.
     */
    private boolean isEmpty(Block output)
    {
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.htmlRenderer.render(output, printer);
        return printer.toString().isBlank();
    }
}
