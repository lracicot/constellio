package com.constellio.model.conf.ldap.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.constellio.model.conf.ldap.RegexFilter;
import com.constellio.model.conf.ldap.config.LDAPServerConfiguration;
import com.constellio.model.conf.ldap.config.LDAPUserSyncConfiguration;
import com.constellio.model.conf.ldap.user.LDAPGroup;
import com.constellio.model.conf.ldap.user.LDAPUser;
import com.constellio.sdk.tests.ConstellioTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 */
public class AzureAdClientTest extends ConstellioTest {

    @Mock
    LDAPServerConfiguration ldapServerConfiguration;

    @Mock
    LDAPUserSyncConfiguration ldapUserSyncConfiguration;

    @Before
    public void setUp()
            throws Exception {
        when(ldapServerConfiguration.getClientId()).thenReturn("69ab5806-25cf-4d80-a818-5b7cb7df1681");
        when(ldapServerConfiguration.getTenantName()).thenReturn("adgrics.onmicrosoft.com");
        when(ldapUserSyncConfiguration.getClientId()).thenReturn("bec3eab8-7c58-4263-b439-71ae66faa656");
        when(ldapUserSyncConfiguration.getClientSecret()).thenReturn("keAVWBUg69oq5pVEKXw1IrPsFuQD8GU4J1D2XGj0Bx0=");

        when(ldapUserSyncConfiguration.isGroupAccepted(any("".getClass()))).thenReturn(true);
        when(ldapUserSyncConfiguration.isUserAccepted(any("".getClass()))).thenReturn(true);
    }

    @Test
    public void testGetUserNameList() throws Exception {
        AzureAdClient azureAdClient = new AzureAdClient(ldapServerConfiguration, ldapUserSyncConfiguration);
        azureAdClient.init();

        final Set<String> results = azureAdClient.getUserNameList();

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isEqualTo(4);
    }

    @Test
    public void testGetGroupNameList() throws Exception {
        AzureAdClient azureAdClient = new AzureAdClient(ldapServerConfiguration, ldapUserSyncConfiguration);
        azureAdClient.init();

        final Set<String> results = azureAdClient.getGroupNameList();

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isEqualTo(2);
    }

    @Test
    public void testGetGroupsAndTheirUsers() throws Exception {
        AzureAdClient.maxResults = 1;

        AzureAdClient azureAdClientSpy = spy(new AzureAdClient(ldapServerConfiguration, ldapUserSyncConfiguration));

        azureAdClientSpy.init();

        final Map<String, LDAPGroup> ldapGroups = new HashMap<>();
        final Map<String, LDAPUser> ldapUsers = new HashMap<>();

        azureAdClientSpy.getGroupsAndTheirUsers(ldapGroups, ldapUsers);

        assertThat(ldapGroups).isNotEmpty();
        assertThat(ldapGroups.size()).isEqualTo(2);
        assertThat(ldapUsers).isNotEmpty();
        assertThat(ldapUsers.size()).isEqualTo(3);

        verify(azureAdClientSpy, atLeast(2)).getAllGroupsResponse(any(String.class));
        verify(azureAdClientSpy, atLeast(2)).getGroupMembersResponse(any(String.class), any(String.class));
    }

    @Test
    public void testGetUsersAndTheirGroups() throws Exception {
        AzureAdClient.maxResults = 1;

        AzureAdClient azureAdClientSpy = spy(new AzureAdClient(ldapServerConfiguration, ldapUserSyncConfiguration));

        azureAdClientSpy.init();

        final Map<String, LDAPGroup> ldapGroups = new HashMap<>();
        final Map<String, LDAPUser> ldapUsers = new HashMap<>();

        azureAdClientSpy.getUsersAndTheirGroups(ldapGroups, ldapUsers);

        assertThat(ldapGroups).isNotEmpty();
        assertThat(ldapGroups.size()).isEqualTo(2);
        assertThat(ldapUsers).isNotEmpty();
        assertThat(ldapUsers.size()).isEqualTo(4);

        verify(azureAdClientSpy, atLeast(4)).getAllUsersResponse(any(String.class));
        verify(azureAdClientSpy, atLeast(2)).getUserGroupsResponse(any(String.class), any(String.class));
    }

    @Test
    public void testAuthenticiate() throws Exception {
        new AzureAdClient(ldapServerConfiguration, ldapUserSyncConfiguration).authenticiate("user1@adgrics.onmicrosoft.com", "Comu12980");
    }
}