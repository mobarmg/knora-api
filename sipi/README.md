# Knora Specific Sipi Scripts and Configurations

This directory holds Knora specific scripts and configurations for [Sipi](https://github.com/dhlab-basel/Sipi).

## Usage

### With Docker

To start Sipi using Docker (with config for integration tests), run from inside this folder:

```
$ make up
$ make down // for cleanup
```

### Using a Locally-Compiled Sipi

Type the following in this directory, assuming that the Sipi source tree is in
`../../Sipi` and the Sipi binary is in the build folder under `../../Sipi/build/sipi`:

```
$ ../../Sipi/build/sipi --config config/sipi.knora-local-config.lua
```

### Starting Sipi with `no-auth` configuration

The `no-auth` configuration omits authorization callbacks to Knora, thus allowing to serve images for
which there is no corresponding resource inside Knora. 

```
$ export DOCKERHOST=LOCAL_IP_ADDRESS
$ docker image rm --force dhlabbasel/sipi:develop // deletes cached image and needs only to be used when newer image is available on dockerhub
$ docker-compose up sipi-no-auth
$ docker-compose down // for cleanup
```

where `LOCAL_IP_ADDRESS` is the IP of the host running the `Knora-Service`.