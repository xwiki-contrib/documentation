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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.TextAreaClass;
import com.xpn.xwiki.objects.classes.TextAreaClass.ContentType;

/**
 * Displays a property of an object of the current documentation page as part of the page XDOM (and not as already
 * rendered HTML, as {@code $doc.display()} does), so that the headings it contains are visible to the other macros of
 * the page, such as the {@code toc} macro of {@code DocApp.Code.DocumentationSheet}. Meant to be used by the content
 * extensions of the documentation sheet (see {@link DocumentationContentExtensions}), to display the structured data
 * of a documentation page, for example the examples of a rendering macro.
 * <p>
 * The property is executed with the same rights and restrictions as {@code $doc.display(property, object)} executes it:
 * with the rights of the document (metadata) author, never with the rights of the author of the page calling the
 * macro. To make sure the macro can't display anything that the current page doesn't already display:
 * <ul>
 * <li>it only displays properties of the current document, which the current user is already viewing;</li>
 * <li>it only displays properties of the classes of the documentation application (in the {@code DocApp.Code}
 * space), whose properties are meant to be displayed on the documentation pages;</li>
 * <li>it only displays text area properties holding wiki content, which {@code $doc.display()} would also execute as
 * wiki content (and not, for example, Velocity code, or a password which {@code $doc.display()} would hide).</li>
 * </ul>
 *
 * @version $Id$
 * @since 1.19
 */
@Component
@Named("documentationProperty")
@Singleton
public class DocumentationPropertyMacro extends AbstractMacro<DocumentationPropertyMacroParameters>
{
    /**
     * The space holding the classes of the documentation application, the only ones whose properties can be displayed.
     */
    private static final List<String> CLASSES_SPACE = List.of("DocApp", "Code");

    /**
     * The properties being displayed by the current thread, used to stop a property which contains this macro from
     * displaying itself again, infinitely.
     */
    private final ThreadLocal<Set<String>> propertiesBeingDisplayed = ThreadLocal.withInitial(HashSet::new);

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private DocumentationPropertyDisplayer propertyDisplayer;

    /**
     * Default constructor.
     */
    public DocumentationPropertyMacro()
    {
        super("Documentation Property",
            "Displays a property of an object of the current documentation page, keeping its headings visible to the "
                + "table of contents.",
            DocumentationPropertyMacroParameters.class);
        // This macro is only meant to be used by the content extensions of the documentation sheet, so it's not
        // proposed to page authors.
        setDefaultCategories(Set.of(DEFAULT_CATEGORY_INTERNAL));
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(DocumentationPropertyMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        // The current document, with its unsaved changes when previewing it, as for $doc.display().
        XWikiDocument document = xcontext.getDoc();
        if (document == null) {
            return List.of();
        }

        DocumentReference classReference = this.documentReferenceResolver.resolve(parameters.getClassName());
        checkClass(classReference, document);
        String property = parameters.getProperty();
        BaseObject object = document.getXObject(classReference);
        if (object == null) {
            return List.of();
        }
        checkProperty(object.getXClass(xcontext), property);

        String propertyKey = this.entityReferenceSerializer.serialize(document.getDocumentReference()) + '/'
            + this.entityReferenceSerializer.serialize(classReference) + '/' + property;
        Set<String> properties = this.propertiesBeingDisplayed.get();
        if (!properties.add(propertyKey)) {
            throw new MacroExecutionException(
                String.format("The property [%s] of class [%s] cannot display itself.", property, classReference));
        }
        try {
            return this.propertyDisplayer.display(document, object, property,
                String.format("property [%s] of class [%s]", property, classReference), context);
        } finally {
            properties.remove(propertyKey);
            if (properties.isEmpty()) {
                // Don't keep the set in the thread once the outermost property is displayed (threads are pooled).
                this.propertiesBeingDisplayed.remove();
            }
        }
    }

    private void checkClass(DocumentReference classReference, XWikiDocument document) throws MacroExecutionException
    {
        List<String> spaces =
            classReference.getSpaceReferences().stream().map(EntityReference::getName).toList();
        if (!classReference.getWikiReference().equals(document.getDocumentReference().getWikiReference())
            || spaces.size() < CLASSES_SPACE.size() || !spaces.subList(0, CLASSES_SPACE.size()).equals(CLASSES_SPACE))
        {
            throw new MacroExecutionException(String.format(
                "Only the properties of the classes of the [%s] space of the current wiki can be displayed.",
                this.entityReferenceSerializer.serialize(
                    new SpaceReference(document.getDocumentReference().getWikiReference().getName(), CLASSES_SPACE))));
        }
    }

    private void checkProperty(BaseClass xclass, String property) throws MacroExecutionException
    {
        // Same condition as TextAreaClass#displayView() for executing the property as wiki content: any content type
        // other than plain text and Velocity (an unknown one included).
        if (xclass == null || !(xclass.get(property) instanceof TextAreaClass textArea)) {
            throw new MacroExecutionException(
                String.format("The property [%s] isn't a text area of class [%s].", property, xclass == null ? null
                    : xclass.getDocumentReference()));
        }
        ContentType contentType = ContentType.getByValue(textArea.getContentType());
        if (contentType == ContentType.PURE_TEXT || contentType == ContentType.VELOCITY_CODE
            || contentType == ContentType.VELOCITYWIKI)
        {
            throw new MacroExecutionException(
                String.format("The property [%s] of class [%s] doesn't hold wiki content.", property,
                    xclass.getDocumentReference()));
        }
    }
}
