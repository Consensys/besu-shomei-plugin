# Changelog

## version 0.5.0
- add optional zktracing on block import [#32](https://github.com/Consensys/besu-shomei-plugin/pull/32)
- use vanilla besu for build and runtime [#31](https://github.com/Consensys/besu-shomei-plugin/pull/31)

## version 0.4.0
- Update to support besu 24.12.x and linea-besu delivery 39.1 +

updates plugin-api
updates junit deps
bumps the revision to 0.4.0

## version 0.3.2
- minor bump for gradle and besu version

## version 0.3.1
Bump besu dependency to 24.6.0
- minor trielog factory interface change
- bump java to 21
- bump gradle to 8.7.0
- add hyperledger artifactory repo for besu maven deps

## version 0.3.0
this release version uses the final build of 23.10.4-SNAPSHOT

- use new trielog plugin api, address self destruct issue

## version v0.2.1.

minor fix relese that prevents class name collisions between shomei and besu-shomei-plugin for net.consensys.shomei.TrieLogLayer

## 0.2.0
- fix cli parameter parsing regression in 0.1.0

## 0.1.0
- Initial build of besu-shomei-plugin
- uses 23.4.1-SNAPSHOT as besu version
