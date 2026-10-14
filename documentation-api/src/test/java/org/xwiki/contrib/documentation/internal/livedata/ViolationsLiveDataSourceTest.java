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
package org.xwiki.contrib.documentation.internal.livedata;

import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataConfigurationResolver;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataPropertyDescriptorStore;
import org.xwiki.livedata.LiveDataSource;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViolationsLiveDataSource}, {@link ViolationsLiveDataConfigurationResolver} and
 * {@link ViolationsLiveDataPropertyDescriptorStore}, which rely on the entry store and on the default configuration.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ ViolationsLiveDataSource.class, ViolationsLiveDataConfigurationResolver.class,
    ViolationsLiveDataPropertyDescriptorStore.class })
class ViolationsLiveDataSourceTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @MockComponent
    @Named(ViolationsLiveDataSource.ID)
    private LiveDataEntryStore entryStore;

    @MockComponent
    @Named(ViolationsLiveDataSource.ID)
    private Provider<LiveDataConfiguration> configurationProvider;

    private final LiveDataPropertyDescriptor descriptor = new LiveDataPropertyDescriptor();

    @BeforeEach
    void setUp()
    {
        this.descriptor.setId("message");
        LiveDataMeta meta = new LiveDataMeta();
        meta.setPropertyDescriptors(List.of(this.descriptor));
        LiveDataConfiguration configuration = new LiveDataConfiguration();
        configuration.setMeta(meta);
        when(this.configurationProvider.get()).thenReturn(configuration);
    }

    @Test
    void source() throws Exception
    {
        LiveDataSource source = this.componentManager.getInstance(LiveDataSource.class, ViolationsLiveDataSource.ID);

        assertSame(this.entryStore, source.getEntries());
        assertEquals(List.of(this.descriptor), List.copyOf(source.getProperties().get()));
    }

    @Test
    void resolve() throws Exception
    {
        LiveDataConfigurationResolver<LiveDataConfiguration> resolver = this.componentManager.getInstance(
            new DefaultParameterizedType(null, LiveDataConfigurationResolver.class, LiveDataConfiguration.class),
            ViolationsLiveDataSource.ID);
        LiveDataConfiguration input = new LiveDataConfiguration();
        input.setId("violations");

        LiveDataConfiguration configuration = resolver.resolve(input);

        assertEquals("violations", configuration.getId());
        assertEquals("message", configuration.getMeta().getPropertyDescriptors().iterator().next().getId());
    }

    @Test
    void getProperties() throws Exception
    {
        LiveDataPropertyDescriptorStore store =
            this.componentManager.getInstance(LiveDataPropertyDescriptorStore.class, ViolationsLiveDataSource.ID);

        assertEquals(List.of(this.descriptor), List.copyOf(store.get()));
    }
}
