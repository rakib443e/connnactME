package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.network.GroupInfoDto
import com.example.core.storage.CachedContactEntity
import com.example.ui.UiActionState
import com.example.ui.components.AvatarCircle
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreationScreen(
  contacts: List<CachedContactEntity>,
  actionState: UiActionState,
  onCreateGroup: (name: String, description: String, memberIds: List<String>) -> Unit,
  onClearError: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var extraUserId by remember { mutableStateOf("") }
  val selectedMemberIds = remember { mutableStateListOf<String>() }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("New Group") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.widthIn(max = 560.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Group Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("group_name_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("group_desc_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = extraUserId,
            onValueChange = { extraUserId = it.trim().uppercase() },
            label = { Text("Add by User ID (e.g. CM100002)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("group_extra_member_input"),
          )
          OutlinedButton(
            onClick = {
              if (extraUserId.isNotBlank() && extraUserId !in selectedMemberIds) {
                selectedMemberIds.add(extraUserId)
                extraUserId = ""
              }
            }
          ) {
            Text("Add")
          }
        }

        if (contacts.isNotEmpty()) {
          Text("Select from Contacts", style = MaterialTheme.typography.titleSmall)
          contacts.forEach { c ->
            val checked = c.userId in selectedMemberIds
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier =
                Modifier.fillMaxWidth().clickable {
                  if (checked) selectedMemberIds.remove(c.userId)
                  else selectedMemberIds.add(c.userId)
                },
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = {
                    if (it) selectedMemberIds.add(c.userId)
                    else selectedMemberIds.remove(c.userId)
                  },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                  Text(c.displayName, style = MaterialTheme.typography.titleSmall)
                  Text("@${c.username} • ${c.userId}", style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }

        if (selectedMemberIds.isNotEmpty()) {
          Text(
            "Selected members: ${selectedMemberIds.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        if (actionState is UiActionState.Error) {
          ErrorStateCard(message = actionState.message, onDismiss = onClearError)
        }

        Button(
          onClick = { onCreateGroup(name.trim(), description.trim(), selectedMemberIds.toList()) },
          enabled = name.isNotBlank() && actionState !is UiActionState.Loading,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("create_group_submit_btn"),
        ) {
          Text("Create Group")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
  groupId: String,
  currentUserId: String,
  groupInfo: GroupInfoDto?,
  actionState: UiActionState,
  onLoadGroup: () -> Unit,
  onUpdateGroupInfo: (name: String, description: String) -> Unit,
  onOpenMembers: () -> Unit,
  onOpenMediaGallery: () -> Unit,
  onLeaveGroup: () -> Unit,
  onClearState: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  LaunchedEffect(groupId) { onLoadGroup() }

  var editName by remember(groupInfo?.name) { mutableStateOf(groupInfo?.name ?: "") }
  var editDesc by remember(groupInfo?.description) { mutableStateOf(groupInfo?.description ?: "") }
  val isAdmin = groupInfo?.adminIds?.contains(currentUserId) == true

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Group Details") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    if (groupInfo == null) {
      LoadingStateView("Loading group details...", modifier = Modifier.padding(padding))
      return@Scaffold
    }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarCircle(name = groupInfo.name, size = 64.dp, isGroup = true)
        Spacer(Modifier.width(14.dp))
        Column {
          Text(groupInfo.name, style = MaterialTheme.typography.headlineSmall)
          Text(
            "${groupInfo.memberCount} members • ID: ${groupInfo.groupId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (isAdmin) {
        OutlinedTextField(
          value = editName,
          onValueChange = { editName = it },
          label = { Text("Group Name (Admin)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("edit_group_name_input"),
        )
        OutlinedTextField(
          value = editDesc,
          onValueChange = { editDesc = it },
          label = { Text("Description (Admin)") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().testTag("edit_group_desc_input"),
        )
        Button(
          onClick = { onUpdateGroupInfo(editName.trim(), editDesc.trim()) },
          enabled = editName.isNotBlank(),
          modifier = Modifier.fillMaxWidth().testTag("save_group_info_btn"),
        ) {
          Text("Update Group Info")
        }
      } else if (groupInfo.description.isNotBlank()) {
        Text(groupInfo.description, style = MaterialTheme.typography.bodyMedium)
      }

      if (actionState is UiActionState.Error) {
        ErrorStateCard(message = actionState.message, onDismiss = onClearState)
      }

      OutlinedButton(
        onClick = onOpenMembers,
        modifier = Modifier.fillMaxWidth().testTag("open_group_members_btn"),
      ) {
        Icon(Icons.Filled.Group, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Manage Members (${groupInfo.memberCount})")
      }

      OutlinedButton(
        onClick = onOpenMediaGallery,
        modifier = Modifier.fillMaxWidth().testTag("open_group_media_btn"),
      ) {
        Icon(Icons.Filled.Collections, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Shared Media Gallery")
      }

      OutlinedButton(
        onClick = onLeaveGroup,
        modifier = Modifier.fillMaxWidth().testTag("leave_group_btn"),
      ) {
        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Leave Group")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
  groupId: String,
  currentUserId: String,
  groupInfo: GroupInfoDto?,
  actionState: UiActionState,
  onLoadGroup: () -> Unit,
  onAddMember: (String) -> Unit,
  onRemoveMember: (String) -> Unit,
  onClearState: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  LaunchedEffect(groupId) { onLoadGroup() }

  var newMemberId by remember { mutableStateOf("") }
  val isAdmin = groupInfo?.adminIds?.contains(currentUserId) == true

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Group Members") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    if (groupInfo == null) {
      LoadingStateView("Loading members...", modifier = Modifier.padding(padding))
      return@Scaffold
    }

    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      if (isAdmin) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newMemberId,
            onValueChange = { newMemberId = it.trim().uppercase() },
            label = { Text("Add User ID (e.g. CM100002)") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("add_group_member_input"),
          )
          Button(
            onClick = {
              if (newMemberId.isNotBlank()) {
                onAddMember(newMemberId)
                newMemberId = ""
              }
            },
            modifier = Modifier.testTag("add_group_member_btn"),
          ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = "Add Member")
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      if (actionState is UiActionState.Error) {
        ErrorStateCard(message = actionState.message, onDismiss = onClearState)
        Spacer(Modifier.height(10.dp))
      }

      LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(groupInfo.members, key = { it.userId }) { member ->
          val displayName = member.profile?.displayName ?: member.userId
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AvatarCircle(name = displayName, size = 44.dp)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                  "${member.userId} • ${member.role.uppercase()}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = if (member.role == "admin") FontWeight.Bold else FontWeight.Normal,
                )
              }
              if (isAdmin && member.userId != currentUserId) {
                IconButton(
                  onClick = { onRemoveMember(member.userId) },
                  modifier = Modifier.testTag("remove_member_${member.userId}"),
                ) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove member")
                }
              }
            }
          }
        }
      }
    }
  }
}
