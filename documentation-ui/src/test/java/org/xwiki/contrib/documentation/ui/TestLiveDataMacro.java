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

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Test replacement of the {@code liveData} macro, displaying the parameters that the tests check as attributes of a
 * group.
 *
 * @version $Id$
 */
@Component
@Named("liveData")
@Singleton
public class TestLiveDataMacro extends AbstractMacro<TestLiveDataMacroParameters>
{
    /**
     * Default constructor.
     */
    public TestLiveDataMacro()
    {
        super("Live Data", "Test replacement of the liveData macro.", new DefaultContentDescriptor(false),
            TestLiveDataMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(TestLiveDataMacroParameters parameters, String content,
        MacroTransformationContext context)
    {
        return List.of(new GroupBlock(Map.of("class", "liveData", "data-id", parameters.getId(), "data-sort",
            parameters.getSort())));
    }
}
