# github-reports

A tool that collects data from GitHub via API and builds CSV reports.

## Contributions Report

Contribution means closed Pull-Request (not a commit!).

The tool creates a `contributions.csv` report which calculates number of contributions grouped by:
- organizations
- repositories
- authors

## Codeowners Report

The tool creates a `codeowners.csv` report which collects codeowners of each repository. The codeowners are read from `CODEOWNERS` file in the default branch of each repository.

## Usage

Issue a GitHub access token with such permissions: `read:org, repo, user:email`.

Prepare `env.properties` file before running:
```shell
echo "GITHUB_TOKEN=_CHANGEME_
ORGANIZATIONS=
SINCE=2023-01-01
UNTIL=2023-12-31
OUTPUT_DIR=" > src/main/resources/env.properties
```

Run using `./gradlew run`.