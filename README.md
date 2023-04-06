# LaunchDarkly OpenFeature provider for the Server-Side SDK for Java

This provider allows for using LaunchDarkly with the OpenFeature SDK for Java.

This provider is designed primarily for use in multi-user systems such as web servers and applications. It follows the server-side LaunchDarkly model for multi-user contexts. It is not intended for use in desktop and embedded systems applications.

This provider is a beta version and should not be considered ready for production use while this message is visible.

# LaunchDarkly overview

[LaunchDarkly](https://www.launchdarkly.com) is a feature management platform that serves over 100 billion feature flags daily to help teams build better software, faster. [Get started](https://docs.launchdarkly.com/home/getting-started) using LaunchDarkly today!

[![Twitter Follow](https://img.shields.io/twitter/follow/launchdarkly.svg?style=social&label=Follow&maxAge=2592000)](https://twitter.com/intent/follow?screen_name=launchdarkly)

## Supported Java versions

This version of the LaunchDarkly provider works with Java 11 and above.

## Getting started

### Requisites

Your project will need compatible versions of the LaunchDarkly Server-Side SDK for Java as well as the OpenFeature java-sdk.

Example gradle dependencies:
```groovy
implementation group: 'com.launchdarkly', name: 'launchdarkly-java-server-sdk', version: '[6.0.0, 7.0.0)'
implementation 'dev.openfeature:sdk:[1.2.0,2.0.0)'
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
        LDClient ldClient = new LDClient("my-sdk-key");
        OpenFeatureAPI.getInstance().setProvider(new Provider(ldClient));
        
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

Check out our [documentation](http://docs.launchdarkly.com) for in-depth instructions on configuring and using LaunchDarkly. You can also head straight to the [complete reference guide for this SDK](https://docs.launchdarkly.com/sdk/server-side/dotnet).

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
