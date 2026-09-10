package com.ogrchatai.app.ui.screen.modelbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ogrchatai.app.ui.components.EmptyStateView
import com.ogrchatai.app.ui.components.LoadingIndicator
import com.ogrchatai.app.ui.components.ModelChip
import com.ogrchatai.app.ui.viewmodel.ModelBrowserAction
import com.ogrchatai.app.ui.viewmodel.ModelBrowserViewModel

private val QUANTIZATION_FILTERS = listOf(
    "Q4_K_M",
    "Q5_K_M",
    "Q8_0",
    "F16",
    "F32"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelBrowserScreen(
    onModelClick: (String) -> Unit,
    onBackClick: () -> Unit,
    viewModel: ModelBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var activeSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Model Browser",
                        fontWeight = FontWeight.Bold
                    )
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
                },
                onSearch = {
                    viewModel.onAction(ModelBrowserAction.UpdateSearchQuery(searchQuery))
                    viewModel.onAction(ModelBrowserAction.Search)
                    activeSearch = false
                },
                active = activeSearch,
                onActiveChange = { activeSearch = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text("Search models...")
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
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QUANTIZATION_FILTERS.forEach { quant ->
                        val isSelected = uiState.filters.quantization == quant
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    viewModel.onAction(ModelBrowserAction.ClearFilters)
                                } else {
                                    viewModel.onAction(
                                        ModelBrowserAction.UpdateFilter(
                                            uiState.filters.copy(quantization = quant)
                                        )
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = quant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            when {
                uiState.isLoading && uiState.searchResults.isEmpty() -> {
                    LoadingIndicator(message = "Searching models...")
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
                    EmptyStateView(
                        icon = Icons.Filled.History,
                        title = "Search for models",
                        subtitle = "Find GGUF models from HuggingFace"
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
                                    onDownloadClick = {
                                        viewModel.onAction(
                                            ModelBrowserAction.DownloadModel(model.id)
                                        )
                                    },
                                    onModelClick = { onModelClick(model.id) }
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
