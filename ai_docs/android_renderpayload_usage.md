# RenderPayload Usage Guide

## Overview

`RenderPayload` is a central data class used throughout the Digia UI framework for passing context and resources during widget rendering. It's analogous to Flutter's `BuildContext` but includes additional features specific to the Digia framework.

## Structure

```kotlin
data class RenderPayload(
    val context: Context,              // Android Context
    val scopeContext: ScopeContext?,   // Expression evaluation context with variables
    val resources: UIResources,        // Colors, fonts, API models, etc.
    val widgetHierarchy: List<String>, // Widget path for debugging/observability
    val currentEntityId: String?       // Current component/page ID
)
```

## Key Features

### 1. Expression Evaluation

RenderPayload provides multiple methods for evaluating expressions:

#### `evalExpr<T>()` - For ExprOr wrapped values
```kotlin
// In your VirtualWidget's Render method
@Composable
override fun Render(payload: RenderPayload) {
    val text = payload.evalExpr(props.text) ?: ""
    val maxLines = payload.evalExpr(props.maxLines)
    // Use evaluated values...
}
```

#### `eval<T>()` - For raw expressions
```kotlin
val dynamicValue = payload.eval<String>(
    expression = someExpression,
    scopeContext = additionalContext,  // Optional: chain additional context
    decoder = { rawValue ->             // Optional: custom type conversion
        rawValue.toString()
    }
)
```

### 2. Resource Access

#### Colors
```kotlin
@Composable
fun MyWidget(payload: RenderPayload) {
    // Get color by key (automatically handles dark/light theme)
    val primaryColor = payload.getColor("primary")
    
    // Evaluate color expression
    val dynamicColor = payload.evalColor(colorExpression)
    
    // Evaluate ExprOr color
    val wrappedColor = payload.evalColorExpr(props.color)
}
```

#### Text Styles
```kotlin
@Composable
fun MyWidget(payload: RenderPayload) {
    val style = payload.getTextStyle(
        token = styleToken,
        fallback = TextStyle.Default  // Optional fallback
    )
}
```

#### Fonts
```kotlin
val fontFactory = payload.getFontFactory()
// Use fontFactory to create custom fonts
```

#### API Models
```kotlin
val apiModel = payload.getApiModel("user-api")
// Use apiModel for network requests
```

### 3. Context Management

#### Extending Widget Hierarchy
```kotlin
// When rendering child widgets, extend the hierarchy for debugging
@Composable
override fun Render(payload: RenderPayload) {
    val childPayload = payload.withExtendedHierarchy("MyWidgetName")
    
    children.forEach { child ->
        child.ToWidget(childPayload)
    }
}
```

#### Component Context
```kotlin
// When entering a component, update the entity ID
val componentPayload = payload.forComponent(componentId = "UserCard")
componentWidget.ToWidget(componentPayload)
```

#### Chaining Scope Context
```kotlin
// Add additional variables to the scope
val newScope = DefaultScopeContext(
    variables = mapOf("index" to 0, "item" to currentItem)
)

val childPayload = payload.copyWithChainedContext(
    scopeContext = newScope,
    context = updatedAndroidContext  // Optional
)
```

#### Manual Copy
```kotlin
val updatedPayload = payload.copyWith(
    context = newAndroidContext,      // Optional
    scopeContext = newScopeContext,   // Optional
    widgetHierarchy = newHierarchy,   // Optional
    currentEntityId = newEntityId     // Optional
)
```

## Complete Widget Example

```kotlin
/** Text widget properties */
data class TextProps(
    val text: ExprOr<String>?,
    val textStyle: JsonLike? = null,
    val maxLines: ExprOr<Int>? = null,
    val alignment: ExprOr<String>? = null,
    val color: ExprOr<String>? = null
)

/** Virtual Text widget */
class VWText(
    override val refName: String?,
    override val commonProps: CommonProps?,
    val props: TextProps
) : VirtualLeafWidget() {

    @Composable
    override fun Render(payload: RenderPayload) {
        // 1. Evaluate expressions
        val text = payload.evalExpr(props.text) ?: ""
        val maxLines = payload.evalExpr(props.maxLines)
        val alignmentStr = payload.evalExpr(props.alignment)
        
        // 2. Get style from resources
        val style = payload.getTextStyle(props.textStyle)
        
        // 3. Get color if specified
        val textColor = payload.evalColorExpr(props.color)
        
        // 4. Convert string values to Compose types
        val textAlign = when (alignmentStr) {
            "center" -> TextAlign.Center
            "left" -> TextAlign.Left
            "right" -> TextAlign.Right
            else -> null
        }
        
        // 5. Render Material3 Text
        Text(
            text = text,
            style = style?.copy(color = textColor ?: style.color) 
                ?: TextStyle.Default,
            maxLines = maxLines ?: Int.MAX_VALUE,
            textAlign = textAlign
        )
    }
}
```

## Usage in Pages

```kotlin
@Composable
fun DUIPage(
    pageId: String,
    pageArgs: Map<String, Any?>?,
    pageDef: PageDefinition,
    registry: VirtualWidgetRegistry
) {
    val context = LocalContext.current
    val resources = LocalUIResources.current

    // Create scope context with page arguments and state
    val scopeContext = DefaultScopeContext(
        variables = resolvedPageArgs + resolvedState
    )

    // Get root widget
    val rootNode = pageDef.layout?.root ?: return
    val virtualWidget = registry.createWidget(rootNode)

    // Create render payload
    val payload = RenderPayload(
        context = context,
        scopeContext = scopeContext,
        resources = resources,
        widgetHierarchy = listOf(pageId),
        currentEntityId = pageId
    )

    // Render the widget tree
    virtualWidget.ToWidget(payload)
}
```

## Best Practices

### 1. Always Evaluate Expressions in Render Methods
```kotlin
// ✅ Good - Evaluate in @Composable Render method
@Composable
override fun Render(payload: RenderPayload) {
    val text = payload.evalExpr(props.text) ?: ""
    Text(text = text)
}

// ❌ Bad - Don't evaluate in constructor or init
class VWText(...) : VirtualWidget() {
    val text = payload.evalExpr(props.text) // Wrong! No payload here
}
```

### 2. Extend Hierarchy for Parent Widgets
```kotlin
// For container/parent widgets
@Composable
override fun Render(payload: RenderPayload) {
    val childPayload = payload.withExtendedHierarchy("Column")
    
    Column {
        children.forEach { child ->
            child.ToWidget(childPayload)
        }
    }
}
```

### 3. Use forComponent for Component Boundaries
```kotlin
// When rendering a component (not a regular widget)
val componentPayload = payload.forComponent(componentId)
componentWidget.ToWidget(componentPayload)
```

### 4. Chain Context for Local Variables
```kotlin
// When introducing new variables (e.g., in loops)
items.forEachIndexed { index, item ->
    val itemScope = DefaultScopeContext(
        variables = mapOf("index" to index, "item" to item)
    )
    val itemPayload = payload.copyWithChainedContext(itemScope)
    
    childWidget.ToWidget(itemPayload)
}
```

### 5. Handle Null Safely
```kotlin
// Always provide fallbacks for optional values
val text = payload.evalExpr(props.text) ?: ""
val maxLines = payload.evalExpr(props.maxLines) ?: Int.MAX_VALUE
val style = payload.getTextStyle(props.textStyle) ?: TextStyle.Default
```

## Common Patterns

### Pattern 1: Conditional Rendering
```kotlin
@Composable
override fun Render(payload: RenderPayload) {
    val showContent = payload.evalExpr(props.visible) ?: true
    
    if (showContent) {
        // Render content
    }
}
```

### Pattern 2: Dynamic Styling
```kotlin
@Composable
override fun Render(payload: RenderPayload) {
    val baseStyle = payload.getTextStyle(props.textStyle)
    val textColor = payload.evalColorExpr(props.color)
    
    val finalStyle = baseStyle?.copy(
        color = textColor ?: baseStyle.color
    ) ?: TextStyle(color = textColor ?: Color.Black)
}
```

### Pattern 3: List Rendering with Scoped Variables
```kotlin
@Composable
override fun Render(payload: RenderPayload) {
    val items = payload.eval<List<Any>>(props.items) ?: emptyList()
    
    Column {
        items.forEachIndexed { index, item ->
            val itemScope = DefaultScopeContext(
                variables = mapOf(
                    "\$index" to index,
                    "\$item" to item
                )
            )
            val itemPayload = payload.copyWithChainedContext(itemScope)
            
            itemTemplate?.ToWidget(itemPayload)
        }
    }
}
```

## Debugging Tips

1. **Check Widget Hierarchy**: Use `payload.widgetHierarchy` to see the rendering path
2. **Inspect Variables**: Access `payload.scopeContext` to see available variables
3. **Verify Resources**: Check `payload.resources` for colors, fonts, and API models
4. **Entity Tracking**: Use `payload.currentEntityId` to track component/page context

## Migration Notes

### From Flutter to Kotlin

| Flutter | Kotlin/Android |
|---------|----------------|
| `BuildContext` | `Context` (Android) |
| `ScopeContext` variables | Same concept |
| `ResourceProvider.of(context)` | `payload.resources` |
| `theme.colors` | `payload.getColor(key)` |
| `executeAction()` | `payload.executeAction()` (TODO) |

### Key Differences

1. **Composable Annotations**: Methods accessing theme (like `getColor`, `getTextStyle`) must be marked `@Composable`
2. **Null Safety**: Kotlin's null safety requires explicit handling with `?` and `?:`
3. **No BuildContext**: Use `LocalContext.current` to get Android Context in Compose
4. **Expression Evaluation**: Already implemented via `Expression.eval()` from digiaexpr library

## Future Enhancements

The following features are planned but not yet implemented:

1. **Action Execution**: Full implementation of `executeAction()` with observability
2. **Icon Support**: Implementation of `getIcon()` method
3. **Observability Context**: Integration with debugging/inspector tools
4. **Deep Expression Evaluation**: Recursive evaluation of nested expressions

## See Also

- [VirtualWidget.kt](digia-ui/src/main/java/com/digia/digiaui/framework/base/VirtualWidget.kt) - Base widget class
- [ExprOr.kt](digia-ui/src/main/java/com/digia/digiaui/framework/models/Types.kt) - Expression wrapper
- [ScopeContext.kt](digia-ui/src/main/java/com/digia/digiaui/framework/expr/ScopeContext.kt) - Expression context
- [UIResources.kt](digia-ui/src/main/java/com/digia/digiaui/framework/UIResources.kt) - Resource definitions
- [VWText.kt](digia-ui/src/main/java/com/digia/digiaui/framework/widgets/VWText.kt) - Example widget implementation
