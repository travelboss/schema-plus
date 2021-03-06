# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [1.0.5] - 2019-05-29
### Fixed
- Correctly refer records in clojurescript

## [1.0.4] - 2019-05-26
### Fixed
- Handle constrained schema maps.

## [1.0.3] - 2019-05-19
### Fixed
- Don't eval schema forms in clojurescript.

## [1.0.2] - 2019-05-17
### Fixed
- Schemas with non specific keys would cause compilation errors.

## [1.0.1] - 2019-05-17
### Fixed
- Previously, only literal schema forms would have builders created.

## [1.0.0] - 2019-04-12
### Added
- Add convenience macro based on schema name
### Removed
- Remove existing convenience macros +-> and +generate->

## [0.1.1] - 2019-04-05
### Fixed
- Fix clojurescript compatibility
- Avoid double-def'ing schemas

## 0.1.0 - 2019-04-05
### Added
- Initial implementation, including defschema+, get-generator, set-generator, +->, and +generate->


[Unreleased]: https://github.com/travelboss/schema-plus/compare/HEAD..1.0.4
[1.0.5]: https://github.com/travelboss/schema-plus/compare/1.0.4..1.0.5
[1.0.4]: https://github.com/travelboss/schema-plus/compare/1.0.3..1.0.4
[1.0.3]: https://github.com/travelboss/schema-plus/compare/1.0.2..1.0.3
[1.0.2]: https://github.com/travelboss/schema-plus/compare/1.0.1..1.0.2
[1.0.1]: https://github.com/travelboss/schema-plus/compare/1.0.0..1.0.1
[1.0.0]: https://github.com/travelboss/schema-plus/compare/0.1.1..1.0.0
[0.1.1]: https://github.com/travelboss/schema-plus/compare/0.1.0..0.1.1
