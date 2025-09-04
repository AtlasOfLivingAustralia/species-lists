# Species lists


| Build | Status |
|---|---|
| lists-frontend-develop |[![AWS CodePipeline Build Status](https://img.shields.io/badge/CodePipeline-passing-brightgreen?logo=amazon-aws&logoColor=white)](https://console.aws.amazon.com/codepipeline/home?region=ap-southeast-2#/view/lists-frontend-develop) |
| lists-backend-develop | [![AWS CodePipeline Build Status](https://img.shields.io/badge/CodePipeline-passing-brightgreen?logo=amazon-aws&logoColor=white)](https://console.aws.amazon.com/codepipeline/home?region=ap-southeast-2#/view/lists-backend-develop) |
| lists-frontend-testing |[![AWS CodePipeline Build Status](https://img.shields.io/badge/CodePipeline-passing-brightgreen?logo=amazon-aws&logoColor=white)](https://console.aws.amazon.com/codepipeline/home?region=ap-southeast-2#/view/lists-frontend-testing) |
| lists-backend-testing | [![AWS CodePipeline Build Status](https://img.shields.io/badge/CodePipeline-passing-brightgreen?logo=amazon-aws&logoColor=white)](https://console.aws.amazon.com/codepipeline/home?region=ap-southeast-2#/view/lists-backend-testing) |


System for managing species lists in the ALA.

This consists of two components:

* [lists-ui](lists-ui) - Web application for browsing and managing species lists
* [lists-service](lists-service) - Spring boot REST and GraphQL web services for accessing, modifying species lists

For detailed information about this project, see the DeepWiki page: [Species Lists Management System Overview](https://deepwiki.com/AtlasOfLivingAustralia/species-lists).

## Architecture

<img src="https://github.com/AtlasOfLivingAustralia/species-lists/assets/444897/7d9f5a2b-39ca-493f-aca3-7bc698aae0d8" width="450">
