# Layout Widgets Documentation

## Overview

This document describes the Column, Row, and ListView widgets implemented for the Digia UI Compose framework. These widgets mirror the Flutter implementation and provide flexible layout and list rendering capabilities.

## Column Widget

Arranges child widgets vertically.

### Widget Type
`"digia/column"`

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mainAxisAlignment` | String | `"start"` | Vertical alignment of children |
| `crossAxisAlignment` | String | `"center"` | Horizontal alignment of children |
| `mainAxisSize` | String | `"max"` | How much vertical space to occupy |
| `spacing` | Number | `0` | Spacing between children (dp) |
| `startSpacing` | Number | `0` | Top padding (dp) |
| `endSpacing` | Number | `0` | Bottom padding (dp) |
| `dataSource` | Expression | - | Data array for dynamic children |
| `isScrollable` | Boolean | `false` | Enable vertical scrolling |

### Main Axis Alignment Values
- `"start"` - Align at top
- `"end"` - Align at bottom  
- `"center"` - Center vertically
- `"spaceBetween"` - Space evenly with no padding
- `"spaceAround"` - Space evenly with half padding at edges
- `"spaceEvenly"` - Space evenly with equal padding

### Cross Axis Alignment Values
- `"start"` - Align to start (left in LTR)
- `"end"` - Align to end (right in LTR)
- `"center"` - Center horizontally

### Example: Static Children

```json
{
  "type": "digia/column",
  "props": {
    "mainAxisAlignment": "center",
    "crossAxisAlignment": "center",
    "spacing": 16
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/text",
        "props": { "text": "Title" }
      },
      {
        "type": "digia/text",
        "props": { "text": "Subtitle" }
      }
    ]
  }
}
```

### Example: Dynamic Children with Data Source

```json
{
  "type": "digia/column",
  "props": {
    "dataSource": "@{users}",
    "spacing": 8,
    "isScrollable": true
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/text",
        "props": { "text": "@{currentItem.name}" }
      }
    ]
  }
}
```

In the data source example:
- The single child template is repeated for each item
- Variables available in child scope:
  - `currentItem` - The current data item
  - `index` - The current item index (0-based)

## Row Widget

Arranges child widgets horizontally.

### Widget Type
`"digia/row"`

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mainAxisAlignment` | String | `"start"` | Horizontal alignment of children |
| `crossAxisAlignment` | String | `"center"` | Vertical alignment of children |
| `mainAxisSize` | String | `"max"` | How much horizontal space to occupy |
| `spacing` | Number | `0` | Spacing between children (dp) |
| `startSpacing` | Number | `0` | Left padding (dp) |
| `endSpacing` | Number | `0` | Right padding (dp) |
| `dataSource` | Expression | - | Data array for dynamic children |
| `isScrollable` | Boolean | `false` | Enable horizontal scrolling |

### Main Axis Alignment Values
- `"start"` - Align to start (left in LTR)
- `"end"` - Align to end (right in LTR)
- `"center"` - Center horizontally
- `"spaceBetween"` - Space evenly with no padding
- `"spaceAround"` - Space evenly with half padding at edges
- `"spaceEvenly"` - Space evenly with equal padding

### Cross Axis Alignment Values
- `"top"` - Align to top
- `"bottom"` - Align to bottom
- `"center"` - Center vertically

### Example: Horizontal Layout

```json
{
  "type": "digia/row",
  "props": {
    "mainAxisAlignment": "spaceBetween",
    "crossAxisAlignment": "center"
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/text",
        "props": { "text": "Left" }
      },
      {
        "type": "digia/text",
        "props": { "text": "Right" }
      }
    ]
  }
}
```

## ListView Widget

Efficiently renders scrollable lists with lazy loading.

### Widget Type
`"digia/list-view"`

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dataSource` | Expression | **Required** | Data array to render |
| `scrollDirection` | String | `"vertical"` | Scroll direction |
| `reverse` | Boolean | `false` | Reverse item order |
| `shrinkWrap` | Boolean | `false` | Wrap content size |
| `allowScroll` | Boolean | `true` | Enable/disable scrolling |
| `initialScrollPosition` | String | - | Initial scroll position |

### Scroll Direction Values
- `"vertical"` - Vertical scrolling (LazyColumn)
- `"horizontal"` - Horizontal scrolling (LazyRow)

### Initial Scroll Position Values
- `"start"` - Scroll to beginning
- `"end"` - Scroll to end
- `"0"`, `"1"`, etc. - Scroll to specific index

### Example: Vertical List

```json
{
  "type": "digia/list-view",
  "props": {
    "dataSource": "@{items}",
    "scrollDirection": "vertical"
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/column",
        "props": { "spacing": 8 },
        "childGroups": {
          "children": [
            {
              "type": "digia/text",
              "props": { "text": "@{currentItem.title}" }
            },
            {
              "type": "digia/text",
              "props": { "text": "@{currentItem.description}" }
            }
          ]
        }
      }
    ]
  }
}
```

### Example: Horizontal List

```json
{
  "type": "digia/list-view",
  "props": {
    "dataSource": "@{images}",
    "scrollDirection": "horizontal",
    "shrinkWrap": true
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/image",
        "props": { "src": "@{currentItem.url}" }
      }
    ]
  }
}
```

## Expression Context

When using `dataSource` in Column, Row, or ListView, the child template has access to:

### Available Variables

- **`currentItem`**: The current item from the data source
  ```json
  { "text": "@{currentItem.name}" }
  ```

- **`index`**: Zero-based index of the current item
  ```json
  { "text": "Item @{index + 1}" }
  ```

### Named References

If the widget has a `refName`, you can access the context object:

```json
{
  "type": "digia/column",
  "refName": "userList",
  "props": {
    "dataSource": "@{users}"
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/text",
        "props": { "text": "@{userList.currentItem.name}" }
      }
    ]
  }
}
```

## Implementation Details

### VWFlex (Column & Row)

**File**: `VWFlex.kt`

#### Key Features:
- Supports both static children and dynamic data source rendering
- Optional scrolling via `SingleChildScrollView`
- Configurable spacing and alignment
- Proper expression context chaining for data items

#### Architecture:
```kotlin
class VWFlex(
    val props: FlexProps,
    val children: List<VirtualWidget>?
) : VirtualWidget()
```

#### Render Flow:
1. Check if data source exists (`shouldRepeatChild`)
2. If yes: Render repeating children with scoped context
3. If no: Render static children
4. Wrap in scroll view if `isScrollable` is true

### VWListView

**File**: `VWListView.kt`

#### Key Features:
- Uses Compose's `LazyColumn` and `LazyRow` for efficient rendering
- Lazy loading - only renders visible items
- Supports initial scroll position
- Bi-directional scrolling (vertical/horizontal)

#### Architecture:
```kotlin
class VWListView(
    val props: ListViewProps,
    val child: VirtualWidget?
) : VirtualWidget()
```

#### Render Flow:
1. Evaluate data source expression
2. Create LazyColumn or LazyRow based on scroll direction
3. Use `itemsIndexed` to render each item
4. Create scoped context with `currentItem` and `index`
5. Render child template with scoped payload

## Performance Considerations

### Column & Row
- **Static children**: All children rendered immediately
- **Data source**: All items rendered (not lazy)
- **Scrollable**: Wraps in `SingleChildScrollView`
- **Best for**: Small to medium lists (<100 items)

### ListView
- **Lazy loading**: Only visible items rendered
- **Efficient**: Recycles views as user scrolls
- **Memory efficient**: Handles large datasets
- **Best for**: Large lists (100+ items)

## Comparison with Flutter

| Feature | Flutter | Kotlin/Compose | Status |
|---------|---------|----------------|--------|
| Column | ✅ | ✅ | Complete |
| Row | ✅ | ✅ | Complete |
| ListView | ✅ | ✅ | Complete |
| Data Source | ✅ | ✅ | Complete |
| Spacing | ✅ | ✅ | Complete |
| Alignment | ✅ | ✅ | Complete |
| Scrolling | ✅ | ✅ | Complete |
| FlexFit/Expanded | ✅ | ⏳ | TODO |
| Flex Value | ✅ | ⏳ | TODO |

## Usage Examples

### Nested Layouts

```json
{
  "type": "digia/column",
  "props": {
    "mainAxisAlignment": "start",
    "spacing": 16
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/text",
        "props": { "text": "Header" }
      },
      {
        "type": "digia/row",
        "props": {
          "mainAxisAlignment": "spaceBetween"
        },
        "childGroups": {
          "children": [
            {
              "type": "digia/text",
              "props": { "text": "Left" }
            },
            {
              "type": "digia/text",
              "props": { "text": "Right" }
            }
          ]
        }
      }
    ]
  }
}
```

### List with Index

```json
{
  "type": "digia/list-view",
  "props": {
    "dataSource": "@{todos}"
  },
  "childGroups": {
    "children": [
      {
        "type": "digia/row",
        "props": { "spacing": 8 },
        "childGroups": {
          "children": [
            {
              "type": "digia/text",
              "props": { "text": "@{index + 1}." }
            },
            {
              "type": "digia/text",
              "props": { "text": "@{currentItem.title}" }
            }
          ]
        }
      }
    ]
  }
}
```

## Future Enhancements

1. **FlexFit/Expanded Support**: Allow children to expand and flex within layout
2. **Flex Value**: Control relative sizing of flexible children
3. **SliverList**: More advanced scrolling capabilities
4. **Grid Layout**: GridView widget for 2D layouts
5. **Custom Scroll Controllers**: Programmatic scroll control
6. **Pull to Refresh**: Built-in refresh functionality

## Testing Recommendations

1. **Static Layouts**: Test various alignment and spacing combinations
2. **Data Sources**: Test with empty, single, and multiple items
3. **Scrolling**: Test scroll behavior with overflow content
4. **Nested Layouts**: Test Column inside Row and vice versa
5. **Expression Context**: Verify `currentItem` and `index` accessibility
6. **Performance**: Test ListView with 1000+ items

## Troubleshooting

### Children not rendering
- Check `childGroups.children` structure
- Verify widget type is registered
- Check data source expression returns array

### Alignment not working
- Verify alignment string values match documentation
- Check if `mainAxisSize` affects layout
- Consider parent constraints

### ListView not scrolling
- Ensure `dataSource` returns array
- Check parent doesn't restrict height/width
- Verify `allowScroll` is not set to false

### Expression variables not available
- Confirm using data source mode
- Check expression syntax `@{currentItem.field}`
- Verify field exists in data items
