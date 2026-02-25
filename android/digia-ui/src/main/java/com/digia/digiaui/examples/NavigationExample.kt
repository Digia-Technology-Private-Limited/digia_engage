//package com.digia.digiaui.examples
//
//import androidx.compose.runtime.Composable
//import com.digia.digiaui.app.DigiaUIApp
//import com.digia.digiaui.framework.DUIFactory
//import com.digia.digiaui.framework.analytics.DUIAnalytics
//import com.digia.digiaui.init.DigiaUI
//
///**
// * Example: Using Navigation in Digia UI
// *
// * This file demonstrates how to set up navigation in your Digia UI application.
// */
//
///**
// * Example 1: Basic Navigation Setup
// *
// * The simplest way to use navigation - just call CreateNavHost()
// */
//@Composable
//fun BasicNavigationExample(digiaUI: DigiaUI) {
//    DigiaUIApp(digiaUI = digiaUI) {
//        // Creates NavHost with all pages from config
//        // Starts with the initialRoute defined in config
//        DUIFactory.getInstance().CreateNavHost()
//    }
//}
//
///**
// * Example 2: Custom Start Page
// *
// * Override the initial route to start with a specific page
// */
//@Composable
//fun CustomStartPageExample(digiaUI: DigiaUI) {
//    DigiaUIApp(digiaUI = digiaUI) {
//        // Start with onboarding page instead of default
//        DUIFactory.getInstance().CreateNavHost(
//            startPageId = "onboarding"
//        )
//    }
//}
//
///**
// * Example 3: Navigation with Analytics
// *
// * Track navigation events with analytics
// */
//@Composable
//fun NavigationWithAnalyticsExample(
//    digiaUI: DigiaUI,
//    analytics: DUIAnalytics
//) {
//    DigiaUIApp(
//        digiaUI = digiaUI,
//        analytics = analytics
//    ) {
//        DUIFactory.getInstance().CreateNavHost()
//    }
//}
//
///**
// * Example 4: Navigation with Resource Overrides
// *
// * Customize icons, colors, and styles for the navigation flow
// */
//@Composable
//fun NavigationWithResourcesExample(
//    digiaUI: DigiaUI,
//    customIcons: Map<String, androidx.compose.ui.graphics.vector.ImageVector>,
//    customColors: Map<String, androidx.compose.ui.graphics.Color>
//) {
//    DigiaUIApp(digiaUI = digiaUI) {
//        DUIFactory.getInstance().CreateNavHost(
//            overrideIcons = customIcons,
//            overrideColors = customColors
//        )
//    }
//}
//
///**
// * Example JSON Configuration for Navigation
// *
// * Define pages with navigation actions in your DUIConfig:
// *
// * {
// *   "pages": {
// *     "home": {
// *       "layout": {
// *         "root": {
// *           "type": "digia/column",
// *           "childGroups": {
// *             "children": [
// *               {
// *                 "type": "digia/text",
// *                 "props": { "text": "Home Page" }
// *               },
// *               {
// *                 "type": "digia/button",
// *                 "props": {
// *                   "text": "Go to Profile",
// *                   "onClick": {
// *                     "steps": [
// *                       {
// *                         "type": "Action.navigateToPage",
// *                         "data": {
// *                           "pageData": {
// *                             "id": "profile",
// *                             "args": {
// *                               "userId": 123,
// *                               "userName": "John Doe"
// *                             }
// *                           }
// *                         }
// *                       }
// *                     ]
// *                   }
// *                 }
// *               }
// *             ]
// *           }
// *         }
// *       }
// *     },
// *     "profile": {
// *       "pageArgDefs": {
// *         "userId": { "name": "userId", "type": "int", "defaultValue": 0 },
// *         "userName": { "name": "userName", "type": "string", "defaultValue": "" }
// *       },
// *       "layout": {
// *         "root": {
// *           "type": "digia/column",
// *           "childGroups": {
// *             "children": [
// *               {
// *                 "type": "digia/text",
// *                 "props": { "text": "Profile: ${userName}" }
// *               },
// *               {
// *                 "type": "digia/text",
// *                 "props": { "text": "User ID: ${userId}" }
// *               },
// *               {
// *                 "type": "digia/button",
// *                 "props": {
// *                   "text": "Back",
// *                   "onClick": {
// *                     "steps": [
// *                       {
// *                         "type": "Action.pop"
// *                       }
// *                     ]
// *                   }
// *                 }
// *               }
// *             ]
// *           }
// *         }
// *       }
// *     }
// *   }
// * }
// */
//
///**
// * Example 5: Programmatic Navigation
// *
// * Trigger navigation from Kotlin code
// */
//fun navigateProgrammatically() {
//    // Navigate to a page
//    com.digia.digiaui.framework.navigation.NavigationManager.navigate(
//        pageId = "details",
//        args = mapOf("id" to 123, "name" to "Product")
//    )
//
//    // Go back
//    com.digia.digiaui.framework.navigation.NavigationManager.pop()
//
//    // Navigate with replace (useful for login flows)
//    com.digia.digiaui.framework.navigation.NavigationManager.navigate(
//        pageId = "dashboard",
//        args = null,
//        replace = true
//    )
//}
