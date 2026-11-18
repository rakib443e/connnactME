package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.network.HealthCheckDto
import com.example.core.network.UserProfileDto
import com.example.core.storage.CachedContactEntity
import com.example.core.storage.CachedConversationEntity
import com.example.core.sync.PollingStatus
import com.example.core.sync.PollingSurface
import com.example.ui.components.AvatarCircle
import com.example.ui.components.BackendConfigDialog
import com.example.ui.components.BackendUnconfiguredBanner
import com.example.ui.components.EmptyStateView
import com.example.ui.components.OfflineBanner
import com.example.ui.theme.ThemePreference
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  currentUser: UserProfileDto?,
  isOnline: Boolean,
  backendUrl: String,
  healthStatus: HealthCheckDto?,
  pollingStatus: PollingStatus,
  conversations: List<CachedConversationEntity>,
  contacts: List<CachedContactEntity>,
  searchResults: List<UserProfileDto>,
  isSearching: Boolean,
  resolvedMediaFiles: Map<String, File>,
  themePreference: ThemePreference,
  onSetPollingSurface: (PollingSurface) -> Unit,
  onRefreshHome: () -> Unit,
  onOpenConversation: (conversationId: String, title: String, isGroup: Boolean) -> Unit,
  onStartDirectChat: (peerUserId: String) -> Unit,
  onSearchUsers: (String) -> Unit,
  onOpenUserProfile: (String) -> Unit,
  onAddContact: (String) -> Unit,
  onRemoveContact: (String) -> Unit,
  onCreateGroup: () -> Unit,
  onOpenProfileSetup: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenUpgrade: () -> Unit,
  onOpenAbout: () -> Unit,
  onSetTheme: (ThemePreference) -> Unit,
  onSaveBackendUrl: (String) -> Unit,
  onTestHealth: () -> Unit,
  onInitializeDrive: () -> Unit,
  onLogout: () -> Unit,
  onResolveMedia: (String) -> Unit,
) {
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }
  var showBackendDialog by remember { mutableStateOf(false) }

  LaunchedEffect(selectedTab) {
    val surface =
      when (selectedTab) {
        0 -> PollingSurface.CONVERSATION_LIST // 5s polling
        else -> PollingSurface.OTHER_ACTIVE // 15s polling
      }
    onSetPollingSurface(surface)
  }

  DisposableEffect(Unit) {
    onDispose { onSetPollingSurface(PollingSurface.IDLE_BACKGROUND) }
  }

  val tabTitles = listOf("Chats", "Contacts", "Search", "Settings")

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(tabTitles[selectedTab], style = MaterialTheme.typography.titleLarge)
              if (currentUser != null) {
                Text(
                  text = "@${currentUser.username} • ${currentUser.userId}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          actions = {
            if (pollingStatus.isSyncing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                strokeWidth = 2.dp,
              )
            }
            IconButton(onClick = onRefreshHome) {
              Icon(Icons.Filled.Refresh, contentDescription = "Sync now")
            }
            IconButton(onClick = onCreateGroup, modifier = Modifier.testTag("home_create_group_btn")) {
              Icon(Icons.Filled.GroupAdd, contentDescription = "Create Group")
            }
          },
        )
      },
      floatingActionButton = {
        if (selectedTab == 0) {
          FloatingActionButton(
            onClick = { selectedTab = 2 },
            modifier = Modifier.testTag("fab_new_chat"),
          ) {
            Icon(Icons.Filled.Search, contentDescription = "Find User to Chat")
          }
        }
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            HomeNavItems(selectedTab = selectedTab, onSelect = { selectedTab = it }).forEach { item ->
              NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = item.icon,
                label = item.label,
                modifier = Modifier.testTag(item.tag),
              )
            }
          }
        }
      },
    ) { padding ->
      Row(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (useNavRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            HomeNavItems(selectedTab = selectedTab, onSelect = { selectedTab = it }).forEach { item ->
              NavigationRailItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = item.icon,
                label = item.label,
                modifier = Modifier.testTag(item.tag),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          OfflineBanner(visible = !isOnline)
          BackendUnconfiguredBanner(
            backendUrl = backendUrl,
            onConfigureClick = { showBackendDialog = true },
          )

          when (selectedTab) {
            0 ->
              ChatListTab(
                conversations = conversations,
                resolvedMediaFiles = resolvedMediaFiles,
                onResolveMedia = onResolveMedia,
                onOpenConversation = onOpenConversation,
                onFindUsers = { selectedTab = 2 },
              )
            1 ->
              ContactsTab(
                contacts = contacts,
                resolvedMediaFiles = resolvedMediaFiles,
                onResolveMedia = onResolveMedia,
                onOpenProfile = onOpenUserProfile,
                onStartChat = onStartDirectChat,
                onRemoveContact = onRemoveContact,
                onFindUsers = { selectedTab = 2 },
              )
            2 ->
              UserSearchTab(
                results = searchResults,
                isSearching = isSearching,
                resolvedMediaFiles = resolvedMediaFiles,
                onResolveMedia = onResolveMedia,
                onQueryChange = onSearchUsers,
                onStartChat = onStartDirectChat,
                onAddContact = onAddContact,
                onViewProfile = onOpenUserProfile,
              )
            3 ->
              SettingsTabContent(
                currentUser = currentUser,
                backendUrl = backendUrl,
                healthStatus = healthStatus,
                themePreference = themePreference,
                onSetTheme = onSetTheme,
                onOpenBackendConfig = { showBackendDialog = true },
                onOpenProfileSetup = onOpenProfileSetup,
                onOpenPrivacy = onOpenPrivacy,
                onOpenUpgrade = onOpenUpgrade,
                onOpenAbout = onOpenAbout,
                onLogout = onLogout,
              )
          }
        }
      }
    }
  }

  if (showBackendDialog) {
    BackendConfigDialog(
      currentUrl = backendUrl,
      healthStatus = healthStatus,
      onSaveUrl = onSaveBackendUrl,
      onTestHealth = onTestHealth,
      onInitializeDrive = onInitializeDrive,
      onDismiss = { showBackendDialog = false },
    )
  }
}

private data class HomeNavItemData(
  val selected: Boolean,
  val onClick: () -> Unit,
  val icon: @Composable () -> Unit,
  val label: @Composable () -> Unit,
  val tag: String,
)

private fun HomeNavItems(selectedTab: Int, onSelect: (Int) -> Unit): List<HomeNavItemData> =
  listOf(
    HomeNavItemData(
      selected = selectedTab == 0,
      onClick = { onSelect(0) },
      icon = {
        Icon(
          if (selectedTab == 0) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
          contentDescription = "Chats",
        )
      },
      label = { Text("Chats") },
      tag = "nav_tab_chats",
    ),
    HomeNavItemData(
      selected = selectedTab == 1,
      onClick = { onSelect(1) },
      icon = {
        Icon(
          if (selectedTab == 1) Icons.Filled.Contacts else Icons.Outlined.Contacts,
          contentDescription = "Contacts",
        )
      },
      label = { Text("Contacts") },
      tag = "nav_tab_contacts",
    ),
    HomeNavItemData(
      selected = selectedTab == 2,
      onClick = { onSelect(2) },
      icon = {
        Icon(
          if (selectedTab == 2) Icons.Filled.Search else Icons.Outlined.Search,
          contentDescription = "Search",
        )
      },
      label = { Text("Search") },
      tag = "nav_tab_search",
    ),
    HomeNavItemData(
      selected = selectedTab == 3,
      onClick = { onSelect(3) },
      icon = {
        Icon(
          if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
          contentDescription = "Settings",
        )
      },
      label = { Text("Settings") },
      tag = "nav_tab_settings",
    ),
  )

@Composable
private fun ChatListTab(
  conversations: List<CachedConversationEntity>,
  resolvedMediaFiles: Map<String, File>,
  onResolveMedia: (String) -> Unit,
  onOpenConversation: (conversationId: String, title: String, isGroup: Boolean) -> Unit,
  onFindUsers: () -> Unit,
) {
  if (conversations.isEmpty()) {
    EmptyStateView(
      icon = Icons.Outlined.ChatBubbleOutline,
      title = "No conversations yet",
      subtitle = "Search by username, user ID (CM...), or phone number to start your first conversation.",
      actionLabel = "Find People",
      onAction = onFindUsers,
    )
    return
  }

  LazyColumn(
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxSize().testTag("chat_list_lazy"),
  ) {
    items(conversations, key = { it.conversationId }) { conv ->
      LaunchedEffect(conv.photoMediaId) {
        conv.photoMediaId?.let { onResolveMedia(it) }
      }
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              onOpenConversation(conv.conversationId, conv.title, conv.type == "group")
            }
            .testTag("conversation_card_${conv.conversationId}"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AvatarCircle(
            name = conv.title,
            photoFile = conv.photoMediaId?.let { resolvedMediaFiles[it] },
            isGroup = conv.type == "group",
          )
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = conv.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              if (conv.type == "group") {
                Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = "GROUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            }
            Spacer(Modifier.height(4.dp))
            Text(
              text = conv.lastMessagePreview.ifBlank { "No messages yet" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (conv.unreadCount > 0) {
            Spacer(Modifier.width(10.dp))
            Badge { Text(conv.unreadCount.toString()) }
          }
        }
      }
    }
  }
}

@Composable
private fun ContactsTab(
  contacts: List<CachedContactEntity>,
  resolvedMediaFiles: Map<String, File>,
  onResolveMedia: (String) -> Unit,
  onOpenProfile: (String) -> Unit,
  onStartChat: (String) -> Unit,
  onRemoveContact: (String) -> Unit,
  onFindUsers: () -> Unit,
) {
  if (contacts.isEmpty()) {
    EmptyStateView(
      icon = Icons.Outlined.Contacts,
      title = "Your contact list is empty",
      subtitle = "Find ConnectMe users via Search and save them here for quick messaging and group invites.",
      actionLabel = "Search Users",
      onAction = onFindUsers,
    )
    return
  }

  LazyColumn(
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(contacts, key = { it.userId }) { contact ->
      LaunchedEffect(contact.photoMediaId) {
        contact.photoMediaId?.let { onResolveMedia(it) }
      }
      Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpenProfile(contact.userId) },
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AvatarCircle(
            name = contact.displayName,
            photoFile = contact.photoMediaId?.let { resolvedMediaFiles[it] },
          )
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
              "@${contact.username} • ${contact.userId}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { onStartChat(contact.userId) }) {
            Icon(Icons.Filled.ChatBubble, contentDescription = "Message ${contact.displayName}")
          }
          IconButton(onClick = { onRemoveContact(contact.userId) }) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove contact")
          }
        }
      }
    }
  }
}

@Composable
private fun UserSearchTab(
  results: List<UserProfileDto>,
  isSearching: Boolean,
  resolvedMediaFiles: Map<String, File>,
  onResolveMedia: (String) -> Unit,
  onQueryChange: (String) -> Unit,
  onStartChat: (String) -> Unit,
  onAddContact: (String) -> Unit,
  onViewProfile: (String) -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }

  Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
    OutlinedTextField(
      value = query,
      onValueChange = {
        query = it
        onQueryChange(it)
      },
      label = { Text("Search username, CM ID, or phone") },
      placeholder = { Text("e.g. alex_r or CM100002") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("user_search_input"),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Private phone numbers are never shown in public search results.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    if (isSearching) {
      Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else if (query.trim().length < 2) {
      EmptyStateView(
        icon = Icons.Outlined.Search,
        title = "Discover people on ConnectMe",
        subtitle = "Enter at least 2 characters to search by username, unique user ID, or registered phone number.",
      )
    } else if (results.isEmpty()) {
      EmptyStateView(
        icon = Icons.Outlined.Search,
        title = "No matching users",
        subtitle = "Verify the username or CM user ID and try again.",
      )
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(results, key = { it.userId }) { user ->
          LaunchedEffect(user.photoMediaId) {
            user.photoMediaId?.let { onResolveMedia(it) }
          }
          Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable { onViewProfile(user.userId) },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AvatarCircle(
                name = user.displayName,
                photoFile = user.photoMediaId?.let { resolvedMediaFiles[it] },
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  user.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  "@${user.username} • ${user.userId}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { onAddContact(user.userId) },
                modifier = Modifier.testTag("add_contact_${user.userId}"),
              ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add contact")
              }
              IconButton(
                onClick = { onStartChat(user.userId) },
                modifier = Modifier.testTag("start_chat_${user.userId}"),
              ) {
                Icon(Icons.Filled.ChatBubble, contentDescription = "Start chat")
              }
            }
          }
        }
      }
    }
  }
}
