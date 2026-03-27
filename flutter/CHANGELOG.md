# Changelog

## [1.1.0] - 2026-03-27

### Changed

- Improved payload handling: inline campaigns now use `type: inline` + `placementKey` instead of `command: SHOW_INLINE` + `placementId`
- Payloads missing both `type` and `command` are now dropped with a warning instead of silently ignored
- `DigiaSlot` lookup updated to use `placementKey`

## [1.0.0] - 2026-03-18

### Added

- Initial stable release of the Digia Engage SDK

## [1.0.0-beta.2] - 2026-03-11

### Added

- Inline support for `DigiaSlot`

## [1.0.0-beta.1] - 2026-03-11

### Added

- Initial beta release
- UI framework for custom UI from engagement platform
- Public API exports for easy integration
