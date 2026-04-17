

## [1.0.0-beta.4] - 2026-04-17

### Added
- Inline's Event support for `DigiaSlot`


## [1.0.0-beta.1] - 2024-12-30

### Added
- Initial release of Digia UI Compose SDK
- Core initialization and configuration loading
- Network client with OkHttp
- Config resolver with multiple source strategies (network, asset, cached)
- Flavor support (Dashboard, Asset, AssetWithUpdate, NetworkFile)
- Environment support (Development, Production, Custom)
- Developer config for debugging (proxy, inspector)
- Preferences store for local data persistence
- State management foundation
- Basic widget system (VWScaffold, VWText)
- Action executor framework
- Validation manager
- Page manager
- Logger utility
- Functional utilities (maybe, asSafe, asType)
- File operations (read, write, delete)
- Asset bundle operations
- Download operations with retry logic
- JSFunctions placeholder for future JS integration

### Infrastructure
- Maven publishing support
- GitHub Actions CI/CD
- Dependabot configuration
- Version catalog for dependencies
- Template app for testing
- Comprehensive documentation

### Documentation
- README.md with setup instructions
- PUBLISHING.md with publishing guide
- USING_AS_PACKAGE.md for consumption
- CONFIG_PROVIDER_OPERATIONS.md
- WIDGET_GUIDE.md
- API documentation in code