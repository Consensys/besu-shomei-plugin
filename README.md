# besu-shomei-plugin

besu plugin supporting shomei state management service for linea

## Building
to build the plugin, run `./gradlew plugin`.  The resulting plugin jar will be in `build/libs`

## Installing
to install the plugin to an existing besu install, copy the jar file from `build/libs` to the `plugins` directory of your besu node.  If you do not already have a plugins directory, create one in your besu installation directory, e.g.:
``` 
mkdir -p /opt/besu/plugins
cp build/libs/besu-shomei-plugin-1.0.0.jar /opt/besu/plugins
```

# Usage 

## Running Besu with the shomei plugin
Besu will autodetect the presence of the plugin and load and configure it at startup.  The plugin extends besu in the following ways:   
* adds RPC endpoints `shomei_getTrieLog` and `shomei_getTrieLogsByRange`.  These RPC endpoints expose trielogs in order for shomei instances to sync to the chain.  The plugin registers an additional RPC namespace `SHOMEI` that needs to be enabled via config, see the example below.
* configures a shomei friendly trielog serializer and deserializer.  It is important that besu is synced FRESH with this plugin enabled, as the default trielog serializer in besu does not store enough information in the trielogs to support linea or shomei.
* configures a trielog observer that 'ships' new block trielogs to shomei as they are processed in besu.  This is how shomei keeps up with the chain head once synced.

## Extra besu CLI args for the shomei plugin:
Currently there are only two configuartion parameters for the plugin, the target host and running shomei.
* `--plugin-shomei-http-host=<host>` specifies the RPC host where shomei is running, defaults to 127.0.0.1 if not specified
* `--plugin-shomei-http-port=<port>` specifies the RPC host port where shomei RPC is running, defaults to 8888

## Example of running linea-besu with the plugin, from docker
The simplest way to use the plugin is with the official docker image, mounting the plugin build folder as a volume directly into the container:

```
docker run -it -v `pwd`/build/libs:/opt/besu/plugins \
consensys/linea-besu:linea-delivery-1 \
--network=linea \
--rpc-http-enabled=true \
--rpc-http-host="0.0.0.0" \
--rpc-http-api=ADMIN,DEBUG,NET,ETH,WEB3,PLUGINS,SHOMEI \
--rpc-http-cors-origins="*" \
--plugin-shomei-http-host=192.168.88.27 \
--plugin-shomei-http-port=8888
```



