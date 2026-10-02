# McGee SMP

Paper plugin for the McGee SMP server, replacing the Bedrock add-on.

## Getting the plugin

Every push builds automatically. Download the newest `McGeeSMP-*.jar` from
[Releases](../../releases) and drop it in your server's `plugins/` folder.

## Server stack

Paper + Geyser + Floodgate, tunnelled with playit.gg so Java, Bedrock and
console players can all join.

## Building locally

Needs JDK 21.

    mvn package

The Paper API version is a property, so it can be overridden:

    mvn package -Dpaper.version=26.1-R0.1-SNAPSHOT
