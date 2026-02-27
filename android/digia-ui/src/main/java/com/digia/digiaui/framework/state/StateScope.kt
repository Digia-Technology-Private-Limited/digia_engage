package com.digia.digiaui.framework.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digia.digiaui.framework.navigation.NavigationManager

val LocalStateTree = compositionLocalOf { StateTree() }

@Composable
fun StateScope(
        namespace: String?,
        initialState: Map<String, Any?> = emptyMap(),
        content: @Composable (stateContext: StateContext) -> Unit
) {
    // Get or create the StateViewModel (survives configuration changes and navigation)
    val stateViewModel: StateViewModel = viewModel()

    // Get StateTree from ViewModel
    val tree = stateViewModel.stateTree

    // Parent is the nearest enclosing StateScope (if any)
    val parentStateContext = LocalStateContextProvider.current

    // Get or create StateContext from ViewModel (persists across navigation)
    val stateContext = stateViewModel.getOrCreateStateContext(namespace, initialState)

    // Maintain the parent-child relationship in the shared StateTree.
    // Required for resolving owners across scopes and for observe()/flush propagation.
    DisposableEffect(tree, parentStateContext, stateContext, namespace) {
        tree.attach(parentStateContext, stateContext)
        onDispose {
            // Walk up the tree to check if this context or any ancestor page is in the backstack.
            // This covers both page-level and internal component StateScopeS inside a pushed page.
            val isUnderBackstack = generateSequence(stateContext) { tree.parentOf(it) }
                .any { ctx -> ctx.namespace != null && NavigationManager.isPageInBackstack(ctx.namespace) }

            if (!isUnderBackstack) {
                tree.detach(stateContext)
            }
        }
    }

    CompositionLocalProvider(
            LocalStateTree provides tree,
            LocalStateContextProvider provides stateContext,
    ) {
        // Read version to trigger recomposition
        stateContext.Version()
        content(stateContext)
    }
}
