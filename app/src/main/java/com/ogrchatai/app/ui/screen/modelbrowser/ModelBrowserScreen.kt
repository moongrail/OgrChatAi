package com.ogrchatai.app.ui.screen.modelbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ogrchatai.app.domain.model.ModelDownloadState
import com.ogrchatai.app.ui.components.EmptyStateView
import com.ogrchatai.app.ui.components.LoadingIndicator
import com.ogrchatai.app.ui.components.ModelChip
import com.ogrchatai.app.ui.viewmodel.ModelBrowserAction
import com.ogrchatai.app.ui.viewmodel.ModelBrowserViewModel

private val ARCHITECTURE_FILTERS = listOf(
    "llama", "mistral", "gemma", "phi", "qwen"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelBrowserScreen(
    onModelClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ModelBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeDownloads = uiState.downloadState.values.filter { it.isDownloading }
    var searchQuery by remember { mutableStateOf("") }
    var activeSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    // The Material3 SearchBar uses an overlay while active. Once a search finishes, collapse
    // it so results, an error, or the empty state are visible instead of a blank surface.
    LaunchedEffect(uiState.searchResults.size, uiState.isLoading, uiState.error) {
        if (activeSearch && !uiState.isLoading &&
            (uiState.searchResults.isNotEmpty() ||
                uiState.error != null ||
                uiState.searchQuery.isNotBlank())
        ) {
            activeSearch = false
            keyboardController?.hide()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && !uiState.isLoadingMore && uiState.hasMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.searchResults.isNotEmpty()) {
            viewModel.onAction(ModelBrowserAction.LoadMore)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.ogrchatai.app.ui.viewmodel.ModelBrowserEvent.ShowSnackbar -> {}
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Model Browser",
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.deviceRamMb > 0) {
                            Text(
                                text = "Device RAM: ${uiState.deviceRamMb} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filters"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { query ->
                    searchQuery = query
                    viewModel.onAction(ModelBrowserAction.UpdateSearchQuery(query))
                },
                onSearch = {
                    viewModel.onAction(ModelBrowserAction.Search)
                    activeSearch = false
                },
                active = activeSearch,
                onActiveChange = { activeSearch = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text("Search GGUF models (e.g. Qwen, Gemma, Mistral...)")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.onAction(ModelBrowserAction.UpdateSearchQuery(""))
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {}

            AnimatedVisibility(
                visible = showFilters,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Architecture",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ARCHITECTURE_FILTERS.forEach { arch ->
                            val isSelected = uiState.filters.architecture == arch
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        viewModel.onAction(ModelBrowserAction.ClearFilters)
                                    } else {
                                        viewModel.onAction(
                                            ModelBrowserAction.UpdateFilter(
                                                uiState.filters.copy(architecture = arch)
                                            )
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = arch,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (activeDownloads.isNotEmpty()) {
                ActiveDownloadsCard(
                    downloads = activeDownloads,
                    onCancelAll = {
                        viewModel.onAction(ModelBrowserAction.CancelAllDownloads)
                    }
                )
            }

            if (uiState.searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "${uiState.searchResults.size} models found",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                uiState.isLoading && uiState.searchResults.isEmpty() -> {
                    LoadingIndicator(message = "Searching HuggingFace...")
                }
                uiState.error != null && uiState.searchResults.isEmpty() -> {
                    com.ogrchatai.app.ui.components.ErrorView(
                        message = uiState.error ?: "Unknown error",
                        onRetry = {
                            viewModel.onAction(ModelBrowserAction.Search)
                        }
                    )
                }
                uiState.searchResults.isEmpty() -> {
                    val trimmedQuery = uiState.searchQuery.trim()
                    EmptyStateView(
                        icon = Icons.Filled.Search,
                        title = if (trimmedQuery.isNotEmpty()) {
                            "No GGUF models found"
                        } else {
                            "Search for GGUF models"
                        },
                        subtitle = if (trimmedQuery.isNotEmpty()) {
                            "Nothing matched \"$trimmedQuery\". Try a shorter or different query."
                        } else {
                            "Enter a model name, author, or architecture."
                        }
                    )
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = {
                            viewModel.onAction(ModelBrowserAction.Search)
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = uiState.searchResults,
                                key = { it.id }
                            ) { model ->
                                ModelCard(
                                    model = model,
                                    downloadState = uiState.downloadState[model.id],
                                    isDownloaded = model.id in uiState.downloadedModelIds,
                                    deviceRamMb = uiState.deviceRamMb,
                                    onDownloadClick = {
                                        viewModel.onAction(
                                            ModelBrowserAction.DownloadModel(model.id)
                                        )
                                    },
                                    onCancelDownload = {
                                        viewModel.onAction(
                                            ModelBrowserAction.CancelDownload(model.id)
                                        )
                                    },
                                    onModelClick = {
                                    viewModel.onAction(ModelBrowserAction.DownloadModel(model.id))
                                }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadsCard(
    downloads: List<ModelDownloadState>,
    onCancelAll: () -> Unit
) {
    val first = downloads.first()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (downloads.size == 1) {
                            "Downloading ${first.modelName}"
                        } else {
                            "${downloads.size} models downloading"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(first.progress * 100).toInt()}%" +
                            if (downloads.size > 1) " • ${first.modelName} +${downloads.size - 1} more" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onCancelAll) {
                    Text("Cancel all")
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { first.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
