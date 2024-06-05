# Change log

All notable changes to the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [0.2.2](https://github.com/launchdarkly/openfeature-java-server/compare/0.2.1...0.2.2) (2024-06-05)


### Bug Fixes

* Include java version for build when publishing. ([#23](https://github.com/launchdarkly/openfeature-java-server/issues/23)) ([d5880fe](https://github.com/launchdarkly/openfeature-java-server/commit/d5880fe0485075dbafef98b098c1022962bf5e49))

## [0.2.1](https://github.com/launchdarkly/openfeature-java-server/compare/0.2.0...0.2.1) (2024-06-05)


### Bug Fixes

* Handle missing targeting key in updated OpenFeature SDK. ([#21](https://github.com/launchdarkly/openfeature-java-server/issues/21)) ([db83218](https://github.com/launchdarkly/openfeature-java-server/commit/db8321827b4a9a603279c9561ede6b32fd571d0f))

## [0.2.0] - 2023-11-02
This version contains breaking changes. You will need to update your LaunchDarkly SDK version as well as the OpenFeature SDK version.

Additionally this version changes how the provider is constructed. For an example please refer to the README.md.

The LDClient is now constructed by the provider, and if you need to access it, then you can use the `getLdClient` method.

### Changed:
- Updated to support the latest LaunchDarkly SDK as well as supporting initialization, shutdown, and eventing specification changes.

## [0.1.0] - 2023-02-24
Initial beta release of the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java.

## [0.1.0] - 2023-02-24
Initial beta release of the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java.
