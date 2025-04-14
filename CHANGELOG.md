# Change log

All notable changes to the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

## [1.1.0](https://github.com/launchdarkly/openfeature-java-server/compare/1.0.1...1.1.0) (2025-04-14)


### Features

* add tracking support ([#36](https://github.com/launchdarkly/openfeature-java-server/issues/36)) ([13ad91f](https://github.com/launchdarkly/openfeature-java-server/commit/13ad91ffa2622c21fb6e84a22f152697ba3f0d8f))
* support tracking ([fa69598](https://github.com/launchdarkly/openfeature-java-server/commit/fa695985956f295a47f7a0f9a9a91ac2352bbf93))

## [1.0.1](https://github.com/launchdarkly/openfeature-java-server/compare/1.0.0...1.0.1) (2024-09-16)


### Bug Fixes

* Remove unused dependencies. ([#34](https://github.com/launchdarkly/openfeature-java-server/issues/34)) ([e6cff17](https://github.com/launchdarkly/openfeature-java-server/commit/e6cff17639fb560b1871a7be59ab8d2b7d204b0a))

## [1.0.0](https://github.com/launchdarkly/openfeature-java-server/compare/0.2.3...1.0.0) (2024-06-07)


### ⚠ BREAKING CHANGES

* 1.0.0 release ([#29](https://github.com/launchdarkly/openfeature-java-server/issues/29))

### Features

* 1.0.0 release ([#29](https://github.com/launchdarkly/openfeature-java-server/issues/29)) ([bf51e20](https://github.com/launchdarkly/openfeature-java-server/commit/bf51e201dd48603a40ffcdbc72753751d70b3a5a))

## [0.2.3](https://github.com/launchdarkly/openfeature-java-server/compare/0.2.2...0.2.3) (2024-06-06)


### Bug Fixes

* Improve client initialization handling. ([#27](https://github.com/launchdarkly/openfeature-java-server/issues/27)) ([cf0ed18](https://github.com/launchdarkly/openfeature-java-server/commit/cf0ed18a91a331f0c686501b65af74832f759f34))

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
