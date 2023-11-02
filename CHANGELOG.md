# Change log

All notable changes to the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java will be documented in this file. This project adheres to [Semantic Versioning](http://semver.org).

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
