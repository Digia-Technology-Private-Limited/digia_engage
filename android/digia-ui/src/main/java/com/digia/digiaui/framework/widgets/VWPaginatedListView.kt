package com.digia.digiaui.framework.widgets

import LocalApiModels
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import com.digia.digiaui.framework.RenderPayload
import com.digia.digiaui.framework.VirtualWidgetRegistry
import com.digia.digiaui.framework.base.VirtualCompositeNode
import com.digia.digiaui.framework.base.VirtualNode
import com.digia.digiaui.framework.expr.DefaultScopeContext
import com.digia.digiaui.framework.models.CommonProps
import com.digia.digiaui.framework.models.ExprOr
import com.digia.digiaui.framework.models.Props
import com.digia.digiaui.framework.models.VWNodeData
import com.digia.digiaui.framework.registerAllChildern
import com.digia.digiaui.framework.utils.JsonLike
import com.digia.digiaui.framework.utils.executeApiAction
import com.digia.digiaui.network.APIModel

/** PaginatedListView widget properties */
data class PaginatedListViewProps(
        val initialScrollPosition: ExprOr<String>? = null,
        val reverse: ExprOr<Boolean>? = null,
        val apiId: String? = null,
        val args: Map<String, ExprOr<Any>?>? = null,
        val transformItems: ExprOr<List<Any>>? = null,
        val firstPageKey: ExprOr<Any>? = null,
        val nextPageKey: ExprOr<Any>? = null,
        val apiDataSource: ExprOr<Any>? = null,
        val dataSource: ExprOr<Any>? = null
) {
    companion object {
        fun fromJson(json: JsonLike): PaginatedListViewProps {
            val apiDataSource = json["apiDataSource"] as? Map<*, *>
            return PaginatedListViewProps(
                    initialScrollPosition = ExprOr.fromJson(json["initialScrollPosition"]),
                    reverse = ExprOr.fromJson(json["reverse"]),
                    apiId = apiDataSource?.get("id") as? String,
                    args =
                            (apiDataSource?.get("args") as? Map<String, Any?>)?.mapValues {
                                ExprOr.fromJson<Any>(it.value)
                            },
                    transformItems = ExprOr.fromJson(json["transformItems"]),
                    firstPageKey = ExprOr.fromJson(json["firstPageKey"]),
                    nextPageKey = ExprOr.fromJson(json["nextPageKey"]),
                    apiDataSource = ExprOr.fromJson(json["apiDataSource"]),
                    dataSource = ExprOr.fromJson(json["dataSource"])
            )
        }
    }
}

/** Virtual PaginatedListView Widget */
class VWPaginatedListView(
        refName: String? = null,
        commonProps: CommonProps? = null,
        props: PaginatedListViewProps,
        parent: VirtualNode? = null,
        slots:
                ((VirtualCompositeNode<PaginatedListViewProps>) -> Map<
                                String, List<VirtualNode>>?)? =
                null,
        parentProps: Props? = null
) :
        VirtualCompositeNode<PaginatedListViewProps>(
                props = props,
                commonProps = commonProps,
                parentProps = parentProps,
                parent = parent,
                refName = refName,
                _slots = slots
        ) {

    @Composable
    override fun Render(payload: RenderPayload) {
        if (child == null) {
            Empty()
            return
        }

        val firstPageLoadingWidget = slot("firstPageLoadingWidget")
        val newPageLoadingWidget = slot("newPageLoadingWidget")

        val isReverse = payload.evalExpr(props.reverse) ?: false

        // Get API models from CompositionLocal
        val apiModels = LocalApiModels.current

        // Create pager with remembered paging source factory
        val pager =
                remember(props, payload.scopeContext, apiModels) {
                    Pager(
                            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                            pagingSourceFactory = {
                                GenericPagingSource(
                                        payload = payload,
                                        props = props,
                                        apiModels = apiModels
                                )
                            }
                    )
                }

        val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

        val listModifier = Modifier.fillMaxWidth()

        LazyColumn(modifier = listModifier, reverseLayout = isReverse) {
            // First Page Loading
            if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
                item { firstPageLoadingWidget?.ToWidget(payload) }
            }

            // Items
            items(count = lazyPagingItems.itemCount) { index ->
                val item = lazyPagingItems[index]
                if (item != null) {
                    val scope = createExprContext(item, index)
                    child?.ToWidget(payload.copyWithChainedContext(scope))
                }
            }

            // Append Loading
            if (lazyPagingItems.loadState.append is LoadState.Loading) {
                item { newPageLoadingWidget?.ToWidget(payload) }
            }
        }
    }

    private fun createExprContext(item: Any?, index: Int): DefaultScopeContext {
        val listObj = mapOf("currentItem" to item, "index" to index)
        return DefaultScopeContext(variables = listObj)
    }

    class GenericPagingSource(
            private val payload: RenderPayload,
            private val props: PaginatedListViewProps,
            private val apiModels: Map<String, APIModel>
    ) : PagingSource<Any, Any>() {

        override fun getRefreshKey(state: PagingState<Any, Any>): Any? {
            return null
        }

        override suspend fun load(params: LoadParams<Any>): LoadResult<Any, Any> {
            try {
                // Determine Page Key
                val key = params.key ?: payload.evalExpr(props.firstPageKey)

                // If no key and firstPageKey is null, check datasource
                if (key == null && params.key == null) {
                    val localItems = payload.eval<List<Any>>(props.dataSource)
                    if (!localItems.isNullOrEmpty()) {
                        return LoadResult.Page(data = localItems, prevKey = null, nextKey = null)
                    }
                }

                val apiId = props.apiId
                if (apiId == null) {
                    val localItems = payload.eval<List<Any>>(props.dataSource) ?: emptyList()
                    return LoadResult.Page(
                            data = if (params.key == null) localItems else emptyList(),
                            prevKey = null,
                            nextKey = null
                    )
                }

                val apiModel =
                        apiModels[apiId]
                                ?: return LoadResult.Error(Exception("API not found: $apiId"))

                val scope =
                        DefaultScopeContext(
                                variables = mapOf("pageKey" to key),
                                enclosing = payload.scopeContext
                        )

                var result: LoadResult<Any, Any> = LoadResult.Error(Exception("No result from API"))

                executeApiAction(
                        scopeContext = scope,
                        apiModel = apiModel,
                        args = props.args,
                        onSuccess = { respObj ->
                            val transformedItems =
                                    props.transformItems?.evaluate<List<Any>>(
                                            DefaultScopeContext(
                                                    variables = mapOf("response" to respObj),
                                                    enclosing = scope
                                            )
                                    )

                            val newItems =
                                    transformedItems
                                            ?: (respObj["body"] as? List<*>)?.filterNotNull()
                                                    ?: emptyList()

                            val nextPageKey =
                                    props.nextPageKey?.evaluate<Any>(
                                            DefaultScopeContext(
                                                    variables = mapOf("response" to respObj),
                                                    enclosing = scope
                                            )
                                    )

                            val nextKey =
                                    if (nextPageKey == null ||
                                                    nextPageKey == "" ||
                                                    (newItems.isEmpty() && nextPageKey == key)
                                    )
                                            null
                                    else nextPageKey

                            result =
                                    LoadResult.Page(
                                            data = newItems,
                                            prevKey = null,
                                            nextKey = nextKey
                                    )
                            return@executeApiAction null
                        },
                        onError = { respObj ->
                            val msg = respObj["error"] as? String ?: "Unknown Error"
                            result = LoadResult.Error(Exception(msg))
                            return@executeApiAction null
                        }
                )

                return result
            } catch (e: Exception) {
                return LoadResult.Error(e)
            }
        }
    }
}

/** Builder function for PaginatedListView widget */
fun paginatedListViewBuilder(
        data: VWNodeData,
        parent: VirtualNode?,
        registry: VirtualWidgetRegistry
): VirtualNode {
    return VWPaginatedListView(
            refName = data.refName,
            commonProps = data.commonProps,
            parent = parent,
            parentProps = data.parentProps,
            props = PaginatedListViewProps.fromJson(data.props.value),
            slots = { self -> registerAllChildern(data.childGroups, self, registry) },
    )
}
