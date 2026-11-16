# Jobstein

Self-hosted, local-first AI-assisted job search automation with user-owned Google infrastructure.

## Development status

Initial CLI foundation; live Google and xAI provisioning is not yet implemented. The operational job-search runtime is deferred.

## Build

Requires JDK 21 and Gradle 8.10 or later.

```sh
gradle test
gradle installDist
./build/install/jobstein/bin/jobstein --help
```

See [architecture](docs/architecture.md) for the administrative CLI scope and security constraints.

Never commit credentials, resumes, personal application data, or environment-specific configuration.
