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
package org.xwiki.contrib.documentation.test;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.documentation.DocumentationCheck;
import org.xwiki.contrib.documentation.DocumentationViolation;
import org.xwiki.contrib.documentation.DocumentationViolationSeverity;
import org.xwiki.contrib.documentation.internal.DefaultDocumentationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultDocumentationManager}.
 *
 * @version $Id$
 */
@OldcoreTest
@AllComponents
class DefaultDocumentationManagerTest
{
    private static final DocumentReference VIOLATION_CLASS_REFERENCE = new DocumentReference("Wiki", List.of(
        "DocApp", "Code"), "DocumentationViolationClass");

    @InjectMockComponents
    private DefaultDocumentationManager manager;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    private XWikiDocument document;

    @BeforeEach
    void setup() throws Exception
    {
        // Register a violation xclass so that it works.
        XWikiDocument violationClassDocument = new XWikiDocument(VIOLATION_CLASS_REFERENCE);
        BaseClass violationClass = new BaseClass();
        violationClass.addTextField("message", "Message", 100);
        violationClass.addTextField("context", "Context", 100);
        violationClass.addStaticListField("severity");
        violationClassDocument.setXClass(violationClass);
        this.oldcore.getSpyXWiki().saveDocument(violationClassDocument, this.oldcore.getXWikiContext());

        // Register the doc to analyse.
        this.document = new XWikiDocument(new DocumentReference("Wiki", "Space", "Page"));
        this.oldcore.getSpyXWiki().saveDocument(this.document, this.oldcore.getXWikiContext());
    }

    @Test
    void analyzeWhenNoExistingViolationXObjects() throws Exception
    {
        // Generate one violation.
        DocumentationCheck check = this.componentManager.registerMockComponent(DocumentationCheck.class, "test");
        when(check.check(this.document)).thenReturn(Collections.singletonList(
            new DocumentationViolation("message", "context", DocumentationViolationSeverity.ERROR)));

        this.manager.analyse(this.document);

        // Verify that there's now a single violation xobject
        List<BaseObject> objects = this.document.getXObjects(VIOLATION_CLASS_REFERENCE);
        assertEquals(1, objects.size());
        assertEquals("message", objects.get(0).getStringValue("message"));
        assertEquals("context", objects.get(0).getStringValue("context"));
        assertEquals("Error", objects.get(0).getStringValue("severity"));

        // Verify the save message
        assertEquals("Documentation analysis", this.document.getComment());

        // Verify the xobject number
        assertEquals(0, objects.get(0).getNumber());
    }

    /**
     * Verify that the existing violation is removed and a new one added (since the new violation is different from the
     * old one).
     */
    @Test
    void analyzeWhenNewViolationAndExistingViolationNotMatching() throws Exception
    {
        // Generate one violation.
        DocumentationCheck check = this.componentManager.registerMockComponent(DocumentationCheck.class, "test");
        when(check.check(this.document)).thenReturn(Collections.singletonList(
            new DocumentationViolation("message", "context", DocumentationViolationSeverity.ERROR)));

        // Add an existing violation xobject.
        addViolationObject("existing message", "existing context", "existing error");

        this.manager.analyse(this.document);

        // Verify that we have a single violation xobject.
        // Note: Because the XWiki API is weird, removing an xobject simply puts a null in place of the baseobject but
        // there are still 2 xobjects...
        List<BaseObject> objects = this.document.getXObjects(VIOLATION_CLASS_REFERENCE);
        assertEquals(2, objects.size());
        assertNull(objects.get(0));
        assertEquals("message", objects.get(1).getStringValue("message"));
        assertEquals("context", objects.get(1).getStringValue("context"));
        assertEquals("Error", objects.get(1).getStringValue("severity"));

        // Verify the save message
        assertEquals("Documentation analysis", this.document.getComment());

        // Verify the xobject numbers
        assertEquals(1, objects.get(1).getNumber());
    }

    /**
     * Verify that there's no new revision since the existing violation and the one are the same.
     */

    @Test
    void analyzeWhenNewViolationAndExistingViolationAreMatching() throws Exception
    {
        // Generate one violation.
        DocumentationCheck check = this.componentManager.registerMockComponent(DocumentationCheck.class, "test");
        when(check.check(this.document)).thenReturn(Collections.singletonList(
            new DocumentationViolation("message", "context", DocumentationViolationSeverity.ERROR)));

        // Add an existing violation xobject.
        addViolationObject("message", "context", "Error");

        this.manager.analyse(this.document);

        // Verify that we have a single violation xobject.
        List<BaseObject> objects = this.document.getXObjects(VIOLATION_CLASS_REFERENCE);
        assertEquals(1, objects.size());
        assertEquals("message", objects.get(0).getStringValue("message"));
        assertEquals("context", objects.get(0).getStringValue("context"));
        assertEquals("Error", objects.get(0).getStringValue("severity"));

        // Verify that there's no new save message since it wasn't saved and that the revision is still 2.1
        // (1.1 for the doc creation and 2.1 for the xobject addition above).
        assertEquals("", this.document.getComment());
        assertEquals("2.1", this.document.getVersion());

        // Verify the xobject numbers
        assertEquals(0, objects.get(0).getNumber());
    }

    @Test
    void analyzeWhenEmptyExistingXObject() throws Exception
    {
        DocumentationCheck check = this.componentManager.registerMockComponent(DocumentationCheck.class, "test");
        when(check.check(this.document)).thenReturn(Collections.emptyList());

        // Add 2 existing violation xobject but remove the 1sr one to simulate an xobject that has been removed.
        BaseObject v1 =  addViolationObject("message1", "context1", "Error");
        addViolationObject("message2", "context2", "Error");
        this.document.removeXObject(v1);

        this.manager.analyse(this.document);

        // Verify all violations have been removed.
        List<BaseObject> objects = this.document.getXObjects(VIOLATION_CLASS_REFERENCE);
        assertEquals(2, objects.size());
        assertNull(objects.get(0));
        assertNull(objects.get(1));
    }

    private BaseObject addViolationObject(String messgae, String context, String severity) throws Exception
    {
        BaseObject violationObject = this.document.newXObject(VIOLATION_CLASS_REFERENCE,
            this.oldcore.getXWikiContext());
        violationObject.set("message", messgae, this.oldcore.getXWikiContext());
        violationObject.set("context", context, this.oldcore.getXWikiContext());
        violationObject.set("severity", severity, this.oldcore.getXWikiContext());
        this.oldcore.getSpyXWiki().saveDocument(this.document, this.oldcore.getXWikiContext());
        return violationObject;
    }
}
