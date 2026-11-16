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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.ImageBlock;
import org.xwiki.rendering.util.IdGenerator;

/**
 * Makes the ids of the headings and images of some blocks inserted in a page unique in that page.
 *
 * @version $Id$
 * @since 1.19
 */
final class UniqueIds
{
    private UniqueIds()
    {
        // Utility class.
    }

    /**
     * Generates the ids of the headings and images of some blocks with the id generator of the page, so that they
     * don't collide with the ids of the headings and images of the rest of the page.
     *
     * @param blocks the blocks whose heading and image ids to regenerate
     * @param idGenerator the id generator of the page in which the blocks are inserted
     */
    static void makeIdsUnique(Block blocks, IdGenerator idGenerator)
    {
        blocks.getBlocks(block -> {
            if (block instanceof HeaderBlock headerBlock) {
                headerBlock.setId(makeIdUnique(idGenerator, headerBlock.getId()));
            } else if (block instanceof ImageBlock imageBlock) {
                imageBlock.setId(makeIdUnique(idGenerator, imageBlock.getId()));
            }
            return false;
        }, Block.Axes.DESCENDANT);
    }

    private static String makeIdUnique(IdGenerator idGenerator, String id)
    {
        if (StringUtils.isBlank(id)) {
            return id;
        }
        // The generated ids are a prefix letter (for example "H" for headings) followed by the text-based part.
        return idGenerator.generateUniqueId(id.substring(0, 1), id.substring(1));
    }
}
