<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->


# Building and Running

@@toc


## Starting a Triplestore

Start a triplestore (GraphDB-Free or GraphDB-SE). Download distribution from [Ontotext](http://ontotext.com).
Unzip distribution to a place of your choosing and run the following:

```
$ cd /to/unziped/location
$ ./bin/graphdb -Dgraphdb.license.file=/path/to/GRAPHDB_SE.license
```

Here we use GraphDB-SE which needs to be licensed separately.

Then in another terminal, initialize the data repository and load some test data:

```
$ cd KNORA_PROJECT_DIRECTORY/webapi/scripts
$ ./graphdb-se-local-init-knora-test.sh
```

Then in another terminal, start the [Redis Server](https://redis.io):

```bash
$ redis-server
```

Then go back to the webapi root directory and use SBT to start the API
server:

```
$ cd KNORA_PROJECT_DIRECTORY
$ sbt
> webapi / compile
> webapi / reStart
```

To shut down Knora:

```
> webapi / reStop
```

## Running the automated tests

Make sure you've started the triplestore as shown above.

Then in another terminal, initialise the repository used for automated
testing:

```
$ cd KNORA_PROJECT_DIRECTORY/webapi/scripts
$ ./graphdb-se-local-init-knora-test-unit.sh
```

Run the automated tests from sbt:

```
> webapi / graphdb:test
```

## Load Testing on Mac OS X

To test Knora with many concurrent connections on Mac OS
X, you will need to adjust some kernel parameters to allow more open
connections, to recycle ephemeral ports more quickly, and to use a wider
range of ephemeral port numbers. The script
`webapi/scripts/macOS-kernel-test-config.sh` will do this.

## Continuous Integration

For continuous integration testing, we use Github CI Actions. Every commit
pushed to the git repository or every pull request, triggers the build.
Additionally, in Github there is a small checkmark beside every commit,
signaling the status of the build (successful, unsuccessful, ongoing).

The build that is executed on Github CI Actions is defined in
`.github/workflows/main.yml`, and looks like this:

@@snip[main.yml](../../../../../.github/workflows/main.yml) { }

## Webapi Server Startup-Flags

The Webapi-Server can be started with a number of flags. These flags can
be supplied either to the `reStart` or the `run` command in sbt, e.g.,:

```
$ sbt
> webapi / reStart flag
```

or

```
$ sbt
> webapi / run flag
```

### `loadDemoData` - Flag

When the webapi-server is started with the `loadDemoData` flag, then at
startup, the data which is configured in `application.conf` under the
`app.triplestore.rdf-data` key is loaded into the triplestore, and any
data in the triplestore is removed beforehand.

Usage:

```
$ sbt
> webapi / reStart loadDemoData
```
### `allowReloadOverHTTP` - Flag

When the webapi.server is started with the `allowReloadOverHTTP` flag (`reStart -r`),
then the `v1/store/ResetTriplestoreContent` route is activated. This
route accepts a `POST` request, with a JSON payload consisting of the
following example content:

```
[
  {
    "path": "../knora-ontologies/knora-base.ttl",
    "name": "http://www.knora.org/ontology/knora-base"
  },
  {
    "path": "../knora-ontologies/salsah-gui.ttl",
    "name": "http://www.knora.org/ontology/salsah-gui"
  },
  {
    "path": "_test_data/ontologies/incunabula-onto.ttl",
    "name": "http://www.knora.org/ontology/0803/incunabula"
  },
  {
    "path": "_test_data/all_data/incunabula-data.ttl",
    "name": "http://www.knora.org/data/incunabula"
  }
]
```

This content corresponds to the payload sent with the
`ResetTriplestoreContent` message, defined inside the
`org.knora.webapi.messages.v1.store.triplestoremessages` package. The
`path` being the relative path to the `ttl` file which will be loaded
into a named graph by the name of `name`.

Usage:

```
$ sbt
> webapi / reStart allowReloadOverHTTP
```