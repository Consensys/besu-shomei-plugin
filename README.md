# Besu-Shomei Plugin

The Besu-Shomei Plugin is a module that supports the Shomei state management service for Linea on Besu.

# Quick Start

Use the Gradle wrapper to build the plugin:
`./gradlew build`

Then, run the linea-besu docker container with the plugin mounted as a volume:
```
docker run -it -v `pwd`/build/libs:/opt/besu/plugins \
consensys/linea-besu:linea-delivery-1 \
--network=linea \
--rpc-http-enabled=true \
--rpc-http-host="0.0.0.0" \
--rpc-http-api=ADMIN,DEBUG,NET,ETH,WEB3,PLUGINS,SHOMEI \
--rpc-http-cors-origins="*" \
--plugin-shomei-http-host=<SHOMEI_HOST_IP> \
--plugin-shomei-http-port=<SHOMEI_HOST_PORT>>
```



# Download
The latest plugin release version is available on the [releases](https://github.com/ConsenSys/besu-shomei-plugin/releases) page.

To use a release version, follow these steps:

1. Download the plugin jar.
2. Create a plugins/ folder in your Besu installation directory.
3. Copy the plugin jar into the plugins/ directory.
4. Start Besu with the Shomei-specific configurations listed below.

# Features
The plugin enhances Besu by:

* Adding RPC endpoints (`shomei_getTrieLog` and `shomei_getTrieLogsByRange`) for Shomei instances to sync with the chain.
* Configuring a Shomei-friendly trielog serializer and deserializer. **Note**: __Besu must be synced fresh with this plugin enabled, as the default trielog serializer in Besu does not store enough information in the trielogs to support Linea or Shomei.__
* Configuring a trielog observer that sends new block trielogs to Shomei as they're processed in Besu. This allows Shomei to keep up with the chain head once synced.


# Configuration
Besu will autodetect the presence of the plugin and load and configure it at startup.  However some configuration is to ensure besu and shomei can communicate.

## Enable SHOMEI RPC-HTTP-API Endpoint
The plugin registers an additional RPC namespace `SHOMEI` for Shomei to query Besu for trielogs. Enable this namespace in Besu's configuration through the [--rpc-http-api config](https://besu.hyperledger.org/en/stable/public-networks/reference/cli/options/#rpc-http-api) option.  

## Configure Shomei Host and Port

Specify the host and port where Shomei is running:

* `--plugin-shomei-http-host=<host>`: Specifies the RPC host where Shomei is running. Default is 127.0.0.1.
* `--plugin-shomei-http-port=<port>`: Specifies the RPC host port for Shomei's RPC. Default is 8888.

### Docker Example
The simplest way to use the plugin is with the [linea-besu](https://hub.docker.com/r/consensys/linea-besu) docker image. Ensure you specify the path to the folder containing the plugin, and a Shomei host and port that Docker can access. For example:

```
docker run -it -v <FOLDER_CONTAINING_PLUGIN_JAR>:/opt/besu/plugins \
consensys/linea-besu:linea-delivery-1 \
--network=linea \
--rpc-http-enabled=true \
--rpc-http-host="0.0.0.0" \
--rpc-http-api=ADMIN,DEBUG,NET,ETH,WEB3,PLUGINS,SHOMEI \
--rpc-http-cors-origins="*" \
--plugin-shomei-http-host=<SHOMEI_HOST> \
--plugin-shomei-http-port=<SHOMEI_PORT>
```


# Development

## Building from Source
To build the plugin from source and run tests, use the following command:

``./gradlew build``

The resulting JAR file will be located in the build/libs directory.

## Installation from Source
After building from source, you can install the plugin on an existing Besu installation. Here's how to do that:

1. Create a plugins directory in your Besu installation directory, if one doesn't already exist:
`mkdir -p /opt/besu/plugins`

2. Copy the built JAR file into the plugins directory: 
mkdir -p /opt/besu/plugins
`cp build/libs/besu-shomei-plugin-0.1.0.jar /opt/besu/plugins`


## Release Process
Here are the steps for releasing a new version of the plugin:
1. Create a tag with the release version number in the format vX.Y.Z (e.g., v.0.2.0).
2. Push the tag to the repository.
3. GitHub Actions will automatically create a draft release for the release tag.
4. Once the release workflow completes, update the release notes, uncheck "Draft", and publish the release.

**Note**: Release tags (of the form v*) are protected and can only be pushed by organization and/or repository owners. 
