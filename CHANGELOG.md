# Changelog

## unreleased

## version 0.7.7
* revert trielog metadata changes for trielogs until a new shomei release is tested [#60](https://github.com/Consensys/besu-shomei-plugin/pull/60) 

## version 0.7.6
* update tracer to rc18, besu to 25.11.0-RC1-linea2 [#59](https://github.com/Consensys/besu-shomei-plugin/pull/59)
* safe tracer for the besu-shomei-plugin  by matkt [#58](https://github.com/Consensys/besu-shomei-plugin/pull/58)
* Add optional decoration metadata to shomei trielogs  by garyschulte [#57](https://github.com/Consensys/besu-shomei-plugin/pull/57)
* shouldn't log, but throw exception if no block found  by letypequividelespoubelles [#56](https://github.com/Consensys/besu-shomei-plugin/pull/56)


## version 0.5.1 - 0.7.5
* chore: update tracer version to rc11  by letypequividelespoubelles [#55](https://github.com/Consensys/besu-shomei-plugin/pull/55)
* Pass the right hardforkId when creating the ZkTracer  by fab-10 [#54](https://github.com/Consensys/besu-shomei-plugin/pull/54)
* Feature/update dependencies  by matkt [#53](https://github.com/Consensys/besu-shomei-plugin/pull/53)
* Bugfix/false positive filtering  by garyschulte [#52](https://github.com/Consensys/besu-shomei-plugin/pull/52)
* bugfix: Return no-op tracer if zktracing is disabled  by garyschulte [#51](https://github.com/Consensys/besu-shomei-plugin/pull/51)
* Update Besu to 25.7.0-linea1 and arithmetization to beta-v3.1-rc6  by fab-10 [#49](https://github.com/Consensys/besu-shomei-plugin/pull/49)
* Update Besu to 25.6.0-linea1 and switch to new Maven coordinates for …  by fab-10 [#48](https://github.com/Consensys/besu-shomei-plugin/pull/48)
* Remove transitive dependencies for vertx-web-client  by garyschulte [#47](https://github.com/Consensys/besu-shomei-plugin/pull/47)
* Use besu bom, fix vertx-web-client dep  by garyschulte [#46](https://github.com/Consensys/besu-shomei-plugin/pull/46)
* Do not spam logs with false negatives on slot filtering  by garyschulte [#45](https://github.com/Consensys/besu-shomei-plugin/pull/45)
* Revert "Force the Jar MANIFEST to expose Tracer version data"  by fluentcrafter [#44](https://github.com/Consensys/besu-shomei-plugin/pull/44)
* Exclude besu dependencies from jar  by fluentcrafter [#43](https://github.com/Consensys/besu-shomei-plugin/pull/43)
* Force the Jar MANIFEST to expose Tracer version data  by fab-10 [#42](https://github.com/Consensys/besu-shomei-plugin/pull/42)
* bugfix dns spi runtime error stemming from slf4j and log4j  by garyschulte [#41](https://github.com/Consensys/besu-shomei-plugin/pull/41)
* fix test-support artifact naming  by garyschulte [#40](https://github.com/Consensys/besu-shomei-plugin/pull/40)
* add release artifact, bump rev  by garyschulte [#39](https://github.com/Consensys/besu-shomei-plugin/pull/39)
* add test-support artifact  by garyschulte [#38](https://github.com/Consensys/besu-shomei-plugin/pull/38)
* Add case to reproduce hub accumulator diffs on contract deploy revert  by garyschulte [#37](https://github.com/Consensys/besu-shomei-plugin/pull/37)
* Add filtering comparison mode  by garyschulte [#36](https://github.com/Consensys/besu-shomei-plugin/pull/36)
* Lookup value from worldstate when decorating accumulator maps  by garyschulte [#35](https://github.com/Consensys/besu-shomei-plugin/pull/35)

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
