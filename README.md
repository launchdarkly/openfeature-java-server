# LaunchDarkly OpenFeature provider for the Server-Side SDK for Java

This provider allows for using LaunchDarkly with the OpenFeature SDK for Java.

This provider is designed primarily for use in multi-user systems such as web servers and applications. It follows the server-side LaunchDarkly model for multi-user contexts. It is not intended for use in desktop and embedded systems applications.

# LaunchDarkly overview

[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves trillions of feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/home/getting-started) using LaunchDarkly today!

[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

## Supported Java versions

This version of the LaunchDarkly provider works with Java 11 and above.

## Feature matrix

This matrix mirrors the [feature matrix of the OpenFeature SDK for Java](https://github.com/open-feature/java-sdk#-features) and describes what this provider supports. Rows which are not supported state whether the limitation comes from the OpenFeature Java SDK or from the provider.

| Status | Feature                         | Notes                                                                                                                                                                                                                     |
|--------|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ✅      | Providers                       | Evaluates boolean, string, integer, double, and object flags through the LaunchDarkly Java SDK.                                                                                                                            |
| ✅      | Targeting                       | The `EvaluationContext` is converted to a LaunchDarkly single or multi-context. See [OpenFeature Specific Considerations](#openfeature-specific-considerations).                                                           |
| ✅      | Multi-provider (experimental)   | Provided by the OpenFeature SDK, which delegates to each provider in turn; no provider support is required.                                                                                                                |
| ✅      | Hooks                           | Hooks are registered on the OpenFeature API and client; the provider requires no additional support and its results are visible to hooks, including [flag metadata](#flag-metadata).                                       |
| ✅      | Tracking                        | `track` sends a LaunchDarkly custom event for the evaluation context, with the tracking event value and remaining details attached.                                                                                        |
| ✅      | Logging                         | The provider logs through the logging configuration of the `LDConfig` it is given.                                                                                                                                         |
| ✅      | Domains                         | Domains bind clients to providers in the OpenFeature SDK; a separate provider instance may be registered per domain.                                                                                                       |
| ✅      | Eventing                        | LaunchDarkly data source status changes are emitted as `PROVIDER_READY`, `PROVIDER_STALE`, and `PROVIDER_ERROR`. Flag changes are emitted as `PROVIDER_CONFIGURATION_CHANGED` with the changed flag key.                    |
| ✅      | Initialization                  | `initialize` reports whether the LaunchDarkly client became ready, and a failure results in the `ERROR` state so that cached or fallback flag data is still evaluated. `Provider(String, LDConfig, Duration)` bounds initialization with a start wait duration; the other constructors wait until the data source becomes valid or permanently fails. |
| ✅      | Shutdown                        | `shutdown` closes the LaunchDarkly client. A closed client cannot be restarted, so a new provider instance is required afterward.                                                                                          |
| ✅      | Transaction Context Propagation | Provided by the OpenFeature SDK, which merges the transaction context into the evaluation context before the provider is called; no provider support is required.                                                          |
| ✅      | Extending                       | This provider is itself an extension of the OpenFeature SDK. The underlying LaunchDarkly client is available through `getLdClient()` for functionality with no OpenFeature equivalent.                                      |
| ✅      | Flag metadata                   | LaunchDarkly evaluation reason details are returned as OpenFeature flag metadata. See [Flag Metadata](#flag-metadata).                                                                                                     |

<sub>Supported: ✅ | Partially supported: ⚠️ | Not supported: ❌</sub>

## Getting started

### Requisites

Your project will need compatible versions of the LaunchDarkly Server-Side SDK for Java as well as the OpenFeature java-sdk.

Example gradle dependencies:
```groovy
implementation group: 'com.launchdarkly', name: 'launchdarkly-java-server-sdk', version: '[7.1.0, 8.0.0)'
implementation 'dev.openfeature:sdk:[1.7.0,2.0.0)'
```

### Installation

First, install the LaunchDarkly OpenFeature provider for the Server-Side SDK for Java as a dependency in your application using your application's dependency manager.

```xml
<dependency>
  <groupId>com.launchdarkly</groupId>
  <artifactId>launchdarkly-openfeature-serverprovider</artifactId>
  <version>0.1.0</version> <!-- use current version number -->
</dependency>
```

```groovy
implementation group: 'com.launchdarkly', name: 'launchdarkly-openfeature-serverprovider', version: '0.1.0'
// Use current version number in place of 0.1.0.
```

### Usage

```java
import dev.openfeature.sdk.OpenFeatureAPI;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.openfeature.serverprovider.Provider;

public class Main {
    public static void main(String[] args) {
        OpenFeatureAPI.getInstance().setProvider(new Provider("my-sdk-key"));
        
        // Refer to OpenFeature documentation for getting a client and performing evaluations.
    }
}

```

Refer to the [SDK reference guide](https://docs.launchdarkly.com/sdk/server-side/java) for instructions on getting started with using the SDK.

For information on using the OpenFeature client please refer to the [OpenFeature Documentation](https://docs.openfeature.dev/docs/reference/concepts/evaluation-api/).

## OpenFeature Specific Considerations

LaunchDarkly evaluates contexts, and it can either evaluate a single-context, or a multi-context. When using OpenFeature both single and multi-contexts must be encoded into a single `EvaluationContext`. This is accomplished by looking for an attribute named `kind` in the `EvaluationContext`.

There are 4 different scenarios related to the `kind`:
1. There is no `kind` attribute. In this case the provider will treat the context as a single context containing a "user" kind.
2. There is a `kind` attribute, and the value of that attribute is "multi". This will indicate to the provider that the context is a multi-context.
3. There is a `kind` attribute, and the value of that attribute is a string other than "multi". This will indicate to the provider a single context of the kind specified.
4. There is a `kind` attribute, and the attribute is not a string. In this case the value of the attribute will be discarded, and the context will be treated as a "user". An error message will be logged.

The `kind` attribute should be a string containing only contain ASCII letters, numbers, `.`, `_` or `-`.

The OpenFeature specification allows for an optional targeting key, but LaunchDarkly requires a key for evaluation. A targeting key must be specified for each context being evaluated. It may be specified using either `targetingKey`, as it is in the OpenFeature specification, or `key`, which is the typical LaunchDarkly identifier for the targeting key. If a `targetingKey` and a `key` are specified, then the `targetingKey` will take precedence.

There are several other attributes which have special functionality within a single or multi-context.
- A key of `privateAttributes`. Must be an array of string values. [Equivalent to the 'privateAttributes' builder method in the SDK.](https://launchdarkly.github.io/java-server-sdk/com/launchdarkly/sdk/ContextBuilder.html#privateAttributes(java.lang.String...))
- A key of `anonymous`. Must be a boolean value.  [Equivalent to the 'anonymous' builder method in the SDK.](https://launchdarkly.github.io/java-server-sdk/com/launchdarkly/sdk/ContextBuilder.html#anonymous(boolean))
- A key of `name`. Must be a string. [Equivalent to the 'name' builder method in the SDK.](https://launchdarkly.github.io/java-server-sdk/com/launchdarkly/sdk/ContextBuilder.html#name(java.lang.String))

### Initialization and Shutdown

The LaunchDarkly provider supports Initialization and Shutdown using the OpenFeature API. Initialization starts as soon as the provider is constructed: the underlying LaunchDarkly SDK is created in the provider's constructor, and it blocks there for up to its configured start wait time.

The `Provider(String, LDConfig, Duration)` constructor sets that start wait and bounds the whole of initialization with it. The provider's `initialize` does not wait a second time; it reports the outcome of that single wait, and fails if the client did not become ready in time. A non-zero duration is strongly recommended. A zero duration means the provider applies no deadline at all: the constructor does not block, and `initialize` waits until the data source becomes valid or fails permanently, which may be indefinitely if neither happens. The other constructors leave the start wait of the given `LDConfig` untouched and also wait indefinitely.

How initialization surfaces depends on how the provider is registered with the OpenFeature API:

- `setProviderAndWait` runs initialization on the calling thread, so the call blocks until the provider is ready or has permanently failed, and throws if it failed. With a zero duration this call can block indefinitely.
- `setProvider` runs initialization on a background thread and returns immediately. A failure is reported as a `PROVIDER_ERROR` event rather than thrown, and evaluations made before the provider is ready return their default value with the `PROVIDER_NOT_READY` error code.

It the provider has been shutdown, because the OpenFeature API has been shutdown, or because the provider was no longer in use by the OpenFeature API, then the underlying LaunchDarkly SDK will be closed.
This is an important consideration if you are using the `getLdClient` method of the provider to access the underlying SDK instance.

### Flag Metadata

Evaluation details include flag metadata containing the parts of the LaunchDarkly evaluation reason that do not fit
into the OpenFeature reason and error code. Each entry is only present when it applies to the evaluation.

| Key | Type | Description |
|-----|------|-------------|
| `variationIndex` | integer | The index of the variation that was returned. Absent when the SDK returned the default value. |
| `inExperiment` | boolean | Only present, and always `true`, when the evaluation was part of an experiment. |
| `ruleIndex` | integer | The index of the targeting rule that matched. Only present for a `RULE_MATCH` reason. |
| `ruleId` | string | The identifier of the targeting rule that matched. Only present for a `RULE_MATCH` reason. |
| `prerequisiteKey` | string | The key of the prerequisite flag that failed. Only present for a `PREREQUISITE_FAILED` reason. |
| `bigSegmentsStatus` | string | The status of the Big Segments store, when the evaluation used Big Segments. |

### Examples

#### A single user context

```java
    EvaluationContext context = new ImmutableContext("the-key");
```

#### A single context of kind "organization"

```java
    EvaluationContext context = new ImmutableContext("org-key", new HashMap(){{
        put("kind", new Value("organization"));
        }});
```

#### A multi-context containing a "user" and an "organization"

```java
EvaluationContext context = new ImmutableContext(new HashMap() {{
    put("kind", new Value("multi"));
    put("organization", new Value(new ImmutableStructure(new HashMap(){{
        put("name", new Value("the-org-name"));
        put("targetingKey", new Value("my-org-key"));
        put("myCustomAttribute", new Value("myAttributeValue"));
    }})));
    put("user", new Value(new ImmutableStructure(new HashMap(){{
        put("key", new Value("my-user-key"));
        put("anonymous", new Value(true));
    }})));
}});
```

#### Setting private attributes in a single context

```java
    EvaluationContext context = new ImmutableContext("org-key", new HashMap(){{
        put("kind", new Value("organization"));
        put("myCustomAttribute", new Value("myAttributeValue"));
        put("privateAttributes", new Value(new ArrayList<Value>() {{
            add(new Value("myCustomAttribute"));
        }}));
    }});
```

#### Setting private attributes in a multi-context

```java
EvaluationContext evaluationContext = new ImmutableContext(new HashMap() {{
    put("kind", new Value("multi"));
    put("organization", new Value(new ImmutableStructure(new HashMap(){{
        put("name", new Value("the-org-name"));
        put("targetingKey", new Value("my-org-key"));
        // This will ONLY apply to the "organization" attributes.
        put("privateAttributes", new Value(new ArrayList<Value>() {{
            add(new Value("myCustomAttribute"));
        }}));
        // This attribute will be private.
        put("myCustomAttribute", new Value("myAttributeValue"));
    }})));
    put("user", new Value(new ImmutableStructure(new HashMap(){{
        put("key", new Value("my-user-key"));
        put("anonymous", new Value(true));
        // This attribute will not be private.
        put("myCustomAttribute", new Value("myAttributeValue"));
    }})));
}});
```

## Learn more

Read our [documentation](http://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for this SDK](https://docs.launchdarkly.com/sdk/server-side/dotnet).

The authoritative description of all properties and methods is in the [java documentation](https://launchdarkly.github.io/java-server-sdk/).

## Contributing

We encourage pull requests and other contributions from the community. Check out our [contributing guidelines](CONTRIBUTING.md) for instructions on how to contribute to this SDK.

## About LaunchDarkly

* LaunchDarkly is a continuous delivery platform that provides feature flags as a service and allows developers to iterate quickly and safely. We allow you to easily flag your features and manage them from the LaunchDarkly dashboard.  With LaunchDarkly, you can:
    * Roll out a new feature to a subset of your users (like a group of users who opt-in to a beta tester group), gathering feedback and bug reports from real-world use cases.
    * Gradually roll out a feature to an increasing percentage of users, and track the effect that the feature has on key metrics (for instance, how likely is a user to complete a purchase if they have feature A versus feature B?).
    * Turn off a feature that you realize is causing performance problems in production, without needing to re-deploy, or even restart the application with a changed configuration file.
    * Grant access to certain features based on user attributes, like payment plan (eg: users on the ‘gold’ plan get access to more features than users in the ‘silver’ plan). Disable parts of your application to facilitate maintenance, without taking everything offline.
* LaunchDarkly provides feature flag SDKs for a wide variety of languages and technologies. Check out [our documentation](https://docs.launchdarkly.com/sdk) for a complete list.
* Explore LaunchDarkly
    * [launchdarkly.com](https://www.launchdarkly.com/ "LaunchDarkly Main Website") for more information
    * [docs.launchdarkly.com](https://docs.launchdarkly.com/  "LaunchDarkly Documentation") for our documentation and SDK reference guides
    * [apidocs.launchdarkly.com](https://apidocs.launchdarkly.com/  "LaunchDarkly API Documentation") for our API documentation
    * [blog.launchdarkly.com](https://blog.launchdarkly.com/  "LaunchDarkly Blog Documentation") for the latest product updates
